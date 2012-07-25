// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Replicated cache entry.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@SuppressWarnings({"TooBroadScope"}) public class GridDhtCacheEntry<K, V> extends GridDistributedCacheEntry<K, V> {
    /** Gets node value from reader ID. */
    private static final GridClosure<ReaderId, UUID> R2N = new C1<ReaderId, UUID>() {
        @Override public UUID apply(ReaderId e) {
            return e.nodeId();
        }
    };

    /** Reader clients. */
    @GridToStringInclude
    private volatile List<ReaderId<K, V>> readers = Collections.emptyList();

    /** Local partition. */
    private final GridDhtLocalPartition<K, V> locPart;

    /**
     * @param ctx Cache context.
     * @param topVer Topology version at the time of creation (if negative, then latest topology is assumed).
     * @param key Cache key.
     * @param hash Key hash value.
     * @param val Entry value.
     * @param next Next entry in the linked list.
     * @param ttl Time to live.
     */
    public GridDhtCacheEntry(GridCacheContext<K, V> ctx, long topVer, K key, int hash, V val,
        GridCacheMapEntry<K, V> next,
        long ttl) {
        super(ctx, key, hash, val, next, ttl);

        // Record this entry with partition.
        locPart = ctx.dht().topology().onAdded(topVer, this);
    }

    /** {@inheritDoc} */
    @Override public boolean isDht() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean partitionValid() {
        return locPart.valid();
    }

    /** {@inheritDoc} */
    @Override public boolean markObsolete(GridCacheVersion ver) {
        boolean rmv = super.markObsolete(ver);

        // Remove this entry from partition mapping.
        if (rmv)
            cctx.dht().topology().onRemoved(this);

        return rmv;
    }

    /**
     * @param nearVer Near version.
     * @param rmv If {@code true}, then add to removed list if not found.
     * @return Local candidate by near version.
     * @throws GridCacheEntryRemovedException If removed.
     */
    @Nullable public GridCacheMvccCandidate<K> localCandidateByNearVersion(GridCacheVersion nearVer, boolean rmv)
        throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            for (GridCacheMvccCandidate<K> c : mvcc.localCandidatesNoCopy(false)) {
                GridCacheVersion ver = c.otherVersion();

                if (ver != null && ver.equals(nearVer))
                    return c;
            }

            if (rmv)
                addRemoved(nearVer);

            return null;
        }
        finally {
            unlock();
        }
    }

    /**
     * Add local candidate.
     *
     * @param nearNodeId Near node ID.
     * @param nearVer Near version.
     * @param topVer Topology version.
     * @param threadId Owning thread ID.
     * @param ver Lock version.
     * @param timeout Timeout to acquire lock.
     * @param reenter Reentry flag.
     * @param ec Eventually consistent flag.
     * @param tx Tx flag.
     * @param implicitSingle Implicit flag.
     * @return New candidate.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     * @throws GridDistributedLockCancelledException If lock was cancelled.
     */
    @Nullable public GridCacheMvccCandidate<K> addDhtLocal(UUID nearNodeId, GridCacheVersion nearVer, long topVer,
        long threadId, GridCacheVersion ver, long timeout, boolean reenter, boolean ec, boolean tx,
        boolean implicitSingle) throws GridCacheEntryRemovedException, GridDistributedLockCancelledException {
        GridCacheMvccCandidate<K> cand;
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        lock();

        try {
            // Check removed locks prior to obsolete flag.
            checkRemoved(ver);
            checkRemoved(nearVer);

            checkObsolete();

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            cand = mvcc.addLocal(this, nearNodeId, nearVer, threadId, ver, timeout, reenter, ec, tx, implicitSingle,
                /*dht-local*/true);

            if (cand == null)
                return null;

            cand.topologyVersion(topVer);

            owner = mvcc.anyOwner();

            boolean emptyAfter = mvcc.isEmpty();

            checkCallbacks(emptyBefore, emptyAfter);

            val = rawGet();
        }
        finally {
            unlock();
        }

        // Don't link reentries.
        if (cand != null && !cand.reentry())
            // Link with other candidates in the same thread.
            cctx.mvcc().addNext(cand);

        checkOwnerChanged(prev, owner, val);

        return cand;
    }

    /** {@inheritDoc} */
    @Override public boolean tmLock(GridCacheTxEx<K, V> tx, long timeout)
        throws GridCacheEntryRemovedException, GridDistributedLockCancelledException {
        if (tx.local()) {
            GridDhtTxLocal<K, V> dhtTx = (GridDhtTxLocal<K, V>)tx;

            // Null is returned if timeout is negative and there is other lock owner.
            return addDhtLocal(dhtTx.nearNodeId(), dhtTx.nearXidVersion(), tx.topologyVersion(), tx.threadId(),
                tx.xidVersion(), timeout, /*reenter*/false, tx.ec(), /*tx*/true, tx.implicitSingle()) != null;
        }

        try {
            addRemote(tx.nodeId(), tx.otherNodeId(), tx.threadId(), tx.xidVersion(), tx.timeout(), tx.ec(), /*tx*/true,
                tx.implicit());

            return true;
        }
        catch (GridDistributedLockCancelledException ignored) {
            if (log.isDebugEnabled())
                log.debug("Attempted to enter tx lock for cancelled ID (will ignore): " + tx);

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> removeLock() {
        GridCacheMvccCandidate<K> ret = super.removeLock();

        locPart.onUnlock();

        return ret;
    }

    /** {@inheritDoc} */
    @Override public boolean removeLock(GridCacheVersion ver) throws GridCacheEntryRemovedException {
        boolean ret = super.removeLock(ver);

        locPart.onUnlock();

        return ret;
    }

    /**
     * @return Tuple with version and value of this entry, or {@code null} if entry is new.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
    @Nullable public GridTuple3<GridCacheVersion, V, byte[]> versionedValue() throws GridCacheEntryRemovedException {
        lock();

        try {
            return isNew() ? null : F.t(ver, val, valBytes);
        }
        finally {
            unlock();
        }
    }

    /**
     * @return Readers.
     * @throws GridCacheEntryRemovedException If removed.
     */
    public Collection<UUID> readers() throws GridCacheEntryRemovedException {
        return F.viewReadOnly(checkReaders(), R2N);
    }

    /**
     * @param nodeId Node ID.
     * @return reader ID.
     */
    @Nullable private ReaderId<K, V> readerId(UUID nodeId) {
        for (ReaderId<K, V> reader : readers)
            if (reader.nodeId().equals(nodeId))
                return reader;

        return null;
    }

    /**
     * @param nodeId Reader to add.
     * @param msgId Message ID.
     * @return Future for all relevant transactions that were active at the time of adding reader,
     *      or {@code null} if reader was added
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridFuture<Boolean> addReader(UUID nodeId, long msgId) throws GridCacheEntryRemovedException {
        // Don't add local node as reader.
        if (cctx.nodeId().equals(nodeId))
            return null;

        GridNode node = cctx.discovery().node(nodeId);

        if (node == null) {
            if (log.isDebugEnabled())
                log.debug("Ignoring near reader because node left the grid: " + nodeId);

            return null;
        }

        // If remote node has no near cache, don't add it.
        if (!U.hasNearCache(node, cctx.dht().near().name()) && !(key instanceof GridCacheInternal)) {
            if (log.isDebugEnabled())
                log.debug("Ignoring near reader because near cache is disabled: " + nodeId);

            return null;
        }

        // If remote node is (primary?) or back up, don't add it as a reader.
        if (U.nodeIds(cctx.affinity(partition(), CU.allNodes(cctx))).contains(nodeId))
            return null;

        boolean ret = false;

        GridCacheMultiTxFuture<K, V> txFut;

        Collection<GridCacheMvccCandidate<K>> cands = null;

        ReaderId<K, V> reader;

        lock();

        try {
            checkObsolete();

            reader = readerId(nodeId);

            if (reader == null) {
                reader = new ReaderId<K, V>(nodeId, msgId);

                readers = new LinkedList<ReaderId<K, V>>(readers);

                readers.add(reader);

                // Seal.
                readers = Collections.unmodifiableList(readers);

                txFut = reader.getOrCreateTxFuture(cctx);

                cands = localCandidates();

                ret = true;
            }
            else {
                txFut = reader.txFuture();

                long id = reader.messageId();

                if (id < msgId)
                    reader.messageId(msgId);
            }
        }
        finally {
            unlock();
        }

        if (ret) {
            assert txFut != null;

            if (!F.isEmpty(cands)) {
                for (GridCacheMvccCandidate<K> c : cands) {
                    GridCacheTxEx<K, V> tx = cctx.tm().<GridCacheTxEx<K, V>>tx(c.version());

                    if (tx != null) {
                        assert tx.local();

                        txFut.addTx(tx);
                    }
                }
            }

            txFut.init();

            if (!txFut.isDone()) {
                final ReaderId<K, V> reader0 = reader;

                txFut.listenAsync(new CI1<GridFuture<?>>() {
                    @Override public void apply(GridFuture<?> f) {
                        lock();

                        try {
                            // Release memory.
                            reader0.resetTxFuture();
                        }
                        finally {
                            unlock();
                        }
                    }
                });
            }
            else {
                lock();

                try {
                    // Release memory.
                    reader.resetTxFuture();
                }
                finally {
                    unlock();
                }

                txFut = null;
            }
        }

        return txFut;
    }

    /**
     * @param nodeId Reader to remove.
     * @param msgId Message ID.
     * @return {@code True} if reader was removed as a result of this operation.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean removeReader(UUID nodeId, long msgId) throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            ReaderId reader = readerId(nodeId);

            if (reader == null || reader.messageId() > msgId)
                return false;

            readers = new LinkedList<ReaderId<K, V>>(readers);

            readers.remove(reader);

            // Seal.
            readers = Collections.unmodifiableList(readers);

            return true;
        }
        finally {
            unlock();
        }
    }

    /**
     * Clears all readers (usually when partition becomes invalid and ready for eviction).
     */
    @Override public void clearReaders() {
        lock();

        try {
            readers = Collections.emptyList();
        }
        finally {
            unlock();
        }
    }

    /**
     * @return Collection of readers after check.
     * @throws GridCacheEntryRemovedException If removed.
     */
    public Collection<ReaderId<K, V>> checkReaders() throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            if (!readers.isEmpty()) {
                List<ReaderId> rmv = null;

                for (ReaderId reader : readers) {
                    if (!cctx.discovery().alive(reader.nodeId())) {
                        if (rmv == null)
                            rmv = new LinkedList<ReaderId>();

                        rmv.add(reader);
                    }
                }

                if (rmv != null) {
                    readers = new LinkedList<ReaderId<K, V>>(readers);

                    for (ReaderId rdr : rmv)
                        readers.remove(rdr);

                    readers = Collections.unmodifiableList(readers);
                }
            }

            return readers;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override protected boolean hasReaders() throws GridCacheEntryRemovedException {
        lock();

        try {
            checkReaders();

            return !readers.isEmpty();
        }
        finally {
            unlock();
        }
    }

    /**
     * Sets mappings into entry.
     *
     * @param ver Version.
     * @param mappings Mappings to set.
     * @return Candidate, if one existed for the version, or {@code null} if candidate was not found.
     * @throws GridCacheEntryRemovedException If removed.
     */
    @Nullable public GridCacheMvccCandidate<K> mappings(GridCacheVersion ver, Collection<UUID> mappings)
        throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            GridCacheMvccCandidate<K> cand = mvcc.candidate(ver);

            if (cand != null)
                cand.mappedNodeIds(mappings);

            return cand;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheEntry<K, V> wrap(boolean prjAware) {
        GridCacheContext<K, V> nearCtx = cctx.dht().near().context();

        GridCacheProjectionImpl<K, V> prjPerCall = nearCtx.projectionPerCall();

        if (prjPerCall != null && prjAware)
            return new GridPartitionedCacheEntryImpl<K, V>(prjPerCall, nearCtx, key, this);

        GridCacheEntryImpl<K, V> wrapper = this.wrapper;

        if (wrapper == null)
            this.wrapper = wrapper = new GridPartitionedCacheEntryImpl<K, V>(null, nearCtx, key, this);

        return wrapper;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtCacheEntry.class, this, "super", super.toString());
    }

    /**
     * Reader ID.
     */
    private static class ReaderId<K, V> {
        /** Node ID. */
        private UUID nodeId;

        /** Message ID. */
        private long msgId;

        /** Transaction future. */
        private GridCacheMultiTxFuture<K, V> txFut;

        /**
         * @param nodeId Node ID.
         * @param msgId Message ID.
         */
        ReaderId(UUID nodeId, long msgId) {
            this.nodeId = nodeId;
            this.msgId = msgId;
        }

        /**
         * @return Node ID.
         */
        UUID nodeId() {
            return nodeId;
        }

        /**
         * @return Message ID.
         */
        long messageId() {
            return msgId;
        }

        /**
         * @param msgId Message ID.
         */
        void messageId(long msgId) {
            this.msgId = msgId;
        }

        /**
         * @param cctx Cache context.
         * @return Transaction future.
         */
        GridCacheMultiTxFuture<K, V> getOrCreateTxFuture(GridCacheContext<K, V> cctx) {
            if (txFut == null)
                txFut = new GridCacheMultiTxFuture<K, V>(cctx);

            return txFut;
        }

        /**
         * @return Transaction future.
         */
        GridCacheMultiTxFuture<K, V> txFuture() {
            return txFut;
        }

        /**
         * Sets multi-transaction future to {@code null}.
         *
         * @return Previous transaction future.
         */
        GridCacheMultiTxFuture<K, V> resetTxFuture() {
            GridCacheMultiTxFuture<K, V> txFut = this.txFut;

            this.txFut = null;

            return txFut;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(ReaderId.class, this);
        }
    }
}

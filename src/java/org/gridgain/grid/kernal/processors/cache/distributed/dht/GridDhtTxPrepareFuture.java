// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 *
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridDhtTxPrepareFuture<K, V> extends GridCompoundIdentityFuture<GridCacheTxEx<K, V>>
    implements GridCacheMvccFuture<K, V, GridCacheTxEx<K, V>> {
    /** Logger reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Context. */
    private GridCacheContext<K, V> cctx;

    /** Future ID. */
    private GridUuid futId;

    /** Transaction. */
    @GridToStringExclude
    private GridDhtTxLocal<K, V> tx;

    /** Near mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> nearMap;

    /** DHT mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> dhtMap;

    /** Logger. */
    private GridLogger log;

    /** Error. */
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /** Replied flag. */
    private AtomicBoolean replied = new AtomicBoolean(false);

    /** All replies flag. */
    private AtomicBoolean allReplies = new AtomicBoolean(false);

    /** Latch to wait for reply to be sent. */
    @GridToStringExclude
    private CountDownLatch replyLatch = new CountDownLatch(1);

    /** Trackable flag. */
    private boolean trackable = true;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtTxPrepareFuture() {
        // No-op.
    }

    /**
     * @param cctx Context.
     * @param tx Transaction.
     */
    public GridDhtTxPrepareFuture(GridCacheContext<K, V> cctx, final GridDhtTxLocal<K, V> tx) {
        super(cctx.kernalContext(), new GridReducer<GridCacheTxEx<K, V>, GridCacheTxEx<K, V>>() {
            @Override public boolean collect(GridCacheTxEx<K, V> e) {
                return true;
            }

            @Override public GridCacheTxEx<K, V> apply() {
                // Nothing to aggregate.
                return tx;
            }
        });

        assert cctx != null;

        this.cctx = cctx;
        this.tx = tx;

        futId = GridUuid.randomUuid();

        log = U.logger(ctx, logRef, GridDhtTxPrepareFuture.class);

        if (tx.ec()) {
            replied.set(true);

            replyLatch.countDown();
        }

        dhtMap = tx.dhtMap();
        nearMap = tx.nearMap();

        assert dhtMap != null;
        assert nearMap != null;
    }

    /** {@inheritDoc} */
    @Override public GridUuid futureId() {
        return futId;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return tx.xidVersion();
    }

    /**
     * @return Involved nodes.
     */
    @Override public Collection<? extends GridNode> nodes() {
        return
            F.viewReadOnly(futures(), new GridClosure<GridFuture<?>, GridRichNode>() {
                @Nullable @Override public GridRichNode apply(GridFuture<?> f) {
                    if (isMini(f)) {
                        return ((MiniFuture)f).node();
                    }

                    return cctx.rich().rich(cctx.discovery().localNode());
                }
            });
    }

    /** {@inheritDoc} */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        if (log.isDebugEnabled())
            log.debug("Transaction future received owner changed callback: " + entry);

        K key = entry.key();

        boolean ret = tx.hasWriteKey(key);

        if (ret && initialized())
            return onDone(tx);

        return ret;
    }

    /** {@inheritDoc} */
    @Override public boolean trackable() {
        return trackable;
    }

    /** {@inheritDoc} */
    @Override public void markNotTrackable() {
        trackable = false;
    }

    /**
     * @return Transaction.
     */
    GridDhtTxLocal<K, V> tx() {
        return tx;
    }

    /**
     * @return {@code True} if all locks are owned.
     */
    private boolean checkLocks() {
        for (GridCacheTxEntry<K, V> txEntry : tx.writeEntries()) {
            while (true) {
                GridCacheEntryEx<K, V> cached = txEntry.cached();

                try {
                    // Don't compare entry against itself.
                    if (!cached.lockedLocally(tx.xid())) {
                        if (log.isDebugEnabled())
                            log.debug("Transaction entry is not locked by transaction (will wait) [entry=" + cached +
                                ", tx=" + tx + ']');

                        return false;
                    }

                    break; // While.
                }
                // Possible if entry cached within transaction is obsolete.
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry in future onAllReplies method (will retry): " + txEntry);

                    txEntry.cached(cctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                }
            }
        }

        return true;
    }

    /**
     * Initializes future.
     */
    public void onPreparedEC() {
        if (tx.ec())
            // No reason to wait for replies.
            tx.state(PREPARED); // TODO: CODE: EC
    }

    /** {@inheritDoc} */
    @Override public boolean onNodeLeft(UUID nodeId) {
        for (GridFuture<?> fut : futures())
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.node().id().equals(nodeId)) {
                    f.onResult(new GridTopologyException("Remote node left grid (will retry): " + nodeId));

                    return true;
                }
            }

        return false;
    }

    /**
     * @param t Error.
     */
    void onError(Throwable t) {
        if (err.compareAndSet(null, t)) {
            tx.setRollbackOnly();

            // TODO: as an improvement, at some point we must rollback right away.
            // TODO: However, in this case need to make sure that reply is sent back
            // TODO: even for non-existing transactions whenever finish request comes in.
//            try {
//                tx.rollback();
//            }
//            catch (GridException ex) {
//                U.error(log, "Failed to automatically rollback transaction: " + tx, ex);
//            }
//
            // If not local node.
            if (!tx.nearNodeId().equals(cctx.nodeId())) {
                // Send reply back to near node.
                GridCacheMessage<K, V> res = new GridNearTxPrepareResponse<K, V>(tx.nearXidVersion(), tx.nearFutureId(),
                    tx.nearMiniId(), tx.xidVersion(), Collections.<Integer>emptySet(), t);

                try {
                    cctx.io().send(tx.nearNodeId(), res);
                }
                catch (GridException e) {
                    U.error(log, "Failed to send reply to originating near node (will rollback): " + tx.nearNodeId(), e);

                    try {
                        tx.rollback();
                    }
                    catch (GridException ex) {
                        U.error(log, "Failed to rollback due to failure to communicate back up nodes: " + tx, ex);
                    }
                }
            }

            onComplete();
        }
    }

    /**
     * @param nodeId Sender.
     * @param res Result.
     */
    void onResult(UUID nodeId, GridDhtTxPrepareResponse<K, V> res) {
        if (!isDone()) {
            for (GridFuture<GridCacheTxEx<K, V>> fut : pending())
                if (isMini(fut)) {
                    MiniFuture f = (MiniFuture)fut;

                    if (f.futureId().equals(res.miniId())) {
                        assert f.node().id().equals(nodeId);

                        f.onResult(res);

                        break;
                    }
                }

            if (initialized() && !hasPending())
                onAllReplies();
        }
    }

    /**
     * Callback for whenever all replies are received.
     */
    public void onAllReplies() {
        // Ready all locks.
        if (allReplies.compareAndSet(false, true) && !tx.ec() && !isDone()) {
            if (log.isDebugEnabled())
                log.debug("Received replies from all participating nodes: " + this);

            for (GridCacheTxEntry<K, V> txEntry : tx.writeEntries()) {
                while (true) {
                    GridDistributedCacheEntry<K, V> entry = (GridDistributedCacheEntry<K, V>)txEntry.cached();

                    try {
                        GridCacheMvccCandidate<K> c = entry.readyLock(tx.xidVersion());

                        if (log.isDebugEnabled())
                            log.debug("Current lock owner for entry [owner=" + c + ", entry=" + entry + ']');

                        break; // While.
                    }
                    // Possible if entry cached within transaction is obsolete.
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry in future onAllReplies method (will retry): " + txEntry);

                        txEntry.cached(cctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(GridCacheTxEx<K, V> tx0, Throwable err) {
        assert err != null || (initialized() && !hasPending());

        if (err == null)
            onAllReplies();

        // If locks were not acquired yet, delay completion.
        if (isDone() || (err == null && !tx.ec() && !checkLocks()))
            return false;

        this.err.compareAndSet(null, err);

        if (replied.compareAndSet(false, true)) {
            try {
                if (!tx.nearNodeId().equals(cctx.nodeId())) {
                    // Send reply back to originating near node.
                    GridDistributedBaseMessage<K, V> res = new GridNearTxPrepareResponse<K, V>(tx.nearXidVersion(),
                        tx.nearFutureId(), tx.nearMiniId(), tx.xidVersion(), tx.invalidPartitions(), this.err.get());

                    GridCacheVersion min = tx.minVersion();

                    res.completedVersions(cctx.tm().committedVersions(min), cctx.tm().rolledbackVersions(min));

                    cctx.io().send(tx.nearNodeId(), res);
                }
            }
            catch (GridException e) {
                onError(e);
            }
            finally {
                replyLatch.countDown();
            }
        }
        else {
            try {
                replyLatch.await();
            }
            catch (InterruptedException e) {
                onError(new GridException("Got interrupted while waiting for replies to be sent.", e));

                replyLatch.countDown();
            }
        }

        return onComplete();
    }

    /**
     * @param f Future.
     * @return {@code True} if mini-future.
     */
    private boolean isMini(GridFuture<?> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     * Completeness callback.
     *
     * @return {@code True} if {@code done} flag was changed as a result of this call.
     */
    private boolean onComplete() {
        tx.state(PREPARED);

        if (super.onDone(tx, err.get())) {
            // Don't forget to clean up.
            cctx.mvcc().removeFuture(this);

            return true;
        }

        return false;
    }

    /**
     * Completes this future.
     */
    void complete() {
        onComplete();
    }

    /**
     * Initializes future.
     */
    void prepare() {
        if (tx.empty()) {
            tx.setRollbackOnly();

            onDone(tx);
        }

        if (!prepare(tx.readEntries(), tx.writeEntries())) {
            markInitialized();

            onAllReplies();

            // If nowhere to send, mark done.
            onDone(tx);
        }
        else
            markInitialized();
    }

    /**
     * @param reads Read entries.
     * @param writes Write entries.
     * @return {@code True} if some mapping was added.
     */
    @SuppressWarnings({"unchecked"})
    private boolean prepare(Iterable<GridCacheTxEntry<K, V>> reads, Iterable<GridCacheTxEntry<K, V>> writes) {
        boolean hasRemoteNodes = false;

        // Assign keys to primary nodes.
        for (GridCacheTxEntry<K, V> read : reads)
            hasRemoteNodes |= map(read);

        for (GridCacheTxEntry<K, V> write : writes)
            hasRemoteNodes |= map(write);

        if (isDone())
            return false;

        tx.needsCompletedVersions(hasRemoteNodes);

        boolean ret = false;

        GridCacheVersion minVer = tx.minVersion();

        // Initialize these collections lazily to avoid unnecessary
        // overhead if they are not needed.
        Collection<GridCacheVersion> committed = null;
        Collection<GridCacheVersion> rolledback = null;

        // Create mini futures.
        for (GridDistributedTxMapping<K, V> dhtMapping : dhtMap.values()) {
            assert !dhtMapping.empty();

            GridRichNode n = dhtMapping.node();

            assert !n.isLocal();

            if (committed == null)
                committed = cctx.tm().committedVersions(minVer);

            if (rolledback == null)
                rolledback = cctx.tm().rolledbackVersions(minVer);

            ret = true;

            GridDistributedTxMapping<K, V> nearMapping = nearMap.get(n.id());

            MiniFuture fut = new MiniFuture(n.id(), dhtMapping, nearMapping);

            add(fut); // Append new future.

            GridDistributedBaseMessage<K, V> req = new GridDhtTxPrepareRequest<K, V>(futId, fut.futureId(),
                tx.topologyVersion(), tx, dhtMapping.writes(), nearMapping == null ? null : nearMapping.writes());

            req.completedVersions(committed, rolledback);

            try {
                cctx.io().send(n, req);
            }
            catch (GridTopologyException e) {
                fut.onResult(e);
            }
            catch (GridException e) {
                // Fail the whole thing.
                fut.onResult(e);
            }
        }

        for (GridDistributedTxMapping<K, V> nearMapping : nearMap.values()) {
            if (!dhtMap.containsKey(nearMapping.node().id())) {
                assert nearMapping.writes() != null;

                if (committed == null)
                    committed = cctx.tm().committedVersions(minVer);

                if (rolledback == null)
                    rolledback = cctx.tm().rolledbackVersions(minVer);

                ret = true;

                MiniFuture fut = new MiniFuture(nearMapping.node().id(), null, nearMapping);

                add(fut); // Append new future.

                GridDistributedBaseMessage<K, V> req = new GridDhtTxPrepareRequest<K, V>(futId, fut.futureId(),
                    tx.topologyVersion(), tx, null, nearMapping.writes());

                req.completedVersions(committed, rolledback);

                try {
                    cctx.io().send(nearMapping.node(), req);
                }
                catch (GridTopologyException e) {
                    fut.onResult(e);
                }
                catch (GridException e) {
                    // Fail the whole thing.
                    fut.onResult(e);
                }
            }
        }

        return ret;
    }

    /**
     * @param entry Transaction entry.
     * @return {@code True} if mapped.
     */
    private boolean map(GridCacheTxEntry<K, V> entry) {
        GridDhtCacheEntry<K, V> cached = (GridDhtCacheEntry<K, V>)entry.cached();

        boolean ret;

        while (true) {
            try {
                Collection<GridNode> dhtNodes = cctx.dht().topology().nodes(cached.partition(), tx.topologyVersion());

                if (log.isDebugEnabled())
                    log.debug("Mapping entry to DHT nodes [nodes=" + U.toShortString(dhtNodes) +
                        ", entry=" + entry + ']');

                Collection<UUID> readers = cached.readers();

                Collection<GridNode> nearNodes = null;

                if (!F.isEmpty(readers)) {
                    nearNodes = cctx.discovery().nodes(readers, F.<UUID>not(F.idForNodeId(tx.nearNodeId())));

                    if (log.isDebugEnabled())
                        log.debug("Mapping entry to near nodes [nodes=" + U.toShortString(nearNodes) +
                            ", entry=" + entry + ']');
                }
                else if (log.isDebugEnabled())
                    log.debug("Entry has no near readers: " + entry);

                // Exclude local node.
                ret = map(entry, F.view(dhtNodes, F.remoteNodes(cctx.nodeId())), dhtMap);

                // Exclude DHT nodes.
                ret |= map(entry, F.view(nearNodes, F.not(F.<GridNode>nodeForNodeIds(dhtMap.keySet()))), nearMap);

                break;
            }
            catch (GridCacheEntryRemovedException ignore) {
                cached = cctx.dht().entryExx(entry.key());

                entry.cached(cached, cached.keyBytes());
            }
        }

        return ret;
    }

    /**
     * @param entry Entry.
     * @param nodes Nodes.
     * @param map Map.
     * @return {@code True} if mapped.
     */
    private boolean map(GridCacheTxEntry<K, V> entry, Iterable<GridNode> nodes,
        Map<UUID, GridDistributedTxMapping<K, V>> map) {
        boolean ret = false;

        if (nodes != null) {
            for (GridNode n : nodes) {
                GridDistributedTxMapping<K, V> m = map.get(n.id());

                if (m == null)
                    map.put(n.id(), m = new GridDistributedTxMapping<K, V>(cctx.rich().rich(n)));

                m.add(entry);

                ret = true;
            }
        }

        return ret;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtTxPrepareFuture.class, this, "super", super.toString());
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<GridCacheTxEx<K, V>> {
        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** Node ID. */
        private UUID nodeId;

        /** DHT mapping. */
        @GridToStringInclude
        private GridDistributedTxMapping<K, V> dhtMapping;

        /** Near mapping. */
        @GridToStringInclude
        private GridDistributedTxMapping<K, V> nearMapping;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            super(cctx.kernalContext());
        }

        /**
         * @param nodeId Node ID.
         * @param dhtMapping Mapping.
         * @param nearMapping nearMapping.
         */
        MiniFuture(UUID nodeId, GridDistributedTxMapping<K, V> dhtMapping, GridDistributedTxMapping<K, V> nearMapping) {
            super(cctx.kernalContext());

            assert dhtMapping == null || nearMapping == null || dhtMapping.node() == nearMapping.node();

            this.nodeId = nodeId;
            this.dhtMapping = dhtMapping;
            this.nearMapping = nearMapping;
        }

        /**
         * @return Future ID.
         */
        GridUuid futureId() {
            return futId;
        }

        /**
         * @return Node ID.
         */
        public GridRichNode node() {
            return dhtMapping != null ? dhtMapping.node() : nearMapping.node();
        }

        /**
         * @param e Error.
         */
        void onResult(Throwable e) {
            if (log.isDebugEnabled())
                log.debug("Failed to get future result [fut=" + this + ", err=" + e + ']');

            // Fail.
            onDone(e);
        }

        /**
         * @param e Node failure.
         */
        void onResult(GridTopologyException e) {
            if (log.isDebugEnabled())
                log.debug("Remote node left grid while sending or waiting for reply (will ignore): " + this);

            if (tx != null)
                tx.removeMapping(nodeId);

            onDone(tx);
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridDhtTxPrepareResponse<K, V> res) {
            if (res.error() != null)
                // Fail the whole compound future.
                onError(res.error());
            else {
                // Process evicted readers (no need to remap).
                if (nearMapping != null && !F.isEmpty(res.nearEvicted())) {
                    nearMapping.evictReaders(res.nearEvicted());

                    for (GridCacheTxEntry<K, V> entry : nearMapping.entries()) {
                        if (res.nearEvicted().contains(entry.key())) {
                            while (true) {
                                try {
                                    GridDhtCacheEntry<K, V> cached = (GridDhtCacheEntry<K, V>)entry.cached();

                                    cached.removeReader(nearMapping.node().id(), res.messageId());

                                    break;
                                }
                                catch (GridCacheEntryRemovedException ignore) {
                                    GridCacheEntryEx<K, V> e = cctx.cache().peekEx(entry.key());

                                    if (e == null)
                                        break;

                                    entry.cached(e, entry.keyBytes());
                                }
                            }
                        }
                    }
                }

                // Process invalid partitions (no need to remap).
                if (!F.isEmpty(res.invalidPartitions())) {
                    for (Iterator<GridCacheTxEntry<K, V>> it = dhtMapping.entries().iterator(); it.hasNext();) {
                        GridCacheTxEntry<K, V> entry  = it.next();

                        if (res.invalidPartitions().contains(entry.cached().partition())) {
                            it.remove();

                            if (log.isDebugEnabled())
                                log.debug("Removed mapping for entry from dht mapping [key=" + entry.key() +
                                    ", tx=" + tx + ", dhtMapping=" + dhtMapping + ']');
                        }
                    }

                    if (dhtMapping.empty()) {
                        dhtMap.remove(nodeId);

                        if (log.isDebugEnabled())
                            log.debug("Removed mapping for node entirely because all partitions are invalid [nodeId=" +
                                nodeId + ", tx=" + tx + ']');
                    }
                }

                // Finish mini future.
                onDone(tx);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, "done", isDone(), "cancelled", isCancelled(), "err", error());
        }
    }
}

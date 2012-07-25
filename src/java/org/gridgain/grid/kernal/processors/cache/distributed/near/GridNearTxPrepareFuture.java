// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
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
public final class GridNearTxPrepareFuture<K, V> extends GridCompoundIdentityFuture<GridCacheTxEx<K, V>>
    implements GridCacheMvccFuture<K, V, GridCacheTxEx<K, V>> {
    /** Logger reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Context. */
    private GridCacheContext<K, V> cctx;

    /** Future ID. */
    private GridUuid futId;

    /** Transaction. */
    @GridToStringExclude
    private GridNearTxLocal<K, V> tx;

    /** Logger. */
    private GridLogger log;

    /** Error. */
    @GridToStringExclude
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /** Trackable flag. */
    private boolean trackable = true;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearTxPrepareFuture() {
        // No-op.
    }

    /**
     * @param cctx Context.
     * @param tx Transaction.
     */
    public GridNearTxPrepareFuture(GridCacheContext<K, V> cctx, final GridNearTxLocal<K, V> tx) {
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
        assert tx != null;

        this.cctx = cctx;
        this.tx = tx;

        futId = GridUuid.randomUuid();

        log = U.logger(ctx, logRef, GridNearTxPrepareFuture.class);
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

        if (owner != null && tx.hasWriteKey(entry.key())) {
            // This will check for locks.
            onDone();

            return true;
        }

        return false;
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
     * @return {@code True} if all locks are owned.
     */
    private boolean checkLocks() {
        for (GridCacheTxEntry<K, V> txEntry : tx.writeEntries()) {
            while (true) {
                GridCacheEntryEx<K, V> cached = txEntry.cached();

                try {
                    GridCacheVersion ver = txEntry.explicitVersion() != null ?
                        txEntry.explicitVersion() : tx.xidVersion();

                    // If locks haven't been acquired yet, keep waiting.
                    if (!cached.lockedBy(ver)) {
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

        if (log.isDebugEnabled())
            log.debug("All locks are acquired for near prepare future: " + this);

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
     * @param e Error.
     */
    void onError(Throwable e) {
        if (err.compareAndSet(null, e)) {
            boolean marked = tx.setRollbackOnly();

            if (e instanceof GridCacheTxRollbackException) {
                if (marked) {
                    try {
                        tx.rollback();
                    }
                    catch (GridException ex) {
                        U.error(log, "Failed to automatically rollback transaction: " + tx, ex);
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
    void onResult(UUID nodeId, GridNearTxPrepareResponse<K, V> res) {
        if (!isDone())
            for (GridFuture<GridCacheTxEx<K, V>> fut : pending())
                if (isMini(fut)) {
                    MiniFuture f = (MiniFuture)fut;

                    if (f.futureId().equals(res.miniId())) {
                        assert f.node().id().equals(nodeId);

                        f.onResult(res);
                    }
                }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(GridCacheTxEx<K, V> t, Throwable err) {
        // If locks were not acquired yet, delay completion.
        if (isDone() || (err == null && !tx.ec() && !checkLocks()))
            return false;

        this.err.compareAndSet(null, err);

        if (err == null)
            tx.state(PREPARED);

        if (super.onDone(tx, err)) {
            // Don't forget to clean up.
            cctx.mvcc().removeFuture(this);

            return true;
        }

        return false;
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
     */
    private void onComplete() {
        if (super.onDone(tx, err.get()))
            // Don't forget to clean up.
            cctx.mvcc().removeFuture(this);
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
        prepare(
            tx.optimistic() && tx.serializable() ? tx.readEntries() : Collections.<GridCacheTxEntry<K, V>>emptyList(),
            tx.writeEntries(), Collections.<UUID, GridDistributedTxMapping<K,V>>emptyMap());

        markInitialized();
    }

    /**
     * @param reads Read entries.
     * @param writes Write entries.
     * @param mapped Previous mappings.
     */
    @SuppressWarnings({"unchecked"})
    private void prepare(Iterable<GridCacheTxEntry<K, V>> reads, Iterable<GridCacheTxEntry<K, V>> writes,
        Map<UUID, GridDistributedTxMapping<K, V>> mapped) {
        Collection<GridRichNode> nodes = CU.allNodes(cctx, tx.topologyVersion());

        ConcurrentMap<UUID, GridDistributedTxMapping<K, V>> mappings =
            new ConcurrentHashMap<UUID, GridDistributedTxMapping<K, V>>(nodes.size());

        // Assign keys to primary nodes.
        for (GridCacheTxEntry<K, V> read : reads)
            map(read, mappings, nodes, mapped);

        for (GridCacheTxEntry<K, V> write : writes)
            map(write, mappings, nodes, mapped);

        if (isDone()) {
            if (log.isDebugEnabled())
                log.debug("Abandoning (re)map because future is done: " + this);

            return;
        }

        tx.addEntryMapping(mappings);

        cctx.mvcc().recheckPendingLocks();

        // Create mini futures.
        for (final GridDistributedTxMapping<K, V> m : mappings.values()) {
            if (isDone())
                return;

            assert !m.empty();

            GridRichNode n = m.node();

            GridNearTxPrepareRequest<K, V> req = new GridNearTxPrepareRequest<K, V>(futId, tx.topologyVersion(), tx,
                tx.optimistic() && tx.serializable() ? m.reads() : null, m.writes(), tx.syncCommit(),
                tx.syncRollback());

            // If this is the primary node for the keys.
            if (n.isLocal()) {
                // Make sure not to provide Near entries to DHT cache.
                req.cloneEntries(cctx);

                req.miniId(GridUuid.randomUuid());

                // At this point, if any new node joined, then it is
                // waiting for this transaction to complete, so
                // partition reassignments are not possible here.
                GridFuture<GridCacheTxEx<K, V>> fut = cctx.near().dht().prepareTx(n, req);

                // Add new future.
                add(new GridEmbeddedFuture<GridCacheTxEx<K, V>, GridCacheTxEx<K, V>>(
                    cctx.kernalContext(),
                    fut,
                    new C2<GridCacheTxEx<K, V>, Exception, GridCacheTxEx<K, V>>() {
                        @Override public GridCacheTxEx<K, V> apply(GridCacheTxEx<K, V> t, Exception ex) {
                            if (ex != null) {
                                onError(ex);

                                return t;
                            }

                            GridCacheTxLocalEx<K, V> dhtTx = (GridCacheTxLocalEx<K, V>)t;

                            Collection<Integer> invalidParts = dhtTx.invalidPartitions();

                            if (!F.isEmpty(invalidParts)) {
                                Collection<GridCacheTxEntry<K, V>> readRemaps =
                                    new LinkedList<GridCacheTxEntry<K, V>>();
                                Collection<GridCacheTxEntry<K, V>> writeRemaps =
                                    new LinkedList<GridCacheTxEntry<K, V>>();

                                addRemaps(m.node().id(), invalidParts, m.reads(), readRemaps);
                                addRemaps(m.node().id(), invalidParts, m.writes(), writeRemaps);

                                // Remap.
                                prepare(readRemaps, writeRemaps,
                                    Collections.<UUID, GridDistributedTxMapping<K,V>>emptyMap());
                            }

                            if (!m.empty()) {
                                tx.addDhtVersion(m.node().id(), dhtTx.xidVersion());

                                GridCacheVersion min = dhtTx.minVersion();

                                GridCacheTxManager<K, V> tm = cctx.near().dht().context().tm();

                                tx.orderCompleted(m, tm.committedVersions(min), tm.rolledbackVersions(min));
                            }

                            return tx;
                        }
                    }
                ));
            }
            else {
                MiniFuture fut = new MiniFuture(m);

                req.miniId(fut.futureId());

                add(fut); // Append new future.

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
        }
    }

    /**
     * @param entry Transaction entry.
     * @param mappings Mappings.
     * @param nodes Nodes.
     * @param mapped Previous mappings.
     */
    private void map(GridCacheTxEntry<K, V> entry, ConcurrentMap<UUID, GridDistributedTxMapping<K, V>> mappings,
        Collection<GridRichNode> nodes, Map<UUID, GridDistributedTxMapping<K, V>> mapped) {
        GridRichNode primary = CU.primary0(cctx.affinity(entry.key(), nodes));

        if (log.isDebugEnabled())
            log.debug("Mapped key to primary node [key=" + entry.key() + ", part=" + cctx.partition(entry.key()) +
                ", primary=" + U.toShortString(primary) + ", allNodes=" + U.toShortString(nodes) + ']');

        if (mapped != null && mapped.containsKey(primary.id())) {
            onDone(new GridException("Failed to remap key to a new node " +
                "(key got remapped to the same node) [primaryId=" + primary.id() + ", mapped=" + mapped + ']'));

            return;
        }

        GridDistributedTxMapping<K, V> m = mappings.get(primary.id());

        if (m == null)
            mappings.put(primary.id(), m = new GridDistributedTxMapping<K, V>(primary));

        m.add(entry);

        entry.nodeId(primary.id());

        while (true) {
            try {
                GridNearCacheEntry<K, V> cached = (GridNearCacheEntry<K, V>)entry.cached();

                cached.dhtNodeId(tx.xidVersion(), primary.id());

                break;
            }
            catch (GridCacheEntryRemovedException ignore) {
                entry.cached(cctx.near().entryEx(entry.key()), entry.keyBytes());
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param invalidParts Invalid partitions.
     * @param entries Entries.
     * @param remaps Remaps.
     */
    private void addRemaps(UUID nodeId, Collection<Integer> invalidParts, Collection<GridCacheTxEntry<K, V>> entries,
        Collection<GridCacheTxEntry<K, V>> remaps) {
        for (Iterator<GridCacheTxEntry<K, V>> it = entries.iterator(); it.hasNext();) {
            GridCacheTxEntry<K, V> e = it.next();

            GridCacheEntryEx<K, V> cached = e.cached();

            int part = cached == null ? cctx.partition(e.key()) : cached.partition();

            if (invalidParts.contains(part)) {
                it.remove();

                tx.removeMapping(nodeId, e.key());

                remaps.add(e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxPrepareFuture.class, this, super.toString());
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<GridCacheTxEx<K, V>> {
        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** Keys. */
        @GridToStringInclude
        private GridDistributedTxMapping<K, V> m;

        /** Flag to signal some result being processed. */
        private AtomicBoolean rcvRes = new AtomicBoolean(false);

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            // No-op.
        }

        /**
         * @param m Mapping.
         */
        MiniFuture(GridDistributedTxMapping<K, V> m) {
            super(cctx.kernalContext());

            this.m = m;
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
            return m.node();
        }

        /**
         * @return Keys.
         */
        public GridDistributedTxMapping<K, V> mapping() {
            return m;
        }

        /**
         * @param e Error.
         */
        void onResult(Throwable e) {
            if (rcvRes.compareAndSet(false, true)) {
                if (log.isDebugEnabled())
                    log.debug("Failed to get future result [fut=" + this + ", err=" + e + ']');

                // Fail.
                onDone(e);
            }
            else
                U.warn(log, "Received error after another result has been processed [fut=" +
                    GridNearTxPrepareFuture.this + ", mini=" + this + ']', e);
        }

        /**
         * @param e Node failure.
         */
        void onResult(GridTopologyException e) {
            if (isDone())
                return;

            if (rcvRes.compareAndSet(false, true)) {
                if (log.isDebugEnabled())
                    log.debug("Remote node left grid while sending or waiting for reply (will retry): " + this);

                // Remove previous mapping.
                tx.removeMapping(m.node().id());

                // Remap.
                prepare(m.reads(), m.writes(), new T2<UUID, GridDistributedTxMapping<K, V>>(m.node().id(), m));

                onDone(tx);
            }
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridNearTxPrepareResponse<K, V> res) {
            if (isDone())
                return;

            if (rcvRes.compareAndSet(false, true)) {
                if (res.error() != null) {
                    // Fail the whole compound future.
                    onError(res.error());
                }
                else {
                    Collection<Integer> invalidParts = res.invalidPartitions();

                    if (!F.isEmpty(invalidParts)) {
                        Collection<GridCacheTxEntry<K, V>> readRemaps = new LinkedList<GridCacheTxEntry<K, V>>();
                        Collection<GridCacheTxEntry<K, V>> writeRemaps = new LinkedList<GridCacheTxEntry<K, V>>();

                        addRemaps(m.node().id(), invalidParts, m.reads(), readRemaps);
                        addRemaps(m.node().id(), invalidParts, m.writes(), writeRemaps);

                        // Remap.
                        prepare(readRemaps, writeRemaps, Collections.<UUID, GridDistributedTxMapping<K,V>>emptyMap());
                    }

                    if (!m.empty()) {
                        // Register DHT version.
                        tx.addDhtVersion(m.node().id(), res.dhtVersion());

                        tx.orderCompleted(m, res.committedVersions(), res.rolledbackVersions());
                    }

                    // Finish this mini future.
                    onDone(tx);
                }
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, "done", isDone(), "cancelled", isCancelled(), "err", error());
        }
    }
}

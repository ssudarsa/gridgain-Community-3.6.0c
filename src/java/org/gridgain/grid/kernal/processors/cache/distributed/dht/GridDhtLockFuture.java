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
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.kernal.processors.timeout.*;
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

/**
 * Cache lock future.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridDhtLockFuture<K, V> extends GridCompoundIdentityFuture<Boolean>
    implements GridCacheMvccFuture<K, V, Boolean>, GridDhtFuture<Boolean>, GridCacheMappedVersion {
    /** Logger reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Cache registry. */
    @GridToStringExclude
    private GridCacheContext<K, V> cctx;

    /** Near node ID. */
    private UUID nearNodeId;

    /** Near lock version. */
    private GridCacheVersion nearLockVer;

    /** Topology version. */
    private long topVer;

    /** Thread. */
    private long threadId;

    /** Keys locked so far. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridToStringExclude
    private List<GridDhtCacheEntry<K, V>> entries;

    /** Near mappings. */
    private Map<GridNode, List<GridDhtCacheEntry<K, V>>> nearMap =
        new ConcurrentHashMap<GridNode, List<GridDhtCacheEntry<K, V>>>();

    /** DHT mappings. */
    private Map<GridNode, List<GridDhtCacheEntry<K, V>>> dhtMap =
        new ConcurrentHashMap<GridNode, List<GridDhtCacheEntry<K, V>>>();

    /** Future ID. */
    private GridUuid futId;

    /** Lock version. */
    private GridCacheVersion lockVer;

    /** Read flag. */
    private boolean read;

    /** Error. */
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /** Timed out flag. */
    private volatile boolean timedOut;

    /** Timeout object. */
    @GridToStringExclude
    private LockTimeoutObject timeoutObj;

    /** Lock timeout. */
    private long timeout;

    /** Logger. */
    @GridToStringExclude
    private GridLogger log;

    /** Filter. */
    private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

    /** Transaction. */
    private GridDhtTxLocal<K, V> tx;

    /** All replies flag. */
    private AtomicBoolean allReplies = new AtomicBoolean(false);

    /** */
    private Collection<Integer> invalidParts = new GridLeanSet<Integer>();

    /** Trackable flag. */
    private boolean trackable = true;

    /** Mutex. */
    private final Object mux = new Object();

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridDhtLockFuture() {
        // No-op.
    }

    /**
     * @param cctx Cache context.
     * @param nearNodeId Near node ID.
     * @param nearLockVer Near lock version.
     * @param topVer Topology version.
     * @param cnt Number of keys to lock.
     * @param read Read flag.
     * @param timeout Lock acquisition timeout.
     * @param tx Transaction.
     * @param filter Filter.
     */
    public GridDhtLockFuture(
        GridCacheContext<K, V> cctx,
        UUID nearNodeId,
        GridCacheVersion nearLockVer,
        long topVer,
        int cnt,
        boolean read,
        long timeout,
        GridDhtTxLocal<K, V> tx,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        super(cctx.kernalContext(), CU.boolReducer());

        assert cctx != null;
        assert nearNodeId != null;
        assert nearLockVer != null;
        assert topVer > 0;

        this.cctx = cctx;
        this.nearNodeId = nearNodeId;
        this.nearLockVer = nearLockVer;
        this.topVer = topVer;
        this.read = read;
        this.timeout = timeout;
        this.filter = filter;
        this.tx = tx;

        if (tx != null)
            tx.topologyVersion(topVer);

        threadId = tx != null ? tx.threadId() : Thread.currentThread().getId();

        lockVer = tx != null ? tx.xidVersion() : cctx.versions().onReceivedAndNext(nearNodeId, nearLockVer);

        futId = GridUuid.randomUuid();

        entries = new ArrayList<GridDhtCacheEntry<K, V>>(cnt);

        log = U.logger(ctx, logRef, GridDhtLockFuture.class);

        if (timeout > 0) {
            timeoutObj = new LockTimeoutObject();

            cctx.time().addTimeoutObject(timeoutObj);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<Integer> invalidPartitions() {
        return invalidParts;
    }

    /**
     * @param invalidPart Partition to retry.
     */
    void addInvalidPartition(int invalidPart) {
        invalidParts.add(invalidPart);

        // Register invalid partitions with transaction.
        if (tx != null)
            tx.addInvalidPartition(invalidPart);

        if (log.isDebugEnabled())
            log.debug("Added invalid partition to future [invalidPart=" + invalidPart + ", fut=" + this + ']');
    }

    /**
     * @return Participating nodes.
     */
    @Override public Collection<? extends GridNode> nodes() {
        return F.viewReadOnly(futures(), new GridClosure<GridFuture<?>, GridNode>() {
            @Nullable @Override public GridNode apply(GridFuture<?> f) {
                if (isMini(f))
                    return ((MiniFuture)f).node();

                return cctx.rich().rich(cctx.discovery().localNode());
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return lockVer;
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
     * @return Entries.
     */
    public Collection<GridDhtCacheEntry<K, V>> entries() {
        return F.view(entries, F.notNull());
    }

    /**
     * @return Entries.
     */
    public Collection<GridDhtCacheEntry<K, V>> entriesCopy() {
        synchronized (mux) {
            return new ArrayList<GridDhtCacheEntry<K, V>>(entries());
        }
    }

    /**
     * @return Future ID.
     */
    @Override public GridUuid futureId() {
        return futId;
    }

    /**
     * @return Near lock version.
     */
    public GridCacheVersion nearLockVersion() {
        return nearLockVer;
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridCacheVersion mappedVersion() {
        return tx == null ? nearLockVer : null;
    }

    /**
     * @return {@code True} if transaction is not {@code null}.
     */
    private boolean inTx() {
        return tx != null;
    }

    /**
     * @return {@code True} if transaction is implicit.
     */
    private boolean implicitSingle() {
        return tx != null && tx.implicitSingle();
    }

    /**
     * @return {@code True} if transaction is not {@code null} and in EC mode.
     */
    private boolean ec() {
        return tx != null && tx.ec();
    }

    /**
     * @return {@code True} if transaction is not {@code null} and has invalidate flag set.
     */
    private boolean isInvalidate() {
        return tx != null && tx.isInvalidate();
    }

    /**
     * @return Transaction isolation or {@code null} if no transaction.
     */
    @Nullable private GridCacheTxIsolation isolation() {
        return tx == null ? null : tx.isolation();
    }

    /**
     * @param cached Entry.
     * @return {@code True} if locked.
     * @throws GridCacheEntryRemovedException If removed.
     */
    private boolean locked(GridCacheEntryEx<K, V> cached) throws GridCacheEntryRemovedException {
        return (cached.lockedLocally(lockVer.id()) && filter(cached)); // If filter failed, lock is failed.
    }

    /**
     * @param cached Entry.
     * @param owner Lock owner.
     * @return {@code True} if locked.
     */
    private boolean locked(GridCacheEntryEx<K, V> cached, GridCacheMvccCandidate<K> owner) {
        // Reentry-aware check (if filter failed, lock is failed).
        return owner != null && owner.matches(lockVer, cctx.nodeId(), threadId) && filter(cached);
    }

    /**
     * Adds entry to future.
     *
     * @param entry Entry to add.
     * @return Lock candidate.
     * @throws GridCacheEntryRemovedException If entry was removed.
     * @throws GridDistributedLockCancelledException If lock is canceled.
     */
    @Nullable public GridCacheMvccCandidate<K> addEntry(GridDhtCacheEntry<K, V> entry)
        throws GridCacheEntryRemovedException, GridDistributedLockCancelledException {
        if (log.isDebugEnabled())
            log.debug("Adding entry: " + entry);

        if (entry == null)
            return null;

        // Check if the future is timed out.
        if (timedOut)
            return null;

        // Add local lock first, as it may throw GridCacheEntryRemovedException.
        GridCacheMvccCandidate<K> c = entry.addDhtLocal(nearNodeId, nearLockVer, topVer, threadId, lockVer, timeout,
            /*reenter*/false, ec(), inTx(), implicitSingle());

        if (c == null && timeout < 0) {
            if (log.isDebugEnabled())
                log.debug("Failed to acquire lock with negative timeout: " + entry);

            onFailed(false);

            return null;
        }

        synchronized (mux) {
            entries.add(c == null || c.reentry() ? null : entry);
        }

        // Double check if the future has already timed out.
        if (timedOut) {
            entry.removeLock(lockVer);

            return null;
        }

        return c;
    }

    /**
     * Undoes all locks.
     *
     * @param dist If {@code true}, then remove locks from remote nodes as well.
     */
    private void undoLocks(boolean dist) {
        // Transactions will undo during rollback.
        Collection<GridDhtCacheEntry<K, V>> entriesCopy = entriesCopy();

        if (dist && tx == null) {
            cctx.dht().removeLocks(nearNodeId, lockVer, F.viewReadOnly(entriesCopy,
                new C1<GridDhtCacheEntry<K, V>, K>() {
                    @Override public K apply(GridDhtCacheEntry<K, V> e) {
                        return e.key();
                    }
                }), false);
        }
        else {
            if (tx != null) {
                if (tx.setRollbackOnly()) {
                    if (log.isDebugEnabled())
                        log.debug("Marked transaction as rollback only because locks could not be acquired: " + tx);
                }
                else if (log.isDebugEnabled())
                    log.debug("Transaction was not marked rollback-only while locks were not acquired: " + tx);
            }

            for (GridCacheEntryEx<K, V> e : entriesCopy) {
                try {
                    e.removeLock(lockVer);
                }
                catch (GridCacheEntryRemovedException ignored) {
                    while (true) {
                        try {
                            e = cctx.cache().peekEx(e.key());

                            if (e != null)
                                e.removeLock(lockVer);

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Attempted to remove lock on removed entry (will retry) [ver=" +
                                    lockVer + ", entry=" + e + ']');
                        }
                    }
                }
            }
        }
    }

    /**
     *
     * @param dist {@code True} if need to distribute lock release.
     */
    private void onFailed(boolean dist) {
        undoLocks(dist);

        complete(false);
    }

    /**
     * @param success Success flag.
     */
    public void complete(boolean success) {
        onComplete(success);
    }

    /**
     * @param nodeId Left node ID
     * @return {@code True} if node was in the list.
     */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public boolean onNodeLeft(UUID nodeId) {
        for (GridFuture<?> fut : futures()) {
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.node().id().equals(nodeId)) {
                    f.onResult(new GridTopologyException("Remote node left grid (will retry): " + nodeId));

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @param nodeId Sender.
     * @param res Result.
     */
    void onResult(UUID nodeId, GridDhtLockResponse<K, V> res) {
        if (!isDone()) {
            if (log.isDebugEnabled())
                log.debug("Received lock response from node [nodeId=" + nodeId + ", res=" + res + ", fut=" + this + ']');

            boolean found = false;

            for (GridFuture<Boolean> fut : pending()) {
                if (isMini(fut)) {
                    MiniFuture mini = (MiniFuture)fut;

                    if (mini.futureId().equals(res.miniId())) {
                        assert mini.node().id().equals(nodeId);

                        if (log.isDebugEnabled())
                            log.debug("Found mini future for response [mini=" + mini + ", res=" + res + ']');

                        found = true;

                        mini.onResult(res);

                        if (log.isDebugEnabled())
                            log.debug("Futures after processed lock response [fut=" + this + ", mini=" + mini +
                                ", res=" + res + ']');

                        break;
                    }
                }
            }

            if (!found)
                U.warn(log, "Failed to find mini future for response (perhaps due to stale message) [res=" + res +
                    ", fut=" + this + ']');
        }

        if (initialized() && !hasPending())
            onAllReplies();
    }

    /**
     * Callback for whenever all replies are received.
     */
    public void onAllReplies() {
        if (allReplies.compareAndSet(false, true) && !isDone()) {
            if (log.isDebugEnabled())
                log.debug("Received replies from all participating nodes: " + this);

            for (int i = 0; i < entries.size(); i++) {
                while (true) {
                    GridDistributedCacheEntry<K, V> entry = entries.get(i);

                    if (entry == null)
                        break; // While.

                    try {
                        GridCacheMvccCandidate<K> owner = entry.readyLock(lockVer);

                        if (timeout < 0)
                            if (owner == null || !owner.version().equals(lockVer)) {
                                onFailed(true);

                                return;
                            }

                        if (!locked(entry, owner))
                            if (log.isDebugEnabled())
                                log.debug("Entry is not locked (will keep waiting) [entry=" + entry +
                                    ", fut=" + this + ']');

                        break; // Inner while loop.
                    }
                    // Possible in concurrent cases, when owner is changed after locks
                    // have been released or cancelled.
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to ready lock because entry was removed (will renew).");

                        entries.set(i, (GridDhtCacheEntry<K, V>)cctx.cache().entryEx(entry.key(), topVer));
                    }
                }
            }
        }
    }

    /**
     * @param e Error.
     */
    public void onError(GridDistributedLockCancelledException e) {
        if (err.compareAndSet(null, e))
            onComplete(false);
    }

    /**
     * @param t Error.
     */
    public void onError(Throwable t) {
        if (err.compareAndSet(null, t))
            onComplete(false);
    }

    /**
     * @param cached Entry to check.
     * @return {@code True} if filter passed.
     */
    private boolean filter(GridCacheEntryEx<K, V> cached) {
        try {
            if (!cctx.isAll(cached, filter)) {
                if (log.isDebugEnabled())
                    log.debug("Filter didn't pass for entry (will fail lock): " + cached);

                onFailed(true);

                return false;
            }

            return true;
        }
        catch (GridException e) {
            onError(e);

            return false;
        }
    }

    /**
     * Callback for whenever entry lock ownership changes.
     *
     * @param entry Entry whose lock ownership changed.
     */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        if (isDone())
            return false; // Check other futures.

        if (log.isDebugEnabled())
            log.debug("Received onOwnerChanged() call back [entry=" + entry + ", owner=" + owner + "]");

        if (owner != null && owner.version().equals(lockVer)) {
            onDone(true);

            return true;
        }

        return false;
    }

    /**
     * @return {@code True} if locks have been acquired.
     */
    private boolean checkLocks() {
        if (!isDone() && initialized() && !hasPending()) {
            for (int i = 0; i < entries.size(); i++) {
                while (true) {
                    GridCacheEntryEx<K, V> entry = entries.get(i);

                    if (entry == null)
                        break; // While.

                    try {
                        if (!locked(entry)) {
                            if (log.isDebugEnabled())
                                log.debug("Lock is still not acquired for entry (will keep waiting) [entry=" +
                                    entry + ", fut=" + this + ']');

                            return false;
                        }

                        break; // While.
                    }
                    // Possible in concurrent cases, when owner is changed after locks
                    // have been released or cancelled.
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry in checkLocks method (will retry): " + entry);

                        // Replace old entry with new one.
                        entries.set(i, (GridDhtCacheEntry<K, V>)cctx.cache().entryEx(entry.key(), topVer));
                    }
                }
            }

            if (log.isDebugEnabled())
                log.debug("Local lock acquired for entries [fut=" + this + ", entries=" + entries + "]");

            onComplete(true);

            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() {
        if (onCancelled())
            onComplete(false);

        return isCancelled();
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(@Nullable Boolean success, @Nullable Throwable err) {
        // Protect against NPE.
        if (success == null) {
            assert err != null;

            success = false;
        }

        assert err == null || !success;
        assert !success || (initialized() && !hasPending()) : "Invalid done callback [success=" + success +
            ", fut=" + this + ']';

        if (log.isDebugEnabled())
            log.debug("Received onDone(..) callback [success=" + success + ", err=" + err + ", fut=" + this + ']');

        if (err == null && success)
            onAllReplies();

        // If locks were not acquired yet, delay completion.
        if (isDone() || (err == null && success && !ec() && !checkLocks()))
            return false;

        this.err.compareAndSet(null, err);

        return onComplete(success);
    }

    /**
     * Completeness callback.
     *
     * @param success {@code True} if lock was acquired.
     * @return {@code True} if complete by this operation.
     */
    private boolean onComplete(boolean success) {
        if (log.isDebugEnabled())
            log.debug("Received onComplete(..) callback [success=" + success + ", fut=" + this + ']');

        if (!success)
            undoLocks(true);

        if (tx != null)
            cctx.tm().txContext(tx);

        if (super.onDone(success, err.get())) {
            if (log.isDebugEnabled())
                log.debug("Completing future: " + this);

            // Clean up.
            cctx.mvcc().removeFuture(this);

            if (timeoutObj != null)
                cctx.time().removeTimeoutObject(timeoutObj);

            return true;
        }

        return false;
    }

    /**
     * Checks for errors.
     *
     * @throws GridException If execution failed.
     */
    private void checkError() throws GridException {
        if (err.get() != null)
            throw U.cast(err.get());
    }

    /**
     * @param f Future.
     * @return {@code True} if mini-future.
     */
    private boolean isMini(GridFuture<?> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     *
     */
    void map() {
        if (!map(entries())) {
            markInitialized();

            if (!isDone()) {
                onAllReplies();

                onDone(true);
            }
        }
        else
            markInitialized();
    }

    /**
     * @param entries Entries.
     * @return {@code True} if some mapping was added.
     */
    @SuppressWarnings( {"ForLoopReplaceableByForEach"})
    private boolean map(Iterable<GridDhtCacheEntry<K, V>> entries) {
        if (log.isDebugEnabled())
            log.debug("Mapping entry for DHT lock future: " + this);

        boolean hasRmtNodes = false;

        // Assign keys to primary nodes.
        for (GridDhtCacheEntry<K, V> entry : entries) {
            try {
                while (true) {
                    try {
                        hasRmtNodes = cctx.dhtMap(nearNodeId, topVer, entry, log, dhtMap, nearMap);

                        GridCacheMvccCandidate<K> cand = entry.mappings(lockVer,
                            F.nodeIds(F.concat(false, dhtMap.keySet(), nearMap.keySet())));

                        // Possible in case of lock cancellation.
                        if (cand == null) {
                            markInitialized();

                            onDone(false);

                            return false;
                        }

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry when mapping DHT lock future (will retry): " + entry);

                        entry = cctx.dht().entryExx(entry.key(), topVer);
                    }
                }
            }
            catch (GridDhtInvalidPartitionException e) {
                assert false : "DHT lock should never get invalid partition [err=" + e + ", fut=" + this + ']';
            }
        }

        if (tx != null) {
            tx.addDhtMapping(dhtMap);
            tx.addNearMapping(nearMap);

            tx.needsCompletedVersions(hasRmtNodes);
        }

        if (isDone()) {
            if (log.isDebugEnabled())
                log.debug("Mapping won't proceed because future is done: " + this);

            return false;
        }

        if (log.isDebugEnabled())
            log.debug("Mapped DHT lock future [dhtMap=" + F.nodeIds(dhtMap.keySet()) + ", nearMap=" +
                F.nodeIds(nearMap.keySet()) + ", dhtLockFut=" + this + ']');

        boolean ret = false;

        GridCacheVersion minVer = tx == null ? lockVer : tx.minVersion();

        // Lazily initialize so we don't have
        Collection<GridCacheVersion> committed = null;
        Collection<GridCacheVersion> rolledback = null;

        // Create mini futures.
        for (Map.Entry<GridNode, List<GridDhtCacheEntry<K, V>>> mapped : dhtMap.entrySet()) {
            GridNode n = mapped.getKey();

            List<GridDhtCacheEntry<K, V>> dhtMapping = mapped.getValue();

            int cnt = F.size(dhtMapping);

            if (cnt > 0) {
                ret = true;

                List<GridDhtCacheEntry<K, V>> nearMapping = nearMap.get(n);

                MiniFuture fut = new MiniFuture(n, dhtMapping, nearMapping);

                GridDhtLockRequest<K, V> req = new GridDhtLockRequest<K, V>(nearNodeId, threadId, futId,
                    fut.futureId(), lockVer, topVer, inTx(), read, isolation(), isInvalidate(), timeout,
                    cnt, F.size(nearMapping));

                try {
                    for (ListIterator<GridDhtCacheEntry<K, V>> it = dhtMapping.listIterator(); it.hasNext();) {
                        GridDhtCacheEntry<K, V> e = it.next();

                        req.addDhtKey(e.key(), e.getOrMarshalKeyBytes(), cctx);

                        it.set(addOwned(req, e));
                    }

                    add(fut); // Append new future.

                    if (committed == null)
                        committed = cctx.tm().committedVersions(minVer);

                    if (rolledback == null)
                        rolledback = cctx.tm().rolledbackVersions(minVer);

                    req.completedVersions(committed, rolledback);

                    assert !n.id().equals(ctx.localNodeId());

                    if (log.isDebugEnabled())
                        log.debug("Sending DHT lock request to DHT node [node=" + n.id() + ", req=" + req + ']');

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

        for (Map.Entry<GridNode, List<GridDhtCacheEntry<K, V>>> mapped : nearMap.entrySet()) {
            GridNode n = mapped.getKey();

            if (!dhtMap.containsKey(n)) {
                ret = true;

                List<GridDhtCacheEntry<K, V>> nearMapping = mapped.getValue();

                int cnt = F.size(nearMapping);

                if (cnt > 0) {
                    MiniFuture fut = new MiniFuture(n, null, nearMapping);

                    GridDhtLockRequest<K, V> req = new GridDhtLockRequest<K, V>(nearNodeId, threadId, futId,
                        fut.futureId(), lockVer, topVer, inTx(), read, isolation(), isInvalidate(), timeout, 0, cnt);

                    try {
                        for (ListIterator<GridDhtCacheEntry<K, V>> it = nearMapping.listIterator(); it.hasNext();) {
                            GridDhtCacheEntry<K, V> e = it.next();

                            req.addNearKey(e.key(), e.getOrMarshalKeyBytes(), cctx);

                            it.set(addOwned(req, e));
                        }

                        add(fut); // Append new future.

                        if (committed == null)
                            committed = cctx.tm().committedVersions(minVer);

                        if (rolledback == null)
                            rolledback = cctx.tm().rolledbackVersions(minVer);

                        req.completedVersions(committed, rolledback);

                        // No need to send message to local node.
                        if (n.id().equals(ctx.localNodeId())) {
                            try {
                                GridNearTxRemote<K, V> nearTx = cctx.dht().near().startRemoteTx(n.id(), req);

                                GridDhtLockResponse<K, V> res = new GridDhtLockResponse<K, V>(req.version(),
                                    req.futureId(), req.miniId(), cnt);

                                // Properly handle eviction.
                                if (nearTx != null)
                                    res.nearEvicted(nearTx.evicted());
                                else if (!F.isEmpty(req.nearKeys()))
                                    res.nearEvicted(req.nearKeys());

                                fut.onResult(res);
                            }
                            catch (GridDistributedLockCancelledException e) {
                                onError(e);

                                break; // For
                            }
                        }
                        else {
                            if (log.isDebugEnabled())
                                log.debug("Sending DHT lock request to near node [node=" + n.id() +
                                    ", req=" + req + ']');

                            cctx.io().send(n, req);
                        }
                    }
                    catch (GridTopologyException e) {
                        fut.onResult(e);
                    }
                    catch (GridException e) {
                        onError(e);

                        break; // For
                    }
                }
            }
        }

        return ret;
    }

    /**
     * @param req Request.
     * @param e Entry.
     * @return Entry.
     */
    private GridDhtCacheEntry<K, V> addOwned(GridDhtLockRequest<K, V> req, GridDhtCacheEntry<K, V> e) {
        while (true) {
            try {
                GridCacheMvccCandidate<K> owner = e.localOwner();

                if (owner != null)
                    req.owned(e.key(), owner.version(), owner.otherVersion());

                break;
            }
            catch (GridCacheEntryRemovedException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Got removed entry when creating DHT lock request (will retry): " + e);

                e = cctx.dht().entryExx(e.key(), topVer);
            }
        }

        return e;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return futId.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtLockFuture.class, this, super.toString());
    }

    /**
     * Lock request timeout object.
     */
    private class LockTimeoutObject implements GridTimeoutObject {
        /** End time. */
        private final long endTime = System.currentTimeMillis() + timeout;

        /** {@inheritDoc} */
        @Override public GridUuid timeoutId() {
            return lockVer.id();
        }

        /** {@inheritDoc} */
        @Override public long endTime() {
            // Account for overflow.
            return endTime < 0 ? Long.MAX_VALUE : endTime;
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"ThrowableInstanceNeverThrown"})
        @Override public void onTimeout() {
            if (log.isDebugEnabled())
                log.debug("Timed out waiting for lock response: " + this);

            timedOut = true;

            onComplete(false);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(LockTimeoutObject.class, this);
        }
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<Boolean> {
        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** Node. */
        @GridToStringExclude
        private GridNode node;

        /** DHT mapping. */
        @GridToStringInclude
        private List<GridDhtCacheEntry<K, V>> dhtMapping;

        /** Near mapping. */
        @GridToStringInclude
        private List<GridDhtCacheEntry<K, V>> nearMapping;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            // No-op.
        }

        /**
         * @param node Node.
         * @param dhtMapping Mapping.
         * @param nearMapping nearMapping.
         */
        MiniFuture(GridNode node, List<GridDhtCacheEntry<K, V>> dhtMapping, List<GridDhtCacheEntry<K, V>> nearMapping) {
            super(cctx.kernalContext());

            assert node != null;

            this.node = node;
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
        public GridNode node() {
            return node;
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
                tx.removeMapping(node.id());

            onDone(true);
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridDhtLockResponse<K, V> res) {
            if (res.error() != null)
                // Fail the whole compound future.
                onError(res.error());
            else {
                if (nearMapping != null && !F.isEmpty(res.nearEvicted())) {
                    if (tx != null) {
                        GridDistributedTxMapping<K, V> m = tx.nearMapping(node.id());

                        if (m != null)
                            m.evictReaders(res.nearEvicted());
                    }

                    evictReaders(cctx, res.nearEvicted(), node.id(), res.messageId(), nearMapping);
                }

                Set<Integer> invalidParts = res.invalidPartitions();

                // Removing mappings for invalid partitions.
                if (!F.isEmpty(invalidParts)) {
                    for (Iterator<GridDhtCacheEntry<K, V>> it = dhtMapping.iterator(); it.hasNext();) {
                        GridDhtCacheEntry<K, V> entry = it.next();

                        if (invalidParts.contains(entry.partition())) {
                            it.remove();

                            if (log.isDebugEnabled())
                                log.debug("Removed mapping for entry [nodeId=" + node.id() + ", entry=" + entry +
                                    ", fut=" + GridDhtLockFuture.this + ']');

                            if (tx != null)
                                tx.removeDhtMapping(node.id(), entry);
                        }
                    }

                    if (dhtMapping.isEmpty())
                        dhtMap.remove(node);
                }

                // Finish mini future.
                onDone(true);
            }
        }

        /**
         * @param cacheCtx Context.
         * @param keys Keys to evict readers for.
         * @param nodeId Node ID.
         * @param msgId Message ID.
         * @param entries Entries to check.
         */
        @SuppressWarnings({"ForLoopReplaceableByForEach"})
        private void evictReaders(GridCacheContext<K, V> cacheCtx, Collection<K> keys, UUID nodeId, long msgId,
            @Nullable List<GridDhtCacheEntry<K, V>> entries) {
            if (entries == null || keys == null || entries.isEmpty() || keys.isEmpty())
                return;

            for (ListIterator<GridDhtCacheEntry<K, V>> it = entries.listIterator(); it.hasNext(); ) {
                GridDhtCacheEntry<K, V> cached = it.next();

                if (keys.contains(cached.key())) {
                    while (true) {
                        try {
                            cached.removeReader(nodeId, msgId);

                            if (tx != null)
                                tx.removeNearMapping(nodeId, cached);

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            GridDhtCacheEntry<K, V> e = cacheCtx.dht().peekExx(cached.key());

                            if (e == null)
                                break;

                            it.set(e);
                        }
                    }
                }
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, "nodeId", node.id(), "super", super.toString());
        }
    }
}

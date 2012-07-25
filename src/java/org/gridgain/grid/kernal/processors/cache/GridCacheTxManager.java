// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.GridSystemProperties.*;
import static org.gridgain.grid.cache.GridCacheTxState.*;
import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;
import static org.gridgain.grid.util.GridConcurrentFactory.*;

/**
 * Cache transaction manager.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheTxManager<K, V> extends GridCacheManager<K, V> {
    /** Maximum number of transactions that have completed (initialized to 100K). */
    private static final int MAX_COMPLETED_TX_CNT = Integer.getInteger(GG_MAX_COMPLETED_TX_COUNT, 102400);

    /** Slow tx warn timeout (initialized to 0). */
    private static final int SLOW_TX_WARN_TIMEOUT = Integer.getInteger(GG_SLOW_TX_WARN_TIMEOUT, 0);

    /** Committing transactions. */
    private final ThreadLocal<GridCacheTxEx> threadCtx = new GridThreadLocalEx<GridCacheTxEx>();

    /** Per-thread transaction map. */
    private final ConcurrentMap<Long, GridCacheTxEx<K, V>> threadMap = newMap();

    /** Per-ID map. */
    private final ConcurrentMap<GridCacheVersion, GridCacheTxEx<K, V>> idMap = newMap();

    /** All transactions. */
    private final Queue<GridCacheTxEx<K, V>> committedQ = new GridConcurrentLinkedDeque<GridCacheTxEx<K, V>>();

    /** Preparing transactions. */
    private final Queue<GridCacheTxEx<K, V>> prepareQ = new GridConcurrentLinkedDeque<GridCacheTxEx<K, V>>();

    /** Minimum start version. */
    private final ConcurrentNavigableMap<GridCacheVersion, AtomicInt> startVerCnts =
        new ConcurrentSkipListMap<GridCacheVersion, AtomicInt>();

    /** Committed local transactions. */
    private final GridBoundedConcurrentOrderedSet<GridCacheVersion> committedVers =
        new GridBoundedConcurrentOrderedSet<GridCacheVersion>(MAX_COMPLETED_TX_CNT);

    /** Rolled back local transactions. */
    private final NavigableSet<GridCacheVersion> rolledbackVers =
        new GridBoundedConcurrentOrderedSet<GridCacheVersion>(MAX_COMPLETED_TX_CNT);

    /** Transaction synchronizations. */
    private final Collection<GridCacheTxSynchronization> syncs =
        new GridConcurrentHashSet<GridCacheTxSynchronization>();

    /** Slow tx warn timeout. */
    private int slowTxWarnTimeout = SLOW_TX_WARN_TIMEOUT;

    /**
     * Near version to DHT version map. Note that we initialize to 5K size from get go,
     * to avoid future map resizings.
     */
    private final ConcurrentMap<GridCacheVersion, GridCacheVersion> mappedVers =
        new ConcurrentHashMap<GridCacheVersion, GridCacheVersion>(5120);

    /** {@inheritDoc} */
    @Override protected void onKernalStart0() {
        cctx.events().addListener(
            new GridLocalEventListener() {
                @Override public void onEvent(GridEvent evt) {
                    assert evt instanceof GridDiscoveryEvent;
                    assert evt.type() == EVT_NODE_FAILED || evt.type() == EVT_NODE_LEFT;

                    GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                    for (GridCacheTxEx<K, V> tx : idMap.values()) {
                        if ((!tx.local() || tx.dht()) && !tx.ec() &&
                            tx.masterNodeIds().contains(discoEvt.eventNodeId())) {
                            if (log.isDebugEnabled())
                                log.debug("Remaining transaction from left node: " + tx);

                            salvageTx(tx, true);
                        }
                    }
                }
            },
            EVT_NODE_FAILED, EVT_NODE_LEFT);

        for (GridCacheTxEx<K, V> tx : idMap.values()) {
            if ((!tx.local() || tx.dht()) && !tx.ec() && !cctx.discovery().aliveAll(tx.masterNodeIds())) {
                if (log.isDebugEnabled())
                    log.debug("Remaining transaction from left node: " + tx);

                salvageTx(tx, true);
            }
        }
    }

    /**
     * Invalidates transaction.
     *
     * @param tx Transaction.
     * @return {@code True} if transaction was salvaged by this call.
     */
    public boolean salvageTx(GridCacheTxEx<K, V> tx) {
        return salvageTx(tx, false);
    }

    /**
     * Invalidates transaction.
     *
     * @param tx Transaction.
     * @param warn {@code True} if warning should be logged.
     * @return {@code True} if transaction was salvaged by this call.
     */
    private boolean salvageTx(GridCacheTxEx<K, V> tx, boolean warn) {
        assert tx != null;

        GridCacheTxState state = tx.state();

        if (state == ACTIVE || state == PREPARING || state == PREPARED) {
            try {
                if (!tx.markFinalizing()) {
                    if (log.isDebugEnabled())
                        log.debug("Will not try to commit invalidate transaction (could not mark finalized): " + tx);

                    return false;
                }

                tx.systemInvalidate(true);

                tx.prepare();

                if (tx.state() == PREPARING) {
                    if (log.isDebugEnabled())
                        log.debug("Ignoring transaction in PREPARING state as it is currently handled " +
                            "by another thread: " + tx);

                    return false;
                }

                if (tx instanceof GridCacheTxRemoteEx) {
                    GridCacheTxRemoteEx<K, V> rmtTx = (GridCacheTxRemoteEx<K, V>)tx;

                    rmtTx.doneRemote(tx.xidVersion(), Collections.<GridCacheVersion>emptyList(),
                        Collections.<GridCacheVersion>emptyList());
                }

                tx.commit();

                if (warn) {
                    // This print out cannot print any peer-deployed entity either
                    // directly or indirectly.
                    U.warn(log, "Invalidated transaction because originating node either " +
                        "crashed or left grid: " + CU.txString(tx));
                }
            }
            catch (GridCacheTxOptimisticException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Optimistic failure while invalidating transaction (will rollback): " +
                        tx.xidVersion());

                try {
                    tx.rollback();
                }
                catch (GridException e) {
                    U.error(log, "Failed to rollback transaction: " + tx.xidVersion(), e);
                }
            }
            catch (GridException e) {
                U.error(log, "Failed to invalidate transaction: " + tx, e);
            }
        }
        else if (state == MARKED_ROLLBACK) {
            try {
                tx.rollback();
            }
            catch (GridException e) {
                U.error(log, "Failed to rollback transaction: " + tx.xidVersion(), e);
            }
        }

        return true;
    }

    /**
     * Prints out memory stats to standard out.
     * <p>
     * USE ONLY FOR MEMORY PROFILING DURING TESTS.
     */
    @Override public void printMemoryStats() {
        GridCacheTxEx<K, V> firstTx = committedQ.peek();

        int committedSize = committedQ.size();

        Map.Entry<GridCacheVersion, AtomicInt> startVerEntry = startVerCnts.firstEntry();

        GridCacheVersion minStartVer = null;
        long dur = 0;

        if (committedSize > 3000) {
            minStartVer = new GridCacheVersion(Long.MAX_VALUE, new GridUuid(cctx.nodeId(), Long.MAX_VALUE));

            GridCacheTxEx<K, V> stuck = null;

            for (GridCacheTxEx<K, V> tx : idMap.values())
                if (tx.startVersion().isLess(minStartVer)) {
                    minStartVer = tx.startVersion();
                    dur = System.currentTimeMillis() - tx.startTime();

                    stuck = tx;
                }

            X.println("Stuck transaction: " + stuck);
        }

        X.println(">>> ");
        X.println(">>> Transaction manager memory stats [grid=" + cctx.gridName() + ", cache=" + cctx.name() + ']');
        X.println(">>>   threadMapSize: " + threadMap.size());
        X.println(">>>   idMap [size=" + idMap.size() + ", minStartVer=" + minStartVer + ", dur=" + dur + "ms]");
        X.println(">>>   committedQueue [size=" + committedSize +
            ", firstStartVersion=" + (firstTx == null ? "null" : firstTx.startVersion()) +
            ", firstEndVersion=" + (firstTx == null ? "null" : firstTx.endVersion()) + ']');
        X.println(">>>   prepareQueueSize: " + prepareQ.size());
        X.println(">>>   startVerCntsSize [size=" + startVerCnts.size() +
            ", firstVer=" + startVerEntry + ']');
        X.println(">>>   committedVersSize: " + committedVers.size());
        X.println(">>>   rolledbackVersSize: " + rolledbackVers.size());
    }

    /**
     * @return Thread map size.
     */
    public int threadMapSize() {
        return threadMap.size();
    }

    /**
     * @return ID map size.
     */
    public int idMapSize() {
        return idMap.size();
    }

    /**
     * @return Committed queue size.
     */
    public int commitQueueSize() {
        return committedQ.size();
    }

    /**
     * @return Prepare queue size.
     */
    public int prepareQueueSize() {
        return prepareQ.size();
    }

    /**
     * @return Start version counts.
     */
    public int startVersionCountsSize() {
        return startVerCnts.size();
    }

    /**
     * @return Committed versions size.
     */
    public int committedVersionsSize() {
        return committedVers.size();
    }

    /**
     * @return Rolled back versions size.
     */
    public int rolledbackVersionsSize() {
        return rolledbackVers.size();
    }

    /**
     *
     * @param tx Transaction to check.
     * @return {@code True} if transaction has been committed or rolled back,
     *      {@code false} otherwise.
     */
    public boolean isCompleted(GridCacheTxEx<K, V> tx) {
        return committedVers.contains(tx.xidVersion()) || rolledbackVers.contains(tx.xidVersion());
    }

    /**
     * @param tx Created transaction.
     * @return Started transaction.
     */
    @Nullable public <T extends GridCacheTxEx<K, V>> T onCreated(T tx) {
        // Start clean.
        txContextReset();

        if (isCompleted(tx)) {
            if (log.isDebugEnabled())
                log.debug("Attempt to create a completed transaction (will ignore): " + tx);

            return null;
        }

        GridCacheTxEx<K, V> t;

        if ((t = idMap.putIfAbsent(tx.xidVersion(), tx)) == null) {
            // Add both, explicit and implicit transactions.
            // It's OK if in case of async operations multiple
            // transactions are started by the same thread. In this
            // case, thread map will contain the latest transaction.
            threadMap.put(tx.threadId(), tx);

            // Handle mapped versions.
            if (tx instanceof GridCacheMappedVersion) {
                GridCacheMappedVersion mapped = (GridCacheMappedVersion)tx;

                GridCacheVersion from = mapped.mappedVersion();

                if (from != null)
                    mappedVers.put(from, tx.xidVersion());

                if (log.isDebugEnabled())
                    log.debug("Added transaction version mapping [from=" + from + ", to=" + tx.xidVersion() +
                        ", tx=" + tx + ']');
            }
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Attempt to create an existing transaction (will ignore) [newTx=" + tx + ", existingTx=" +
                    t + ']');

            return null;
        }

        if (cctx.config().isTxSerializableEnabled()) {
            while (true) {
                AtomicInt prev = startVerCnts.putIfAbsent(tx.startVersion(), new AtomicInt(1));

                // If there was a previous counter.
                if (prev != null) {
                    assert prev.get() >= 0;

                    // Previous value was 0, which means that it will be deleted
                    // by another thread in "decrementStartVersionCount(..)" method.
                    // In that case, we delete here too, so we can safely try again.
                    if (prev.incrementAndGet() == 1) {
                        if (startVerCnts.remove(tx.startVersion(), prev))
                            if (log.isDebugEnabled())
                                log.debug("Removed count from onCreated callback: " + tx);

                        continue;
                    }
                }

                break;
            }
        }

        if (tx.timeout() > 0) {
            cctx.time().addTimeoutObject(tx);

            if (log.isDebugEnabled())
                log.debug("Registered transaction with timeout processor: " + tx);
        }

        if (log.isDebugEnabled())
            log.debug("Transaction created: " + tx);

        return tx;
    }

    /**
     * Transaction start callback (has to do with when any operation was
     * performed on this transaction).
     *
     * @param tx Started transaction.
     * @return {@code True} if transaction is not in completed set.
     */
    public boolean onStarted(GridCacheTxEx<K, V> tx) {
        assert tx.state() == ACTIVE || tx.isRollbackOnly() : "Invalid transaction state [locId=" + cctx.nodeId() +
            ", tx=" + tx + ']';

        if (isCompleted(tx)) {
            if (log.isDebugEnabled())
                log.debug("Attempt to start a completed transaction (will ignore): " + tx);

            return false;
        }

        onTxStateChange(null, ACTIVE, tx);

        if (log.isDebugEnabled())
            log.debug("Transaction started: " + tx);

        return true;
    }

    /**
     * Reverse mapped version look up.
     *
     * @param dhtVer Dht version.
     * @return Near version.
     */
    @Nullable public GridCacheVersion nearVersion(GridCacheVersion dhtVer) {
        if (cctx.isDht()) {
            GridCacheTxEx<K, V> tx = idMap.get(dhtVer);

            if (tx != null) {
                GridDhtTxLocal<K, V> dhtTx = (GridDhtTxLocal<K, V>)tx;

                return dhtTx.nearXidVersion();
            }
        }

        return null;
    }

    /**
     * @param from Near version.
     * @return DHT version for a near version.
     */
    public GridCacheVersion mappedVersion(GridCacheVersion from) {
        GridCacheVersion to = mappedVers.get(from);

        if (log.isDebugEnabled())
            log.debug("Found mapped version [from=" + from + ", to=" + to);

        return to;
    }

    /**
     *
     * @param ver Alternate version.
     * @param tx Transaction.
     */
    public void addAlternateVersion(GridCacheVersion ver, GridCacheTxEx<K, V> tx) {
        if (idMap.putIfAbsent(ver, tx) == null)
            if (log.isDebugEnabled())
                log.debug("Registered alternate transaction version [ver=" + ver + ", tx=" + tx + ']');
    }

    /**
     * @return Local transaction.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public <T> T localTx() {
        GridCacheTxEx<K, V> tx = tx();

        return tx != null && tx.local() ? (T)tx : null;
    }

    /**
     * @return Transaction for current thread.
     */
    @SuppressWarnings({"unchecked"})
    public <T> T threadLocalTx() {
        GridCacheTxEx<K, V> tx = tx(Thread.currentThread().getId());

        return tx != null && tx.local() && !tx.dht() && !tx.implicit() ? (T)tx : null;
    }

    /**
     * @return Transaction for current thread.
     */
    @SuppressWarnings({"unchecked"})
    public <T> T tx() {
        GridCacheTxEx<K, V> tx = txContext();

        return tx != null ? (T)tx : (T)tx(Thread.currentThread().getId());
    }

    /**
     * @return Local transaction.
     */
    @Nullable public GridCacheTxEx<K, V> localTxx() {
        GridCacheTxEx<K, V> tx = txx();

        return tx != null && tx.local() ? tx : null;
    }

    /**
     * @return Transaction for current thread.
     */
    @SuppressWarnings({"unchecked"})
    public GridCacheTxEx<K, V> txx() {
        return (GridCacheTxEx<K, V>)tx();
    }

    /**
     * @return User transaction for current thread.
     */
    @Nullable public GridCacheTx userTx() {
        GridCacheTxEx<K, V> tx = txContext();

        if (tx != null && tx.user() && tx.state() == ACTIVE)
            return tx;

        tx = tx(Thread.currentThread().getId());

        return tx != null && tx.user() && tx.state() == ACTIVE ? tx : null;
    }

    /**
     * @return User transaction.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public <T extends GridCacheTxLocalEx<K, V>> T userTxx() {
        return (T)userTx();
    }

    /**
     * @param threadId Id of thread for transaction.
     * @return Transaction for thread with given ID.
     */
    @SuppressWarnings({"unchecked"})
    public <T> T tx(long threadId) {
        return (T)threadMap.get(threadId);
    }

    /**
     * @return {@code True} if current thread is currently within transaction.
     */
    public boolean inUserTx() {
        return userTx() != null;
    }

    /**
     * @param txId Transaction ID.
     * @return Transaction with given ID.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public <T extends GridCacheTxEx<K, V>> T tx(GridCacheVersion txId) {
        return (T)idMap.get(txId);
    }

    /**
     * @param txId Transaction ID.
     * @return Transaction with given ID.
     */
    @Nullable public GridCacheTxEx<K, V> txx(GridCacheVersion txId) {
        return idMap.get(txId);
    }

    /**
     * Handles prepare stage of 2PC.
     *
     * @param tx Transaction to prepare.
     * @throws GridException If preparation failed.
     */
    public void prepareTx(GridCacheTxEx<K, V> tx) throws GridException {
        if (tx.state() == MARKED_ROLLBACK) {
            if (tx.timedOut())
                throw new GridCacheTxTimeoutException("Transaction timed out: " + this);

            throw new GridException("Transaction is marked for rollback: " + tx);
        }

        if (tx.remainingTime() == 0) {
            tx.setRollbackOnly();

            throw new GridCacheTxTimeoutException("Transaction timed out: " + this);
        }

        // Clean up committed transactions queue.
        if (tx.pessimistic() || tx.ec()) {
            if (tx.enforceSerializable() && cctx.config().isTxSerializableEnabled()) {
                for (Iterator<GridCacheTxEx<K, V>> it = committedQ.iterator(); it.hasNext();) {
                    GridCacheTxEx<K, V> committedTx = it.next();

                    assert committedTx != tx;

                    // Clean up.
                    if (isSafeToForget(committedTx))
                        it.remove();
                }
            }

            if (tx.pessimistic())
                // Nothing else to do in pessimistic mode.
                return;
        }

        if (cctx.config().isTxSerializableEnabled() && tx.optimistic() && tx.enforceSerializable()) {
            Set<K> readSet = tx.readSet();
            Set<K> writeSet = tx.writeSet();

            GridCacheVersion startTn = tx.startVersion();

            GridCacheVersion finishTn = cctx.versions().last();

            prepareQ.offer(tx);

            // Check that our read set does not intersect with write set
            // of all transactions that completed their write phase
            // while our transaction was in read phase.
            for (Iterator<GridCacheTxEx<K, V>> it = committedQ.iterator(); it.hasNext();) {
                GridCacheTxEx<K, V> committedTx = it.next();

                assert committedTx != tx;

                // Clean up.
                if (isSafeToForget(committedTx)) {
                    it.remove();

                    continue;
                }

                GridCacheVersion tn = committedTx.endVersion();

                // We only care about transactions
                // with tn > startTn and tn <= finishTn
                if (tn.compareTo(startTn) <= 0 || tn.compareTo(finishTn) > 0)
                    continue;

                if (tx.serializable()) {
                    if (GridFunc.intersects(committedTx.writeSet(), readSet)) {
                        tx.setRollbackOnly();

                        throw new GridCacheTxOptimisticException("Failed to prepare transaction " +
                            "(committed vs. read-set conflict): " + tx);
                    }
                }
            }

            // Check that our read and write sets do not intersect with write
            // sets of all active transactions.
            for (Iterator<GridCacheTxEx<K, V>> iter = prepareQ.iterator(); iter.hasNext();) {
                GridCacheTxEx<K, V> prepareTx = iter.next();

                if (prepareTx == tx)
                    // Skip yourself.
                    continue;

                // Optimistically remove completed transactions.
                if (prepareTx.done()) {
                    iter.remove();

                    if (log.isDebugEnabled())
                        log.debug("Removed finished transaction from active queue: " + prepareTx);

                    continue;
                }

                // Check if originating node left.
                if (cctx.discovery().node(prepareTx.nodeId()) == null) {
                    iter.remove();

                    rollbackTx(prepareTx);

                    if (log.isDebugEnabled())
                        log.debug("Removed and rolled back transaction because sender node left grid: " +
                            CU.txString(prepareTx));

                    continue;
                }

                if (tx.serializable() && !prepareTx.isRollbackOnly()) {
                    Set<K> prepareWriteSet = prepareTx.writeSet();

                    if (GridFunc.intersects(prepareWriteSet, readSet, writeSet)) {
                        // Remove from active set.
                        iter.remove();

                        tx.setRollbackOnly();

                        throw new GridCacheTxOptimisticException(
                            "Failed to prepare transaction (read-set/write-set conflict): " + tx);
                    }
                }
            }
        }

        assert tx.ec() || tx.optimistic();

        // OPTIMISTIC or EVENTUALLY_CONSISTENT.
        if (!lockMultiple(tx, tx.writeEntries())) {
            tx.setRollbackOnly();

            throw new GridCacheTxOptimisticException("Failed to prepare transaction (lock conflict): " + tx);
        }
    }

    /**
     * @param tx Transaction to check.
     * @return {@code True} if transaction can be discarded.
     */
    private boolean isSafeToForget(GridCacheTxEx<K, V> tx) {
        Map.Entry<GridCacheVersion, AtomicInt> e = startVerCnts.firstEntry();

        if (e == null)
            return true;

        assert e.getValue().get() >= 0;

        return tx.endVersion().compareTo(e.getKey()) <= 0;
    }

    /**
     * Decrement start version count.
     *
     * @param tx Cache transaction.
     */
    private void decrementStartVersionCount(GridCacheTxEx<K, V> tx) {
        AtomicInt cnt = startVerCnts.get(tx.startVersion());

        assert cnt != null : "Failed to find start version count for transaction [startVerCnts=" + startVerCnts +
            ", tx=" + tx + ']';

        assert cnt.get() > 0;

        if (cnt.decrementAndGet() == 0)
            if (startVerCnts.remove(tx.startVersion(), cnt))
                if (log.isDebugEnabled())
                    log.debug("Removed start version for transaction: " + tx);
    }

    /**
     * @param tx Transaction.
     */
    private void removeObsolete(GridCacheTxEx<K, V> tx) {
        Collection<GridCacheTxEntry<K, V>> entries = tx.near() ? tx.allEntries() : tx.writeEntries();

        for (GridCacheTxEntry<K, V> entry : entries) {
            GridCacheEntryEx<K, V> cached = entry.cached();

            if (cached == null)
                cached = cctx.cache().peekEx(entry.key());

            // Near entries are marked obsolete in many cases even
            // if transaction operation is not DELETE.
            if (cached != null) {
                if (tx.near() || entry.op() == DELETE)
                    if (cached.obsolete())
                        cctx.cache().removeIfObsolete(entry.key());
            }

            if (tx.dht()) {
                GridNearCacheEntry<K, V> e = cctx.dht().near().peekExx(entry.key());

                if (e != null && e.obsolete())
                    cctx.dht().near().removeIfObsolete(e.key());
            }
        }
    }

    /**
     * @param c Collection to copy.
     * @return Copy of the collection.
     */
    private Collection<GridCacheVersion> copyOf(Iterable<GridCacheVersion> c) {
        Collection<GridCacheVersion> l = new LinkedList<GridCacheVersion>();

        for (GridCacheVersion v : c)
            l.add(v);

        return l;
    }

    /**
     * Gets committed transactions starting from the given version (inclusive). // TODO: why inclusive?
     *
     * @param min Start (or minimum) version.
     * @return Committed transactions starting from the given version (non-inclusive).
     */
    public Collection<GridCacheVersion> committedVersions(GridCacheVersion min) {
        Set<GridCacheVersion> set = committedVers.tailSet(min, true);

        return set == null || set.isEmpty() ? Collections.<GridCacheVersion>emptyList() : copyOf(set);
    }

    /**
     * Gets rolledback transactions starting from the given version (inclusive). // TODO: why inclusive?
     *
     * @param min Start (or minimum) version.
     * @return Committed transactions starting from the given version (non-inclusive).
     */
    public Collection<GridCacheVersion> rolledbackVersions(GridCacheVersion min) {
        Set<GridCacheVersion> set = rolledbackVers.tailSet(min, true);

        return set == null || set.isEmpty() ? Collections.<GridCacheVersion>emptyList() : copyOf(set);
    }

    /**
     * @param tx Committed transaction.
     * @return If transaction was not already present in committed set.
     */
    public boolean addCommittedTx(GridCacheTxEx<K, V> tx) {
        return addCommittedTx(tx.xidVersion());
    }

    /**
     * @param tx Committed transaction.
     * @return If transaction was not already present in committed set.
     */
    public boolean addRolledbackTx(GridCacheTxEx<K, V> tx) {
        return addRolledbackTx(tx.xidVersion());
    }

    /**
     * @param xidVer Completed transaction version.
     * @return If transaction was not already present in completed set.
     */
    public boolean addCommittedTx(GridCacheVersion xidVer) {
        assert !rolledbackVers.contains(xidVer);

        if (committedVers.add(xidVer)) {
            if (log.isDebugEnabled())
                log.debug("Added transaction to committed version set: " + xidVer);

            return true;
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Transaction is already present in committed version set: " + xidVer);

            return false;
        }
    }

    /**
     * @param xidVer Completed transaction version.
     * @return If transaction was not already present in completed set.
     */
    public boolean addRolledbackTx(GridCacheVersion xidVer) {
        assert !committedVers.contains(xidVer);

        if (rolledbackVers.add(xidVer)) {
            if (log.isDebugEnabled())
                log.debug("Added transaction to rolled back version set: " + xidVer);

            return true;
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Transaction is already present in rolled back version set: " + xidVer);

            return false;
        }
    }

    /**
     * @param tx Transaction.
     */
    private void processCompletedEntries(GridCacheTxEx<K, V> tx) {
        if (tx.needsCompletedVersions()) {
            GridCacheVersion min = minVersion(tx.readEntries(), tx.xidVersion(), tx);

            min = minVersion(tx.writeEntries(), min, tx);

            assert min != null;

            tx.completedVersions(min, committedVersions(min), rolledbackVersions(min));
        }
    }

    /**
     * Go through all candidates for entries involved in transaction and find their min
     * version. We know that these candidates will commit after this transaction, and
     * therefore we can grab the min version so we can send all committed and rolled
     * back versions from min to current to remote nodes for re-ordering.
     *
     * @param entries Entries.
     * @param min Min version so far.
     * @param tx Transaction.
     * @return Minimal available version.
     */
    private GridCacheVersion minVersion(Iterable<GridCacheTxEntry<K, V>> entries, GridCacheVersion min,
        GridCacheTxEx<K, V> tx) {
        for (GridCacheTxEntry<K, V> txEntry : entries) {
            GridCacheEntryEx<K, V> cached = txEntry.cached();

            // We are assuming that this method is only called on commit. In that
            // case, if lock is held, entry can never be removed.
            assert tx.ec() || txEntry.isRead() || !cached.obsolete(tx.xidVersion()) :
                "Invalid obsolete version for transaction [entry=" + cached + ", tx=" + tx + ']';

            for (GridCacheMvccCandidate<K> cand : cached.remoteMvccSnapshot())
                if (min == null || cand.version().isLess(min))
                    min = cand.version();
        }

        return min;
    }

    /**
     * Commits a transaction.
     *
     * @param tx Transaction to commit.
     */
    public void commitTx(GridCacheTxEx<K, V> tx) {
        assert tx != null;
        assert tx.state() == COMMITTING : "Invalid transaction state for commit from tm [state=" + tx.state() +
            ", expected=COMMITTING, tx=" + tx + ']';

        if (log.isDebugEnabled())
            log.debug("Committing from TM: " + tx);

        if (tx.timeout() > 0) {
            cctx.time().removeTimeoutObject(tx);

            if (log.isDebugEnabled())
                log.debug("Unregistered transaction with timeout processor: " + tx);
        }

        /*
         * Note that write phase is handled by transaction adapter itself,
         * so we don't do it here.
         */

        // 1. Make sure that committed version has been recorded.
        assert tx.ec() || committedVers.contains(tx.xidVersion()) || tx.writeSet().isEmpty() :
            "Missing commit version [ver=" + tx.xidVersion() + ", firstVer=" + committedVers.firstx() +
                ", lastVer=" + committedVers.lastx() + ", tx=" + tx + ']';

        if (idMap.remove(tx.xidVersion(), tx)) {
            // 2. Must process completed entries before unlocking!
            processCompletedEntries(tx);

            // 3. Add to eviction policy queue prior to unlocking for proper ordering.
            cctx.evicts().touch(tx);

            // 3.1 Call dataStructures manager.
            cctx.dataStructures().onTxCommitted(tx);

            // 4. Unlock write resources.
            unlockMultiple(tx, tx.writeEntries());

            // 5. For pessimistic transaction, unlock read resources if required.
            if (tx.pessimistic() && !tx.readCommitted())
                unlockMultiple(tx, tx.readEntries());

            // 6. Remove obsolete entries from cache.
            removeObsolete(tx);

            // 7. Assign transaction number at the end of transaction.
            tx.endVersion(cctx.versions().next());

            // 8. Clean start transaction number for this transaction.
            if (cctx.config().isTxSerializableEnabled())
                decrementStartVersionCount(tx);

            // 9. Add to committed queue only if it is possible
            //    that this transaction can affect other ones.
            if (cctx.config().isTxSerializableEnabled() && tx.enforceSerializable() && !isSafeToForget(tx))
                committedQ.add(tx);

            // 10. Remove from per-thread storage.
            threadMap.remove(tx.threadId(), tx);

            // 11. Unregister explicit locks.
            if (!tx.alternateVersions().isEmpty())
                for (GridCacheVersion ver : tx.alternateVersions())
                    idMap.remove(ver);

            // 12. Remove Near-2-DHT mappings.
            if (tx instanceof GridCacheMappedVersion) {
                GridCacheVersion mapped = ((GridCacheMappedVersion)tx).mappedVersion();

                if (mapped != null)
                    mappedVers.remove(mapped);
            }

            // 13. Clear context.
            txContextReset();

            // 14. Update metrics.
            if (!tx.dht() && tx.local())
                cctx.cache().metrics0().onTxCommit();

            if (slowTxWarnTimeout > 0 && tx.local() &&
                System.currentTimeMillis() - tx.startTime() > slowTxWarnTimeout)
                U.warn(log, "Slow transaction detected [tx=" + tx +
                    ", slowTxWarnTimeout=" + slowTxWarnTimeout + ']') ;

            if (log.isDebugEnabled())
                log.debug("Committed from TM: " + tx);
        }
        else if (log.isDebugEnabled())
            log.debug("Did not commit from TM (was already committed): " + tx);
    }

    /**
     * Rolls back a transaction.
     *
     * @param tx Transaction to rollback.
     */
    public void rollbackTx(GridCacheTxEx<K, V> tx) {
        assert tx != null;

        if (log.isDebugEnabled())
            log.debug("Rolling back from TM: " + tx);

        // 1. Record transaction version to avoid duplicates.
        addRolledbackTx(tx);

        if (idMap.remove(tx.xidVersion(), tx)) {
            // 2. Add to eviction policy queue prior to unlocking for proper ordering.
            cctx.evicts().touch(tx);

            // 3. Unlock write resources.
            unlockMultiple(tx, tx.writeEntries());

            // 4. For pessimistic transaction, unlock read resources if required.
            if (tx.pessimistic() && !tx.readCommitted())
                unlockMultiple(tx, tx.readEntries());

            // 5. Clean start transaction number for this transaction.
            if (cctx.config().isTxSerializableEnabled())
                decrementStartVersionCount(tx);

            // 6. Remove from per-thread storage.
            threadMap.remove(tx.threadId(), tx);

            // 7. Unregister explicit locks.
            if (!tx.alternateVersions().isEmpty())
                for (GridCacheVersion ver : tx.alternateVersions())
                    idMap.remove(ver);

            // 8. Remove Near-2-DHT mappings.
            if (tx instanceof GridDhtTxLocal)
                mappedVers.remove(((GridDhtTxLocal)tx).nearXidVersion());

            // 9. Clear context.
            txContextReset();

            // 10. Update metrics.
            if (!tx.dht() && tx.local())
                cctx.cache().metrics0().onTxRollback();

            if (log.isDebugEnabled())
                log.debug("Rolled back from TM: " + tx);
        }
        else if (log.isDebugEnabled())
            log.debug("Did not rollback from TM (was already rolled back): " + tx);
    }

    /**
     * Callback invoked whenever a member of a transaction acquires
     * lock ownership.
     *
     * @param entry Cache entry.
     * @param owner Candidate that won ownership.
     * @return {@code True} if transaction was notified, {@code false} otherwise.
     */
    public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        // We only care about acquired locks.
        if (owner != null) {
            GridCacheTxAdapter<K, V> tx = tx(owner.version());

            if (tx != null) {
                if (!tx.local()) {
                    if (log.isDebugEnabled())
                        log.debug("Found transaction for owner changed event [owner=" + owner + ", entry=" + entry +
                            ", tx=" + tx + ']');

                    tx.onOwnerChanged(entry, owner);

                    return true;
                }
                else if (log.isDebugEnabled())
                    log.debug("Ignoring local transaction for owner change event: " + tx);
            }
            else if (log.isDebugEnabled())
                log.debug("Transaction not found for owner changed event [owner=" + owner + ", entry=" + entry + ']');
        }

        return false;
    }

    /**
     * @param tx Transaction.
     * @param entries Entries to lock.
     * @return {@code True} if all keys were locked.
     * @throws GridException If lock has been cancelled.
     */
    private boolean lockMultiple(GridCacheTxEx<K, V> tx, Iterable<GridCacheTxEntry<K, V>> entries)
        throws GridException {
        assert tx.optimistic() || tx.ec();

        long remainingTime = System.currentTimeMillis() - (tx.startTime() + tx.timeout());

        // For serializable transactions, failure to acquire lock means
        // that there is a serializable conflict. For all other isolation levels,
        // we wait for the lock.
        long timeout = tx.timeout() == 0 ? 0 : remainingTime;

        for (GridCacheTxEntry<K, V> txEntry1 : entries) {
            while (true) {
                try {
                    GridCacheEntryEx<K, V> entry1 = txEntry1.cached();

                    if (!entry1.tmLock(tx, timeout)) {
                        // Unlock locks locked so far.
                        for (GridCacheTxEntry<K, V> txEntry2 : entries) {
                            if (txEntry2 == txEntry1)
                                break;

                            txEntry2.cached().txUnlock(tx);
                        }

                        return false;
                    }

                    tx.addLocalCandidates(txEntry1.key(), entry1.localCandidates(tx.xidVersion()));

                    break;
                }
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry in TM lockMultiple(..) method (will retry): " + txEntry1);

                    try {
                        // Renew cache entry.
                        txEntry1.cached(cctx.cache().entryEx(txEntry1.key()), txEntry1.keyBytes());
                    }
                    catch (GridDhtInvalidPartitionException e) {
                        assert tx.dht() : "Received invalid partition for non DHT transaction [tx=" +
                            tx + ", invalidPart=" + e.partition() + ']';

                        // If partition is invalid, we ignore this entry.
                        tx.addInvalidPartition(e.partition());

                        break;
                    }
                }
                catch (GridDistributedLockCancelledException ignore) {
                    tx.setRollbackOnly();

                    throw new GridException("Entry lock has been cancelled for transaction: " + tx);
                }
            }
        }

        return true;
    }

    /**
     * @param tx Owning transaction.
     * @param entries Entries to unlock.
     */
    private void unlockMultiple(GridCacheTxEx<K, V> tx, Iterable<GridCacheTxEntry<K, V>> entries) {
        for (GridCacheTxEntry<K, V> txEntry : entries) {
            while (true) {
                try {
                    GridCacheEntryEx<K, V> entry = txEntry.cached();

                    assert entry != null;

                    entry.txUnlock(tx);

                    break;
                }
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry in TM unlockMultiple(..) method (will retry): " + txEntry);

                    // Renew cache entry.
                    txEntry.cached(cctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                }
            }
        }
    }

    /**
     * @param sync Transaction synchronizations to add.
     */
    public void addSynchronizations(GridCacheTxSynchronization... sync) {
        if (F.isEmpty(sync))
            return;

        F.copy(syncs, sync);
    }

    /**
     * @param sync Transaction synchronizations to remove.
     */
    public void removeSynchronizations(GridCacheTxSynchronization... sync) {
        if (F.isEmpty(sync))
            return;

        F.lose(syncs, false, Arrays.asList(sync));
    }

    /**
     * @return Registered transaction synchronizations
     */
    public Collection<GridCacheTxSynchronization> synchronizations() {
        return Collections.unmodifiableList(new LinkedList<GridCacheTxSynchronization>(syncs));
    }

    /**
     * @param prevState Previous state.
     * @param newState New state.
     * @param tx Cache transaction.
     */
    public void onTxStateChange(@Nullable GridCacheTxState prevState, GridCacheTxState newState, GridCacheTx tx) {
        // Notify synchronizations.
        for (GridCacheTxSynchronization s : syncs)
            s.onStateChanged(prevState, newState, tx);
    }

    /**
     * @param tx Committing transaction.
     */
    public void txContext(GridCacheTxEx tx) {
        threadCtx.set(tx);
    }

    /**
     * @return Currently committing transaction.
     */
    @SuppressWarnings({"unchecked"})
    private GridCacheTxEx<K, V> txContext() {
        return threadCtx.get();
    }

    /**
     * Gets version of transaction in tx context or {@code null}
     * if tx context is empty.
     * <p>
     * This is a convenience method provided mostly for debugging.
     *
     * @return Transaction version from transaction context.
     */
    @Nullable public GridCacheVersion txContextVersion() {
        GridCacheTxEx<K, V> tx = txContext();

        return tx == null ? null : tx.xidVersion();
    }

    /**
     * Commit ended.
     */
    public void txContextReset() {
        threadCtx.set(null);
    }

    /**
     * @return All transactions.
     */
    public Collection<GridCacheTxEx<K, V>> txs() {
        return idMap.values();
    }

    /**
     * @return Slow tx warn timeout.
     */
    public int slowTxWarnTimeout() {
        return slowTxWarnTimeout;
    }

    /**
     * @param slowTxWarnTimeout Slow tx warn timeout.
     */
    public void slowTxWarnTimeout(int slowTxWarnTimeout) {
        this.slowTxWarnTimeout = slowTxWarnTimeout;
    }

    /**
     * Atomic integer that compares only using references, not values.
     */
    private static final class AtomicInt extends AtomicInteger {
        /**
         * @param initVal Initial value.
         */
        private AtomicInt(int initVal) {
            super(initVal);
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object obj) {
            // Reference only.
            return obj == this;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return super.hashCode();
        }
    }
}

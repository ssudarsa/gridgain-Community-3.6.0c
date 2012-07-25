// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;

import java.util.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.cache.GridCachePreloadMode.*;

/**
 * Class that takes care about entries preloading in replicated cache.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridReplicatedPreloader<K, V> extends GridCachePreloaderAdapter<K, V> {
    /** Busy lock to control activeness of threads (loader, sender). */
    private final ReadWriteLock busyLock = new ReentrantReadWriteLock();

    /** Future to wait for the end of preloading on. */
    private final GridFutureAdapter<?> syncPreloadFut = new GridFutureAdapter(cctx.kernalContext());

    /** Lock to prevent preloading for the time of eviction. */
    private final ReentrantReadWriteLock evictLock = new ReentrantReadWriteLock();

    /** Supply pool. */
    private GridReplicatedPreloadSupplyPool<K, V> supplyPool;

    /** Demand pool. */
    private GridReplicatedPreloadDemandPool<K, V> demandPool;

    /** Eviction history. */
    private volatile Map<K, GridCacheVersion> evictHist = new HashMap<K, GridCacheVersion>();

    /**
     * @param cctx Cache context.
     */
    public GridReplicatedPreloader(GridCacheContext<K, V> cctx) {
        super(cctx);
    }

    /**
     * @throws GridException In case of error.
     */
    @Override public void start() throws GridException {
        demandPool = new GridReplicatedPreloadDemandPool<K, V>(cctx, busyLock, evictLock,
            new P2<K, GridCacheVersion>() {
                @Override public boolean apply(K key, GridCacheVersion ver) {
                    return preloadingPermitted(key, ver);
                }
            });

        supplyPool = new GridReplicatedPreloadSupplyPool<K, V>(cctx,
            new PA() {
                @Override public boolean apply() {
                    return syncPreloadFut.isDone();
                }
            }, busyLock);
    }

    /**
     * @throws GridException In case of error.
     */
    @Override public void onKernalStart() throws GridException {
        if (cctx.preloadEnabled()) {
            if (log.isDebugEnabled())
                log.debug("Creating initial assignments.");

            createAssignments(EVT_NODE_JOINED, syncPreloadFut);
        }

        supplyPool.start();
        demandPool.start();

        // Clear eviction history on preload finish.
        syncPreloadFut.listenAsync(
            new CIX1<GridFuture<?>>() {
                @Override public void applyx(GridFuture<?> gridFuture) {
                    // Wait until all eviction activities finish.
                    evictLock.writeLock().lock();

                    try {
                        evictHist = null;
                    }
                    finally {
                        evictLock.writeLock().unlock();
                    }
                }
            }
        );

        if (cctx.config().getPreloadMode() == SYNC) {
            U.log(log, "Starting preloading in SYNC mode: " + cctx.name());

            long start = System.currentTimeMillis();

            syncPreloadFut.get();

            U.log(log, "Completed preloading in SYNC mode [name=" + cctx.name() + ", time=" +
                (System.currentTimeMillis() - start) + " ms]");
        }
    }

    /**
     * @param discoEvtType Corresponding discovery event.
     * @param finishFut Finish future for assignments.
     */
    void createAssignments(final int discoEvtType, GridFutureAdapter<?> finishFut) {
        assert cctx.preloadEnabled();
        assert finishFut != null;

        long maxOrder = cctx.localNode().order() - 1; // Preload only from elder nodes.

        Collection<GridReplicatedPreloadAssignment> assigns = new LinkedList<GridReplicatedPreloadAssignment>();

        Collection<GridRichNode> rmts = CU.allNodes(cctx, maxOrder);

        if (!rmts.isEmpty()) {
            for (int part : partitions(cctx.localNode())) {
                Collection<GridRichNode> partNodes = cctx.config().getAffinity().nodes(part, rmts);

                int cnt = partNodes.size();

                if (cnt == 0)
                    continue;

                for (int mod = 0; mod < cnt; mod++) {
                    GridReplicatedPreloadAssignment assign =
                        new GridReplicatedPreloadAssignment(part, mod, cnt);

                    assigns.add(assign);

                    if (log.isDebugEnabled())
                        log.debug("Created assignment: " + assign);
                }
            }
        }

        cctx.events().addPreloadEvent(-1, EVT_CACHE_PRELOAD_STARTED, cctx.discovery().shadow(cctx.localNode()),
            discoEvtType, cctx.localNode().metrics().getNodeStartTime());

        if (!assigns.isEmpty())
            demandPool.assign(assigns, finishFut, maxOrder);
        else
            finishFut.onDone();

        // Preloading stopped event notification.
        finishFut.listenAsync(
            new CIX1<GridFuture<?>>() {
                @Override public void applyx(GridFuture<?> gridFuture) {
                    cctx.events().addPreloadEvent(-1, EVT_CACHE_PRELOAD_STOPPED,
                        cctx.discovery().shadow(cctx.localNode()),
                        discoEvtType, cctx.localNode().metrics().getNodeStartTime());
                }
            }
        );
    }

    /**
     * @param node Node.
     * @return Collection of partition numbers for the node.
     */
    Set<Integer> partitions(GridRichNode node) {
        assert node != null;

        GridCacheAffinity<Object> aff = cctx.config().getAffinity();

        Collection<GridRichNode> nodes = CU.allNodes(cctx);

        Set<Integer> parts = new HashSet<Integer>();

        int partCnt = aff.partitions();

        for (int i = 0; i < partCnt; i++) {
            Collection<GridRichNode> affNodes = aff.nodes(i, nodes);

            if (affNodes.contains(node))
                parts.add(i);
        }

        return parts;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    @Override public void onKernalStop() {
        if (log.isDebugEnabled())
            log.debug("Replicated preloader onKernalStop callback.");

        // Acquire write lock.
        busyLock.writeLock().lock();

        supplyPool.stop();
        demandPool.stop();

        if (log.isDebugEnabled())
            log.debug("Replicated preloader has been stopped.");
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> startFuture() {
        return cctx.config().getPreloadMode() != SYNC ? new GridFinishedFuture() : syncPreloadFut;
    }

    /**
     * Acquires lock for evictions to proceed (this makes preloading impossible).
     *
     * @return {@code True} if lock was acuired.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    public boolean lock() {
        if (!syncPreloadFut.isDone()) {
            evictLock.writeLock().lock();

            return true;
        }

        return false;
    }

    /**
     * Makes preloading possible.
     */
    public void unlock() {
        evictLock.writeLock().unlock();
    }

    /**
     * @param key Key.
     * @param ver Version.
     */
    public void onEntryEvicted(K key, GridCacheVersion ver) {
        assert key != null;
        assert ver != null;
        assert evictLock.isWriteLockedByCurrentThread(); // Only one thread can enter this method at a time.

        if (syncPreloadFut.isDone())
            // Ignore since preloading finished.
            return;

        Map<K, GridCacheVersion> evictHist0 = evictHist;

        assert evictHist0 != null;

        GridCacheVersion ver0 = evictHist0.get(key);

        if (ver0 == null || ver0.isLess(ver)) {
            GridCacheVersion ver1 = evictHist0.put(key, ver);

            assert ver1 == ver0;
        }
    }

    /**
     * @param key Key.
     * @param ver Version.
     * @return {@code True} if preloading is permitted.
     */
    public boolean preloadingPermitted(K key, GridCacheVersion ver) {
        assert key != null;
        assert ver != null;
        assert evictLock.getReadHoldCount() == 1;
        assert !syncPreloadFut.isDone();

        Map<K, GridCacheVersion> evictHist0 = evictHist;

        assert evictHist0 != null;

        GridCacheVersion ver0 = evictHist0.get(key);

        // Permit preloading if version in history
        // is missing or less than passed in.
        return ver0 == null || ver0.isLess(ver);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridReplicatedPreloader.class, this);
    }
}

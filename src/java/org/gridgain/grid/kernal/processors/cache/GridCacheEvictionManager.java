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
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.cache.distributed.replicated.preloader.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.GridSystemProperties.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.kernal.processors.cache.distributed.dht.GridDhtPartitionState.*;
import static org.gridgain.grid.lang.utils.GridConcurrentLinkedDeque.*;

/**
 * Cache eviction manager.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheEvictionManager<K, V> extends GridCacheManager<K, V> {
    /** Number of entries in the queue before unwinding happens. */
    private static final int ENTRY_UNWIND_THRESHOLD = Integer.getInteger(GG_EVICT_UNWIND_THRESHOLD, 100);

    /** How much are queues allowed to outgrow their maximums before they are forced to downsize. */
    private static final int QUEUE_OUTGROW_RATIO = 3;

    /** Eviction policy. */
    private GridCacheEvictionPolicy<K, V> policy;

    /** Eviction filter. */
    private GridCacheEvictionFilter<K, V> filter;

    /** Entries queue. */
    private final GridConcurrentLinkedDeque<GridCacheEntryEx<K, V>> entries =
        new GridConcurrentLinkedDeque<GridCacheEntryEx<K, V>>();

    /** Transactions queue. */
    private final GridConcurrentLinkedDeque<GridCacheTxEx<K, V>> txs =
        new GridConcurrentLinkedDeque<GridCacheTxEx<K, V>>();

    /** Entries queue size (including transaction entries). */
    private final AtomicInteger entryCnt = new AtomicInteger();

    /** Controlling lock for unwinding entries. */
    private final ReadWriteLock unwindLock = new ReentrantReadWriteLock();

    /** Unwinding flag. */
    private final AtomicBoolean unwinding = new AtomicBoolean(false);

    /** Eviction buffer. */
    private final GridConcurrentLinkedDeque<EvictionInfo> bufEvictQ = new GridConcurrentLinkedDeque<EvictionInfo>();

    /** Attribute name used to queue node in entry metadata. */
    private final String meta = UUID.randomUUID().toString();

    /** Evicting flag to make sure that only one thread processes eviction queue. */
    private final AtomicBoolean buffEvicting = new AtomicBoolean(false);

    /** Active eviction futures. */
    private final Map<Long, EvictionFuture> futs = new ConcurrentHashMap<Long, EvictionFuture>();

    /** Futures count modification lock. */
    private final Lock futsCntLock = new ReentrantLock();

    /** Futures count condition. */
    private final Condition futsCntCond = futsCntLock.newCondition();

    /** Active futures count. */
    private volatile int activeFutsCnt;

    /** Max active futures count. */
    private int maxActiveFuts;

    /** Generator of future IDs. */
    private final AtomicLong idGen = new AtomicLong();

    /** Entry unwind threshold. */
    private int entryUnwindThreshold = ENTRY_UNWIND_THRESHOLD;

    /** Evict backup synchronized flag. */
    private boolean evictSync;

    /** Evict near synchronized flag. */
    private boolean nearSync;

    /** Backup entries worker. */
    private BackupWorker backupWorker;

    /** Backup entries worker thread. */
    private GridThread backupWorkerThread;

    /** Busy lock. */
    private final GridBusyLock busyLock = new GridBusyLock();

    /** Stopping flag. */
    private final AtomicBoolean stopping = new AtomicBoolean();

    /** {@inheritDoc} */
    @Override public void start0() throws GridException {
        GridCacheConfigurationAdapter cfg = cctx.config();

        policy = cctx.isNear() ? cfg.<K, V>getNearEvictionPolicy() : cfg.<K, V>getEvictionPolicy();

        if (policy == null) {
            if (cctx.isNear())
                throw new GridException("Configuration parameter 'nearEvictionPolicy' cannot be null.");
            else
                throw new GridException("Configuration parameter 'evictionPolicy' cannot be null.");
        }

        filter = cfg.getEvictionFilter();

        if (cfg.getEvictMaxOverflowRatio() < 0)
            throw new GridException("Configuration parameter 'maxEvictionOverflowRatio' cannot be negative.");

        if (cfg.getEvictSynchronisedKeyBufferSize() < 0)
            throw new GridException("Configuration parameter 'evictionKeyBufferSize' cannot be negative.");

        evictSync = cfg.isEvictSynchronized() && (cctx.isReplicated() || cctx.isDht()) && !cctx.isSwapEnabled();

        nearSync = cfg.isEvictNearSynchronized() && cctx.isDht();

        reportConfigurationProblems();

        if (evictSync && cctx.isDht()) {
            backupWorker = new BackupWorker();

            cctx.events().addListener(
                new GridLocalEventListener() {
                    @Override public void onEvent(GridEvent evt) {
                        assert evt.type() == EVT_NODE_FAILED || evt.type() == EVT_NODE_LEFT ||
                            evt.type() == EVT_NODE_JOINED;

                        GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                        // Notify backup worker on each topology change.
                        backupWorker.addEvent(discoEvt);
                    }
                },
                EVT_NODE_FAILED, EVT_NODE_LEFT, EVT_NODE_JOINED);
        }

        if (evictSync || nearSync) {
            if (cfg.getEvictSynchronizedTimeout() <= 0)
                throw new GridException("Configuration parameter 'evictSynchronousTimeout' should be positive.");

            if (cfg.getEvictSynchronizedConcurrencyLevel() <= 0)
                throw new GridException("Configuration parameter 'evictSynchronousConcurrencyLevel' " +
                    "should be positive.");

            maxActiveFuts = cfg.getEvictSynchronizedConcurrencyLevel();

            cctx.io().addHandler(GridCacheEvictionRequest.class, new CI2<UUID, GridCacheEvictionRequest<K, V>>() {
                @Override public void apply(UUID nodeId, GridCacheEvictionRequest<K, V> msg) {
                    processEvictionRequest(nodeId, msg);
                }
            });

            cctx.io().addHandler(GridCacheEvictionResponse.class, new CI2<UUID, GridCacheEvictionResponse<K, V>>() {
                @Override public void apply(UUID nodeId, GridCacheEvictionResponse<K, V> msg) {
                    processEvictionResponse(nodeId, msg);
                }
            });

            cctx.events().addListener(
                new GridLocalEventListener() {
                    @Override public void onEvent(GridEvent evt) {
                        assert evt.type() == EVT_NODE_FAILED || evt.type() == EVT_NODE_LEFT;

                        GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                        for (EvictionFuture fut : futs.values())
                            fut.onNodeLeft(discoEvt.eventNodeId());
                    }
                },
                EVT_NODE_FAILED, EVT_NODE_LEFT);
        }

        if (log.isDebugEnabled())
            log.debug("Eviction manager started on node: " + cctx.nodeId());
    }

    /**
     * @return {@code True} if eviction policy tracking is enabled.
     */
    private boolean policyEnabled() {
        return cctx.isNear() && cctx.config().isNearEvictionEnabled() ||
            !cctx.isNear() && cctx.config().isEvictionEnabled();
    }

    /**
     * Outputs warnings if potential configuration problems are detected.
     */
    private void reportConfigurationProblems() {
        GridCacheMode mode = cctx.config().getCacheMode();

        if (!cctx.isNear()) {
            if ((mode == REPLICATED || mode == PARTITIONED) && !evictSync) {
                U.warn(log, "Evictions are not synchronized with other nodes in topology " +
                    "which may cause data inconsistency (consider changing 'evictSynchronized' " +
                    "configuration property).",
                    "Evictions are not synchronized for cache: " + cctx.namexx());
            }

            if (mode == PARTITIONED && !nearSync) {
                U.warn(log, "Evictions on primary node are not synchronized with near nodes " +
                    "which which may cause some entries not to be evicted (consider changing " +
                    "'nearEvictSynchronized' configuration property).",
                    "Evictions on primary node are not synchronized with near nodes for cache: " + cctx.namexx());
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected void onKernalStart0() throws GridException {
        super.onKernalStart0();

        if (evictSync && cctx.isDht()) {
            // Add dummy event to worker.
            backupWorker.addEvent(new GridDiscoveryEvent(cctx.localNodeId(), "Dummy event.", EVT_NODE_JOINED,
                cctx.localNodeId()));

            backupWorkerThread = new GridThread(backupWorker);
            backupWorkerThread.start();
        }
    }

    /** {@inheritDoc} */
    @Override protected void stop0(boolean cancel, boolean wait) {
        super.stop0(cancel, wait);

        // Change stopping first.
        stopping.set(true);

        busyLock.block();

        // Stop backup worker.
        if (evictSync && cctx.isDht() && backupWorker != null) {
            backupWorker.cancel();

            U.join(backupWorkerThread, log);
        }

        // Cancel all active futures.
        for (EvictionFuture fut : futs.values())
            fut.cancel();

        if (log.isDebugEnabled())
            log.debug("Eviction manager stopped on node: " + cctx.nodeId());
    }

    /**
     * This method is meant to be used for testing and potentially for management as well.
     *
     * @param entryUnwindThreshold Entry unwind threshold.
     */
    public void setEntryUnwindThreshold(int entryUnwindThreshold) {
        assert entryUnwindThreshold > 0;

        this.entryUnwindThreshold = entryUnwindThreshold;
    }

    /**
     * Resets unwind thresholds back to default values.
     */
    public void resetEntryUnwindThreshold() {
        entryUnwindThreshold = ENTRY_UNWIND_THRESHOLD;
    }

    /**
     * @return Current size of evict queue.
     */
    public int evictQueueSize() {
        return bufEvictQ.sizex();
    }

    /**
     * @param nodeId Sender node ID.
     * @param res Response.
     */
    private void processEvictionResponse(UUID nodeId, GridCacheEvictionResponse<K, V> res) {
        assert nodeId != null;
        assert res != null;

        if (log.isDebugEnabled())
            log.debug("Processing eviction response [node=" + nodeId + ", localNode=" + cctx.nodeId() +
                ", res=" + res + ']');

        if (!busyLock.enterBusy())
            return;

        try {
            EvictionFuture fut = futs.get(res.futureId());

            if (fut != null)
                fut.onResponse(nodeId, res);
            else if (log.isDebugEnabled())
                log.debug("Eviction future for response is not found [res=" + res + ", node=" + nodeId +
                    ", localNode=" + cctx.nodeId() + ']');
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param req Request.
     */
    private void processEvictionRequest(UUID nodeId, GridCacheEvictionRequest<K, V> req) {
        assert nodeId != null;
        assert req != null;

        if (!busyLock.enterBusy())
            return;

        try {
            if (req.classError() != null) {
                if (log.isDebugEnabled())
                    log.debug("Class got undeployed during eviction: " + req.classError());

                sendEvictionResponse(nodeId, new GridCacheEvictionResponse<K, V>(req.futureId(), true));

                return;
            }

            long topVer = lockTopology();

            try {
                if (topVer != req.topologyVersion()) {
                    if (log.isDebugEnabled())
                        log.debug("Topology version is different [locTopVer=" + topVer +
                            ", rmtTopVer=" + req.topologyVersion() + ']');

                    sendEvictionResponse(nodeId, new GridCacheEvictionResponse<K, V>(req.futureId(), true));

                    return;
                }

                processEvictionRequest0(nodeId, req);
            }
            finally {
                unlockTopology();
            }
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param req Request.
     */
    private void processEvictionRequest0(UUID nodeId, GridCacheEvictionRequest<K, V> req) {
        if (log.isDebugEnabled())
            log.debug("Processing eviction request [node=" + nodeId + ", localNode=" + cctx.nodeId() +
                ", reqSize=" + req.entries().size() + ']');

        // Partition -> {{Key, Version}, ...}.
        // Group DHT and replicated cache entries by their partitions.
        Map<Integer, Collection<GridTuple3<K, GridCacheVersion, Boolean>>> dhtEntries =
            new HashMap<Integer, Collection<GridTuple3<K, GridCacheVersion, Boolean>>>();

        Collection<GridTuple3<K, GridCacheVersion, Boolean>> nearEntries =
            new LinkedList<GridTuple3<K, GridCacheVersion, Boolean>>();

        for (GridTuple3<K, GridCacheVersion, Boolean> t : req.entries()) {
            Boolean near = t.get3();

            if (!near) {
                // Lock is required.
                Collection<GridTuple3<K, GridCacheVersion, Boolean>> col =
                    F.addIfAbsent(dhtEntries, cctx.partition(t.get1()),
                        new LinkedList<GridTuple3<K, GridCacheVersion, Boolean>>());

                assert col != null;

                col.add(t);
            }
            else
                nearEntries.add(t);
        }

        GridCacheEvictionResponse<K, V> res = new GridCacheEvictionResponse<K, V>(req.futureId());

        GridCacheVersion obsoleteVer = cctx.versions().next();

        // DHT and replicated cache entries.
        for (Map.Entry<Integer, Collection<GridTuple3<K, GridCacheVersion, Boolean>>> e : dhtEntries.entrySet()) {
            int part = e.getKey();

            boolean locked = lockPartition(part); // Will return false if preloading is disabled.

            try {
                for (GridTuple3<K, GridCacheVersion, Boolean> t : e.getValue()) {
                    K key = t.get1();
                    GridCacheVersion ver = t.get2();
                    Boolean near = t.get3();

                    assert !near;

                    boolean evicted = evictLocally(key, ver, near, obsoleteVer);

                    if (log.isDebugEnabled())
                        log.debug("Evicted key [key=" + key + ", ver=" + ver + ", near=" + near +
                            ", evicted=" + evicted +']');

                    if (locked && evicted)
                        // Preloading is in progress, we need to save eviction info.
                        saveEvictionInfo(key, ver, part);

                    if (!evicted)
                        res.addRejected(key);
                }
            }
            finally {
                if (locked)
                    unlockPartition(part);
            }
        }

        // Near entries.
        for (GridTuple3<K, GridCacheVersion, Boolean> t : nearEntries) {
            K key = t.get1();
            GridCacheVersion ver = t.get2();
            Boolean near = t.get3();

            assert near;

            boolean evicted = evictLocally(key, ver, near, obsoleteVer);

            if (log.isDebugEnabled())
                log.debug("Evicted key [key=" + key + ", ver=" + ver + ", near=" + near +
                    ", evicted=" + evicted +']');

            if (!evicted)
                res.addRejected(key);
        }

        sendEvictionResponse(nodeId, res);
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void sendEvictionResponse(UUID nodeId, GridCacheEvictionResponse<K, V> res) {
        try {
            cctx.io().send(nodeId, res);

            if (log.isDebugEnabled())
                log.debug("Sent eviction response [node=" + nodeId + ", localNode=" + cctx.nodeId() +
                    ", res" + res + ']');
        }
        catch (GridTopologyException ignored) {
            if (log.isDebugEnabled())
                log.debug("Failed to send eviction response since initiating node left grid " +
                    "[node=" + nodeId + ", localNode=" + cctx.nodeId() + ']');
        }
        catch (GridException e) {
            U.error(log, "Failed to send eviction response to node [node=" + nodeId +
                ", localNode=" + cctx.nodeId() + ", res" + res + ']', e);
        }
    }

    /**
     * @param key Key.
     * @param ver Version.
     * @param p Partition ID.
     */
    private void saveEvictionInfo(K key, GridCacheVersion ver, int p) {
        assert cctx.preloadEnabled();

        if (cctx.isDht()) {
            try {
                GridDhtLocalPartition<K, V> part = cctx.dht().topology().localPartition(p, -1, false);

                assert part != null;

                part.onEntryEvicted(key, ver);
            }
            catch (GridDhtInvalidPartitionException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Partition does not belong to local node [part=" + p +
                        ", nodeId" + cctx.localNode().id() + ']');
            }
        }
        else if (cctx.isReplicated()) {
            GridReplicatedPreloader<K, V> preldr = (GridReplicatedPreloader<K, V>)cctx.cache().preloader();

            preldr.onEntryEvicted(key, ver);
        }
        else
            assert false : "Failed to save eviction info: " + cctx.namexx();
    }

    /**
     * @param p Partition ID.
     * @return {@code True} if partition has been actually locked,
     *      {@code false} if preloading is finished or disabled and no lock is needed.
     */
    private boolean lockPartition(int p) {
        if (!cctx.preloadEnabled())
            return false;

        if (cctx.isReplicated()) {
            GridReplicatedPreloader<K, V> preldr = (GridReplicatedPreloader<K, V>)cctx.cache().preloader();

            return preldr.lock();
        }
        else if (cctx.isDht()) {
            try {
                GridDhtLocalPartition<K, V> part = cctx.dht().topology().localPartition(p, -1, false);

                if (part != null && part.reserve()) {
                    part.lock();

                    if (part.state() != MOVING) {
                        part.unlock();

                        part.release();

                        return false;
                    }

                    return true;
                }
            }
            catch (GridDhtInvalidPartitionException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Partition does not belong to local node [part=" + p +
                        ", nodeId" + cctx.localNode().id() + ']');
            }
        }

        // No lock is needed.
        return false;
    }

    /**
     * @param p Partition ID.
     */
    private void unlockPartition(int p) {
        if (!cctx.preloadEnabled())
            return;

        if (cctx.isReplicated()) {
            GridReplicatedPreloader<K, V> preldr = (GridReplicatedPreloader<K, V>)cctx.cache().preloader();

            preldr.unlock();
        }
        else if (cctx.isDht()) {
            try {
                GridDhtLocalPartition<K, V> part = cctx.dht().topology().localPartition(p, -1, false);

                if (part != null) {
                    part.unlock();

                    part.release();
                }
            }
            catch (GridDhtInvalidPartitionException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Partition does not belong to local node [part=" + p +
                        ", nodeId" + cctx.localNode().id() + ']');
            }
        }
    }

    /**
     * Locks topology (for DHT cache only) and returns its version.
     *
     * @return Topology version after lock.
     */
    private long lockTopology() {
        if (cctx.isReplicated())
            return cctx.discovery().topologyVersion();
        else if (cctx.isDht()) {
            cctx.dht().topology().readLock();

            return cctx.dht().topology().topologyVersion();
        }

        return 0;
    }

    /**
     * Unlocks topology.
     */
    private void unlockTopology() {
        if (cctx.isDht())
            cctx.dht().topology().readUnlock();
    }

    /**
     * @param key Key to evict.
     * @param ver Entry version on initial node.
     * @param near {@code true} if entry should be evicted from near cache.
     * @param obsoleteVer Obsolete version.
     * @return {@code true} if evicted successfully, {@code false} if could not be evicted.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    private boolean evictLocally(K key, final GridCacheVersion ver, boolean near, GridCacheVersion obsoleteVer) {
        assert key != null;
        assert ver != null;
        assert obsoleteVer != null;
        assert evictSync || nearSync;
        assert cctx.isDht() || cctx.isReplicated();

        if (log.isDebugEnabled())
            log.debug("Evicting key locally [key=" + key + ", ver=" + ver + ", obsoleteVer=" + obsoleteVer +
                ", localNode=" + cctx.localNode() + ']');

        GridCacheAdapter<K, V> cache = near ? cctx.dht().near() : cctx.cache();

        GridCacheEntryEx<K, V> entry = cache.peekEx(key);

        if (entry == null)
            return true;

        try {
            // If entry should be evicted from near cache it can be done safely
            // without any consistency risks. We don't use filter in this case.
            if (near)
                return evict0(cache, entry, obsoleteVer, null);

            // Create filter that will not evict entry if its version changes after we get it.
            GridPredicate<? super GridCacheEntry<K, V>>[] filter =
                cctx.vararg(new P1<GridCacheEntry<K, V>>() {
                    @Override public boolean apply(GridCacheEntry<K, V> e) {
                        GridCacheVersion v = (GridCacheVersion)e.version();

                        return ver.equals(v);
                    }
                });

            GridCacheVersion v = entry.version();

            // 1. If received version is less or greater than entry local version,
            // then don't evict.
            // 2. Do not touch DHT or replicated entry backup.
            return ver.equals(v) && evict0(cache, entry, obsoleteVer, filter);
        }
        catch (GridCacheEntryRemovedException ignored) {
            // Entry was concurrently removed.
            return true;
        }
        catch (GridException e) {
            U.error(log, "Failed to evict entry on remote node [key=" + key + ", localNode=" + cctx.nodeId() + ']', e);

            return false;
        }
    }

    /**
     * @param cache Cache from which to evict entry.
     * @param entry Entry to evict.
     * @param obsoleteVer Obsolete version.
     * @param filter Filter.
     * @return {@code true} if entry has been evicted.
     * @throws GridException If failed to evict entry.
     */
    private boolean evict0(GridCacheAdapter<K, V> cache, GridCacheEntryEx<K, V> entry, GridCacheVersion obsoleteVer,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        assert cache != null;
        assert entry != null;
        assert obsoleteVer != null;

        boolean evicted = entry.evictInternal(cctx.isSwapEnabled(), obsoleteVer, filter);

        if (evicted) {
            cache.removeEntry(entry);

            cctx.events().addEvent(entry.partition(), entry.key(), cctx.nodeId(), (GridUuid)null, null,
                EVT_CACHE_ENTRY_EVICTED, null, null);

            if (log.isDebugEnabled())
                log.debug("Entry was evicted [entry=" + entry + ", localNode=" + cctx.nodeId() + ']');
        }
        else if (log.isDebugEnabled())
            log.debug("Entry was not evicted [entry=" + entry + ", localNode=" + cctx.nodeId() + ']');

        return evicted;
    }

    /**
     * @param tx Transaction to register for eviction policy notifications.
     */
    public void touch(GridCacheTxEx<K, V> tx) {
        if (!busyLock.enterBusy())
            return;

        try {
            if (!policyEnabled())
                return;

            if (!tx.local()) {
                if (cctx.isNear())
                    return;

                if (cctx.isDht() && (evictSync || nearSync))
                    return;
            }

            if (log.isDebugEnabled())
                log.debug("Touching transaction [tx=" + CU.txString(tx) + ", localNode=" + cctx.nodeId() + ']');

            unwindLock.readLock().lock();

            try {
                txs.add(tx);

                entryCnt.addAndGet(tx.allEntries().size());

                if (evictSync || nearSync) {
                    for (GridCacheTxEntry<K, V> e : tx.allEntries()) {
                        Node<EvictionInfo> node = e.cached().removeMeta(meta);

                        if (node != null)
                            bufEvictQ.unlinkx(node);
                    }
                }
            }
            finally {
                unwindLock.readLock().unlock();
            }
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * @param entry Entry for eviction policy notification.
     */
    public void touch(GridCacheEntryEx<K, V> entry) {
        if (!busyLock.enterBusy())
            return;

        try {
            if (!policyEnabled())
                return;

            // Don't track non-primary entries if evicts are synchronized.
            if (cctx.isDht() && (evictSync || nearSync))
                if (!cctx.primary(cctx.localNode(), entry.partition()))
                    return;

            if (log.isDebugEnabled())
                log.debug("Touching entry [entry=" + entry + ", localNode=" + cctx.nodeId() + ']');

            unwindLock.readLock().lock();

            try {
                entries.add(entry);

                entryCnt.incrementAndGet();

                if (evictSync || nearSync) {
                    Node<EvictionInfo> node = entry.removeMeta(meta);

                    if (node != null)
                        bufEvictQ.unlinkx(node);
                }
            }
            finally {
                unwindLock.readLock().unlock();
            }
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * Note that this method should be called within
     * read lock ({@link #unwindLock}).
     * <p>
     * Buffer nodes will not be unlinked here, since if node presents
     * in queue, there will be another eviction iteration - do not postpone it.
     *
     * @param entry Entry for eviction policy notification.
     */
    private void touch0(GridCacheEntryEx<K, V> entry) {
        assert (evictSync || nearSync) && policyEnabled();

        entries.add(entry);

        entryCnt.incrementAndGet();
    }

    /**
     * @param entries Entries for eviction policy notification.
     */
    private void touchOnTopologyChange(Collection<? extends GridCacheEntryEx<K, V>> entries) {
        if (F.isEmpty(entries) || !policyEnabled())
            return;

        if (log.isDebugEnabled())
            log.debug("Touching entries [entries=" + entries + ", localNode=" + cctx.nodeId() + ']');

        unwindLock.readLock().lock();

        // This method is intended to call only on topology changes when node becomes
        // primary for this partition. No need to notify futures and clear buffer nodes,
        // as these entries have never been tracked.
        try {
            this.entries.addAll(entries);

            entryCnt.addAndGet(entries.size());
        }
        finally {
            unwindLock.readLock().unlock();
        }
    }

    /**
     * @param entry Entry to attempt to evict.
     * @param obsoleteVer Obsolete version.
     * @param filter Optional entry filter.
     * @param explicit {@code True} if evict is called explicitly, {@code false} if it's called
     *      from eviction policy.
     * @return {@code True} if entry was marked for eviction.
     * @throws GridException In case of error.
     */
    public boolean evict(@Nullable GridCacheEntryEx<K, V> entry, @Nullable GridCacheVersion obsoleteVer,
        boolean explicit, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (entry == null)
            return true;

        // Do not evict internal entries.
        if (entry.key() instanceof GridCacheInternal)
            return false;

        if (evictSync || nearSync) {
            assert cctx.isReplicated() || cctx.isDht(); // Make sure cache is not NEAR.

            if (entry.wrap(false).backup())
                // Do not track backups.
                return !explicit;

            try {
                if (!cctx.isAll(entry, filter))
                    return false;

                if (entry.lockedByAny())
                    return false;

                // Add entry to eviction queue.
                enqueue(entry, filter);
            }
            catch (GridCacheEntryRemovedException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Entry got removed while evicting [entry=" + entry +
                        ", localNode=" + cctx.nodeId() + ']');
            }
        }
        else {
            if (obsoleteVer == null)
                obsoleteVer = cctx.versions().next();

            // Do not touch entry if not evicted:
            // 1. If it is call from policy, policy tracks it on its own.
            // 2. If it is explicit call, entry is touched on tx commit.
            return evict0(cctx.cache(), entry, obsoleteVer, filter);
        }

        return true;
    }

    /**
     * Enqueues entry for synchronized eviction.
     * <p>
     * Most of the time this method is called within write lock
     * {@link #unwindLock}, except cases when evict is called explicitly.
     *
     * @param entry Entry.
     * @param filter Filter.
     * @throws GridCacheEntryRemovedException If entry got removed.
     */
    private void enqueue(GridCacheEntryEx<K, V> entry, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException {
        Node<EvictionInfo> node = entry.meta(meta);

        if (node == null) {
            node = bufEvictQ.addLastx(new EvictionInfo(entry, entry.version(), filter));

            if (entry.putMetaIfAbsent(meta, node) != null)
                // Was concurrently added, need to clear it from queue.
                bufEvictQ.unlinkx(node);
            else if (log.isDebugEnabled())
                log.debug("Added entry to eviction queue: " + entry);
        }
    }

    /**
     * Checks eviction queue.
     */
    private void checkEvictionQueue() {
        int maxSize = maxQueueSize();

        int bufSize = bufEvictQ.sizex();

        if (bufSize > maxSize && buffEvicting.compareAndSet(false, true)) {
            Collection<EvictionInfo> evictionInfos;

            try {
                if (log.isDebugEnabled())
                    log.debug("Processing eviction queue: " + bufSize);

                evictionInfos = new ArrayList<EvictionInfo>(bufSize);

                for (int i = 0; i < bufSize; i++) {
                    EvictionInfo info = bufEvictQ.poll();

                    if (info == null)
                        break;

                    evictionInfos.add(info);
                }
            }
            finally {
                buffEvicting.set(false);
            }

            if (!evictionInfos.isEmpty())
                createEvictionFuture(evictionInfos);
        }
    }

    /**
     * @return Max queue size.
     */
    private int maxQueueSize() {
        int size = Math.min((int)(cctx.cache().keySize() * cctx.config().getEvictMaxOverflowRatio()) / 100,
            cctx.config().getEvictSynchronisedKeyBufferSize());

        return size > 0 ? size : 500;
    }

    /**
     * Processes eviction queue (sends required requests, etc.).
     *
     * @param evictionInfos Eviction information to create future with.
     */
    private void createEvictionFuture(Collection<EvictionInfo> evictionInfos) {
        EvictionFuture fut = new EvictionFuture(evictionInfos);

        // Put future in map.
        futsCntLock.lock();

        try {
            activeFutsCnt++;
        }
        finally {
            futsCntLock.unlock();
        }

        futs.put(fut.id(), fut);

        fut.prepare();

        // Listen to the future completion.
        fut.listenAsync(new CI1<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> f) {
                long topVer = lockTopology();

                try {
                    onFutureCompleted((EvictionFuture)f, topVer);
                }
                finally {
                    unlockTopology();
                }
            }
        });
    }

    /**
     * @param fut Completed eviction future.
     * @param topVer Topology version on future complete.
     */
    private void onFutureCompleted(EvictionFuture fut, long topVer) {
        if (!busyLock.enterBusy())
            return;

        boolean signalled = false;

        try {
            GridTuple2<Collection<EvictionInfo>, Collection<EvictionInfo>> t;

            try {
                t = fut.get();
            }
            catch (GridFutureCancelledException ignored) {
                assert false : "Future has been cancelled, but manager is not stopping: " + fut;

                return;
            }
            catch (GridException e) {
                U.error(log, "Eviction future finished with error (all entries will be touched): " + fut, e);

                // Signal first, then get read lock.
                signalUnwind();

                signalled = true;

                if (policyEnabled()) {
                    unwindLock.readLock().lock();

                    try {
                        for (EvictionInfo info : fut.entries())
                            touch0(info.entry());
                    }
                    finally {
                        unwindLock.readLock().unlock();
                    }
                }

                return;
            }

            // Check if topology version is different.
            if (fut.topologyVersion() != topVer) {
                if (log.isDebugEnabled())
                    log.debug("Topology has changed, all entries will be touched: " + fut);

                // Signal first, then get read lock.
                signalUnwind();

                signalled = true;

                if (policyEnabled()) {
                    unwindLock.readLock().lock();

                    try {
                        for (EvictionInfo info : fut.entries())
                            touch0(info.entry());
                    }
                    finally {
                        unwindLock.readLock().unlock();
                    }
                }

                return;
            }

            // Evict remotely evicted entries.
            GridCacheVersion obsoleteVer = null;

            Collection<EvictionInfo> evictedEntries = t.get1();

            for (EvictionInfo info : evictedEntries) {
                GridCacheEntryEx<K, V> entry = info.entry();

                try {
                    // Remove readers on which the entry was evicted.
                    for (GridTuple2<GridRichNode, Long> r : fut.evictedReaders(entry.key())) {
                        UUID readerId = r.get1().id();
                        Long msgId = r.get2();

                        ((GridDhtCacheEntry<K, V>)entry).removeReader(readerId, msgId);
                    }

                    if (obsoleteVer == null)
                        obsoleteVer = cctx.versions().next();

                    // Do not touch primary entries, if not evicted.
                    // They will be touched within updating transactions.
                    evict0(cctx.cache(), entry, obsoleteVer, versionFilter(info));
                }
                catch (GridException e) {
                    U.error(log, "Failed to evict entry [entry=" + entry +
                        ", localNode=" + cctx.nodeId() + ']', e);
                }
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Entry was concurrently removed while evicting [entry=" + entry +
                            ", localNode=" + cctx.nodeId() + ']');
                }
            }

            Collection<EvictionInfo> rejectedEntries = t.get2();

            // Signal first, then get read lock.
            signalUnwind();

            signalled = true;

            // Touch rejected entries (only if policy is enabled).
            if (policyEnabled() && !rejectedEntries.isEmpty()) {
                unwindLock.readLock().lock();

                try {
                    // Touch remotely rejected entries.
                    for (EvictionInfo info : rejectedEntries)
                        touch0(info.entry());
                }
                finally {
                    unwindLock.readLock().unlock();
                }
            }
        }
        finally {
            busyLock.leaveBusy();

            // Signal unwind on future completion (safety).
            if (!signalled)
                signalUnwind();
        }
    }

    /**
     * This method should be called when eviction future is processed
     * and unwind may continue.
     */
    private void signalUnwind() {
        futsCntLock.lock();

        try {
            // Avoid volatile read on assertion.
            int cnt = --activeFutsCnt;

            assert cnt >= 0 : "Invalid futures count: " + cnt;

            futsCntCond.signalAll();
        }
        finally {
            futsCntLock.unlock();
        }
    }

    /**
     * @param info Eviction info.
     * @return Version aware filter.
     */
    private GridPredicate<? super GridCacheEntry<K, V>>[] versionFilter(final EvictionInfo info) {
        // If version has changed since we started the whole process
        // then we should not evict entry.
        return cctx.vararg(new P1<GridCacheEntry<K, V>>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                GridCacheVersion ver = (GridCacheVersion)e.version();

                return info.version().equals(ver) && F.isAll(info.filter());
            }
        });
    }

    /**
     * Gets a collection of nodes to send eviction requests to.
     *
     * @param entry Entry.
     * @return Tuple of two collections: dht (in case of partitioned cache) nodes
     *      and readers (empty for replicated cache).
     * @throws GridCacheEntryRemovedException If entry got removed during method
     *      execution.
     */
    @SuppressWarnings( {"IfMayBeConditional"})
    private GridTuple2<Collection<GridRichNode>, Collection<GridRichNode>> remoteNodes(GridCacheEntryEx<K, V> entry)
        throws GridCacheEntryRemovedException {
        assert entry != null;

        Collection<GridRichNode> backups;

        Collection<GridRichNode> readers;

        GridCacheAffinity<Object> aff = cctx.config().getAffinity();

        if (cctx.config().getCacheMode() == REPLICATED) {
            if (evictSync) {
                backups = new HashSet<GridRichNode>(
                    F.view(aff.nodes(entry.partition(), CU.allNodes(cctx)), F.notEqualTo(cctx.localNode())));
            }
            else
                backups = Collections.emptySet();

            readers = Collections.emptySet();
        }
        else {
            assert cctx.config().getCacheMode() == PARTITIONED;

            if (evictSync) {
                // TODO: What topology version to pass?
                backups = F.transform(cctx.dht().topology().nodes(entry.partition(), -1), cctx.rich().richNode(),
                    F.<GridNode>notEqualTo(cctx.localNode()));
            }
            else
                backups = Collections.emptySet();

            if (nearSync) {
                readers = F.transform(((GridDhtCacheEntry<K, V>)entry).readers(), new C1<UUID, GridRichNode>() {
                    @Override @Nullable public GridRichNode apply(UUID nodeId) {
                        return cctx.node(nodeId);
                    }
                });
            }
            else
                readers = Collections.emptySet();
        }

        return new GridPair<Collection<GridRichNode>>(backups, readers);
    }

    /**
     * Notifications.
     */
    public void unwind() {
        if (!busyLock.enterBusy())
            return;

        try {
            if (policyEnabled()) {
                if (entryCnt.get() >= entryUnwindThreshold) {
                    // Only one thread should unwind for efficiency.
                    if (unwinding.compareAndSet(false, true)) {
                        GridCacheFlag[] old = cctx.forceLocal();

                        try {
                            if (activeFutsCnt >= maxActiveFuts || !unwindSafe(true)) {
                                unwindLock.writeLock().lock();

                                try {
                                    boolean ret = unwindSafe(false); // Unwind within lock.

                                    assert ret;

                                    if (stopping.get())
                                        return;
                                }
                                finally {
                                    unwindLock.writeLock().unlock();
                                }
                            }
                        }
                        finally {
                            cctx.forceFlags(old);

                            unwinding.set(false);
                        }
                    }
                }
            }
            else
                assert entryCnt.get() == 0;

            if (evictSync || nearSync)
                checkEvictionQueue();
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * @param cap {@code True} if unwind process should cap after queue grows to certain limit.
     * @return {@code True} if unwind thread was able to succeed, {@code false} if queue
     *      kept growing during unwind.
     */
    private boolean unwindSafe(boolean cap) {
        int cnt = 0;

        int startCnt = entryCnt.get();

        // Touch first.
        for (GridCacheEntryEx<K, V> e = entries.poll(); e != null; e = entries.poll()) {
            cnt = entryCnt.decrementAndGet();

            notifyPolicy(e);

            if (!cap && (evictSync || nearSync)) {
                waitForEvictionFutures();

                // Check stopping flag on return from wait.
                if (stopping.get())
                    return true;
            }

            if (cap && cnt >= startCnt * QUEUE_OUTGROW_RATIO)
                return false;
        }

        for (Iterator<GridCacheTxEx<K, V>> it = txs.iterator(); it.hasNext(); ) {
            GridCacheTxEx<K, V> tx = it.next();

            if (!tx.done())
                return true;

            it.remove();

            Collection<GridCacheTxEntry<K, V>> txEntries = tx.allEntries();

            cnt = entryCnt.addAndGet(-txEntries.size());

            if (!tx.internal()) {
                for (GridCacheTxEntry<K, V> txe : txEntries) {
                    GridCacheEntryEx<K, V> e = txe.cached();

                    notifyPolicy(e);
                }
            }

            if (!cap && (evictSync || nearSync)) {
                waitForEvictionFutures();

                // Check stopping flag on return from wait.
                if (stopping.get())
                    return true;
            }

            if (cap && cnt >= startCnt * QUEUE_OUTGROW_RATIO)
                return false;
        }

        assert cap || cnt == 0 : "Invalid entry count [cnt=" + cnt + ", size=" + txs.size() + ", txs=" + txs +
            ", entries=" + entries + ']';

        return true;
    }

    /**
     * @param e Entry to notify eviction policy.
     */
    @SuppressWarnings({"IfMayBeConditional", "RedundantIfStatement"})
    private void notifyPolicy(GridCacheEntryEx<K, V> e) {
        boolean notify;

        if (e.key() instanceof GridCacheInternal)
            notify = false;
        // if near cache is disabled, then we always notify it,
        // so the entry can be removed.
        else if (cctx.isNear() && !cctx.config().isNearEnabled())
            notify = true;
        else if (filter != null && !filter.evictAllowed(e.wrap(false)))
            notify = false;
        else
            notify = true;

        if (notify)
            policy.onEntryAccessed(e.obsolete(), e.evictWrap());
    }

    /**
     *
     */
    @SuppressWarnings("TooBroadScope")
    private void waitForEvictionFutures() {
        if (activeFutsCnt >= maxActiveFuts) {
            boolean interrupted = false;

            futsCntLock.lock();

            try {
                while(!stopping.get() && activeFutsCnt >= maxActiveFuts) {
                    try {
                        futsCntCond.await(2000, MILLISECONDS);
                    }
                    catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
            }
            finally {
                futsCntLock.unlock();

                if (interrupted)
                    Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Prints out eviction stats.
     */
    public void printStats() {
        X.println("Eviction stats [grid=" + cctx.gridName() + ", cache=" + cctx.cache().name() +
            ", txs=" + txs.size() + ", entries=" + entries.size() + ", buffEvictQ=" + bufEvictQ.sizex() + ']');
    }

    /** {@inheritDoc} */
    @Override protected void printMemoryStats() {
        X.println(">>> ");
        X.println(">>> Eviction manager memory stats [grid=" + cctx.gridName() + ", cache=" + cctx.name() + ']');
        X.println(">>>   buffEvictQ size: " + bufEvictQ.sizex());
        X.println(">>>   txsSize: " + txs.size());
        X.println(">>>   entriesSize: " + entries.size());
        X.println(">>>   futsSize: " + futs.size());
        X.println(">>>   futsCreated: " + idGen.get());
    }

    /**
     *
     */
    private class BackupWorker extends GridWorker {
        /** */
        private final BlockingQueue<GridDiscoveryEvent> evts = new LinkedBlockingQueue<GridDiscoveryEvent>();

        /** */
        private final Collection<Integer> primaryParts = new HashSet<Integer>();

        /**
         *
         */
        private BackupWorker() {
            super(cctx.gridName(), "cache-eviction-backup-worker", log);
        }

        /**
         * @param evt New event.
         */
        void addEvent(GridDiscoveryEvent evt) {
            assert evt != null;

            evts.add(evt);
        }

        /**
         * {@inheritDoc}
         */
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            assert cctx.isDht() && evictSync;

            GridNode loc = cctx.localNode();

            int parts = cctx.partitions();

            // Initialize.
            for (int p = 0; p < parts; p++)
                if (cctx.primary(loc, p))
                    primaryParts.add(p);

            while (!isCancelled()) {
                GridDiscoveryEvent evt = evts.take();

                if (log.isDebugEnabled())
                    log.debug("Processing event: " + evt);

                // Remove partitions that are no longer primary.
                for (Iterator<Integer> it = primaryParts.iterator(); it.hasNext();) {
                    if (!evts.isEmpty())
                        break;

                    if (!cctx.primary(loc, it.next()))
                        it.remove();
                }

                // Move on to next event.
                if (!evts.isEmpty())
                    continue;

                for (GridDhtLocalPartition<K, V> part : cctx.topology().localPartitions()) {
                    if (!evts.isEmpty())
                        break;

                    if (part.primary() && primaryParts.add(part.id())) {
                        if (log.isDebugEnabled())
                            log.debug("Touching partition entries: " + part);

                        // Make sure copy values to local collection.
                        Collection<GridDhtCacheEntry<K, V>> entries = new LinkedList<GridDhtCacheEntry<K, V>>();

                        for (GridDhtCacheEntry<K, V> e : part.entries())
                            entries.add(e);

                        touchOnTopologyChange(entries);
                    }
                }
            }
        }
    }

    /**
     * Wrapper around an entry to be put into queue.
     */
    private class EvictionInfo {
        /** Cache entry. */
        private GridCacheEntryEx<K, V> entry;

        /** Start version. */
        private GridCacheVersion ver;

        /** Filter to pass before entry will be evicted. */
        private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

        /**
         * @param entry Entry.
         * @param ver Version.
         * @param filter Filter.
         */
        EvictionInfo(GridCacheEntryEx<K, V> entry, GridCacheVersion ver,
            GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert entry != null;
            assert ver != null;

            this.entry = entry;
            this.ver = ver;
            this.filter = filter;
        }

        /**
         * @return Entry.
         */
        GridCacheEntryEx<K, V> entry() {
            return entry;
        }

        /**
         * @return Version.
         */
        GridCacheVersion version() {
            return ver;
        }

        /**
         * @return Filter.
         */
        GridPredicate<? super GridCacheEntry<K, V>>[] filter() {
            return filter;
        }
    }

    /**
     * Future for synchronized eviction. Result is a tuple: {evicted entries, rejected entries}.
     */
    private class EvictionFuture extends GridFutureAdapter<GridTuple2<Collection<EvictionInfo>,
        Collection<EvictionInfo>>> {
        /** */
        private final long id = idGen.incrementAndGet();

        /** */
        private Collection<EvictionInfo> evictionInfos;

        /** */
        private final ConcurrentMap<K, EvictionInfo> entries = new ConcurrentHashMap<K, EvictionInfo>();

        /** */
        private final ConcurrentMap<K, Collection<GridRichNode>> readers =
            new ConcurrentHashMap<K, Collection<GridRichNode>>();

        /** */
        private final Collection<EvictionInfo> evictedEntries = new GridConcurrentHashSet<EvictionInfo>();

        /** */
        private final ConcurrentMap<K, EvictionInfo> rejectedEntries = new ConcurrentHashMap<K, EvictionInfo>();

        /** Request map. */
        private final ConcurrentMap<UUID, GridCacheEvictionRequest<K, V>> reqMap =
            new ConcurrentHashMap<UUID, GridCacheEvictionRequest<K, V>>();

        /** Response map. */
        private final ConcurrentMap<UUID, GridCacheEvictionResponse<K, V>> resMap =
            new ConcurrentHashMap<UUID, GridCacheEvictionResponse<K, V>>();

        /** To make sure that future is completing within a single thread. */
        private final AtomicBoolean completing = new AtomicBoolean(false);

        /** Lock. */
        @GridToStringExclude
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        /** Object to force future completion on elapsing eviction timeout. */
        @GridToStringExclude
        private GridTimeoutObject timeoutObj;

        /** Topology version future is processed on. */
        private long topVer;

        /**
         * @param evictionInfos Eviction information to create future with.
         */
        EvictionFuture(Collection<EvictionInfo> evictionInfos) {
            super(cctx.kernalContext());

            assert evictionInfos != null && !evictionInfos.isEmpty();

            this.evictionInfos = evictionInfos;
        }

        /**
         * Required by {@code Externalizable}.
         */
        public EvictionFuture() {
            // No-op.
        }

        /**
         * Prepares future (sends all required requests).
         */
        private void prepare() {
            if (log.isDebugEnabled())
                log.debug("Preparing eviction future [futId=" + id + ", localNode=" + cctx.nodeId() +
                    ", infos=" + evictionInfos + ']');

            assert evictionInfos != null && !evictionInfos.isEmpty();

            topVer = lockTopology();

            try {
                Collection<EvictionInfo> locals = null;

                for (EvictionInfo info : evictionInfos) {
                    // Queue node may have been stored in entry metadata concurrently, but we don't care
                    // about it since we are currently processing this entry.
                    Node<EvictionInfo> queueNode = info.entry().removeMeta(meta);

                    if (queueNode != null)
                        bufEvictQ.unlinkx(queueNode);

                    GridTuple2<Collection<GridRichNode>, Collection<GridRichNode>> tup;

                    try {
                        tup = remoteNodes(info.entry());
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Entry got removed while preparing eviction future (ignoring) [entry=" +
                                info.entry() + ", nodeId=" + cctx.nodeId() + ']');

                        continue;
                    }

                    Collection<GridRichNode> entryReaders =
                        F.addIfAbsent(readers, info.entry().key(), new GridConcurrentHashSet<GridRichNode>());

                    assert entryReaders != null;

                    // Add entry readers so that we could remove them right before local eviction.
                    entryReaders.addAll(tup.get2());

                    Collection<GridRichNode> nodes = F.concat(true, tup.get1(), tup.get2());

                    if (!nodes.isEmpty()) {
                        entries.put(info.entry().key(), info);

                        // There are remote participants.
                        for (GridRichNode node : nodes) {
                            GridCacheEvictionRequest<K, V> req = F.addIfAbsent(reqMap, node.id(),
                                new GridCacheEvictionRequest<K, V>(id, evictionInfos.size(), topVer));

                            assert req != null;

                            req.addKey(info.entry().key(), info.version(), entryReaders.contains(node));
                        }
                    }
                    else {
                        if (locals == null)
                            locals = new HashSet<EvictionInfo>(evictionInfos.size(), 1.0f);

                        // There are no remote participants, need to keep the entry as local.
                        locals.add(info);
                    }
                }

                if (locals != null) {
                    // Evict entries without remote participant nodes immediately.
                    GridCacheVersion obsoleteVer = cctx.versions().next();

                    for (EvictionInfo info : locals) {
                        if (log.isDebugEnabled())
                            log.debug("Evicting key without remote participant nodes: " + info);

                        try {
                            // Touch primary entry (without backup nodes) if not evicted
                            // to keep tracking.
                            if (!evict0(cctx.cache(), info.entry(), obsoleteVer, versionFilter(info)))
                                touch(info.entry());
                        }
                        catch (GridException e) {
                            U.error(log, "Failed to evict entry: " + info.entry(), e);
                        }
                    }
                }

                // If there were only local entries.
                if (entries.isEmpty()) {
                    complete(false);

                    return;
                }
            }
            finally {
                unlockTopology();
            }

            // Send eviction requests.
            for (Map.Entry<UUID, GridCacheEvictionRequest<K, V>> e : reqMap.entrySet()) {
                UUID nodeId = e.getKey();

                GridCacheEvictionRequest<K, V> req = e.getValue();

                if (log.isDebugEnabled())
                    log.debug("Sending eviction request [node=" + nodeId + ", req=" + req + ']');

                try {
                    cctx.io().send(nodeId, req);
                }
                catch (GridTopologyException ignored) {
                    // Node left the topology.
                    onNodeLeft(nodeId);
                }
                catch (GridException ex) {
                    U.error(log, "Failed to send eviction request to node [node=" + nodeId + ", req=" + req + ']', ex);

                    rejectEntries(nodeId);
                }
            }

            registerTimeoutObject();
        }

        /**
         *
         */
        private void registerTimeoutObject() {
            // Check whether future has not been completed yet.
            if (lock.readLock().tryLock()) {
                try {
                    timeoutObj = new GridTimeoutObject() {
                        private final GridUuid id = GridUuid.randomUuid();

                        private final long endTime =
                            System.currentTimeMillis() + cctx.config().getEvictSynchronizedTimeout();

                        @Override public GridUuid timeoutId() {
                            return id;
                        }

                        @Override public long endTime() {
                            return endTime;
                        }

                        @Override public void onTimeout() {
                            complete(true);
                        }
                    };

                    cctx.time().addTimeoutObject(timeoutObj);
                }
                finally {
                    lock.readLock().unlock();
                }
            }
        }

        /**
         * @return Future ID.
         */
        long id() {
            return id;
        }

        /**
         * @return Topology version.
         */
        long topologyVersion() {
            return topVer;
        }

        /**
         * @return Keys to readers mapping.
         */
        Map<K, Collection<GridRichNode>> readers() {
            return readers;
        }

        /**
         * @return All entries associated with future that should be evicted (or rejected).
         */
        Collection<EvictionInfo> entries() {
            return entries.values();
        }

        /**
         * Reject all entries on behalf of specified node.
         *
         * @param nodeId Node ID.
         */
        private void rejectEntries(UUID nodeId) {
            assert nodeId != null;

            if (lock.readLock().tryLock()) {
                try {
                    if (log.isDebugEnabled())
                        log.debug("Rejecting entries for node: " + nodeId);

                    GridCacheEvictionRequest<K, V> req = reqMap.remove(nodeId);

                    for (GridTuple3<K, GridCacheVersion, Boolean> t : req.entries()) {
                        EvictionInfo info = entries.get(t.get1());

                        assert info != null;

                        rejectedEntries.put(t.get1(), info);
                    }
                }
                finally {
                    lock.readLock().unlock();
                }
            }

            checkDone();
        }

        /**
         * @param nodeId Node id that left the topology.
         */
        void onNodeLeft(UUID nodeId) {
            assert nodeId != null;

            if (lock.readLock().tryLock()) {
                try {
                    // Stop waiting response from this node.
                    reqMap.remove(nodeId);

                    resMap.remove(nodeId);
                }
                finally {
                    lock.readLock().unlock();
                }

                checkDone();
            }
        }

        /**
         * @param nodeId Sender node ID.
         * @param res Response.
         */
        void onResponse(UUID nodeId, GridCacheEvictionResponse<K, V> res) {
            assert nodeId != null;
            assert res != null;

            if (lock.readLock().tryLock()) {
                try {
                    if (log.isDebugEnabled())
                        log.debug("Entered to eviction future onResponse() [fut=" + this + ", node=" + nodeId +
                            ", res=" + res + ']');

                    GridRichNode node = cctx.node(nodeId);

                    if (node != null)
                        resMap.put(nodeId, res);
                    else
                        // Sender node left grid.
                        reqMap.remove(nodeId);
                }
                finally {
                    lock.readLock().unlock();
                }

                if (res.error())
                    // Complete future, since there was a class loading error on at least one node.
                    complete(false);
                else
                    checkDone();
            }
            else {
                if (log.isDebugEnabled())
                    log.debug("Ignored eviction response [fut=" + this + ", node=" + nodeId + ", res=" + res + ']');
            }
        }

        /**
         *
         */
        private void checkDone() {
            if (reqMap.isEmpty() || resMap.keySet().containsAll(reqMap.keySet()))
                complete(false);
        }

        /**
         * Completes future.
         *
         * @param timedOut {@code True} if future is being forcibly completed on timeout.
         */
        @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
        private void complete(boolean timedOut) {
            if (completing.compareAndSet(false, true)) {
                // Lock will never be released intentionally.
                lock.writeLock().lock();

                futs.remove(id);

                if (timeoutObj != null && !timedOut)
                    // If future is timed out, corresponding object is already removed.
                    cctx.time().removeTimeoutObject(timeoutObj);

                if (log.isDebugEnabled())
                    log.debug("Building eviction future result [fut=" + this + ", timedOut=" + timedOut + ']');

                boolean err = F.forAny(resMap.values(), new P1<GridCacheEvictionResponse<K, V>>() {
                    @Override public boolean apply(GridCacheEvictionResponse<K, V> res) {
                        return res.error();
                    }
                });

                if (err) {
                    Collection<UUID> ids = F.view(resMap.keySet(), new P1<UUID>() {
                        @Override public boolean apply(UUID e) {
                            return resMap.get(e).error();
                        }
                    });

                    assert !ids.isEmpty();

                    U.warn(log, "Remote node(s) failed to process eviction request " +
                        "due to topology changes " +
                        "(some backup or remote values maybe lost): " + ids);
                }

                if (timedOut)
                    U.warn(log, "Timed out waiting for eviction future " +
                        "(consider changing 'evictSynchronousTimeout' and 'evictSynchronousConcurrencyLevel' " +
                        "configuration parameters).");

                if (err || timedOut) {
                    // Future has not been completed successfully, all entries should be rejected.
                    assert evictedEntries.isEmpty();

                    rejectedEntries.putAll(entries);
                }
                else {
                    // Copy map to filter remotely rejected entries,
                    // as they will be touched within corresponding txs.
                    Map<K, EvictionInfo> rejectedEntries0 = new HashMap<K, EvictionInfo>(rejectedEntries);

                    // Future has been completed successfully - build result.
                    for (EvictionInfo info : entries.values()) {
                        K key = info.entry().key();

                        if (rejectedEntries0.containsKey(key))
                            // Was already rejected.
                            continue;

                        boolean rejected = false;

                        for (GridCacheEvictionResponse<K, V> res : resMap.values()) {
                            if (res.rejectedKeys().contains(key)) {
                                // Modify copied map.
                                rejectedEntries0.put(key, info);

                                rejected = true;

                                break;
                            }
                        }

                        if (!rejected)
                            evictedEntries.add(info);
                    }
                }

                // Pass entries that were rejected due to topology changes
                // or due to timeout or class loading issues.
                // Remotely rejected entries will be touched within corresponding txs.
                onDone(F.t(evictedEntries, rejectedEntries.values()));
            }
        }

        /**
         * @param key Key.
         * @return Reader nodes on which given key was evicted.
         */
        Collection<GridTuple2<GridRichNode, Long>> evictedReaders(K key) {
            Collection<GridRichNode> mappedReaders = readers.get(key);

            if (mappedReaders == null)
                return Collections.emptyList();

            Collection<GridTuple2<GridRichNode, Long>> col = new LinkedList<GridTuple2<GridRichNode, Long>>();

            for (Map.Entry<UUID, GridCacheEvictionResponse<K, V>> e : resMap.entrySet()) {
                GridRichNode node = cctx.node(e.getKey());

                // If node has left or response did not arrive from near node
                // then just skip it.
                if (node == null || !mappedReaders.contains(node))
                    continue;

                GridCacheEvictionResponse<K, V> res = e.getValue();

                if (!res.rejectedKeys().contains(key))
                    col.add(F.t(node, res.messageId()));
            }

            return col;
        }

        /** {@inheritDoc} */
        @SuppressWarnings("LockAcquiredButNotSafelyReleased")
        @Override public boolean cancel() {
            if (completing.compareAndSet(false, true)) {
                // Lock will never be released intentionally.
                lock.writeLock().lock();

                if (timeoutObj != null)
                    cctx.time().removeTimeoutObject(timeoutObj);

                boolean b = onCancelled();

                assert b;

                return true;
            }

            return false;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(EvictionFuture.class, this);
        }
    }
}

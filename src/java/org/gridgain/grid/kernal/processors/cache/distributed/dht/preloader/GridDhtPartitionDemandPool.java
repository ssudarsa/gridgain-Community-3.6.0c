// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.stopwatch.*;
import org.gridgain.grid.util.tostring.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.processors.cache.distributed.dht.GridDhtPartitionState.*;

/**
 * Thread pool for requesting partitions from other nodes
 * and populating local cache.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@SuppressWarnings( {"NonConstantFieldWithUpperCaseName"})
public class GridDhtPartitionDemandPool<K, V> {
    /** Dummy message to wake up a blocking queue if a node leaves. */
    private final SupplyMessage<K, V> DUMMY_TOP = new SupplyMessage<K, V>();

    /** */
    private final GridCacheContext<K, V> cctx;

    /** */
    private final GridLogger log;

    /** */
    private final ReadWriteLock busyLock;

    /** */
    private final GridDhtPartitionTopology<K, V> top;

    /** */
    @GridToStringInclude
    private final Collection<DemandWorker> dmdWorkers;

    /** */
    @GridToStringInclude
    private final ExchangeWorker exchWorker;

    /** Future for preload mode {@link GridCachePreloadMode#SYNC}. */
    @GridToStringInclude
    private SyncFuture syncFut;

    /** Preload timeout. */
    private final AtomicLong timeout;

    /** Last partition refresh. */
    private final AtomicLong lastRefresh = new AtomicLong(-1);

    /** Allows demand threads to synchronize their step. */
    private final CyclicBarrier barrier;

    /** Demand lock. */
    private final ReadWriteLock demandLock = new ReentrantReadWriteLock();

    /** */
    private int poolSize;

    /**
     * @param cctx Cache context.
     * @param busyLock Shutdown lock.
     */
    public GridDhtPartitionDemandPool(GridCacheContext<K, V> cctx, ReadWriteLock busyLock) {
        assert cctx != null;
        assert busyLock != null;

        this.cctx = cctx;
        this.busyLock = busyLock;

        log = cctx.logger(getClass());

        top = cctx.dht().topology();

        poolSize = cctx.preloadEnabled() ? cctx.config().getPreloadThreadPoolSize() : 1;

        barrier = new CyclicBarrier(poolSize, new Runnable() {
            @Override public void run() {
                GridDhtPartitionDemandPool.this.cctx.deploy().unwind();
            }
        });

        dmdWorkers = new ArrayList<DemandWorker>(poolSize);

        for (int i = 0; i < poolSize; i++)
            dmdWorkers.add(new DemandWorker(i));

        exchWorker = new ExchangeWorker();

        syncFut = new SyncFuture(dmdWorkers);

        timeout = new AtomicLong(cctx.gridConfig().getNetworkTimeout());
    }

    /**
     * @param exchFut Exchange future for this node.
     */
    void start(GridDhtPartitionsExchangeFuture<K, V> exchFut) {
        assert exchFut.exchangeId().nodeId().equals(cctx.nodeId());

        for (DemandWorker w : dmdWorkers)
            new GridThread(cctx.gridName(), "preloader-demand-worker", w).start();

        new GridThread(cctx.gridName(), "exchange-worker", exchWorker).start();

        onDiscoveryEvent(cctx.nodeId(), exchFut);
    }

    /**
     *
     */
    void stop() {
        U.cancel(exchWorker);
        U.cancel(dmdWorkers);

        if (log.isDebugEnabled())
            log.debug("Before joining on exchange worker: " + exchWorker);

        U.join(exchWorker, log);

        if (log.isDebugEnabled())
            log.debug("Before joining on demand workers: " + dmdWorkers);

        U.join(dmdWorkers, log);

        if (log.isDebugEnabled())
            log.debug("After joining on demand workers: " + dmdWorkers);
    }

    /**
     * @return Future for {@link GridCachePreloadMode#SYNC} mode.
     */
    GridFuture syncFuture() {
        return syncFut;
    }

    /**
     * @return Size of this thread pool.
     */
    int poolSize() {
        return poolSize;
    }

    /**
     * Resend partition map.
     */
    void resendPartitions() {
        try {
            refreshPartitions(0);
        }
        catch (GridInterruptedException e) {
            U.warn(log, "Partitions were not refreshed (thread got interrupted): " + e,
                "Partitions were not refreshed (thread got interrupted)");
        }
    }

    /**
     * @return {@code true} if entered to busy state.
     */
    private boolean enterBusy() {
        if (busyLock.readLock().tryLock())
            return true;

        if (log.isDebugEnabled())
            log.debug("Failed to enter to busy state (demander is stopping): " + cctx.nodeId());

        return false;
    }

    /**
     *
     */
    private void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * @param type Type.
     * @param discoEvt Discovery event.
     */
    private void preloadEvent(int type, GridDiscoveryEvent discoEvt) {
        preloadEvent(-1, type, discoEvt);
    }

    /**
     * @param part Partition.
     * @param type Type.
     * @param discoEvt Discovery event.
     */
    private void preloadEvent(int part, int type, GridDiscoveryEvent discoEvt) {
        assert discoEvt != null;

        cctx.events().addPreloadEvent(part, type, discoEvt.shadow(), discoEvt.type(), discoEvt.timestamp());
    }

    /**
     * @return Dummy node-left message.
     */
    private SupplyMessage<K, V> dummyTopology() {
        return DUMMY_TOP;
    }

    /**
     * @param msg Message to check.
     * @return {@code True} if dummy message.
     */
    private boolean dummyTopology(SupplyMessage<K, V> msg) {
        return msg == DUMMY_TOP;
    }

    /**
     * @param discoEvt Discovery evet.
     * @param reassign Dummy reassign flag.
     * @return Dummy partition exchange to handle reassignments if partition topology
     *      changes after preloading started.
     */
    private GridDhtPartitionsExchangeFuture<K, V> dummyExchange(boolean reassign,
        @Nullable GridDiscoveryEvent discoEvt) {
        return new GridDhtPartitionsExchangeFuture<K, V>(cctx, reassign, discoEvt);
    }

    /**
     * @param exch Exchange.
     * @return {@code True} if dummy exchange.
     */
    private boolean dummyExchange(GridDhtPartitionsExchangeFuture<K, V> exch) {
        return exch.dummy();
    }

    /**
     * @param exch Exchange.
     * @return {@code True} if dummy reassign.
     */
    private boolean dummyReassign(GridDhtPartitionsExchangeFuture<K, V> exch) {
        return exch.dummy() && exch.reassign();
    }

    /**
     * @return Current worker.
     */
    private GridWorker worker() {
        GridWorker w = GridWorkerGroup.instance(cctx.gridName()).currentWorker();

        assert w != null;

        return w;
    }

    /**
     * @param nodeId New node ID.
     * @param fut Exchange future.
     */
    void onDiscoveryEvent(UUID nodeId, GridDhtPartitionsExchangeFuture<K, V> fut) {
        if (!enterBusy())
            return;

        try {
            addFuture(fut);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * @param deque Deque to poll from.
     * @param time Time to wait.
     * @return Polled item.
     * @throws InterruptedException If interrupted.
     */
    @Nullable private <T> T poll(LinkedBlockingDeque<T> deque, long time) throws InterruptedException {
        beforeWait();

        return deque.poll(time, MILLISECONDS);
    }

    /**
     * @param deque Deque to poll from.
     * @return Polled item.
     * @throws InterruptedException If interrupted.
     */
    @Nullable private <T> T take(LinkedBlockingDeque<T> deque) throws InterruptedException {
        beforeWait();

        return deque.take();
    }

    /**
     * @param fut Future.
     * @return {@code True} if added.
     */
    boolean addFuture(GridDhtPartitionsExchangeFuture<K, V> fut) {
        if (fut.onAdded()) {
            exchWorker.addFuture(fut);

            synchronized (dmdWorkers) {
                for (DemandWorker w: dmdWorkers)
                    w.addMessage(DUMMY_TOP);
            }

            return true;
        }

        return false;
    }

    /**
     * There is currently a case where {@code interrupted}
     * flag on a thread gets flipped during stop which causes the pool to hang.  This check
     * will always make sure that interrupted flag gets reset before going into wait conditions.
     * <p>
     * The true fix should actually make sure that interrupted flag does not get reset or that
     * interrupted exception gets propagated. Until we find a real fix, this method should
     * always work to make sure that there is no hanging during stop.
     */
    private void beforeWait() {
        GridWorker w = worker();

        if (w != null && w.isCancelled())
            Thread.currentThread().interrupt();
    }

    /**
     * Refresh partitions.
     *
     * @param timeout Timeout.
     * @throws GridInterruptedException If interrupted.
     */
    private void refreshPartitions(long timeout) throws GridInterruptedException {
        long last = lastRefresh.get();

        long now = System.currentTimeMillis();

        if (last != -1 && now - last >= timeout && lastRefresh.compareAndSet(last, now)) {
            if (log.isDebugEnabled())
                log.debug("Refreshing partitions [last=" + last + ", now=" + now + ", delta=" + (now - last) +
                    ", timeout=" + timeout + ", lastRefresh=" + lastRefresh + ']');

            cctx.dht().dhtPreloader().refreshPartitions();
        }
        else
            if (log.isDebugEnabled())
                log.debug("Partitions were not refreshed [last=" + last + ", now=" + now + ", delta=" + (now - last) +
                    ", timeout=" + timeout + ", lastRefresh=" + lastRefresh + ']');
    }

    /**
     * @param p Partition.
     * @param allNodes All nodes.
     * @return Picked owners.
     */
    private Collection<GridNode> pickedOwners(int p, Collection<GridRichNode> allNodes) {
        Collection<GridRichNode> affNodes = cctx.affinity(p, allNodes);

        int affCnt = affNodes.size();

        Collection<GridNode> rmts = remoteOwners(p);

        assert allNodes.containsAll(rmts) : "All nodes does not contain all remote nodes [allNodes=" +
            F.nodeIds(allNodes) + ", rmtNodes=" + F.nodeIds(rmts) + ']';

        int rmtCnt = rmts.size();

        if (rmtCnt <= affCnt)
            return rmts;

        List<GridNode> sorted = new ArrayList<GridNode>(rmts);

        // Sort in descending order, so nodes with higher order will be first.
        Collections.sort(sorted, CU.nodeComparator(false));

        // Pick newest nodes.
        return sorted.subList(0, affCnt);
    }

    /**
     * @param p Partition.
     * @return Nodes owning this partition.
     */
    private Collection<GridNode> remoteOwners(int p) {
        return F.view(top.owners(p), cctx.remotes());
    }

    /**
     * @param assigns Assignments.
     */
    private void addAssignments(Assignments assigns) {
        if (log.isDebugEnabled())
            log.debug("Adding partition assignments: " + assigns);

        synchronized (dmdWorkers) {
            for (DemandWorker w : dmdWorkers) {
                w.addAssignments(assigns);

                w.addMessage(DUMMY_TOP);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPartitionDemandPool.class, this);
    }

    /**
     *
     */
    private class DemandWorker extends GridWorker {
        /** Worker ID. */
        private int id = -1;

        /** Partition-to-node assignments. */
        private final LinkedBlockingDeque<Assignments> assignQ = new LinkedBlockingDeque<Assignments>();

        /** Message queue. */
        private final LinkedBlockingDeque<SupplyMessage<K, V>> msgQ =
            new LinkedBlockingDeque<SupplyMessage<K, V>>();

        /** Counter. */
        private long cntr;

        /**
         * @param id Worker ID.
         */
        private DemandWorker(int id) {
            super(cctx.gridName(), "preloader-demand-worker", log);

            assert id >= 0;

            this.id = id;
        }

        /**
         * @param assigns Assignments.
         */
        void addAssignments(Assignments assigns) {
            assert assigns != null;

            assignQ.offer(assigns);

            if (log.isDebugEnabled())
                log.debug("Added assignments to worker: " + this);
        }

        /**
         * @return {@code True} if topology changed.
         */
        private boolean topologyChanged() {
            return !assignQ.isEmpty() || exchWorker.topologyChanged();
        }

        /**
         * @param msg Message.
         */
        private void addMessage(SupplyMessage<K, V> msg) {
            if (!enterBusy())
                return;

            try {
                assert dummyTopology(msg) || msg.supply().workerId() == id;

                msgQ.offer(msg);
            }
            finally {
                leaveBusy();
            }
        }

        /**
         * @param timeout Timed out value.
         */
        private void growTimeout(long timeout) {
            long newTimeout = (long)(timeout * 1.5D);

            // Account for overflow.
            if (newTimeout < 0)
                newTimeout = Long.MAX_VALUE;

            // Grow by 50% only if another thread didn't do it already.
            if (GridDhtPartitionDemandPool.this.timeout.compareAndSet(timeout, newTimeout))
                U.warn(log, "Increased preloading message timeout from " + timeout + "ms to " +
                    newTimeout + "ms.");
        }

        /**
         * @param pick Node picked for preloading.
         * @param p Partition.
         * @param entry Preloaded entry.
         * @return {@code False} if partition has become invalid during preloading.
         * @throws GridInterruptedException If interrupted.
         */
        private boolean preloadEntry(GridNode pick, int p, GridCacheEntryInfo<K, V> entry)
            throws GridException, GridInterruptedException {
            try {
                GridCacheEntryEx<K, V> cached = null;

                try {
                    cached = cctx.dht().entryEx(entry.key());

                    if (log.isDebugEnabled())
                        log.debug("Preloading key [key=" + entry.key() + ", part=" + p + ", node=" + pick.id() + ']');

                    if (cached.initialValue(
                        entry.value(),
                        entry.valueBytes(),
                        entry.version(),
                        entry.ttl(),
                        entry.expireTime(),
                        entry.metrics())) {
                        cctx.evicts().touch(cached); // Start tracking.
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Preloading entry is already in cache (will ignore) [key=" + cached.key() +
                            ", part=" + p + ']');
                }
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Entry has been concurrently removed while preloading (will ignore) [key=" +
                            cached.key() + ", part=" + p + ']');
                }
                catch (GridDhtInvalidPartitionException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Partition became invalid during preloading (will ignore): " + p);

                    return false;
                }
            }
            catch (GridInterruptedException e) {
                throw e;
            }
            catch (GridException e) {
                throw new GridException("Failed to cache preloaded entry (will stop preloading) [local=" +
                    cctx.nodeId() + ", node=" + pick.id() + ", key=" + entry.key() + ", part=" + p + ']', e);
            }

            return true;
        }

        /**
         * @param idx Unique index for this topic.
         * @return Topic name for partition.
         */
        public String topic(long idx) {
            return TOPIC_CACHE.name(cctx.namexx(), "preloader#" + id, "idx#" + Long.toString(idx));
        }

        /**
         * @param node Node to demand from.
         * @param topVer Topology version.
         * @param d Demand message.
         * @param exchFut Exchange future.
         * @return Missed partitions.
         * @throws InterruptedException If interrupted.
         * @throws GridTopologyException If node left.
         * @throws GridException If failed to send message.
         */
        private Set<Integer> demandFromNode(GridNode node, long topVer,  GridDhtPartitionDemandMessage<K, V> d,
            GridDhtPartitionsExchangeFuture<K, V> exchFut) throws InterruptedException, GridException {
            GridRichNode loc = cctx.localNode();

            cntr++;

            d.topic(topic(cntr));
            d.workerId(id);

            Set<Integer> missed = new HashSet<Integer>();

            // Get the same collection that will be sent in the message.
            Collection<Integer> remaining = d.partitions();

            // Drain queue before processing a new node.
            drainQueue();

            if (isCancelled() || topologyChanged())
                return missed;

            cctx.io().addOrderedHandler(d.topic(), new CI2<UUID, GridDhtPartitionSupplyMessage<K, V>>() {
                @Override public void apply(UUID nodeId, GridDhtPartitionSupplyMessage<K, V> msg) {
                    addMessage(new SupplyMessage<K, V>(nodeId, msg));
                }
            });

            GridStopwatch watch = W.stopwatch("PARTITION_NODE_DEMAND");

            try {
                boolean retry;

                // DoWhile.
                // =======
                do {
                    retry = false;

                    // Create copy.
                    d = new GridDhtPartitionDemandMessage<K, V>(d);

                    long timeout = GridDhtPartitionDemandPool.this.timeout.get();

                    d.timeout(timeout);

                    if (log.isDebugEnabled())
                        log.debug("Sending demand message [node=" + node.id() + ", demand=" + d + ']');

                    // Send demand message.
                    cctx.io().send(node, d);

                    watch.step("PARTITION_DEMAND_SENT");

                    // While.
                    // =====
                    while (!isCancelled() && !topologyChanged()) {
                        SupplyMessage<K, V> s = poll(msgQ, timeout);

                        // If timed out.
                        if (s == null) {
                            if (msgQ.isEmpty()) { // Safety check.
                                U.warn(log, "Timed out waiting for partitions to load, will retry in " + timeout +
                                    " ms (you may need to increase 'networkTimeout' or 'preloadBatchSize'" +
                                    " configuration properties).");

                                growTimeout(timeout);

                                // Ordered listener was removed if timeout expired.
                                cctx.io().removeOrderedHandler(d.topic());

                                // Must create copy to be able to work with IO manager thread local caches.
                                d = new GridDhtPartitionDemandMessage<K, V>(d);

                                // Create new topic.
                                d.topic(topic(++cntr));

                                // Create new ordered listener.
                                cctx.io().addOrderedHandler(d.topic(),
                                    new CI2<UUID, GridDhtPartitionSupplyMessage<K, V>>() {
                                        @Override public void apply(UUID nodeId,
                                            GridDhtPartitionSupplyMessage<K, V> msg) {
                                            addMessage(new SupplyMessage<K, V>(nodeId, msg));
                                        }
                                    });

                                // Resend message with larger timeout.
                                retry = true;

                                break; // While.
                            }
                            else
                                continue; // While.
                        }

                        // If topology changed.
                        if (dummyTopology(s)) {
                            if (topologyChanged())
                                break; // While.
                            else
                                continue; // While.
                        }

                        // Check that message was received from expected node.
                        if (!s.senderId().equals(node.id())) {
                            U.warn(log, "Received supply message from unexpected node [expectedId=" + node.id() +
                                ", rcvdId=" + s.senderId() + ", msg=" + s + ']');

                            continue; // While.
                        }

                        watch.step("SUPPLY_MSG_RCVD");

                        GridDhtPartitionSupplyMessage<K, V> supply = s.supply();

                        // Check whether there were class loading errors on unmarshalling.
                        if (supply.classError() != null) {
                            if (log.isDebugEnabled())
                                log.debug("Class got undeployed during preloading: " + supply.classError());

                            retry = true;

                            // Quit preloading.
                            break;
                        }

                        // Preload.
                        for (Map.Entry<Integer, Collection<GridCacheEntryInfo<K, V>>> e : supply.infos().entrySet()) {
                            int p = e.getKey();

                            if (cctx.belongs(p, topVer, loc)) {
                                GridDhtLocalPartition<K, V> part = top.localPartition(p, topVer, true);

                                assert part != null;

                                if (part.state() == MOVING) {
                                    boolean reserved = part.reserve();

                                    assert reserved : "Failed to reserve partition [gridName=" +
                                        cctx.gridName() + ", cacheName=" + cctx.namex() + ", part=" + part + ']';

                                    part.lock();

                                    try {
                                        Collection<Integer> invalidParts = new GridLeanSet<Integer>();

                                        // Loop through all received entries and try to preload them.
                                        for (GridCacheEntryInfo<K, V> entry : e.getValue()) {
                                            if (!invalidParts.contains(p)) {
                                                if (!part.preloadingPermitted(entry.key(), entry.version())) {
                                                    if (log.isDebugEnabled())
                                                        log.debug("Preloading is not permitted for entry due to " +
                                                            "evictions [key=" + entry.key() +
                                                            ", ver=" + entry.version() + ']');

                                                    continue;
                                                }

                                                if (!preloadEntry(node, p, entry)) {
                                                    invalidParts.add(p);

                                                    if (log.isDebugEnabled())
                                                        log.debug("Got entries for invalid partition during " +
                                                            "preloading (will skip) [p=" + p + ", entry=" + entry + ']');
                                                }
                                            }
                                        }

                                        watch.step("PRELOADED_ENTRIES");

                                        boolean last = supply.last().contains(p);

                                        // If message was last for this partition,
                                        // then we take ownership.
                                        if (last) {
                                            remaining.remove(p);

                                            top.own(part);

                                            if (log.isDebugEnabled())
                                                log.debug("Finished preloading partition: " + part);

                                            watch.step("LAST_PARTITION");

                                            preloadEvent(p, EVT_CACHE_PRELOAD_PART_LOADED, exchFut.discoveryEvent());
                                        }
                                    }
                                    finally {
                                        part.unlock();
                                        part.release();
                                    }
                                }
                                else {
                                    remaining.remove(p);

                                    if (log.isDebugEnabled())
                                        log.debug("Skipping loading partition (state is not MOVING): " + part);
                                }
                            }
                            else {
                                remaining.remove(p);

                                if (log.isDebugEnabled())
                                    log.debug("Skipping loading partition (it does not belong on current node): " + p);
                            }
                        }

                        remaining.removeAll(s.supply().missed());

                        // Only request partitions based on latest topology version.
                        missed.addAll(F.view(s.supply().missed(), new P1<Integer>() {
                            @Override public boolean apply(Integer p) {
                                return cctx.belongs(p, cctx.localNode());
                            }
                        }));

                        if (remaining.isEmpty())
                            break; // While.

                        if (s.supply().ack()) {
                            retry = true;

                            break;
                        }
                    }
                }
                while (retry && !isCancelled() && !topologyChanged());

                return missed;
            }
            finally {
                cctx.io().removeOrderedHandler(d.topic());

                watch.stop();
            }
        }

        /**
         * @throws InterruptedException If interrupted.
         */
        private void drainQueue() throws InterruptedException {
            while (!msgQ.isEmpty()) {
                SupplyMessage<K, V> msg = msgQ.take();

                if (log.isDebugEnabled())
                    log.debug("Drained supply message: " + msg);
            }
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"ThrowFromFinallyBlock", "LockAcquiredButNotSafelyReleased"})
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            syncFut.addWatch("PRELOAD_SYNC");

            try {
                GridDhtPartitionsExchangeFuture<K, V> exchFut = null;

                while (!isCancelled()) {
                    try {
                        // Barrier check must come first because we must always execute it.
                        if (barrier.await() == 0 && exchFut != null && !exchFut.dummy()) {
                            preloadEvent(EVT_CACHE_PRELOAD_STOPPED, exchFut.discoveryEvent());
                        }
                    }
                    catch (BrokenBarrierException ignore) {
                        throw new InterruptedException("Demand worker stopped.");
                    }

                    // Sync up all demand threads at this step.
                    Assignments assigns = null;

                    while (assigns == null) {
                        assigns = poll(assignQ, cctx.gridConfig().getNetworkTimeout());

                        if (assigns == null) {
                            if (demandLock.writeLock().tryLock()) {
                                try {
                                    cctx.deploy().unwind();
                                }
                                finally {
                                    demandLock.writeLock().unlock();
                                }
                            }
                        }
                    }

                    demandLock.readLock().lock();

                    try {
                        exchFut = assigns.exchangeFuture();

                        // Assignments are empty if preloading is disabled.
                        if (assigns.isEmpty())
                            continue;

                        boolean resync = false;

                        // While.
                        // =====
                        while (!isCancelled() && !topologyChanged() && !resync) {
                            Collection<Integer> missed = new HashSet<Integer>();

                            // For.
                            // ===
                            for (GridNode node : assigns.keySet()) {
                                if (topologyChanged() || isCancelled())
                                    break; // For.

                                GridDhtPartitionDemandMessage<K, V> d = assigns.remove(node);

                                // If another thread is already processing this message,
                                // move to the next node.
                                if (d == null)
                                    continue; // For.

                                try {
                                    Set<Integer> set = demandFromNode(node, assigns.topologyVersion(), d, exchFut);

                                    if (!set.isEmpty()) {
                                        if (log.isDebugEnabled())
                                            log.debug("Missed partitions from node [nodeId=" + node.id() + ", missed=" +
                                                set + ']');

                                        missed.addAll(set);
                                    }
                                }
                                catch (GridInterruptedException e) {
                                    throw e;
                                }
                                catch (GridTopologyException e) {
                                    if (log.isDebugEnabled())
                                        log.debug("Node left during preloading (will retry) [node=" + node.id() +
                                            ", msg=" + e.getMessage() + ']');

                                    resync = true;

                                    break; // For.
                                }
                                catch (GridException e) {
                                    U.error(log, "Failed to receive partitions from node (preloading will not " +
                                        "fully finish) [node=" + node.id() + ", msg=" + d + ']', e);
                                }
                            }

                            // Processed missed entries.
                            if (!missed.isEmpty()) {
                                if (log.isDebugEnabled())
                                    log.debug("Reassigning partitions that were missed: " + missed);

                                exchWorker.addFuture(dummyExchange(true, exchFut.discoveryEvent()));
                            }
                            else
                                break; // While.
                        }
                    }
                    finally {
                        demandLock.readLock().unlock();

                        syncFut.onWorkerDone(this);
                    }

                    resendPartitions();
                }
            }
            finally {
                // Safety.
                syncFut.onWorkerDone(this);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(DemandWorker.class, this, "assignQ", assignQ, "msgQ", msgQ, "super", super.toString());
        }
    }

    /**
     * Partition to node assignments.
     */
    private class Assignments extends ConcurrentHashMap<GridNode, GridDhtPartitionDemandMessage<K, V>> {
        /** Exchange future. */
        @GridToStringExclude
        private final GridDhtPartitionsExchangeFuture<K, V> exchFut;

        /** Last join order. */
        private final long topVer;

        /**
         * @param exchFut Exchange future.
         * @param topVer Last join order.
         */
        Assignments(GridDhtPartitionsExchangeFuture<K, V> exchFut, long topVer) {
            assert exchFut != null;
            assert topVer > 0;

            this.exchFut = exchFut;
            this.topVer = topVer;
        }

        /**
         * @return Exchange future.
         */
        GridDhtPartitionsExchangeFuture<K, V> exchangeFuture() {
            return exchFut;
        }

        /**
         * @return Topology version.
         */
        long topologyVersion() {
            return topVer;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(Assignments.class, this, "exchId", exchFut.exchangeId(), "super", super.toString());
        }
    }

    /**
     * Exchange future thread. All exchanges happen only by one thread and next
     * exchange will not start until previous one completes.
     */
    private class ExchangeWorker extends GridWorker {
        /** Future queue. */
        private final LinkedBlockingDeque<GridDhtPartitionsExchangeFuture<K, V>> futQ =
            new LinkedBlockingDeque<GridDhtPartitionsExchangeFuture<K, V>>();

        /** Busy flag used as performance optimization to stop current preloading. */
        private volatile boolean busy;

        /**
         *
         */
        private ExchangeWorker() {
            super(cctx.gridName(), "partition-exchanger", log);
        }

        /**
         * @param exchFut Exchange future.
         */
        void addFuture(GridDhtPartitionsExchangeFuture<K, V> exchFut) {
            assert exchFut != null;

            if (!exchFut.dummy() || (futQ.isEmpty() && !busy))
                futQ.offer(exchFut);

            if (log.isDebugEnabled())
                log.debug("Added exchange future to exchange worker: " + exchFut);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            long timeout = cctx.gridConfig().getNetworkTimeout();

            while (!isCancelled()) {
                GridStopwatch watch = W.stopwatch("PARTITION_MAP_SYNCUP");

                GridDhtPartitionsExchangeFuture<K, V> exchFut = null;

                try {
                    // If not first preloading and no more topology events present,
                    // then we periodically refresh partition map.
                    if (futQ.isEmpty() && syncFut.isDone()) {
                        refreshPartitions(timeout);

                        timeout = cctx.gridConfig().getNetworkTimeout();
                    }

                    // After workers line up and before preloading starts we initialize all futures.
                    if (log.isDebugEnabled())
                        log.debug("Before waiting for exchange futures [futs" +
                            F.view((cctx.dht().dhtPreloader()).exchangeFutures(), F.unfinishedFutures()) +
                            ", worker=" + worker() + ']');

                    // Take next exchange future.
                    exchFut = poll(futQ, timeout);

                    if (exchFut == null)
                        continue; // Main while loop.

                    busy = true;

                    Assignments assigns;

                    try {
                        watch.step("RECEIVED_EXCHANGE_FUTURE");

                        if (isCancelled())
                            break;

                        if (!dummyExchange(exchFut)) {
                            exchFut.init();

                            exchFut.get();

                            if (log.isDebugEnabled())
                                log.debug("After waiting for exchange future [exchFut=" + exchFut + ", worker=" +
                                    worker() + ']');

                            watch.step("EXCHANGE_FUTURE_DONE");

                            // Just pick first worker to do this, so we don't
                            // invoke topology callback more than once for the
                            // same event.
                            if (top.afterExchange(exchFut.exchangeId()))
                                resendPartitions(); // Force topology refresh.

                            // Preload event notification.
                            preloadEvent(EVT_CACHE_PRELOAD_STARTED, exchFut.discoveryEvent());
                        }
                        else {
                            if (log.isDebugEnabled())
                                log.debug("Got dummy exchange (will reassign)");

                            if (!dummyReassign(exchFut)) {
                                timeout = 0; // Force refresh.

                                continue;
                            }
                        }

                        assigns = assign(exchFut);

                        watch.step("REASSIGNED_PARTITIONS");
                    }
                    finally {
                        // Must flip busy flag before assignments are given to demand workers.
                        busy = false;
                    }

                    addAssignments(assigns);
                }
                catch (GridInterruptedException e) {
                    throw e;
                }
                catch (GridException e) {
                    U.error(log, "Failed to wait for completion of partition map exchange " +
                        "(preloading will not start): " + exchFut, e);
                }
                finally {
                    watch.stop();
                }
            }
        }

        /**
         * @return {@code True} if another exchange future has been queued up.
         */
        boolean topologyChanged() {
            return !futQ.isEmpty() || busy;
        }

        /**
         * @param exchFut Exchange future.
         * @return Assignments of partitions to nodes.
         */
        private Assignments assign(GridDhtPartitionsExchangeFuture<K, V> exchFut) {
            // No assignments for disabled preloader.
            if (!cctx.preloadEnabled())
                return new Assignments(exchFut, top.topologyVersion());

            int partCnt = cctx.partitions();

            GridRichNode loc = cctx.localNode();

            assert dummyReassign(exchFut) || exchFut.exchangeId().topologyVersion() == top.topologyVersion() :
                "Topology version mismatch [exchId=" + exchFut.exchangeId() + ", topVer=" + top.topologyVersion() + ']';

            Assignments assigns = new Assignments(exchFut, top.topologyVersion());

            Collection<GridRichNode> allNodes = CU.allNodes(cctx, assigns.topologyVersion());

            for (int p = 0; p < partCnt && !isCancelled() && futQ.isEmpty(); p++) {
                // If partition belongs to local node.
                if (cctx.belongs(p, loc, allNodes)) {
                    GridDhtLocalPartition<K, V> part = top.localPartition(p, assigns.topologyVersion(), true);

                    assert part != null;
                    assert part.id() == p;

                    if (part.state() != MOVING) {
                        if (log.isDebugEnabled())
                            log.debug("Skipping partition assignment (state is not MOVING): " + part);

                        continue; // For.
                    }

                    Collection<GridNode> picked = pickedOwners(p, allNodes);

                    if (picked.isEmpty()) {
                        top.own(part);

                        if (log.isDebugEnabled())
                            log.debug("Owning partition as there are no other owners: " + part);
                    }
                    else {
                        GridNode n = F.first(picked);

                        GridDhtPartitionDemandMessage<K, V> msg = assigns.get(n);

                        if (msg == null)
                            msg = F.addIfAbsent(assigns, n,
                                new GridDhtPartitionDemandMessage<K, V>(top.updateSequence()));

                        msg.addPartition(p);
                    }
                }
            }

            return assigns;
        }
    }

    /**
     *
     */
    private class SyncFuture extends GridFutureAdapter<Object> {
        /** Remaining workers. */
        private Collection<DemandWorker> remaining;

        /**
         * @param workers List of workers.
         */
        private SyncFuture(Collection<DemandWorker> workers) {
            super(cctx.kernalContext());

            assert workers.size() == poolSize();

            remaining = Collections.synchronizedList(new LinkedList<DemandWorker>(workers));
        }

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public SyncFuture() {
            assert false;
        }

        /**
         * @param w Worker who iterated through all partitions.
         */
        void onWorkerDone(DemandWorker w) {
            if (isDone())
                return;

            if (remaining.remove(w))
                if (log.isDebugEnabled())
                    log.debug("Completed full partition iteration for worker [worker=" + w + ']');

            if (remaining.isEmpty()) {
                if (log.isDebugEnabled())
                    log.debug("Completed sync future.");

                lastRefresh.compareAndSet(-1, System.currentTimeMillis());

                onDone();
            }
        }
    }

    /**
     * Supply message wrapper.
     */
    private static class SupplyMessage<K, V> {
        /** Sender ID. */
        private UUID senderId;

        /** Supply message. */
        private GridDhtPartitionSupplyMessage<K, V> supply;

        /**
         * Dummy constructor.
         */
        private SupplyMessage() {
            // No-op.
        }

        /**
         * @param senderId Sender ID.
         * @param supply Supply message.
         */
        SupplyMessage(UUID senderId, GridDhtPartitionSupplyMessage<K, V> supply) {
            this.senderId = senderId;
            this.supply = supply;
        }

        /**
         * @return Sender ID.
         */
        UUID senderId() {
            return senderId;
        }

        /**
         * @return Message.
         */
        GridDhtPartitionSupplyMessage<K, V> supply() {
            return supply;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SupplyMessage.class, this);
        }
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * Thread pool for supplying entries to demanding nodes.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class GridReplicatedPreloadSupplyPool<K, V> {
    /** Cache context. */
    private final GridCacheContext<K, V> cctx;

    /** Logger. */
    private final GridLogger log;

    /** Predicate to check whether local node has already finished preloading. */
    private final GridAbsPredicate preloadFinished;

    /** Busy lock. */
    private final ReadWriteLock busyLock;

    /** Workers. */
    private final Collection<SupplyWorker> workers = new LinkedList<SupplyWorker>();

    /** Queue. */
    private final BlockingQueue<DemandMessage<K, V>> queue = new LinkedBlockingQueue<DemandMessage<K, V>>();

    /**
     * @param cctx Cache context.
     * @param preloadFinished Preload finished callback.
     * @param busyLock Shutdown lock.
     */
    GridReplicatedPreloadSupplyPool(GridCacheContext<K, V> cctx, GridAbsPredicate preloadFinished,
        ReadWriteLock busyLock) {
        assert cctx != null;
        assert preloadFinished != null;
        assert busyLock != null;

        this.cctx = cctx;
        this.preloadFinished = preloadFinished;
        this.busyLock = busyLock;

        log = cctx.logger(getClass());

        int poolSize = cctx.preloadEnabled() ? cctx.config().getPreloadThreadPoolSize() : 0;

        for (int i = 0; i < poolSize; i++)
            workers.add(new SupplyWorker());

        cctx.io().addHandler(GridReplicatedPreloadDemandMessage.class,
            new CI2<UUID, GridReplicatedPreloadDemandMessage<K, V>>() {
                @Override public void apply(UUID id, GridReplicatedPreloadDemandMessage<K, V> m) {
                    addMessage(id, m);
                }
            });
    }

    /**
     *
     */
    void start() {
        for (SupplyWorker w : workers)
            new GridThread(cctx.gridName(), "preloader-supply-worker", w).start();

        if (log.isDebugEnabled())
            log.debug("Started supply pool: " + workers.size());
    }

    /**
     *
     */
    void stop() {
        U.cancel(workers);
        U.join(workers, log);
    }

    /**
     * @return Size of this thread pool.
     */
    int poolSize() {
        return cctx.config().getPreloadThreadPoolSize();
    }

    /**
     * @return {@code true} if entered busy state.
     */
    private boolean enterBusy() {
        if (busyLock.readLock().tryLock())
            return true;

        if (log.isDebugEnabled())
            log.debug("Failed to enter to busy state (supplier is stopping): " + cctx.nodeId());

        return false;
    }

    /**
     *
     */
    private void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * @param nodeId Sender node ID.
     * @param d Message.
     */
    private void addMessage(UUID nodeId, GridReplicatedPreloadDemandMessage<K, V> d) {
        if (!enterBusy())
            return;

        try {
            if (cctx.preloadEnabled()) {
                if (log.isDebugEnabled())
                    log.debug("Received demand message [node=" + nodeId + ", msg=" + d + ']');

                queue.offer(new DemandMessage<K, V>(nodeId, d));
            }
            else
                U.warn(log, "Received demand message when preloading is disabled (will ignore): " + d);
        }
        finally {
            leaveBusy();
        }
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
     * @return Current worker.
     */
    private GridWorker worker() {
        GridWorker w = GridWorkerGroup.instance(cctx.gridName()).currentWorker();

        assert w != null;

        return w;
    }

    /**
     * @param queue Deque to poll from.
     * @return Polled item.
     * @throws InterruptedException If interrupted.
     */
    private <T> T take(BlockingQueue<T> queue) throws InterruptedException {
        beforeWait();

        return queue.take();
    }

    /**
     * Supply worker.
     */
    private class SupplyWorker extends GridWorker {
        /**
         * Default constructor.
         */
        private SupplyWorker() {
            super(cctx.gridName(), "preloader-supply-worker", log);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            while (!isCancelled()) {
                DemandMessage<K, V> msg = take(queue);

                GridRichNode node = cctx.discovery().richNode(msg.senderId());

                if (node == null) {
                    if (log.isDebugEnabled())
                        log.debug("Received message from non-existing node (will ignore): " + msg);

                    continue;
                }

                GridReplicatedPreloadDemandMessage<K, V> d = msg.message();

                if (!preloadFinished.apply()) {
                    // Local node has not finished preloading yet and cannot supply.
                    GridReplicatedPreloadSupplyMessage<K, V> s = new GridReplicatedPreloadSupplyMessage<K, V>(
                        d.workerId(), true);

                    try {
                        reply(node, d, s);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to send supply message to node: " + node.id(), e);
                    }

                    continue;
                }

                GridFuture<?> fut = cctx.partitionReleaseFuture(Collections.singleton(d.partition()), node.order());

                try {
                    fut.get(d.timeout());
                }
                catch (GridException e) {
                    U.error(log, "Failed to wait until partition is released: " + d.partition(), e);

                    continue;
                }

                GridReplicatedPreloadSupplyMessage<K, V> s = new GridReplicatedPreloadSupplyMessage<K, V>(d.workerId());

                try {
                    boolean nodeLeft = false;

                    for (GridCacheEntryEx<K, V> entry : cctx.cache().allEntries()) {
                        // Mod entry hash to the number of nodes.
                        if (Math.abs(entry.hashCode() % d.nodeCount()) != d.mod() ||
                            cctx.partition(entry.key()) != d.partition())
                            continue;

                        GridCacheEntryInfo<K, V> info = entry.info();

                        if (info != null && info.value() != null)
                            s.addEntry(info, cctx);

                        if (s.size() >= cctx.config().getPreloadBatchSize()) {
                            if (!reply(node, d, s)) {
                                // Demander left grid.
                                nodeLeft = true;

                                break;
                            }

                            s = new GridReplicatedPreloadSupplyMessage<K, V>(d.workerId());
                        }
                    }

                    // Do that only if node has not left yet.
                    if (!nodeLeft) {
                        // Cache entries are fully iterated at this point.
                        s.last(true);

                        reply(node, d, s);
                    }
                }
                catch (GridInterruptedException e) {
                    throw e;
                }
                catch (GridException e) {
                    log.error("Failed to send supply message to node: " + node.id(), e);
                }
            }
        }

        /**
         * @param n Node.
         * @param d Demand message.
         * @param s Supply message.
         * @return {@code True} if message was sent, {@code false} if recipient left grid.
         * @throws GridException If failed.
         */
        private boolean reply(GridNode n, GridReplicatedPreloadDemandMessage<K, V> d,
            GridReplicatedPreloadSupplyMessage<K, V> s) throws GridException {
            try {
                cctx.io().sendOrderedMessage(n, d.topic(), cctx.io().messageId(d.topic(), n.id()), s, d.timeout());

                if (log.isDebugEnabled())
                    log.debug("Replied to demand message [node=" + n.id() + ", demand=" + d + ", supply=" + s + ']');

                return true;
            }
            catch (GridTopologyException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Failed to send supply message because node left grid: " + n.id());

                return false;
            }
        }
    }

    /**
     * Demand message wrapper.
     */
    private static class DemandMessage<K, V> extends GridTuple2<UUID, GridReplicatedPreloadDemandMessage<K, V>> {
        /**
         * @param senderId Sender ID.
         * @param msg Message.
         */
        DemandMessage(UUID senderId, GridReplicatedPreloadDemandMessage<K, V> msg) {
            super(senderId, msg);
        }

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public DemandMessage() {
            // No-op.
        }

        /**
         * @return Sender ID.
         */
        UUID senderId() {
            return get1();
        }

        /**
         * @return Message.
         */
        public GridReplicatedPreloadDemandMessage<K, V> message() {
            return get2();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "DemandMessage [senderId=" + senderId() + ", msg=" + message() + ']';
        }
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.timeout.*;
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
import java.util.concurrent.locks.*;

/**
 * Future for exchanging partition maps.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridDhtPartitionsExchangeFuture<K, V> extends GridFutureAdapter<Object>
    implements Comparable<GridDhtPartitionsExchangeFuture<K, V>> {
    /** Dummy flag. */
    private final boolean dummy;

    /** Dummy reassign flag. */
    private final boolean reassign;

    /** */
    private final GridDhtPartitionTopology<K, V> top;

    /** Discovery event. */
    private volatile GridDiscoveryEvent discoEvt;

    /** */
    @GridToStringInclude
    private final Collection<UUID> rcvdIds = new GridConcurrentHashSet<UUID>();

    /** Remote nodes. */
    private volatile Collection<GridRichNode> rmtNodes;

    /** Remote nodes. */
    @GridToStringInclude
    private volatile Collection<UUID> rmtIds;

    /** Oldest node. */
    @GridToStringExclude
    private final AtomicReference<GridNode> oldestNode = new AtomicReference<GridNode>();

    /** ExchangeFuture id. */
    private final GridDhtPartitionExchangeId exchId;

    /** Init flag. */
    @GridToStringInclude
    private final AtomicBoolean init = new AtomicBoolean(false);

    /** Ready for reply flag. */
    @GridToStringInclude
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /** Replied flag. */
    @GridToStringInclude
    private final AtomicBoolean replied = new AtomicBoolean(false);

    /** Timeout object. */
    @GridToStringExclude
    private volatile GridTimeoutObject timeoutObj;

    /** Cache context. */
    private final GridCacheContext<K, V> cctx;

    /** Busy lock to prevent activities from accessing exchanger while it's stopping. */
    private ReadWriteLock busyLock;

    /** */
    private AtomicBoolean added = new AtomicBoolean(false);

    /** Event latch. */
    @GridToStringExclude
    private CountDownLatch evtLatch = new CountDownLatch(1);

    /** */
    private GridFutureAdapter<Boolean> initFut;

    /**
     * Messages received on non-coordinator are stored in case if this node
     * becomes coordinator.
     */
    private final Map<UUID, GridDhtPartitionsSingleMessage<K, V>> msgs =
        new ConcurrentHashMap<UUID, GridDhtPartitionsSingleMessage<K, V>>();

    /** */
    @SuppressWarnings( {"FieldCanBeLocal", "UnusedDeclaration"})
    @GridToStringInclude
    private volatile GridFuture<?> partReleaseFut;

    /** */
    private final Object mux = new Object();

    /** Logger. */
    private GridLogger log;

    /**
     * Dummy future created to trigger reassignments if partition
     * topology changed while preloading.
     *
     * @param cctx Cache context.
     * @param reassign Dummy reassign flag.
     * @param discoEvt Discovery event.
     */
    public GridDhtPartitionsExchangeFuture(GridCacheContext<K, V> cctx, boolean reassign, GridDiscoveryEvent discoEvt) {
        super(cctx.kernalContext());
        dummy = true;

        top = null;
        exchId = null;

        this.reassign = reassign;
        this.discoEvt = discoEvt;
        this.cctx = cctx;

        syncNotify(true);

        onDone();
    }

    /**
     * @param cctx Cache context.
     * @param busyLock Busy lock.
     * @param exchId Exchange ID.
     */
    GridDhtPartitionsExchangeFuture(GridCacheContext<K, V> cctx, ReadWriteLock busyLock,
        GridDhtPartitionExchangeId exchId) {
        super(cctx.kernalContext());

        syncNotify(true);

        assert busyLock != null;
        assert exchId != null;

        dummy = false;
        reassign = false;

        this.cctx = cctx;
        this.busyLock = busyLock;
        this.exchId = exchId;

        log = cctx.logger(getClass());

        top = cctx.dht().topology();

        GridRichNode loc = cctx.localNode();

        // Grab all nodes with order of equal or less than last joined node.
        Collection<GridRichNode> allNodes = new LinkedList<GridRichNode>(CU.allNodes(cctx, exchId.topologyVersion()));

        oldestNode.set(F.isEmpty(allNodes) ? loc : CU.oldest(allNodes));

        assert oldestNode.get() != null;

        initFut = new GridFutureAdapter<Boolean>(ctx, true);

        addWatch(cctx.stopwatch("EXCHANGE_PARTITIONS"));

        if (log.isDebugEnabled())
            log.debug("Creating exchange future [cacheName=" + cctx.namex() + ", localNode=" + cctx.nodeId() +
                ", fut=" + this + ']');
    }

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtPartitionsExchangeFuture() {
        assert false;

        dummy = true;
        reassign = false;

        top = null;
        exchId = null;
        cctx = null;
    }

    /**
     * @return Dummy flag.
     */
    boolean dummy() {
        return dummy;
    }

    /**
     * @return Dummy reassign flag.
     */
    boolean reassign() {
        return reassign;
    }

    /**
     * Rechecks topology.
     */
    void initTopology() {
        // Grab all nodes with order of equal or less than last joined node.
        Collection<GridRichNode> allNodes = new LinkedList<GridRichNode>(CU.allNodes(cctx, exchId.topologyVersion()));

        rmtNodes = new ConcurrentLinkedQueue<GridRichNode>(F.view(allNodes, F.remoteNodes(cctx.nodeId())));

        rmtIds = Collections.unmodifiableSet(new HashSet<UUID>(F.nodeIds(rmtNodes)));

        for (Map.Entry<UUID, GridDhtPartitionsSingleMessage<K, V>> m :
            new HashMap<UUID, GridDhtPartitionsSingleMessage<K, V>>(msgs).entrySet()) {
            // If received any messages, process them.
            onReceive(m.getKey(), m.getValue());
        }

        // If this is the oldest node.
        if (oldestNode.get().id().equals(cctx.nodeId()))
            if (allReceived() && ready.get() && replied.compareAndSet(false, true)) {
                spreadPartitions(top.partitionMap(true));

                onDone();
            }
    }

    /**
     * @return {@code True}
     */
    boolean onAdded() {
        return added.compareAndSet(false, true);
    }

    /**
     * Event callback.
     *
     * @param exchId Exchange ID.
     * @param discoEvt Discovery event.
     */
    void onEvent(GridDhtPartitionExchangeId exchId, GridDiscoveryEvent discoEvt) {
        assert exchId.equals(this.exchId);

        this.discoEvt = discoEvt;

        evtLatch.countDown();
    }

    /**
     * @return Discovery event.
     */
    GridDiscoveryEvent discoveryEvent() {
        return discoEvt;
    }

    /**
     * @return Exchange id.
     */
    GridDhtPartitionExchangeId key() {
        return exchId;
    }

    /**
     * @return Oldest node.
     */
    GridNode oldestNode() {
        return oldestNode.get();
    }

    /**
     * @return Exchange ID.
     */
    GridDhtPartitionExchangeId exchangeId() {
        return exchId;
    }

    /**
     * @return Init future.
     */
    GridFuture<?> initFuture() {
        return initFut;
    }

    /**
     * @return {@code true} if entered to busy state.
     */
    private boolean enterBusy() {
        if (busyLock.readLock().tryLock())
            return true;

        if (log.isDebugEnabled())
            log.debug("Failed to enter busy state (exchanger is stopping): " + this);

        return false;
    }

    /**
     *
     */
    private void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * @return Init flag.
     */
    boolean isInit() {
        return init.get();
    }

    /**
     * Starts activity.
     *
     * @throws GridInterruptedException If interrupted.
     */
    void init() throws GridInterruptedException {
        assert oldestNode != null;

        if (init.compareAndSet(false, true)) {
            if (isDone())
                return;

            try {
                // Wait for event to occur to make sure that discovery
                // will return corresponding nodes.
                U.await(evtLatch);

                assert discoEvt != null;

                // Must initialize topology after we get discovery event.
                initTopology();

                // Update before waiting for locks.
                top.updateTopologyVersion(exchId);

                // Only wait in case of join event. If a node leaves,
                // then transactions handled by it will be either remapped
                // or invalidated.
                if (exchId.isJoined()) {
                    GridNode node = cctx.node(exchId.nodeId());

                    if (node == null) {
                        if (log.isDebugEnabled())
                            log.debug("Joined node left before exchange completed (nothing to do): " + this);

                        onDone();

                        return;
                    }

                    long topVer = exchId.topologyVersion();

                    assert topVer == top.topologyVersion();

                    Set<Integer> parts = cctx.primaryPartitions(node, topVer);

                    GridFuture<?> partReleaseFut = cctx.partitionReleaseFuture(parts, topVer);

                    // Assign to class variable so it will be included into toString() method.
                    this.partReleaseFut = partReleaseFut;

                    if (log.isDebugEnabled())
                        log.debug("Before waiting for partition release future: " + this);

                    partReleaseFut.get();

                    if (log.isDebugEnabled())
                        log.debug("After waiting for partition release future: " + this);
                }

                top.beforeExchange(exchId);
            }
            catch (GridInterruptedException e) {
                onDone(e);

                throw e;
            }
            catch (GridException e) {
                U.error(log, "Failed to reinitialize local partitions (preloading will be stopped): " + exchId, e);

                onDone(e);

                return;
            }

            if (F.isEmpty(rmtIds)) {
                onDone();

                return;
            }

            ready.set(true);

            initFut.onDone(true);

            if (log.isDebugEnabled())
                log.debug("Initialized future: " + this);

            // If this node is not oldest.
            if (!oldestNode.get().id().equals(cctx.nodeId()))
                sendPartitions();
            else if (allReceived() && replied.compareAndSet(false, true)) {
                if (spreadPartitions(top.partitionMap(true)))
                    onDone();
            }

            scheduleRecheck();

            watch.step("EXCHANGE_STARTED");
        }
        else
            assert false : "Skipped init future: " + this;
    }

    /**
     * @param node Node.
     * @param id ID.
     * @throws GridException If failed.
     */
    private void sendLocalPartitions(GridNode node, @Nullable GridDhtPartitionExchangeId id) throws GridException {
        GridDhtPartitionsSingleMessage<K, V> m = new GridDhtPartitionsSingleMessage<K, V>(id, top.localPartitionMap());

        if (log.isDebugEnabled())
            log.debug("Sending local partitions [nodeId=" + node.id() + ", exchId=" + exchId + ", msg=" + m + ']');

        cctx.io().send(node, m);
    }

    /**
     * @param nodes Nodes.
     * @param id ID.
     * @param partMap Partition map.
     * @throws GridException If failed.
     */
    private void sendAllPartitions(Collection<? extends GridNode> nodes, GridDhtPartitionExchangeId id,
        GridDhtPartitionFullMap partMap) throws GridException {
        GridDhtPartitionsFullMessage<K, V> m = new GridDhtPartitionsFullMessage<K, V>(id, partMap);

        if (log.isDebugEnabled())
            log.debug("Sending full partition map [nodeIds=" + F.viewReadOnly(nodes, F.node2id()) +
                ", exchId=" + exchId + ", msg=" + m + ']');

        cctx.io().safeSend(nodes, m, null);
    }

    /**
     *
     */
    private void sendPartitions() {
        GridNode oldestNode = this.oldestNode.get();

        try {
            sendLocalPartitions(oldestNode, exchId);
        }
        catch (GridTopologyException ignore) {
            if (log.isDebugEnabled())
                log.debug("Oldest node left during partition exchange [nodeId=" + oldestNode.id() +
                    ", exchId=" + exchId + ']');
        }
        catch (GridException e) {
            scheduleRecheck();

            U.error(log, "Failed to send local partitions to oldest node (will retry after timeout) [oldestNodeId=" +
                oldestNode.id() + ", exchId=" + exchId + ']', e);
        }
    }

    /**
     * @param partMap Partition map.
     * @return {@code True} if succeeded.
     */
    private boolean spreadPartitions(GridDhtPartitionFullMap partMap) {
        try {
            sendAllPartitions(rmtNodes, exchId, partMap);

            return true;
        }
        catch (GridException e) {
            scheduleRecheck();

            U.error(log, "Failed to send full partition map to nodes (will retry after timeout) [nodes=" +
                F.nodeId8s(rmtNodes) + ", exchangeId=" + exchId + ']', e);

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(Object res, Throwable err) {
        if (super.onDone(res, err) && !dummy) {
            if (log.isDebugEnabled())
                log.debug("Completed partition exchange [localNode=" + cctx.nodeId() + ", exchange= " + this + ']');

            initFut.onDone(err == null);

            GridTimeoutObject timeoutObj = this.timeoutObj;

            // Deschedule timeout object.
            if (timeoutObj != null)
                cctx.time().removeTimeoutObject(timeoutObj);

            return true;
        }

        return dummy;
    }

    /**
     * @return {@code True} if all replies are received.
     */
    private boolean allReceived() {
        Collection<UUID> rmtIds = this.rmtIds;

        assert rmtIds != null : "Remote Ids can't be null: " + this;

        return rcvdIds.containsAll(rmtIds);
    }

    /**
     * @param nodeId Sender node id.
     * @param msg Single partition info.
     */
    void onReceive(final UUID nodeId, final GridDhtPartitionsSingleMessage<K, V> msg) {
        assert msg != null;

        assert msg.exchangeId().equals(exchId);

        if (isDone()) {
            if (log.isDebugEnabled())
                log.debug("Received message for finished future (will reply only to sender) [msg=" + msg +
                    ", fut=" + this + ']');

            try {
                GridNode n = cctx.node(nodeId);

                if (n != null && oldestNode.get().id().equals(cctx.nodeId()))
                    sendAllPartitions(F.asList(n), exchId, top.partitionMap(true));
            }
            catch (GridException e) {
                scheduleRecheck();

                U.error(log, "Failed to send full partition map to node (will retry after timeout) [node=" + nodeId +
                    ", exchangeId=" + exchId + ']', e);
            }
        }
        else {
            initFut.listenAsync(new CI1<GridFuture<Boolean>>() {
                @Override public void apply(GridFuture<Boolean> t) {
                    try {
                        if (!t.get()) // Just to check if there was an error.
                            return;

                        AtomicReference<GridNode> oldestRef = oldestNode;

                        GridNode loc = cctx.localNode();

                        msgs.put(nodeId, msg);

                        boolean match = true;

                        // Check if oldest node has changed.
                        if (!oldestRef.get().equals(loc)) {
                            match = false;

                            synchronized (mux) {
                                // Double check.
                                if (oldestRef.get().equals(loc))
                                    match = true;
                            }
                        }

                        if (match) {
                            if (rcvdIds.add(nodeId))
                                top.update(exchId, msg.partitions());

                            // If got all replies, and initialization finished, and reply has not been sent yet.
                            if (allReceived() && ready.get() && replied.compareAndSet(false, true)) {
                                spreadPartitions(top.partitionMap(true));

                                onDone();
                            }
                            else if (log.isDebugEnabled())
                                log.debug("Exchange future full map is not sent [allReceived=" + allReceived() +
                                    ", ready=" + ready + ", replied=" + replied.get() + ", init=" + init.get() +
                                    ", fut=" + this + ']');
                        }
                    }
                    catch (GridException e) {
                        log.error("Failed to initialize exchange future: " + this, e);
                    }
                }
            });
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Full partition info.
     */
    void onReceive(UUID nodeId, final GridDhtPartitionsFullMessage<K, V> msg) {
        assert msg != null;

        if (isDone()) {
            if (log.isDebugEnabled())
                log.debug("Received message for finished future [msg=" + msg + ", fut=" + this + ']');

            return;
        }

        if (!nodeId.equals(oldestNode.get().id())) {
            if (log.isDebugEnabled())
                log.debug("Received full partition map from unexpected node [oldest=" + oldestNode.get().id() +
                    ", unexpectedNodeId=" + nodeId + ']');

            return;
        }

        assert msg.exchangeId().equals(exchId);

        if (log.isDebugEnabled())
            log.debug("Received full partition map from node [nodeId=" + nodeId + ", msg=" + msg + ']');

        initFut.listenAsync(new CI1<GridFuture<Boolean>>() {
            @Override public void apply(GridFuture<Boolean> t) {
                top.update(exchId, msg.partitions());

                onDone();
            }
        });
    }

    /**
     * @param nodeId Left node id.
     */
    public void onNodeLeft(final UUID nodeId) {
        if (isDone())
            return;

        if (!enterBusy())
            return;

        try {
            // Wait for initialization part of this future to complete.
            initFut.listenAsync(new CI1<GridFuture<?>>() {
                @Override public void apply(GridFuture<?> f) {
                    if (isDone())
                        return;

                    if (!enterBusy())
                        return;

                    try {
                        // Pretend to have received message from this node.
                        rcvdIds.add(nodeId);

                        Collection<UUID> rmtIds = GridDhtPartitionsExchangeFuture.this.rmtIds;

                        assert rmtIds != null;

                        AtomicReference<GridNode> oldestNode = GridDhtPartitionsExchangeFuture.this.oldestNode;

                        GridNode oldest = oldestNode.get();

                        if (oldest.id().equals(nodeId)) {
                            if (log.isDebugEnabled())
                                log.debug("Oldest node left or failed on partition exchange " +
                                    "(will restart exchange process)) [cacheName=" + cctx.namex() +
                                    ", oldestNodeId=" + oldest.id() + ", exchangeId=" + exchId + ']');

                            boolean set = false;

                            GridNode newOldest = CU.oldest(CU.allNodes(cctx, exchId.topologyVersion()));

                            // If local node is now oldest.
                            if (newOldest.id().equals(cctx.nodeId())) {
                                synchronized (mux) {
                                    if (oldestNode.compareAndSet(oldest, newOldest)) {
                                        // If local node is just joining.
                                        if (exchId.nodeId().equals(cctx.nodeId())) {
                                            try {
                                                top.beforeExchange(exchId);
                                            }
                                            catch (GridException e) {
                                                onDone(e);

                                                return;
                                            }
                                        }

                                        set = true;
                                    }
                                }
                            }
                            else {
                                boolean b;

                                synchronized (mux) {
                                    b = oldestNode.compareAndSet(oldest, newOldest);
                                }

                                if (b && log.isDebugEnabled())
                                    log.debug("Reassigned oldest node [this=" + cctx.localNodeId() +
                                        ", old=" + oldest.id() + ", new=" + newOldest.id() + ']');
                            }

                            if (set) {
                                for (Map.Entry<UUID, GridDhtPartitionsSingleMessage<K, V>> m :
                                    new HashMap<UUID, GridDhtPartitionsSingleMessage<K, V>>(msgs).entrySet()) {
                                    // If received any messages, process them.
                                    onReceive(m.getKey(), m.getValue());
                                }

                                // Reassign oldest node and resend.
                                recheck();
                            }
                        }
                        else if (rmtIds.contains(nodeId)) {
                            if (log.isDebugEnabled())
                                log.debug("Remote node left of failed during partition exchange (will ignore) " +
                                    "[rmtNode=" + nodeId + ", exchangeId=" + exchId + ']');

                            assert rmtNodes != null;

                            for (Iterator<GridRichNode> it = rmtNodes.iterator(); it.hasNext();)
                                if (it.next().id().equals(nodeId))
                                    it.remove();

                            if (allReceived() && ready.get() && replied.compareAndSet(false, true))
                                if (spreadPartitions(top.partitionMap(true)))
                                    onDone();
                        }
                    }
                    finally {
                        leaveBusy();
                    }
                }
            });
        }
        finally {
            leaveBusy();
        }
    }

    /**
     *
     */
    private void recheck() {
        // If this is the oldest node.
        if (oldestNode.get().id().equals(cctx.nodeId())) {
            Collection<UUID> remaining = remaining();

            if (!remaining.isEmpty()) {
                try {
                    cctx.io().safeSend(cctx.discovery().nodes(remaining),
                        new GridDhtPartitionsSingleRequest<K, V>(exchId), null);
                }
                catch (GridException e) {
                    U.error(log, "Failed to request partitions from nodes [exchangeId=" + exchId +
                        ", nodes=" + remaining + ']', e);
                }
            }
            // Resend full partition map because last attempt failed.
            else {
                if (spreadPartitions(top.partitionMap(true)))
                    onDone();
            }
        }
        else
            sendPartitions();

        // Schedule another send.
        scheduleRecheck();
    }

    /**
     *
     */
    private void scheduleRecheck() {
        if (!isDone()) {
            GridTimeoutObject old = timeoutObj;

            if (old != null)
                cctx.time().removeTimeoutObject(old);

            GridTimeoutObject timeoutObj = new GridTimeoutObject() {
                /** */
                private final GridUuid timeoutId = GridUuid.randomUuid();

                /** */
                private final long startTime = System.currentTimeMillis();

                @Override public GridUuid timeoutId() {
                    return timeoutId;
                }

                @Override public long endTime() {
                    long t = cctx.gridConfig().getNetworkTimeout() * cctx.gridConfig().getCacheConfiguration().length;

                    // Account for overflow.
                    long endTime = t < 0 ? Long.MAX_VALUE : startTime + t;

                    // Account for overflow.
                    endTime = endTime < 0 ? Long.MAX_VALUE : endTime;

                    return endTime;
                }

                @Override public void onTimeout() {
                    if (isDone())
                        return;

                    if (!enterBusy())
                        return;

                    try {
                        U.warn(log,
                            "Retrying preload partition exchange due to timeout [done=" + isDone() +
                                ", dummy=" + dummy + ", exchId=" +  exchId + ", rcvdIds=" + F.id8s(rcvdIds) +
                                ", rmtIds=" + F.id8s(rmtIds) + ", init=" + init +  ", initFut=" + initFut.isDone() +
                                ", ready=" + ready + ", replied=" + replied + ", added=" + added +
                                ", oldest=" + U.id8(oldestNode.get().id()) + ", oldestOrder=" +
                                oldestNode.get().order() + ", evtLatch=" + evtLatch.getCount() + ']',
                            "Retrying preload partition exchange due to timeout.");

                        recheck();
                    }
                    finally {
                        leaveBusy();
                    }
                }
            };

            this.timeoutObj = timeoutObj;

            cctx.time().addTimeoutObject(timeoutObj);
        }
    }

    /**
     * @return Remaining node IDs.
     */
    Collection<UUID> remaining() {
        if (rmtIds == null || rcvdIds == null)
            return Collections.emptyList();

        return F.lose(rmtIds, true, rcvdIds);
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridDhtPartitionsExchangeFuture<K, V> fut) {
        return exchId.compareTo(fut.exchId);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        GridDhtPartitionsExchangeFuture fut = (GridDhtPartitionsExchangeFuture)o;

        return exchId.equals(fut.exchId);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return exchId.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPartitionsExchangeFuture.class, this,
            "oldest", oldestNode == null ? "null" : oldestNode.get().id(),
            "oldestOrder", oldestNode == null ? "null" : oldestNode.get().order(),
            "evtLatch", evtLatch == null ? "null" : evtLatch.getCount(),
            "remaining", remaining(),
            "super", super.toString());
    }
}

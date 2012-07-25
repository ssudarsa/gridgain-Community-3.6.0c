// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.discovery;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.processors.jobmetrics.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.segmentation.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.spi.metrics.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridNodeAttributes.*;
import static org.gridgain.grid.segmentation.GridSegmentationPolicy.*;

/**
 * Discovery SPI manager.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridDiscoveryManager extends GridManagerAdapter<GridDiscoverySpi> {
    /** System line separator. */
    private static final String NL = System.getProperty("line.separator");

    /** */
    private static final String PREFIX = "Topology snapshot";

    /** Predicate filtering out daemon nodes. */
    private static final GridPredicate<GridNode> daemonFilter = new P1<GridNode>() {
        @Override public boolean apply(GridNode n) {
            return !isDaemon(n);
        }
    };

    /** Alive filter. */
    private final GridPredicate<GridNode> aliveFilter = new P1<GridNode>() {
        @Override public boolean apply(GridNode n) {
            return node(n.id()) != null;
        }
    };

    /** Discovery event worker. */
    private final DiscoveryWorker discoWrk = new DiscoveryWorker();

    /** Discovery event worker thread. */
    private GridThread discoWrkThread;

    /** Network segment check worker. */
    private GridWorker segChkWrk;

    /** Network segment check thread. */
    private GridThread segChkThread;

    /** Reconnect worker. */
    private ReconnectWorker reconWrk;

    /** Reconnect thread. */
    private GridThread reconThread;

    /** Last logged topology. */
    private final AtomicLong lastLoggedTop = new AtomicLong(0);

    /** Local node. */
    private GridNode locNode;

    /** Local node daemon flag. */
    private boolean isLocDaemon;

    /** Network segment check enabled flag. */
    private boolean segChkEnabled;

    /** Last segment check result. */
    private final AtomicBoolean lastSegChkRes = new AtomicBoolean(true);

    /** Discovery cache. */
    private final AtomicReference<DiscoCache> discoCache = new AtomicReference<DiscoCache>();

    /** Topology version. */
    private final GridAtomicLong topVer = new GridAtomicLong();

    /**
     * @param ctx Context.
     */
    public GridDiscoveryManager(GridKernalContext ctx) {
        super(GridDiscoverySpi.class, ctx, ctx.config().getDiscoverySpi());
    }

    /**
     * Sets local node attributes into discovery SPI.
     *
     * @param attrs Attributes to set.
     */
    public void setNodeAttributes(Map<String, Object> attrs) {
        getSpi().setNodeAttributes(attrs);
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        isLocDaemon = ctx.isDaemon();

        segChkEnabled = !F.isEmpty(ctx.config().getSegmentationResolvers());

        if (segChkEnabled) {
            assert ctx.isEnterprise();

            checkSegmentOnStart();
        }

        getSpi().setMetricsProvider(new GridDiscoveryMetricsProvider() {
            /** */
            private final long startTime = System.currentTimeMillis();

            /** {@inheritDoc} */
            @Override public GridNodeMetrics getMetrics() {
                GridJobMetrics jm = ctx.jobMetric().getJobMetrics();

                GridDiscoveryMetricsAdapter nm = new GridDiscoveryMetricsAdapter();

                nm.setLastUpdateTime(System.currentTimeMillis());

                // Job metrics.
                nm.setMaximumActiveJobs(jm.getMaximumActiveJobs());
                nm.setCurrentActiveJobs(jm.getCurrentActiveJobs());
                nm.setAverageActiveJobs(jm.getAverageActiveJobs());
                nm.setMaximumWaitingJobs(jm.getMaximumWaitingJobs());
                nm.setCurrentWaitingJobs(jm.getCurrentWaitingJobs());
                nm.setAverageWaitingJobs(jm.getAverageWaitingJobs());
                nm.setMaximumRejectedJobs(jm.getMaximumRejectedJobs());
                nm.setCurrentRejectedJobs(jm.getCurrentRejectedJobs());
                nm.setAverageRejectedJobs(jm.getAverageRejectedJobs());
                nm.setMaximumCancelledJobs(jm.getMaximumCancelledJobs());
                nm.setCurrentCancelledJobs(jm.getCurrentCancelledJobs());
                nm.setAverageCancelledJobs(jm.getAverageCancelledJobs());
                nm.setTotalRejectedJobs(jm.getTotalRejectedJobs());
                nm.setTotalCancelledJobs(jm.getTotalCancelledJobs());
                nm.setTotalExecutedJobs(jm.getTotalExecutedJobs());
                nm.setMaximumJobWaitTime(jm.getMaximumJobWaitTime());
                nm.setCurrentJobWaitTime(jm.getCurrentJobWaitTime());
                nm.setAverageJobWaitTime(jm.getAverageJobWaitTime());
                nm.setMaximumJobExecuteTime(jm.getMaximumJobExecuteTime());
                nm.setCurrentJobExecuteTime(jm.getCurrentJobExecuteTime());
                nm.setAverageJobExecuteTime(jm.getAverageJobExecuteTime());
                nm.setCurrentIdleTime(jm.getCurrentIdleTime());
                nm.setTotalIdleTime(jm.getTotalIdleTime());
                nm.setAverageCpuLoad(jm.getAverageCpuLoad());

                GridLocalMetrics lm = ctx.localMetric().metrics();

                // VM metrics.
                nm.setAvailableProcessors(lm.getAvailableProcessors());
                nm.setCurrentCpuLoad(lm.getCurrentCpuLoad());
                nm.setHeapMemoryInitialized(lm.getHeapMemoryInitialized());
                nm.setHeapMemoryUsed(lm.getHeapMemoryUsed());
                nm.setHeapMemoryCommitted(lm.getHeapMemoryCommitted());
                nm.setHeapMemoryMaximum(lm.getHeapMemoryMaximum());
                nm.setNonHeapMemoryInitialized(lm.getNonHeapMemoryInitialized());
                nm.setNonHeapMemoryUsed(lm.getNonHeapMemoryUsed());
                nm.setNonHeapMemoryCommitted(lm.getNonHeapMemoryCommitted());
                nm.setNonHeapMemoryMaximum(lm.getNonHeapMemoryMaximum());
                nm.setUpTime(lm.getUptime());
                nm.setStartTime(lm.getStartTime());
                nm.setNodeStartTime(startTime);
                nm.setCurrentThreadCount(lm.getThreadCount());
                nm.setMaximumThreadCount(lm.getPeakThreadCount());
                nm.setTotalStartedThreadCount(lm.getTotalStartedThreadCount());
                nm.setCurrentDaemonThreadCount(lm.getDaemonThreadCount());
                nm.setFileSystemFreeSpace(lm.getFileSystemFreeSpace());
                nm.setFileSystemTotalSpace(lm.getFileSystemTotalSpace());
                nm.setFileSystemUsableSpace(lm.getFileSystemUsableSpace());

                // Data metrics.
                nm.setLastDataVersion(ctx.cache().lastDataVersion());

                return nm;
            }
        });

        // Start reconnect worker first.
        if (segChkEnabled && ctx.config().getSegmentationPolicy() == RECONNECT) {
            reconWrk = new ReconnectWorker();

            reconThread = new GridThread(reconWrk);

            reconThread.start();
        }

        // Start discovery worker.
        discoWrkThread = new GridThread(ctx.gridName(), "disco-event-worker", discoWrk);

        discoWrkThread.start();

        // Start segment check worker.
        if (segChkEnabled && ctx.config().getSegmentCheckFrequency() > 0) {
            segChkWrk = new SegmentCheckWorker();

            segChkThread = new GridThread(segChkWrk);

            segChkThread.start();
        }

        getSpi().setListener(new GridDiscoverySpiListener() {
            @Override public void onDiscovery(int type, long topVer, GridNode node) {
                if (type != EVT_NODE_METRICS_UPDATED)
                    discoCache.set(new DiscoCache(localNode(), getSpi().getRemoteNodes()));

                if (topVer > 0 && (type == EVT_NODE_JOINED || type == EVT_NODE_FAILED || type == EVT_NODE_LEFT)) {
                    boolean set = GridDiscoveryManager.this.topVer.setIfGreater(topVer);

                    assert set : "Topology version has not been updated [this.topVer=" +
                        GridDiscoveryManager.this.topVer + ", topVer=" + topVer + ", node=" + node +
                        ", evt=" + U.gridEventName(type) + ']';
                }

                discoWrk.addEvent(type, topVer, node);
            }
        });

        startSpi();

        checkAttributes();

        locNode = getSpi().getLocalNode();

        topVer.setIfGreater(locNode.order());

        if (!isLocDaemon)
            ackTopology();

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /**
     * Checks segment on start waiting for correct segment if necessary.
     *
     * @throws GridException If check failed.
     */
    @SuppressWarnings({"BusyWait"})
    private void checkSegmentOnStart() throws GridException {
        assert segChkEnabled;

        if (log.isDebugEnabled())
            log.debug("Starting network segment check.");

        while (true) {
            if (ctx.segmentation().isValidSegment())
                break;

            if (ctx.config().isWaitForSegmentOnStart()) {
                LT.warn(log, null, "Failed to check network segment (retrying every 2000 ms).");

                // Wait and check again.
                try {
                    Thread.sleep(2000);
                }
                catch (InterruptedException ignored) {
                    throw new GridException("Thread has been interrupted.");
                }
            }
            else
                throw new GridException("Failed to check network segment.");
        }

        if (log.isDebugEnabled())
            log.debug("Finished network segment check successfully.");
    }

    /**
     * Checks whether edition and build version of the local node
     * are consistent with remote nodes.
     *
     * @throws GridException If check fails.
     */
    private void checkAttributes() throws GridException {
        GridNode locNode = getSpi().getLocalNode();

        assert locNode != null;

        // Fetch local node attributes once.
        String locEnt = locNode.attribute(ATTR_ENT_EDITION);
        String locBuildVer = locNode.attribute(ATTR_BUILD_VER);
        String locPreferIpV4 = locNode.attribute("java.net.preferIPv4Stack");

        boolean warned = false;

        for (GridNode n : discoCache().remoteNodes()) {
            String rmtEnt = n.attribute(ATTR_ENT_EDITION);
            String rmtBuildVer = n.attribute(ATTR_BUILD_VER);
            String rmtPreferIpV4 = n.attribute("java.net.preferIPv4Stack");

            if (!F.eq(rmtEnt, locEnt))
                throw new GridException("Local node's edition differs from remote node's " +
                    "(all nodes in topology should have identical edition) " +
                    "[locEdition=" + editionName(locEnt) + ", rmtEdition=" + editionName(rmtEnt) +
                    ", locNode=" + locNode + ", rmtNode=" + n + ']');

            if (!F.eq(rmtBuildVer, locBuildVer))
                throw new GridException("Local node's build version differs from remote node's " +
                    "(all nodes in topology should have identical build version) " +
                    "[locBuildVer=" + locBuildVer + ", rmtBuildVer=" + rmtBuildVer +
                    ", locNode=" + locNode + ", rmtNode=" + n + ']');

            if (!F.eq(rmtPreferIpV4, locPreferIpV4)) {
                if (!warned)
                    U.warn(log, "Local node's value of 'java.net.preferIPv4Stack' " +
                        "system property differs from remote node's " +
                        "(all nodes in topology should have identical value) " +
                        "[locPreferIpV4=" + locPreferIpV4 + ", rmtPreferIpV4=" + rmtPreferIpV4 +
                        ", locId8=" + U.id8(locNode.id()) + ", rmtId8=" + U.id8(n.id()) + ']',
                        "Local and remote 'java.net.preferIPv4Stack' system properties do not match.");

                warned = true;
            }
        }

        if (log.isDebugEnabled())
            log.debug("Finished node attributes consistency check.");
    }

    /**
     * Returns edition name.
     *
     * @param ent {@code 'True'} string for enterprise edition.
     * @return Edition name.
     */
    private String editionName(String ent) {
        return "true".equalsIgnoreCase(ent) ? "Enterprise edition" : "Community edition";
    }

    /**
     * Tests whether this node is a daemon node.
     *
     * @param node Node to test.
     * @return {@code True} if given node is daemon.
     */
    private static boolean isDaemon(GridNode node) {
        return "true".equalsIgnoreCase(node.<String>attribute(ATTR_DAEMON));
    }

    /**
     * Logs grid size for license compliance.
     */
    private void ackTopology() {
        assert !isLocDaemon;

        Collection<GridNode> rmtNodes = remoteNodes();

        GridNode locNode = localNode();

        Collection<GridNode> allNodes = allNodes();

        long hash = topologyHash(allNodes);

        // Prevent ack-ing topology for the same topology.
        // Can happen only during node startup.
        if (lastLoggedTop.getAndSet(hash) == hash)
            return;

        int totalCpus = ctx.grid().cpus();

        if (log.isQuiet())
            U.quiet(PREFIX + " [" +
                "ver=" + topologyVersion() +
                ", nodes=" + (rmtNodes.size() + 1) +
                ", CPUs=" + totalCpus +
                ", hash=0x" + Long.toHexString(hash).toUpperCase() +
                ']');
        else if (log.isDebugEnabled()) {
            String dbg = "";

            dbg += NL + NL +
                ">>> +----------------+" + NL +
                ">>> " + PREFIX + "." + NL +
                ">>> +----------------+" + NL +
                ">>> Grid name: " + (ctx.gridName() == null ? "default" : ctx.gridName()) + NL +
                ">>> Number of nodes: " + (rmtNodes.size() + 1) + NL +
                ">>> Topology version: " + topologyVersion() + NL +
                ">>> Topology hash: 0x" + Long.toHexString(hash).toUpperCase() + NL;

            dbg += ">>> Local: " +
                locNode.id().toString().toUpperCase() + ", " +
                getAddresses(locNode) + ", " +
                locNode.attribute("os.name") + ' ' +
                locNode.attribute("os.arch") + ' ' +
                locNode.attribute("os.version") + ", " +
                System.getProperty("user.name") + ", " +
                locNode.attribute("java.runtime.name") + ' ' +
                locNode.attribute("java.runtime.version") + NL;

            for (GridNode node : rmtNodes)
                dbg += ">>> Remote: " +
                    node.id().toString().toUpperCase() +  ", " +
                    getAddresses(node) +  ", " +
                    node.attribute("os.name") + ' ' +
                    node.attribute("os.arch") +  ' ' +
                    node.attribute("os.version") + ", " +
                    node.attribute(ATTR_USER_NAME) + ", " +
                    node.attribute("java.runtime.name") + ' ' +
                    node.attribute("java.runtime.version") + NL;

            dbg += ">>> Total number of CPUs: " + totalCpus + NL;

            log.debug(dbg);
        }
        else if (log.isInfoEnabled())
            log.info(PREFIX + " [" +
                "ver=" + topologyVersion() +
                ", nodes=" + (rmtNodes.size() + 1) +
                ", CPUs=" + totalCpus +
                ", hash=0x" + Long.toHexString(hash).toUpperCase() +
                ']');
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop0(boolean cancel, boolean wait) {
        // Stop receiving notifications.
        getSpi().setListener(null);

        // Stop segment check worker.
        if (segChkWrk != null) {
            segChkWrk.cancel();

            U.join(segChkThread, log);
        }

        // Stop discovery worker.
        discoWrk.cancel();

        U.join(discoWrkThread, log);

        // Stop reconnect worker.
        if (reconWrk != null) {
            reconWrk.cancel();

            U.join(reconThread, log);
        }
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        // Stop SPI itself.
        stopSpi();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * Gets node shadow.
     *
     * @param node Node.
     * @return Node's shadow.
     */
    public GridNodeShadow shadow(GridNode node) {
        return new GridDiscoveryNodeShadowAdapter(node);
    }

    /**
     * @param nodeIds Node IDs to check.
     * @return {@code True} if at least one ID belongs to an alive node.
     */
    public boolean aliveAny(@Nullable Collection<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty())
            return false;

        for (UUID id : nodeIds)
            if (alive(id))
                return true;

        return false;
    }

    /**
     * @param nodeIds Node IDs to check.
     * @return {@code True} if at least one ID belongs to an alive node.
     */
    public boolean aliveAll(@Nullable Collection<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty())
            return false;

        for (UUID id : nodeIds)
            if (!alive(id))
                return false;

        return true;
    }

    /**
     * @param nodeId Node ID.
     * @return {@code True} if node for given ID is alive.
     */
    public boolean alive(UUID nodeId) {
        assert nodeId != null;

        boolean alive = getSpi().getNode(nodeId) != null; // Go directly to SPI without checking disco cache.

        // Refresh disco cache if some node died.
        if (!alive) {
            while (true) {
                DiscoCache c = discoCache();

                if (c.node(nodeId) != null) {
                    if (discoCache.compareAndSet(c, null))
                        break;
                }
                else
                    break;
            }
        }

        return alive;
    }

    /**
     * @param node Node.
     * @return {@code True} if node is alive.
     */
    public boolean alive(GridNode node) {
        assert node != null;

        return alive(node.id());
    }

    /**
     * @param nodeId ID of the node.
     * @return {@code True} if ping succeeded.
     */
    public boolean pingNode(UUID nodeId) {
        assert nodeId != null;

        return getSpi().pingNode(nodeId);
    }

    /**
     * @param nodeId ID of the node.
     * @return Node for ID.
     */
    @Nullable public GridNode node(UUID nodeId) {
        assert nodeId != null;

        return discoCache().node(nodeId);
    }

    /**
     * @param nodeId Node ID.
     * @return Rich node for ID.
     */
    @Nullable public GridRichNode richNode(UUID nodeId) {
        return ctx.rich().rich(node(nodeId));
    }

    /**
     * @param nodes Nodes.
     * @return Alive nodes.
     */
    @SuppressWarnings( {"unchecked"})
    public Collection<GridNode> aliveNodes(Collection<? extends GridNode> nodes) {
        return F.view((Collection<GridNode>) nodes, aliveFilter);
    }

    /**
     * @param p Filters.
     * @return Collection of nodes for given filters.
     */
    public Collection<GridNode> nodes(GridPredicate<GridNode>... p) {
        return F.isEmpty(p) ? allNodes() : F.view(allNodes(), p);
    }

    /**
     * Gets collection of node for given node IDs and predicates.
     *
     * @param ids Ids to include.
     * @param p Filter for IDs.
     * @return Collection with all alive nodes for given IDs.
     */
    public Collection<GridNode> nodes(@Nullable Collection<UUID> ids, GridPredicate<UUID>... p) {
        return F.isEmpty(ids) ? Collections.<GridNode>emptyList() :
            F.view(
                F.viewReadOnly(ids, U.id2Node(ctx), p),
                F.notNull()
            );
    }

    /**
     * Gets collection of rich nodes for given node IDs.
     *
     * @param ids Ids to include.
     * @return Collection with all alive nodes for given IDs.
     */
    public Collection<GridRichNode> richNodes(@Nullable Collection<UUID> ids) {
        return F.isEmpty(ids) ? Collections.<GridRichNode>emptyList() :
            F.view(
                F.viewReadOnly(ids, U.id2RichNode(ctx)),
                F.notNull()
            );
    }

    /**
     * Gets topology hash for given set of nodes.
     *
     * @param nodes Subset of grid nodes for hashing.
     * @return Hash for given topology.
     * @see Grid#topologyHash(Iterable)
     */
    public long topologyHash(Iterable<? extends GridNode> nodes) {
        assert nodes != null;

        Iterator<? extends GridNode> iter = nodes.iterator();

        if (!iter.hasNext())
            return 0; // Special case.

        List<String> uids = new ArrayList<String>();

        for (GridNode node : nodes)
            uids.add(node.id().toString());

        Collections.sort(uids);

        CRC32 hash = new CRC32();

        for (String uuid : uids)
            hash.update(uuid.getBytes());

        return hash.getValue();
    }

    /**
     * @return All node count.
     */
    public int count() {
        return remoteNodes().size() + 1;
    }

    /**
     * Gets discovery collection cache from SPI safely guarding against "floating" collections.
     *
     * @return Discovery collection cache.
     */
    public DiscoCache discoCache() {
        DiscoCache cur;

        while ((cur = discoCache.get()) == null)
            // Wrap the SPI collection to avoid possible floating collection.
            if (discoCache.compareAndSet(null, cur = new DiscoCache(localNode(), getSpi().getRemoteNodes())))
                return cur;

        return cur;
    }

    /**
     * @return All non-daemon remote nodes in topology.
     */
    public Collection<GridNode> remoteNodes() {
        return discoCache().remoteNodes();
    }

    /**
     * @return All non-daemon nodes in topology.
     */
    public Collection<GridNode> allNodes() {
        return discoCache().allNodes();
    }

    /**
     * @return All daemon nodes in topology.
     */
    public Collection<GridNode> daemonNodes() {
        return discoCache().daemonNodes();
    }

    /**
     * @return Local node.
     */
    public GridNode localNode() {
        return locNode == null ? getSpi().getLocalNode() : locNode;
    }

    /**
     * @return Topology version.
     */
    public long topologyVersion() {
        return topVer.get();
    }

    /**
     * @param node Grid node to get addresses for.
     * @return String containing distinct internal and external addresses.
     */
    private String getAddresses(GridNode node) {
        Collection<String> addrs = new HashSet<String>();

        addrs.addAll(node.internalAddresses());
        addrs.addAll(node.externalAddresses());

        return addrs.toString();
    }

    /**
     * Stops local node.
     *
     */
    private void stopNode() {
        new Thread(
            new Runnable() {
                @Override public void run() {
                    ctx.markSegmented();

                    G.stop(ctx.gridName(), true, false);
                }
            }
        ).start();
    }

    /**
     * Restarts JVM.
     */
    private void restartJvm() {
        new Thread(
            new Runnable() {
                @Override public void run() {
                    ctx.markSegmented();

                    G.restart(true, false);
                }
            }
        ).start();
    }

    /**
     * Worker for network segment checks.
     */
    private class SegmentCheckWorker extends GridWorker {
        /**
         *
         */
        private SegmentCheckWorker() {
            super(ctx.gridName(), "disco-net-seg-chk-worker", log);

            assert segChkEnabled;
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"BusyWait"})
        @Override protected void body() throws InterruptedException {
            int timeout = ctx.config().getSegmentCheckFrequency();

            assert timeout > 0;

            while (!isCancelled()) {
                if (lastSegChkRes.get()) {
                    boolean segValid = ctx.segmentation().isValidSegment();

                    if (!segValid) {
                        discoWrk.addEvent(EVT_NODE_SEGMENTED, 0, getSpi().getLocalNode());

                        lastSegChkRes.set(false);
                    }
                }

                Thread.sleep(timeout);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SegmentCheckWorker.class, this);
        }
    }

    /**
     * Worker for network segment checks.
     */
    private class ReconnectWorker extends GridWorker {
        /** */
        private final BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();

        /**
         *
         */
        private ReconnectWorker() {
            super(ctx.gridName(), "disco-recon-worker", log);

            assert segChkEnabled;
            assert ctx.config().getSegmentationPolicy() == RECONNECT;
        }

        /**
         *
         */
        public void scheduleReconnect() {
            queue.add(new Object());
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"BusyWait"})
        @Override protected void body() throws InterruptedException {
            assert segChkEnabled;

            int timeout = ctx.config().getSegmentCheckFrequency();

            assert timeout > 0;

            while (!isCancelled()) {
                queue.take();

                try {
                    checkSegmentOnStart();

                    topVer.set(0);

                    getSpi().reconnect();

                    // Refresh local node.
                    locNode = getSpi().getLocalNode();

                    topVer.setIfGreater(locNode.order());
                }
                catch (GridException e) {
                    U.error(log, "Failed to reconnect discovery SPI to topology (will stop node).", e);

                    stopNode();

                    G.stop(ctx.gridName(), true, false);
                }

            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(ReconnectWorker.class, this);
        }
    }

    /**
     * Worker for discovery events.
     */
    private class DiscoveryWorker extends GridWorker {
        /** Event queue. */
        private final BlockingQueue<GridTuple3<Integer, Long, GridNode>> evts =
            new LinkedBlockingQueue<GridTuple3<Integer, Long, GridNode>>();

        /** Node segmented event fired flag. */
        private boolean nodeSegFired;

        /**
         *
         */
        private DiscoveryWorker() {
            super(ctx.gridName(), "discovery-worker", log);
        }

        /**
         * @param rmtNode Remote node to verify configuration for.
         */
        private void verifyVersion(GridNode rmtNode) {
            assert rmtNode != null;

            GridNode locNode = getSpi().getLocalNode();

            String locVer = locNode.attribute(ATTR_BUILD_VER);
            String rmtVer = rmtNode.attribute(ATTR_BUILD_VER);

            assert locVer != null;
            assert rmtVer != null;

            if (!locVer.equals(rmtVer) && (!locVer.contains("x.x") && !rmtVer.contains("x.x")))
                U.warn(log, "Remote node has inconsistent build version [locVer=" + locVer + ", rmtVer=" +
                    rmtVer + ", rmtNodeId=" + rmtNode.id() + ']');
        }

        /**
         * Method is called when any discovery event occurs.
         *
         * @param type Discovery event type. See {@link GridDiscoveryEvent} for more details.
         * @param topVer Topology version.
         * @param node Remote node this event is connected with.
         */
        private void recordEvent(int type, long topVer, GridNode node) {
            assert node != null;

            if (ctx.event().isRecordable(type)) {
                GridDiscoveryEvent evt = new GridDiscoveryEvent();

                evt.nodeId(ctx.localNodeId());
                evt.eventNodeId(node.id());
                evt.type(type);
                evt.shadow(new GridDiscoveryNodeShadowAdapter(node));
                evt.topologyVersion(topVer);

                if (type == EVT_NODE_METRICS_UPDATED)
                    evt.message("Metrics were updated: " + node);

                else if (type == EVT_NODE_JOINED)
                    evt.message("Node joined: " + node);

                else if (type == EVT_NODE_LEFT)
                    evt.message("Node left: " + node);

                else if (type == EVT_NODE_FAILED)
                    evt.message("Node failed: " + node);

                else if (type == EVT_NODE_SEGMENTED)
                    evt.message("Node segmented: " + node);

                else if (type == EVT_NODE_RECONNECTED)
                    evt.message("Node reconnected: " + node);

                else
                    assert false;

                ctx.event().record(evt);
            }
        }

        /**
         * @param type Event type.
         * @param topVer Topology version.
         * @param node Node.
         */
        void addEvent(int type, long topVer, GridNode node) {
            assert node != null;

            evts.add(F.t(type, topVer, node));
        }

        /**
         *
         * @param node Node to get a short description for.
         * @return Short description for the node to be used in 'quiet' mode.
         */
        private String quietNode(GridNode node) {
            assert node != null;

            return "nodeId8=" + node.id().toString().substring(0, 8) + ", " +
                "addr=" + getAddresses(node) + ", " +
                "CPUs=" + node.metrics().getTotalCpus();
        }

        /**
         * Checks whether network segment is correct.
         * <p>
         * This check is intended to be performed on nodes leaves/failures.
         */
        private void checkSegment() {
            if (!segChkEnabled)
                return;

            if (lastSegChkRes.get()) {
                boolean segValid = ctx.segmentation().isValidSegment();

                if (!segValid) {
                    addEvent(EVT_NODE_SEGMENTED, 0, localNode());

                    lastSegChkRes.set(false);
                }
            }
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"DuplicateCondition"})
        @Override protected void body() throws InterruptedException {
            while (!isCancelled()) {
                GridTuple3<Integer, Long, GridNode> evt = evts.take();

                int type = evt.get1();

                long topVer = evt.get2();

                GridNode node = evt.get3();

                boolean isDaemon = isDaemon(node);

                switch (type) {
                    case EVT_NODE_JOINED: {
                        assert topVer == 0 || topVer == node.order() : "Invalid topology version [topVer=" + topVer +
                            ", node=" + node + ']';

                        if (!isDaemon)
                            if (!isLocDaemon) {
                                if (log.isQuiet())
                                    U.quiet("Node JOINED [" + quietNode(node) + ']');
                                else if (log.isInfoEnabled())
                                    log.info("Added new node to topology: " + node);

                                verifyVersion(node);

                                ackTopology();
                            }
                            else if (log.isDebugEnabled())
                                log.debug("Added new node to topology: " + node);
                        else if (log.isDebugEnabled())
                                log.debug("Added new daemon node to topology: " + node);

                        break;
                    }

                    case EVT_NODE_LEFT: {
                        checkSegment();

                        if (!isDaemon)
                            if (!isLocDaemon) {
                                if (log.isQuiet())
                                    U.quiet("Node LEFT [" + quietNode(node) + ']');
                                else if (log.isInfoEnabled())
                                    log.info("Node left topology: " + node);

                                ackTopology();
                            }
                            else if (log.isDebugEnabled())
                                log.debug("Node left topology: " + node);
                        else if (log.isDebugEnabled())
                                log.debug("Daemon node left topology: " + node);

                        break;
                    }

                    case EVT_NODE_FAILED: {
                        checkSegment();

                        if (!isDaemon)
                            if (!isLocDaemon) {
                                U.warn(log, "Node FAILED: " + node);

                                ackTopology();
                            }
                            else if (log.isDebugEnabled())
                                log.debug("Node FAILED: " + node);
                        else if (log.isDebugEnabled())
                                log.debug("Daemon node FAILED: " + node);

                        break;
                    }

                    case EVT_NODE_SEGMENTED: {
                        assert F.eqNodes(localNode(), node);

                        if (nodeSegFired) {
                            if (log.isDebugEnabled()) {
                                log.debug("Ignored node segmented event [type=EVT_NODE_SEGMENTED, " +
                                    "node=" + node + ']');
                            }

                            continue;
                        }

                        // Ignore all further EVT_NODE_SEGMENTED events
                        // until EVT_NODE_RECONNECTED is fired.
                        nodeSegFired = true;

                        lastLoggedTop.set(0);

                        onSegmentation();

                        if (!isLocDaemon)
                            U.warn(log, "Local node SEGMENTED: " + node);
                        else if (log.isDebugEnabled())
                            log.debug("Local node SEGMENTED: " + node);

                        break;
                    }

                    case EVT_NODE_RECONNECTED: {
                        // Refresh local node.
                        locNode = getSpi().getLocalNode();

                        assert F.eqNodes(locNode, node);

                        // Re-init disco cache.
                        discoCache.set(new DiscoCache(localNode(), getSpi().getRemoteNodes()));

                        // Do not ignore EVT_NODE_SEGMENTED events any more.
                        nodeSegFired = false;

                        // Allow background segment check.
                        lastSegChkRes.set(true);

                        if (!isLocDaemon) {
                            if (log.isQuiet())
                                U.quiet("Local node RECONNECTED [" + quietNode(node) + ']');
                            else if (log.isInfoEnabled())
                                log.info("Local node RECONNECTED: " + node);

                            ackTopology();
                        }
                        else if (log.isDebugEnabled())
                            log.debug("Local node RECONNECTED: " + node);

                        break;
                    }

                    // Don't log metric update to avoid flooding the log.
                    case EVT_NODE_METRICS_UPDATED:
                        break;

                    default:
                        assert false : "Invalid discovery event: " + type;
                }

                recordEvent(type, topVer, node);
            }
        }

        /**
         *
         */
        private void onSegmentation() {
            GridSegmentationPolicy segPlc = ctx.config().getSegmentationPolicy();

            switch (segPlc) {
                case RECONNECT:
                    // Disconnect SPI synchronously to maintain consistent
                    // local topology.
                    try {
                        U.warn(log, "Will try to reconnect discovery SPI to topology " +
                            "(according to configured segmentation policy).");

                        getSpi().disconnect();

                        reconWrk.scheduleReconnect();
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to disconnect discovery SPI (will stop node).", e);

                        // Stop from separate thread only.
                        stopNode();
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to reconnect discovery SPI (will stop node).", e);

                        // Stop from separate thread only.
                        stopNode();
                    }

                    break;

                case RESTART_JVM:
                    U.warn(log, "Restarting JVM according to configured segmentation policy.");

                    restartJvm();

                    break;

                case STOP:
                    U.warn(log, "Stopping local node according to configured segmentation policy.");

                    stopNode();

                    break;

                default:
                    assert segPlc == NOOP : "Unsupported segmentation policy value: " + segPlc;
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(DiscoveryWorker.class, this);
        }
    }

    /**
     * Cache for discovery collections.
     */
    private static class DiscoCache {
        /** Remote nodes. */
        private final List<GridNode> rmtNodes;

        /** All nodes. */
        private final List<GridNode> allNodes;

        /** Daemon nodes. */
        private final List<GridNode> daemonNodes;

        /** Node map. */
        private final Map<UUID, GridNode> nodeMap;

        /** Local node. */
        private final GridNode loc;

        /**
         * @param loc Local node.
         * @param rmts Remote nodes.
         */
        private DiscoCache(GridNode loc, Collection<GridNode> rmts) {
            this.loc = loc;

            rmtNodes = Collections.unmodifiableList(new ArrayList<GridNode>(F.view(rmts, daemonFilter)));

            List<GridNode> all = new ArrayList<GridNode>(rmtNodes.size() + 1);

            if (!isDaemon(loc))
                all.add(loc);

            all.addAll(rmtNodes);

            allNodes = Collections.unmodifiableList(all);

            daemonNodes = Collections.unmodifiableList(new ArrayList<GridNode>(
                F.view(F.concat(false, loc, rmts), F.not(daemonFilter))));

            Map<UUID, GridNode> nodeMap = new HashMap<UUID, GridNode>(allNodes().size());

            for (GridNode n : F.concat(false, allNodes(), daemonNodes()))
                nodeMap.put(n.id(), n);

            this.nodeMap = nodeMap;
        }

        /**
         * @return Local node.
         */
        GridNode localNode() {
            return loc;
        }

        /**
         * @return Remote nodes.
         */
        Collection<GridNode> remoteNodes() {
            return rmtNodes;
        }

        /**
         * @return All nodes.
         */
        Collection<GridNode> allNodes() {
            return allNodes;
        }

        /**
         * @return Daemon nodes.
         */
        Collection<GridNode> daemonNodes() {
            return daemonNodes;
        }

        /**
         * @param id Node ID.
         * @return Node.
         */
        @Nullable GridNode node(UUID id) {
            return nodeMap.get(id);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(DiscoCache.class, this, "allNodesWithDaemons", U.toShortString(allNodes));
        }
    }
}

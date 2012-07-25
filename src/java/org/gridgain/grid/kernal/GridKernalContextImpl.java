// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.controllers.affinity.*;
import org.gridgain.grid.kernal.controllers.license.*;
import org.gridgain.grid.kernal.controllers.rest.*;
import org.gridgain.grid.kernal.controllers.segmentation.*;
import org.gridgain.grid.kernal.managers.checkpoint.*;
import org.gridgain.grid.kernal.managers.collision.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.managers.discovery.*;
import org.gridgain.grid.kernal.managers.eventstorage.*;
import org.gridgain.grid.kernal.managers.failover.*;
import org.gridgain.grid.kernal.managers.loadbalancer.*;
import org.gridgain.grid.kernal.managers.metrics.*;
import org.gridgain.grid.kernal.managers.swapspace.*;
import org.gridgain.grid.kernal.managers.topology.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.closure.*;
import org.gridgain.grid.kernal.processors.email.*;
import org.gridgain.grid.kernal.processors.job.*;
import org.gridgain.grid.kernal.processors.jobmetrics.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.kernal.processors.resource.*;
import org.gridgain.grid.kernal.processors.rich.*;
import org.gridgain.grid.kernal.processors.schedule.*;
import org.gridgain.grid.kernal.processors.session.*;
import org.gridgain.grid.kernal.processors.task.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridSystemProperties.*;
import static org.gridgain.grid.kernal.GridKernalState.*;

/**
 * Implementation of kernal context.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridToStringExclude
public class GridKernalContextImpl extends GridMetadataAwareAdapter implements GridKernalContext, Externalizable {
    /** */
    private static final ThreadLocal<String> stash = new ThreadLocal<String>();

    /*
     * Managers.
     * ========
     */

    /** */
    @GridToStringExclude
    private GridDeploymentManager depMgr;

    /** */
    @GridToStringExclude
    private GridIoManager ioMgr;

    /** */
    @GridToStringExclude
    private GridDiscoveryManager discoMgr;

    /** */
    @GridToStringExclude
    private GridCheckpointManager cpMgr;

    /** */
    @GridToStringExclude
    private GridEventStorageManager evtMgr;

    /** */
    @GridToStringExclude
    private GridFailoverManager failoverMgr;

    /** */
    @GridToStringExclude
    private GridTopologyManager topMgr;

    /** */
    @GridToStringExclude
    private GridCollisionManager colMgr;

    /** */
    @GridToStringExclude
    private GridLoadBalancerManager loadMgr;

    /** */
    @GridToStringExclude
    private GridLocalMetricsManager metricsMgr;

    /** */
    @GridToStringExclude
    private GridSwapSpaceManager swapspaceMgr;

    /*
     * Processors.
     * ==========
     */

    /** */
    @GridToStringInclude
    private GridTaskProcessor taskProc;

    /** */
    @GridToStringInclude
    private GridJobProcessor jobProc;

    /** */
    @GridToStringInclude
    private GridTimeoutProcessor timeProc;

    /** */
    @GridToStringInclude
    private GridResourceProcessor rsrcProc;

    /** */
    @GridToStringInclude
    private GridJobMetricsProcessor metricsProc;

    /** */
    @GridToStringInclude
    private GridClosureProcessor closureProc;

    /** */
    @GridToStringInclude
    private GridCacheProcessor cacheProc;

    /** */
    @GridToStringInclude
    private GridTaskSessionProcessor sesProc;

    /** */
    @GridToStringInclude
    private GridPortProcessor portProc;

    /** */
    @GridToStringInclude
    private GridEmailProcessor emailProc;

    /** */
    @GridToStringInclude
    private GridRichProcessor richProc;

    /** */
    @GridToStringInclude
    private GridScheduleProcessor scheduleProc;

    /*
     * Controllers.
     * ===========
     */

    /** */
    @GridToStringInclude
    private GridRestController restCtrl;

    /** */
    @GridToStringInclude
    private GridLicenseController licCtrl;

    @GridToStringInclude
    private GridAffinityController affCtrl;

    @GridToStringInclude
    private GridSegmentationController segCtrl;

    /** */
    @GridToStringExclude
    private List<GridComponent> comps = new LinkedList<GridComponent>();

    /** */
    private Grid grid;

    /** */
    private GridConfiguration cfg;

    /** */
    private GridKernalGateway gw;

    /** Network segmented flag. */
    private final AtomicBoolean segFlag = new AtomicBoolean();

    /**
     * No-arg constructor is required by externalization.
     */
    public GridKernalContextImpl() {
        // No-op.
    }

    /**
     * Creates new kernal context.
     *
     * @param grid Grid instance managed by kernal.
     * @param cfg Grid configuration.
     * @param gw Kernal gateway.
     */
    protected GridKernalContextImpl(Grid grid, GridConfiguration cfg, GridKernalGateway gw) {
        assert grid != null;
        assert cfg != null;
        assert gw != null;

        this.grid = grid;
        this.cfg = cfg;
        this.gw = gw;
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridComponent> iterator() {
        return comps.iterator();
    }

    /** {@inheritDoc} */
    @Override public List<GridComponent> components() {
        return Collections.unmodifiableList(comps);
    }

    /**
     * @param comp Manager to add.
     */
    public void add(GridComponent comp) {
        assert comp != null;

        /*
         * Managers.
         * ========
         */

        if (comp instanceof GridDeploymentManager)
            depMgr = (GridDeploymentManager)comp;
        else if (comp instanceof GridIoManager)
            ioMgr = (GridIoManager)comp;
        else if (comp instanceof GridDiscoveryManager)
            discoMgr = (GridDiscoveryManager)comp;
        else if (comp instanceof GridCheckpointManager)
            cpMgr = (GridCheckpointManager)comp;
        else if (comp instanceof GridEventStorageManager)
            evtMgr = (GridEventStorageManager)comp;
        else if (comp instanceof GridFailoverManager)
            failoverMgr = (GridFailoverManager)comp;
        else if (comp instanceof GridTopologyManager)
            topMgr = (GridTopologyManager)comp;
        else if (comp instanceof GridCollisionManager)
            colMgr = (GridCollisionManager)comp;
        else if (comp instanceof GridLocalMetricsManager)
            metricsMgr = (GridLocalMetricsManager)comp;
        else if (comp instanceof GridLoadBalancerManager)
            loadMgr = (GridLoadBalancerManager)comp;
        else if (comp instanceof GridSwapSpaceManager)
            swapspaceMgr = (GridSwapSpaceManager)comp;

        /*
         * Processors.
         * ==========
         */

        else if (comp instanceof GridTaskProcessor)
            taskProc = (GridTaskProcessor)comp;
        else if (comp instanceof GridJobProcessor)
            jobProc = (GridJobProcessor)comp;
        else if (comp instanceof GridTimeoutProcessor)
            timeProc = (GridTimeoutProcessor)comp;
        else if (comp instanceof GridResourceProcessor)
            rsrcProc = (GridResourceProcessor)comp;
        else if (comp instanceof GridJobMetricsProcessor)
            metricsProc = (GridJobMetricsProcessor)comp;
        else if (comp instanceof GridCacheProcessor)
            cacheProc = (GridCacheProcessor)comp;
        else if (comp instanceof GridTaskSessionProcessor)
            sesProc = (GridTaskSessionProcessor)comp;
        else if (comp instanceof GridPortProcessor)
            portProc = (GridPortProcessor)comp;
        else if (comp instanceof GridEmailProcessor)
            emailProc = (GridEmailProcessor)comp;
        else if (comp instanceof GridClosureProcessor)
            closureProc = (GridClosureProcessor)comp;
        else if (comp instanceof GridRichProcessor)
            richProc = (GridRichProcessor)comp;
        else if (comp instanceof GridScheduleProcessor)
            scheduleProc = (GridScheduleProcessor)comp;

        /*
         * Controllers.
         * ===========
         */
        else if (comp instanceof GridRestController)
            restCtrl = (GridRestController)comp;
        else if (comp instanceof GridLicenseController)
            licCtrl = (GridLicenseController)comp;
        else if (comp instanceof GridAffinityController)
            affCtrl = (GridAffinityController)comp;
        else if (comp instanceof GridSegmentationController)
            segCtrl = (GridSegmentationController)comp;

        else
            assert false : "Unknown manager class: " + comp.getClass();

        comps.add(comp);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, grid.name());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        stash.set(U.readString(in));
    }

    /** {@inheritDoc} */
    @Override public String version() {
        return grid().version();
    }

    /** {@inheritDoc} */
    @Override public String build() {
        return grid().build();
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            return ((GridKernal)G.grid(stash.get())).context();
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isEnterprise() {
        return U.isEnterprise();
    }

    /** {@inheritDoc} */
    @Override public boolean isStopping() {
        GridKernalState state = gw.getState();

        return state == STOPPING || state == STOPPED;
    }

    /** {@inheritDoc} */
    @Override public UUID localNodeId() {
        return discovery() == null ? cfg.getNodeId() : discovery().localNode().id();
    }

    /** {@inheritDoc} */
    @Override public String gridName() {
        return cfg.getGridName();
    }

    /** {@inheritDoc} */
    @Override public GridKernalGateway gateway() {
        return gw;
    }

    /** {@inheritDoc} */
    @Override public Grid grid() {
        return grid;
    }

    /** {@inheritDoc} */
    @Override public GridRichProcessor rich() {
        return richProc;
    }

    /** {@inheritDoc} */
    @Override public GridConfiguration config() {
        return cfg;
    }

    /** {@inheritDoc} */
    @Override public GridTaskProcessor task() {
        return taskProc;
    }

    /** {@inheritDoc} */
    @Override public GridJobProcessor job() {
        return jobProc;
    }

    /** {@inheritDoc} */
    @Override public GridTimeoutProcessor timeout() {
        return timeProc;
    }

    /** {@inheritDoc} */
    @Override public GridResourceProcessor resource() {
        return rsrcProc;
    }

    /** {@inheritDoc} */
    @Override public GridJobMetricsProcessor jobMetric() {
        return metricsProc;
    }

    /** {@inheritDoc} */
    @Override public GridCacheProcessor cache() {
        return cacheProc;
    }

    /** {@inheritDoc} */
    @Override public GridTaskSessionProcessor session() {
        return sesProc;
    }

    /** {@inheritDoc} */
    @Override public GridClosureProcessor closure() {
        return closureProc;
    }

    /** {@inheritDoc} */
    @Override public GridPortProcessor ports() {
        return portProc;
    }

    /** {@inheritDoc} */
    @Override public GridEmailProcessor email() {
        return emailProc;
    }

    /** {@inheritDoc} */
    @Override public GridScheduleProcessor schedule() {
        return scheduleProc;
    }

    /** {@inheritDoc} */
    @Override public GridDeploymentManager deploy() {
        return depMgr;
    }

    /** {@inheritDoc} */
    @Override public GridIoManager io() {
        return ioMgr;
    }

    /** {@inheritDoc} */
    @Override public GridDiscoveryManager discovery() {
        return discoMgr;
    }

    /** {@inheritDoc} */
    @Override public GridCheckpointManager checkpoint() {
        return cpMgr;
    }

    /** {@inheritDoc} */
    @Override public GridEventStorageManager event() {
        return evtMgr;
    }

    /** {@inheritDoc} */
    @Override public GridFailoverManager failover() {
        return failoverMgr;
    }

    /** {@inheritDoc} */
    @Override public GridTopologyManager topology() {
        return topMgr;
    }

    /** {@inheritDoc} */
    @Override public GridCollisionManager collision() {
        return colMgr;
    }

    /** {@inheritDoc} */
    @Override public GridLocalMetricsManager localMetric() {
        return metricsMgr;
    }

    /** {@inheritDoc} */
    @Override public GridLoadBalancerManager loadBalancing() {
        return loadMgr;
    }

    /** {@inheritDoc} */
    @Override public GridSwapSpaceManager swap() {
        return swapspaceMgr;
    }

    /** {@inheritDoc} */
    @Override public GridLicenseController license() {
        return licCtrl;
    }

    /** {@inheritDoc} */
    @Override public GridAffinityController affinity() {
        return affCtrl;
    }

    /** {@inheritDoc} */
    @Override public GridRestController rest() {
        return restCtrl;
    }

    /** {@inheritDoc} */
    @Override public GridSegmentationController segmentation() {
        return segCtrl;
    }

    /** {@inheritDoc} */
    @Override public GridLogger log() {
        return config().getGridLogger();
    }

    /** {@inheritDoc} */
    @Override public GridLogger log(Class<?> cls) {
        return config().getGridLogger().getLogger(cls);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridKernalContextImpl.class, this);
    }

    /** {@inheritDoc} */
    @Override public void markSegmented() {
        segFlag.set(true);
    }

    /** {@inheritDoc} */
    @Override public boolean segmented() {
        return segFlag.get();
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        X.println(">>> ");
        X.println(">>> Grid memory stats [grid=" + gridName() + ']');

        for (GridComponent comp : comps)
            comp.printMemoryStats();
    }

    /** {@inheritDoc} */
    @Override public boolean isDaemon() {
        return config().isDaemon() || "true".equalsIgnoreCase(System.getProperty(GG_DAEMON));
    }
}

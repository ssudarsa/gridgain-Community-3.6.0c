// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.loadbalancer;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Load balancing manager.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridLoadBalancerManager extends GridManagerAdapter<GridLoadBalancingSpi> {
    /**
     * @param ctx Grid kernal context.
     */
    public GridLoadBalancerManager(GridKernalContext ctx) {
        super(GridLoadBalancingSpi.class, ctx, ctx.config().getLoadBalancingSpi());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        startSpi();

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        stopSpi();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * @param ses Task session.
     * @param top Task topology.
     * @param job Job to balance.
     * @return Next balanced node.
     * @throws GridException If anything failed.
     */
    public GridNode getBalancedNode(GridTaskSessionImpl ses, List<GridNode> top, GridJob job)
        throws GridException {
        assert ses != null;
        assert top != null;
        assert job != null;

        // Check cache affinity routing first.
        GridNode affNode = cacheAffinityNode(ses.deployment(), job, top);

        if (affNode != null) {
            if (log.isDebugEnabled())
                log.debug("Found affinity node for the job [job=" + job + ", affNode=" + affNode.id() + "]");

            return affNode;
        }

        return getSpi(ses.getLoadBalancingSpi()).getBalancedNode(ses, top, job);
    }

    /**
     * @param ses Grid task session.
     * @param top Task topology.
     * @return Load balancer.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public GridLoadBalancer getLoadBalancer(final GridTaskSessionImpl ses, final List<GridNode> top) {
        assert ses != null;

        return new GridLoadBalancerAdapter() {
            @Nullable @Override public GridNode getBalancedNode(GridJob job, @Nullable Collection<GridNode> exclNodes)
                throws GridException {
                A.notNull(job, "job");

                if (F.isEmpty(exclNodes))
                    return GridLoadBalancerManager.this.getBalancedNode(ses, top, job);

                List<GridNode> nodes = F.loseList(top, true, exclNodes);

                if (nodes.isEmpty())
                    return null;

                // Exclude list of nodes from topology.
                return GridLoadBalancerManager.this.getBalancedNode(ses, nodes, job);
            }
        };
    }

    /**
     * @param dep Deployment.
     * @param job Grid job.
     * @param top Topology.
     * @return Cache affinity node or {@code null} if this job is not routed with cache affinity key.
     * @throws GridException If failed to determine whether to use affinity routing.
     */
    @Nullable private GridNode cacheAffinityNode(GridDeployment dep, GridJob job, Collection<GridNode> top)
        throws GridException {
        assert dep != null;
        assert job != null;
        assert top != null;

        if (log.isDebugEnabled())
            log.debug("Looking for cache affinity node [job=" + job + "]");

        Collection<GridRichNode> nodes = F.viewReadOnly(top, ctx.rich().richNode());

        Object key = dep.annotatedValue(job, GridCacheAffinityMapped.class);

        String cacheName = (String)dep.annotatedValue(job, GridCacheName.class);

        if (log.isDebugEnabled())
            log.debug("Affinity properties [key=" + key + ", cacheName=" + cacheName + "]");

        GridNode affNode = null;

        if (key != null) {
            if (!ctx.isEnterprise()) {
                if (U.hasCache(ctx.discovery().localNode(), cacheName)) {
                    GridCacheAffinity<Object> aff = ctx.cache().cache(cacheName).configuration().getAffinity();

                    affNode = CU.primary(aff.nodes(aff.partition(key), nodes));
                }
                else {
                    throw new GridEnterpriseFeatureException("Affinity detection on nodes without cache running is " +
                        " not supported in community edition.");
                }
            }
            else {
                GridNode node;

                try {
                    node = ctx.affinity().mapKeyToNode(cacheName, nodes, key, true);
                }
                catch (GridException e) {
                    throw new GridException("Failed to map affinity key to node for job [gridName=" + ctx.gridName() +
                        ", job=" + job + ']', e);
                }

                if (node == null)
                    throw new GridException("Failed to map key to node (is cache with given name started?) [gridName=" +
                        ctx.gridName() + ", key=" + key + ", cacheName=" + cacheName +
                        ", nodes=" + U.toShortString(nodes) + ']');

                for (GridNode n : top)
                    if (node.id().equals(n.id())) {
                        affNode = node;

                        break;
                    }
            }
        }

        return affNode;
    }
}

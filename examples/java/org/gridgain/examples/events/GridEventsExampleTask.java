// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.events;

import org.gridgain.grid.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;

/**
 * Example task used to generate events for event demonstration example.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridEventsExampleTask extends GridTaskNoReduceAdapter<String> {
    /** Injected load balancer. */
    @GridLoadBalancerResource
    private GridLoadBalancer balancer;

    /** {@inheritDoc} */
    @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, String arg) throws GridException {
        GridJob job = new GridJobAdapterEx() {
            @Nullable
            @Override public Serializable execute() {
                X.println(">>> Executing event example job on this node.");

                // This job does not return any result.
                return null;
            }
        };

        Map<GridJob, GridNode> jobs = new HashMap<GridJob, GridNode>(1);

        // Pick the next best balanced node for the job.
        jobs.put(job, balancer.getBalancedNode(job, null));

        return jobs;
    }
}

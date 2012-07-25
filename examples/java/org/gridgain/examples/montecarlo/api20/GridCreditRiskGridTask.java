// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.montecarlo.api20;

import org.gridgain.examples.montecarlo.*;
import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.spi.loadbalancing.adaptive.*;
import java.util.*;

/**
 * This class represents all the logic necessary for split and reduce grid
 * enabling of credit risk calculation. There are two methods in this class
 * responsible for splitting method invocation into multiple sub-calls and
 * aggregating results back from splits.
 * <p>
 * <h1 class="header">Load Balancing</h1>
 * Please configure different {@link GridLoadBalancingSpi} implementations to use
 * various load balancers. For example, if you would like grid to automatically
 * adapt to the load, use {@link GridAdaptiveLoadBalancingSpi} SPI.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCreditRiskGridTask extends GridTaskSplitAdapter<GridifyArgument, Double> {
    /** {@inheritDoc} */
    @Override protected Collection<? extends GridJob> split(int gridSize, final GridifyArgument arg) {
        // Split number of iterations.
        Integer iterations = ((Integer)arg.getMethodParameters()[2]);

        // Number of iterations should be done by each node.
        int iterPerNode = Math.round(iterations / (float)gridSize);

        // Number of iterations for the last/the only node.
        int lastNodeIter = iterations - (gridSize - 1) * iterPerNode;

        Collection<GridJobAdapterEx> jobs = new ArrayList<GridJobAdapterEx>(gridSize);

        // Note that for the purpose of this example we perform a simple homogeneous
        // (non weighted) split assuming that all computing resources in this split
        // will be identical. In real life scenarios when heterogeneous environment
        // is used a split that is weighted by, for example, CPU benchmarks of each
        // node in the split will be more efficient. It is fairly easy addition and
        // GridGain comes with convenient Spring-compatible benchmark that can be
        // used for weighted splits.
        for (int i = 0; i < gridSize; i++) {
            // Add new job reference to the split.
            jobs.add(new GridJobAdapterEx(i == gridSize - 1 ? lastNodeIter : iterPerNode) {
                /*
                 * Executes grid-enabled method with passed in argument.
                 */
                @Override public Double execute() {
                    Object[] params = arg.getMethodParameters();

                    return new GridCreditRiskManager().calculateCreditRiskMonteCarlo((GridCredit[])params[0],
                        (Integer)params[1], this.<Integer>argument(0), (Double)params[3]);
                }
            });
        }

        // Return jobs to execute remotely.
        return jobs;
    }

    /** {@inheritDoc} */
    @Override public Double reduce(List<GridJobResult> results) {
        double taskRes = 0;

        // Reduce results by summing them.
        for (GridJobResult res : results) {
            taskRes += (Double)res.getData();
        }

        return taskRes / results.size();
    }
}

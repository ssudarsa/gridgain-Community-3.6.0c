// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.closure;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Demonstrates the replacement of functional APIs with traditional task-based
 * execution. This example should be used together with corresponding functional
 * example code to see the difference in coding approach.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridClosureExample2
 */
public class GridTaskExample2 {
    /**
     * Ensures singleton.
     */
    private GridTaskExample2() {
        /* No-op. */
    }

    /**
     * Executes GCD and LCM calculation without closures and functional API.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        // Starts grid.
        if (args.length == 0) {
            G.start();
        }
        else {
            G.start(args[0]);
        }

        try {
            Grid g = G.grid();

            int bound = 100;
            int size = 10;

            // Initialises collection of pair random numbers.
            Collection<int[]> pairs = new ArrayList<int[]>(size);

            // Fills collection.
            for (int i = 0; i < size; i++) {
                pairs.add(new int[] {
                    GridNumberUtilExample.getRand(bound), GridNumberUtilExample.getRand(bound)
                });
            }

            // Executes task.
            g.execute(new GridNumberCalculationTask(), pairs).get();

            // Prints.
            X.println(">>>>> Check all nodes for number and their GCD and LCM output.");
        }
        finally {
            // Stops grid.
            G.stop(true);
        }
    }

    /**
     * This class defines grid task for this example.
     * This particular implementation creates a collection of jobs for each number pair.
     * Each job will calculate and print GCD and LCM for each pair.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class GridNumberCalculationTask extends GridTaskNoReduceSplitAdapter<Collection<int[]>> {
        @Override protected Collection<? extends GridJob> split(int gridSize, Collection<int[]> pairs)
            throws GridException {
            // Creates collection of jobs.
            Collection<GridJob> jobs = new ArrayList<GridJob>(pairs.size());

            // Fills collection with calculation jobs.
            for(final int[] pair : pairs) {
                jobs.add(new GridJobOneWayAdapter() {
                    @Override public void oneWay() {
                        int gcd = GridNumberUtilExample.getGCD(pair[0], pair[1]);
                        int lcm = GridNumberUtilExample.getLCM(pair[0], pair[1]);

                        System.out.printf(">>>>> Numbers: %d and %d. GCD: %d. LCM: %d.%n", pair[0], pair[1], gcd, lcm);
                    }
                });
            }

            return jobs;
        }
    }
}

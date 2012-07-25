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
import org.jetbrains.annotations.Nullable;

import java.math.*;
import java.util.*;

/**
 * Demonstrates the replacement of functional APIs with traditional task-based
 * execution. This example should be used together with corresponding functional
 * example code to see the difference in coding approach.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridClosureExample1
 */
public class GridTaskExample1 {
    /**
     * Ensures singleton.
     */
    private GridTaskExample1() {
        /* No-op. */
    }

    /**
     * Executes factorial calculation example without closures.
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

            // Initialises number for factorial calculation.
            int num = 50;

            // Executes task.
            BigInteger fact = g.execute(new GridFactorialTask(g.localNode()), num).get();

            // Prints result.
            X.println(">>>>>");
            X.println(">>>>> Factorial for number " + num + " is " + fact);
            X.println(">>>>>");
        }
        finally {
            // Stops grid.
            G.stop(true);
        }
    }

    /**
     * This class defines grid task for this example.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class GridFactorialTask extends GridTaskAdapter<Integer, BigInteger> {
        /** Execution node. */
        private GridNode node;

        /**
         * Creates task for factorial calculation.
         *
         * @param node Execution node.
         */
        GridFactorialTask(GridNode node) {
            this.node = node;
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> gridNodes, final Integer num)
            throws GridException {
            assert num != null;

            return Collections.singletonMap(new GridJobAdapterEx() {
                @Override public Object execute() {
                    return GridNumberUtilExample.factorial(num);
                }
            }, node);
        }

        /** {@inheritDoc} */
        @Nullable
        @Override public BigInteger reduce(List<GridJobResult> results) throws GridException {
            return results.isEmpty() ? null : results.get(0).<BigInteger>getData();
        }
    }
}

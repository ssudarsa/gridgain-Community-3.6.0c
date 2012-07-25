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
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Demonstrates the replacement of functional APIs with traditional task-based
 * execution. This example should be used together with corresponding functional
 * example code to see the difference in coding approach.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridClosureExample3
 */
public class GridTaskExample3 {
    /**
     * Ensures singleton.
     */
    private GridTaskExample3() {
        /* No-op. */
    }

    /**
     * Executes broadcasting message example without using closures and functional API.
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

            // Executes task.
            g.execute(new GridMessageBroadcastTask(g.nodes()), ">>>>>\n>>>>> Hello Node! :)\n>>>>>").get();

            // Prints.
            X.println(">>>>> Check all nodes for numbers output.");
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
    private static class GridMessageBroadcastTask extends GridTaskNoReduceAdapter<String> {
        /** Execution nodes. */
        private Iterable<GridRichNode> nodes;

        /**
         * Creates task for message broadcast.
         *
         * @param nodes Nodes for execution.
         */
        GridMessageBroadcastTask(Iterable<GridRichNode> nodes) {
            this.nodes = nodes;
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> gridNodes,
            @Nullable final String msg) throws GridException {
            // For each node create a job for message output.
            Map<GridJob, GridNode> map = new HashMap<GridJob, GridNode>();

            for (GridNode node : nodes) {
                if (gridNodes.contains(node)) {
                    map.put(new GridJobOneWayAdapter() {
                        @Override public void oneWay() {
                            X.println(msg);
                        }
                    }, node);
                }
            }

            return map;
        }
    }
}

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
 * @see GridClosureExample4
 */
public class GridTaskExample4 {
    /**
     * Ensures singleton.
     */
    private GridTaskExample4() {
        /* No-op. */
    }

    /**
     * Executes information gathering example without using closures and functional API.
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
            String res = g.execute(new GridNodeInformationGatheringTask(g.nodes()), null).get();

            // Prints result.
            X.println("Nodes system information:");
            X.println(res);
        }
        finally {
            // Stops grid.
            G.stop(true);
        }
    }

    /**
     * This class defines grid task for this example.
     * This particular implementation create job for each checked
     * node for information gathering about it.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class GridNodeInformationGatheringTask extends GridTaskAdapter<Void, String> {
        /** Execution nodes. */
        private Iterable<GridRichNode> nodes;

        /**
         * Creates task for gathering information.
         *
         * @param nodes Nodes for execution.
         */
        GridNodeInformationGatheringTask(Iterable<GridRichNode> nodes) {
            this.nodes = nodes; 
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> gridNodes,
            @Nullable Void msg) throws GridException {
            // For each checked node create a job for gathering system information.
            Map<GridJob, GridNode> map = new HashMap<GridJob, GridNode>();

            for (GridNode node : nodes) {
                if (gridNodes.contains(node)) {
                    map.put(new GridJobAdapterEx() {
                        @Override public Object execute() {
                            StringBuilder buf = new StringBuilder();

                            buf.append("OS: ").append(System.getProperty("os.name"))
                                .append(" ").append(System.getProperty("os.version"))
                                .append(" ").append(System.getProperty("os.arch"))
                                .append("\nUser: ").append(System.getProperty("user.name"))
                                .append("\nJRE: ").append(System.getProperty("java.runtime.name"))
                                .append(" ").append(System.getProperty("java.runtime.version"));

                            return buf.toString();
                        }
                    }, node);
                }
            }

            return map;
        }

        /** {@inheritDoc} */
        @Nullable
        @Override public String reduce(List<GridJobResult> results) throws GridException {
            // Reduce results to one string.
            String res = null;

            if (!results.isEmpty()) {
                StringBuilder buf = new StringBuilder();

                for (GridJobResult result : results) {
                    buf.append("\n").append(result.<String>getData()).append("\n");
                }

                res = buf.toString();
            }

            return res;
        }
    }
}

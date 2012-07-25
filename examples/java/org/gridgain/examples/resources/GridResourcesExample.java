// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.resources;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

/**
 * This example shows how to use injected user resources inside grid jobs.
 * Jobs use context for insertion data in storage. Only one context object
 * will be created and injected in all split jobs.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridResourcesExample {
    /**
     * Ensure singleton.
     */
    private GridResourcesExample() {
        // No-op.
    }

    /**
     * Execute {@code Resources} example on the grid.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws Exception If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CIX1 -> GridInClosureX
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        G.in(args.length == 0 ? null : args[0], new CIX1<Grid>() {
            @Override public void applyx(Grid g) throws GridException {
                // Execute task.
                GridTaskFuture<Integer> fut = g.execute(GridResourcesTask.class,
                    "Grid Computing Made Simple with GridGain");

                // Wait for task completion.
                int phraseLen = fut.get();

                X.println(">>>");
                X.println(">>> Finished executing Grid \"Grid Computing Made Simple with GridGain\" " +
                    "example with custom task.");
                X.println(">>> Total number of characters in the phrase is '" + phraseLen + "'.");
                X.println(">>> You should see print out of words from phrase" +
                    " \"Grid Computing Made Simple with GridGain\" on different nodes.");
                X.println(">>> Check all nodes for output (this node is also part of the grid).");
                X.println(">>>");
            }
        });
    }
}

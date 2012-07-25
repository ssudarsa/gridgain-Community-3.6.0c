// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.metrics;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

/**
 * Example demonstrates how to use node metrics data. In this example we start up
 * one or more remote nodes and then run benchmark on nodes with more than one
 * processor and idle time percentage of node idle time more 50%.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridMetricsExample {
    /**
     * Ensure singleton.
     */
    private GridMetricsExample() {
        // No-op.
    }

    /**
     * Executes example on the grid.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
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
                g.execute(GridMetricsTask.class, null).get();

                X.println(">>>");
                X.println(">>> Finished execution of GridMetricsExample.");
                X.println(">>> Check log output for every node.");
                X.println(">>>");
            }
        });
    }
}

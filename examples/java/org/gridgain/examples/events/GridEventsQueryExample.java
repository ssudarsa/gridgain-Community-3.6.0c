// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.events;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

/**
 * Example of querying events. In this example we execute a dummy
 * task to generate events, and then query them.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridEventsQueryExample {
    /**
     * Ensure singleton.
     */
    private GridEventsQueryExample() {
        // No-op.
    }

    /**
     * Executes an example task on the grid in order to generate events and then lists
     * all events that have occurred from task execution.
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
                // Executes example task to generate events.
                g.execute(GridEventsExampleTask.class, null).get();

                // Retrieve and filter nodes events.
                for (GridEvent evt : g.remoteEvents(F.<GridEvent>alwaysTrue(), 0)) {
                    X.println(">>> Found grid event: " + evt);
                }

                X.println(">>>");
                X.println(">>> Finished executing Grid Events Query Example.");
                X.println(">>> Check local node output.");
                X.println(">>>");
            }
        });
    }
}

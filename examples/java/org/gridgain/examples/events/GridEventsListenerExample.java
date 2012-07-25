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
import static org.gridgain.grid.GridEventType.*;

/**
 * Example for listening to local grid events. In this example we register
 * a local grid event listener and then demonstrate how it gets notified
 * of all events that occur from a dummy task execution.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridEventsListenerExample {
    /**
     * Ensure singleton.
     */
    private GridEventsListenerExample() {
        // No-op.
    }

    /**
     * Registers a listener to local grid events and prints all events that
     * occurred from a sample task execution.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        if (args.length == 0) {
            G.start();
        }
        else {
            G.start(args[0]);
        }

        try {
            Grid grid = G.grid();

            // Define event listener
            GridLocalEventListener lsnr = new GridLocalEventListener() {
                @Override public void onEvent(GridEvent evt) {
                    X.println(">>> Grid event occurred: " + evt);
                }
            };

            // Register event listener for all task execution local events.
            grid.addLocalEventListener(lsnr, EVTS_TASK_EXECUTION);

            // Executes example task to generate events.
            grid.execute(GridEventsExampleTask.class, null).get();

            // Remove local event listener.
            grid.removeLocalEventListener(lsnr);

            X.println(">>>");
            X.println(">>> Finished executing Grid Event Listener Example.");
            X.println(">>> Check local node output.");
            X.println(">>>");
        }
        finally {
            G.stop(true);
        }
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.schedule;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

import static org.gridgain.grid.GridClosureCallMode.*;

/**
 * Demonstrates a cron-based {@link Runnable} execution scheduling.
 * Test runnable object broadcasts a phrase to all grid nodes every minute
 * ten times with initial scheduling delay equal to five seconds.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridScheduleRunnableExample {
    /**
     * Ensures singleton.
     */
    private GridScheduleRunnableExample() {
        /* No-op. */
    }

    /**
     * Executes scheduling example.
     *
     * @param args Command line arguments, none required but if provided
     *             first one should point to the Spring XML configuration file. See
     *             {@code "examples/config/"} for configuration file examples.
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
            final Grid g = G.grid();

            // Schedule output message every minute.
            g.scheduleLocal(
                new CA() {
                    @Override public void apply() {
                        try {
                            g.run(BROADCAST, F.<String>println().curry("Howdy! :)"));
                        }
                        catch (GridException e) {
                            throw F.wrap(e);
                        }
                    }
                },
                "{5, 10} * * * * *" // Cron expression.
            );

            // Sleep.
            Thread.sleep(1000 * 60 * 2);

            // Prints.
            X.println(">>>>> Check all nodes for hello message output.");
        }
        finally {
            // Stops grid.
            G.stop(true);
        }
    }
}

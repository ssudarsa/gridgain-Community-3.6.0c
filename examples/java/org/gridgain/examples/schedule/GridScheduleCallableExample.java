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

import java.util.*;
import java.util.concurrent.*;

/**
 * Demonstrates a cron-based {@link Callable} execution scheduling.
 * The example schedules task that returns result. To trace the execution result it uses method
 * {@link GridScheduleFuture#get()} blocking current thread and waiting for result of the next execution.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridScheduleCallableExample {
    /**
     * Ensures singleton.
     */
    private GridScheduleCallableExample() {
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
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CIX1 -> GridInClosureX
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        G.in(args.length == 0 ? null : args[0], new CIX1<Grid>() {
            @Override public void applyx(Grid g) throws GridException {
                // Schedule callable that returns incremented value each time.
                GridScheduleFuture<Integer> fut = g.scheduleLocal(
                    new CO<Integer>() {
                        private int cnt;

                        @Override public Integer apply() { return ++cnt; }
                    },
                    "{1, 3} * * * * *" // Cron expression.
                );

                X.println(">>> Started scheduling callable execution at " + new Date() + ". " +
                    "Wait for 3 minutes and check the output.");

                X.println(">>> First execution result: " + fut.get() + ", time: " + new Date());
                X.println(">>> Second execution result: " + fut.get() + ", time: " + new Date());
                X.println(">>> Third execution result: " + fut.get() + ", time: " + new Date());

                X.println(">>> Execution scheduling stopped after 3 executions.");

                // Prints.
                X.println(">>> Check local node for output.");
            }
        });
    }
}

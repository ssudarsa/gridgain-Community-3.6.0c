// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.examples

import java.util.concurrent.*
import org.gridgain.grid.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Demonstrates a cron-based {@link Callable} execution scheduling.
 * The example schedules task that returns result. To trace the execution result it uses method
 * {@link GridScheduleFuture#get()} blocking current thread and waiting for result of the next execution.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
@Use(GroverGridCategory)
class GroverScheduleCallableExample {
    /** Ensures singleton. */
    private GridScheduleCallableExample() {
        /* No-op. */
    }

    /**
     * @param args Command line arguments.
     */
    static void main(String[] args) {
        grover { Grid g ->
            def cnt = new AtomicInteger()

            // Schedule callable that returns incremented value each time.
            def fut = g.scheduleLocalCall(
                { -> cnt.incrementAndGet() },
                "{1, 3} * * * * *" // Cron expression.
            )

            println(">>> Started scheduling callable execution at " + new Date() + ". " +
                "Wait for 3 minutes and check the output.")

            println(">>> First execution result: " + fut.get() + ", time: " + new Date())
            println(">>> Second execution result: " + fut.get() + ", time: " + new Date())
            println(">>> Third execution result: " + fut.get() + ", time: " + new Date())

            println(">>> Execution scheduling stopped after 3 executions.")

            // Prints.
            println(">>> Check local node for output.")
        }
    }
}

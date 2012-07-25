// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */

package org.gridgain.scalar.examples

import org.gridgain.scalar.scalar
import scalar._
import org.gridgain.grid.Grid
import java.util.Date

/**
 * Demonstrates a cron-based `Callable` execution scheduling.
 * The example schedules task that returns result. To trace the execution result it uses method
 * `GridScheduleFuture.get()` blocking current thread and waiting for result of the next execution.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarScheduleCallableExample {
    /**
     * Example entry point. No arguments required.
     */
    def main(args: Array[String]) {
        scalar { g: Grid =>
            var cnt = 0

            // Schedule callable that returns incremented value each time.
            val fut = g.scheduleLocalCall(
                () => {
                    cnt += 1

                    cnt
                },
                "{1, 3} * * * * *" // Cron expression.
            )

            println(">>> Started scheduling callable execution at " + new Date + ". " +
                "Wait for 3 minutes and check the output.")

            println(">>> First execution result: " + fut.get + ", time: " + new Date)
            println(">>> Second execution result: " + fut.get + ", time: " + new Date)
            println(">>> Third execution result: " + fut.get + ", time: " + new Date)

            println(">>> Execution scheduling stopped after 3 executions.")

            println(">>> Check local node for output.")
        }
    }
}

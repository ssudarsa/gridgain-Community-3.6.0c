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

import org.gridgain.grid.*
import static org.gridgain.grid.GridClosureCallMode.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*

/**
 * Demonstrates a cron-based {@link Runnable} execution scheduling.
 * Test runnable object broadcasts a phrase to all grid nodes every minute
 * ten times with initial scheduling delay equal to five seconds.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
@Use(GroverGridCategory)
class GroverScheduleRunnableExample {
    /** Ensures singleton. */
    private GridScheduleRunnableExample() {
        /* No-op. */
    }

    /**
     * @param args Command line arguments.
     */
    static void main(String[] args) {
        grover { Grid g ->
            // Schedule output message every minute.
            g.scheduleLocalRun(
                { -> g.run(BROADCAST) { -> println("Howdy! :)") } },
                "{5, 10} * * * * *" // Cron expression.
            )

            // Sleep.
            Thread.sleep(1000 * 60 * 2)

            // Prints.
            println(">>>>> Check all nodes for hello message output.")
        }
    }
}

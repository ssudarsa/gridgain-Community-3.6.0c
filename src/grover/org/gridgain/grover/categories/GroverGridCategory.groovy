// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.categories

import java.util.concurrent.*
import org.gridgain.grid.*
import org.jetbrains.annotations.*

/**
 * Extensions for {@link Grid}.
 * <p>
 * To access methods from the category target class has
 * to be annotated: {@code @Use(GroverGridCategory)}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
class GroverGridCategory {
    /**
     * Schedules closure for execution using local <b>cron-based</b> scheduling.
     *
     * @param c Closure to schedule to run as a background cron-based job.
     *      If {@code null} - this method is no-op.
     * @param pattern Scheduling pattern in UNIX cron format with optional prefix <tt>{n1, n2}</tt>
     *      where {@code n1} is delay of scheduling in seconds and {@code n2} is the number of execution. Both
     *      parameters are optional.
     * @return Scheduled execution future.
     * @throws GridException Thrown in case of any errors.
     */
    static GridScheduleFuture<?> scheduleLocalRun(Grid g, @Nullable Runnable r, String pattern) {
        assert g != null
        assert pattern != null

        g.scheduleLocal(r, pattern)
    }

    /**
     * Schedules closure for execution using local <b>cron-based</b> scheduling.
     *
     * @param c Closure to schedule to run as a background cron-based job.
     *       If {@code null} - this method is no-op.
     * @param pattern Scheduling pattern in UNIX cron format with optional prefix <tt>{n1, n2}</tt>
     *      where {@code n1} is delay of scheduling in seconds and {@code n2} is the number of execution. Both
     *      parameters are optional.
     * @return Scheduled execution future.
     * @throws GridException Thrown in case of any errors.
     */
    static <R> GridScheduleFuture<R> scheduleLocalCall(Grid g, @Nullable Callable c, String pattern) {
        assert g != null
        assert pattern != null

        g.scheduleLocal(c, pattern)
    }
}

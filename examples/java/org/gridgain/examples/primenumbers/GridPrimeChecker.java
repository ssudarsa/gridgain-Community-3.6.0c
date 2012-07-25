// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.primenumbers;

import org.gridgain.examples.primenumbers.api20.*;
import org.gridgain.examples.primenumbers.gridify.*;
import org.gridgain.grid.gridify.*;
import org.jetbrains.annotations.Nullable;

/**
 * Simple prime checkers. The implementation of this class iterates
 * through the passed in list of divisors and checks if the value
 * is divisible by any of these divisors.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridPrimeChecker {
    /**
     * Enforces singleton.
     */
    private GridPrimeChecker() {
        // No-op.
    }

    /**
     * This method is used by both, {@link GridPrimeExample} and {@link GridifyPrimeExample},
     * examples. Whenever invoked from {@link GridifyPrimeExample}, this method will
     * be executed on the grid by the virtue of {@link Gridify @Gridify} annotation
     * attached to it. Note that we specify in the annotation that {@link GridifyPrimeTask}
     * should be used for grid-enabled execution.
     *
     * @param val Value to check for prime.
     * @param minRage Lower boundary of divisors range.
     * @param maxRange Upper boundary of divisors range.
     * @return First divisor found or {@code null} if no divisor was found.
     */
    @Nullable
    @Gridify(taskClass = GridifyPrimeTask.class)
    public static Long checkPrime(long val, long minRage, long maxRange) {
        // Loop through all divisors in the range and check if the value passed
        // in is divisible by any of these divisors.
        // Note that we also check for thread interruption which may happen
        // if the job was cancelled from the grid task.
        for (long divisor = minRage; divisor <= maxRange && !Thread.currentThread().isInterrupted(); divisor++) {
            if (divisor != 1 && divisor != val && val % divisor == 0) {
                return divisor;
            }
        }

        return null;
    }
}

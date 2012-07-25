// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.gridify;

import org.jetbrains.annotations.Nullable;

/**
 * Simple prime checkers. The implementation of this class iterates
 * through the passed in list of divisors and checks if the value
 * is divisible by any of these divisors.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridSimplePrimeChecker {
    /**
     * Enforces singleton.
     */
    private GridSimplePrimeChecker() {
        // No-op.
    }

    /**
     * Method to check value for prime.
     * Returns first divisor found or {@code null} if no divisor was found.
     *
     * @param val Value to check for prime.
     * @param minRage Lower boundary of divisors range.
     * @param maxRange Upper boundary of divisors range.
     * @return First divisor found or {@code null} if no divisor was found.
     */
    @Nullable
    public static Long checkPrime(long val, long minRage, long maxRange) {
        // Loop through all divisors in the range and check if the value passed
        // in is divisible by any of these divisors.
        // Note that we also check for thread interruption which may happen
        // if the job was cancelled from the grid task.
        for (long divisor = minRage; divisor <= maxRange && !Thread.currentThread().isInterrupted();
            divisor++) {
            if (divisor != 1 && divisor != val && val % divisor == 0) {
                return divisor;
            }
        }

        return null;
    }
}

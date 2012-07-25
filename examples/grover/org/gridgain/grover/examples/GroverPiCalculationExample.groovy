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
 * This example calculates Pi number in parallel on the grid.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
@Use(GroverProjectionCategory)
class GroverPiCalculationExample {
    /** Number of calculations per node. */
    private static int N = 10000

    /**
     * Example entry point. No arguments required.
     */
    static void main(String[] args) {
        grover { Grid g ->
            println("Pi estimate: " +
                g.reduce$(
                    SPREAD,
                    (0 ..< g.size()).collect { { -> calcPi(it * N) } },
                    { it.sum() }
                )
            )
        }
    }

    /**
     * Calculates Pi range starting with given number.
     *
     * @param start Start of the `{start, start + N}` range.
     * @return Range calculation.
     */
    private static double calcPi(int start) {
        (start ..< (start + N)).inject(0) { double sum, int i ->
            sum + (4.0 * (1 - (i % 2) * 2) / (2 * i + 1))
        }
    }
}

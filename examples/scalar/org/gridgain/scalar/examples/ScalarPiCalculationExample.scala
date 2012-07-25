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

import org.gridgain.scalar._
import scalar._
import org.gridgain.grid.GridClosureCallMode._
import org.gridgain.grid.Grid

/**
  * This example calculates Pi number in parallel on the grid.
  *
  * @author 2012 Copyright (C) GridGain Systems
  * @version 3.6.0c.13012012
  */
object ScalarPiCalculationExample {
    /** Number of calculations per node. */
    private val N = 10000

    /**
      * Starts examples and calculates Pi number on the grid.
      *
      * @param args Command line arguments - none required.
      */
    def main(args: Array[String]) = scalar { g: Grid =>
        println("Pi estimate: " +
            g.@<[Double, Double](SPREAD, for (i <- 0 until g.size()) yield () => calcPi(i * N), _.sum)

            // Just another way w/o for-expression.
            // Note that map's parameter type inference doesn't work in 2.9.0.
            // g.@<[Double, Double](SPREAD, 0 until g.size() map ((i: Int) => () => calcPi(i * N)), _.sum)
        )
    }

    /**
      * Calculates Pi range starting with given number.
      *
      * @param start Start the of the `{start, start + N}` range.
      * @return Range calculation.
      */
    def calcPi(start: Int): Double =
        (start until (start + N)) map (i => 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)) sum
}

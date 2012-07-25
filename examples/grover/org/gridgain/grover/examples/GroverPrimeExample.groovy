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
import org.gridgain.grid.lang.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*

/**
 * Prime Number calculation example based on GridGain 3.0 API.
 * <p>
 * <h1 class="header">Starting Remote Nodes</h1>
 * To try this example you should (but don't have to) start remote grid instances.
 * You can start as many as you like by executing the following script:
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh}</pre>
 * Once remote instances are started, you can execute this example from
 * Eclipse, IntelliJ IDEA, or NetBeans (and any other Java IDE) by simply hitting run
 * button. You will see that all nodes discover each other and
 * all of the nodes will participate in task execution (check node
 * output).
 * <p>
 * Note that when running this example on a multi-core box, simply
 * starting additional grid node on the same box will speed up
 * prime number calculation by a factor of 2.
 * <p>
 * <h1 class="header">XML Configuration</h1>
 * If no specific configuration is provided, GridGain will start with
 * all defaults. For information about GridGain default configuration
 * refer to {@link org.gridgain.grid.GridFactory} documentation. If you would like to
 * try out different configurations you should pass a path to Spring
 * configuration file as 1st command line argument into this example.
 * The path can be relative to {@code GRIDGAIN_HOME} environment variable.
 * You should also pass the same configuration file to all other
 * grid nodes by executing startup script as follows (you will need
 * to change the actual file name):
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh} examples/config/specific-config-file.xml</pre>
 * <p>
 * GridGain examples come with multiple configuration files you can try.
 * All configuration files are located under {@code GRIDGAIN_HOME/examples/config}
 * folder.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
@Use(GroverProjectionCategory)
class GroverPrimeExample {
    /** Enforces singleton. */
    private GridPrimeExample() {
        // No-op.
    }

    /**
     * @param args Command line arguments.
     * @throws org.gridgain.grid.GridException If example execution failed.
     */
    static void main(String[] args) throws GridException {
        grover { Grid g ->
            // Values we want to check for prime.
            def checkVals = [32452841L, 32452843L, 32452847L, 32452849L, 236887699L, 217645199L]

            println(">>>")
            println(">>> Starting to check the following numbers for primes: " + checkVals)

            def start = System.currentTimeMillis()

            checkVals.each {
                def divisor = g.reduce$(SPREAD, closures(g.size(), it)) { c-> c.find { it != null} }

                if (divisor == null)
                    println(">>> Value '" + it + "' is a prime number")
                else
                    println(">>> Value '" + it + "' is divisible by '" + divisor + '\'')
            }

            long totalTime = System.currentTimeMillis() - start

            println(">>> Total time to calculate all primes (milliseconds): " + totalTime)
            println(">>>")
        }
    }

    /**
     * Creates closures for checking passed in value for prime.
     * <p>
     * Every closure gets a range of divisors to check. The lower and
     * upper boundaries of this range are passed into closure.
     * Closures checks if the value passed in is divisible by any of
     * the divisors in the range.
     *
     * @param gridSize Size of the grid.
     * @param checkVal Value to check.
     * @return Collection of closures.
     */
    private static Collection<GridOutClosure<Long>> closures(int gridSize, long checkVal) {
        def cls = new ArrayList<GridOutClosure<Long>>(gridSize)

        long taskMinRange = 2

        long numbersPerTask = checkVal / gridSize < 10 ? 10 : checkVal / gridSize

        long jobMinRange
        long jobMaxRange = 0

        // In this loop we create as many grid jobs as
        // there are nodes in the grid.
        for (def i = 0; jobMaxRange < checkVal; i++) {
            jobMinRange = i * numbersPerTask + taskMinRange
            jobMaxRange = (i + 1) * numbersPerTask + taskMinRange - 1

            if (jobMaxRange > checkVal)
                jobMaxRange = checkVal

            GridOutClosure<Long> c = { ->
                (jobMinRange .. jobMaxRange).find { long v ->
                    v != 1 && v != checkVal && checkVal % v == 0
                }
            }

            cls.add(c)
        }

        cls
    }
}

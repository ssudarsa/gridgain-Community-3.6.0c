// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.primenumbers.api20;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Main bootstrap class for API-based Prime Number calculation on the grid. This
 * class starts up GridGain Grid and executes {@link GridPrimeTask} for
 * every number we want to check for prime.
 * <p>
 * Refer to {@link GridPrimeTask} for documentation on how prime numbers
 * are calculated.
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
 * refer to {@link GridFactory} documentation. If you would like to
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
public final class GridPrimeExample {
    /**
     * Enforces singleton.
     */
    private GridPrimeExample() {
        // No-op.
    }

    /**
     * Starts up grid and checks all provided values for prime.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        // Starts grid.
        Grid grid = args.length == 0 ? G.start() : G.start(args[0]);

        // Values we want to check for prime.
        long[] checkVals = {
            32452841, 32452843, 32452847, 32452849, 236887699, 217645199
        };

        X.println(">>>");
        X.println(">>> Starting to check the following numbers for primes: " + Arrays.toString(checkVals));

        try {
            long start = System.currentTimeMillis();

            for (long checkVal : checkVals) {
                // Execute grid task to check a specific value for prime.
                // Find any divisor. If the divisor is null,
                // then the number is prime.
                Long divisor = grid.execute(GridPrimeTask.class, checkVal).get();

                if (divisor == null) {
                    X.println(">>> Value '" + checkVal + "' is a prime number");
                }
                else {
                    X.println(">>> Value '" + checkVal + "' is divisible by '" + divisor + '\'');
                }
            }

            long totalTime = System.currentTimeMillis() - start;

            X.println(">>> Total time to calculate all primes (milliseconds): " + totalTime);
            X.println(">>>");
        }
        finally {
            // Stops grid.
            G.stop(grid.name(), true);
        }
    }
}

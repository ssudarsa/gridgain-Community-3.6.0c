// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.functional;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

import static org.gridgain.grid.GridClosureCallMode.*;

/**
 * Demonstrates various functional APIs from {@link org.gridgain.grid.lang.GridFunc} class.
 * <p>
 * <h1 class="header">Starting Remote Nodes</h1>
 * To try this example you should (but don't have to) start remote grid instances.
 * You can start as many as you like by executing the following script:
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh}</pre>
 * Once remote instances are started, you can execute this example from
 * Eclipse, IDEA, or NetBeans (or any other IDE) by simply hitting run
 * button. You will see that all nodes discover each other and
 * some of the nodes will participate in task execution (check node
 * output).
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
public class GridFunctionClosureJobsExample {
    /** Random number generator. */
    private static final Random RAND = new Random();

    /**
     * Ensures singleton.
     */
    private GridFunctionClosureJobsExample() {
        /* No-op. */
    }

    /**
     * Executes example on the grid. Finds minimal divider for a set of numbers.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CI1 -> GridInClosure
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        // Starts grid.
        if (args.length == 0) {
            G.start();
        }
        else {
            G.start(args[0]);
        }

        try {
            int size = 20;

            Collection<Integer> nums = new ArrayList<Integer>(size);

            // Generate list of random integers.
            for (int i = 0; i < size; i++) {
                nums.add(RAND.nextInt(size));
            }

            G.grid().run(
                BALANCE,
                new CI1<Integer>() {
                    @Override public void apply(Integer num) {
                        if (num <= 0) {
                            X.println(num + " is not valid number. Must be greater than 0.");
                        }
                        else {
                            int res = -1;

                            if (num > 1) {
                                for (int i = 2; i * i <= num; i++) {
                                    if (num % i == 0) {
                                        res = i;
                                    }
                                }
                            }

                            if (res == -1) {
                                res = num;
                            }

                            X.println("The minimum divider for number " + num + " is " + res);
                        }
                    }
                },
                nums);

            X.println("Check all nodes for number and their divider output.");
        }
        finally {
            G.stop(true);
        }
    }
}

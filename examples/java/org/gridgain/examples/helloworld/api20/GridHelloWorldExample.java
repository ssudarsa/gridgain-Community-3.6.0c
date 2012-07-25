// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.api20;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

/**
 * Demonstrates a simple use of GridGain grid.
 * <p>
 * String "Hello, World!" is passed as an argument for execution
 * of {@code HelloWorld} task. As an outcome, two participating
 * nodes will print out a single word from "Hello World" string.
 * One node will print out "Hello, " and the other will printout
 * "World!". If there is only one node participating, then it will
 * print out both words.
 * <p>
 * Grid task {@link GridHelloWorldTask} handles actual splitting
 * into sub-jobs, remote execution, and result aggregation
 * (see {@link GridTask}).
 * <p>
 * <h1 class="header">Starting Remote Nodes</h1>
 * To try this example you should (but don't have to) start remote grid instances.
 * You can start as many as you like by executing the following script:
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh}</pre>
 * Once remote instances are started, you can execute this example from
 * Eclipse, IntelliJ IDEA, or NetBeans (and any other Java IDE) by simply hitting run
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
public final class GridHelloWorldExample {
    /**
     * Ensure singleton.
     */
    private GridHelloWorldExample() {
        // No-op.
    }

    /**
     * Execute {@code HelloWorld} example on the grid.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        if (args.length == 0) {
            G.start();
        }
        else {
            G.start(args[0]);
        }

        try {
            Grid grid = G.grid();

            // Execute Hello World task.
            GridTaskFuture<Integer> fut = grid.execute(GridHelloWorldTask.class, "Hello World");

            // Wait for task completion.
            int phraseLen = fut.get();

            X.println(">>>");
            X.println(">>> Finished executing Grid \"Hello World\" example with custom task.");
            X.println(">>> Total number of characters in the phrase is '" + phraseLen + "'.");
            X.println(">>> You should see print out of 'Hello' on one node and 'World' on another node.");
            X.println(">>> Check all nodes for output (this node is also part of the grid).");
            X.println(">>>");
        }
        finally {
            G.stop(true);
        }
    }
}

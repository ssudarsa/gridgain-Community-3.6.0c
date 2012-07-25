// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.spring;

import org.gridgain.examples.helloworld.gridify.state.*;
import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.gridify.aop.spring.*;
import org.gridgain.grid.typedef.*;

/**
 * Demonstrates a simple use of GridGain grid with
 * {@code Gridify} annotation and custom grid task by utilizing
 * Spring proxy-based AOP implementation.
 * <p>
 * String "Hello World" is set into {@link GridifySpringHelloWorld} instance
 * as its state. Since method {@link GridifySpringHelloWorld#sayIt()} is annotated
 * with {@code @Gridify} annotation it is automatically grid-enabled. The
 * {@link GridifySpringHelloWorldTask} task specified in {@link Gridify}
 * annotation will split the state of {@link GridifySpringHelloWorld} instance into
 * words and send each word as job argument for execution on separate nodes.
 * Jobs on remote nodes will create {@link GridifySpringHelloWorldImpl} instances and will
 * pre-initialize them with state passed in as job's argument. As an outcome,
 * two participating nodes will print out a single word from "Hello World"
 * string. One node will print out "Hello" and the other will print out
 * "World". If there is only one node participating, then it will
 * print out both words.
 * <p>
 * Grid task {@link GridifySpringHelloWorldTask} handles actual splitting
 * into sub-jobs, remote execution, and result reduction (see {@link GridTask}).
 * <p>
 * NOTE: This example is slightly modified version of {@link GridifyHelloWorldStateExample}
 * to make it work with Spring Framework AOP implementation.
 * <h1 class="header">Spring AOP</h1>
 * Spring AOP framework is based on dynamic proxy implementation and doesn't require
 * any specific runtime parameters for online weaving. All weaving is on-demand and should
 * be performed by calling method {@link GridifySpringEnhancer#enhance(Object)} for the object
 * that has method with {@link Gridify} annotation.
 * <p>
 * Note that this method of weaving is rather inconvenient and AspectJ or JbossAOP is
 * recommended over it. Spring AOP can be used in situation when code augmentation is
 * undesired and cannot be used. It also allows for very fine grained control of what gets
 * weaved.
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
public final class GridifySpringHelloWorldExample {
    /**
     * Ensure singleton.
     */
    private GridifySpringHelloWorldExample() {
        // No-op.
    }

    /**
     * Execute {@code HelloWorld} example grid-enabled with {@code Gridify} annotation.
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
            // Simple example stateful instance to demonstrate
            // how object state can be handled with grid-enabled methods.
            GridifySpringHelloWorld helloWorld = new GridifySpringHelloWorldImpl("Hello World");

            // Required step with proxy-based Spring AOP.
            // The 'enhance' method will make sure that all methods
            // with Gridify annotation will be grid-enabled.
            helloWorld = GridifySpringEnhancer.enhance(helloWorld);

            // This method is grid-enabled and
            // will be executed on remote grid nodes.
            int phraseLen = helloWorld.sayIt();

            X.println(">>>");
            X.println(">>> Finished executing Gridify \"Hello World\" Spring AOP stateful example.");
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

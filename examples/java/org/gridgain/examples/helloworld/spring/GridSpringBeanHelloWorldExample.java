// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.spring;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;
import org.springframework.context.support.*;

import java.io.*;
import java.util.concurrent.*;

/**
 * Demonstrates a simple use of GridGain grid from Spring.
 * <p>
 * String "Hello World." is printed out by Callable passed into
 * the executor service provided by Grid. This statement could be printed
 * out on any node in the grid.
 * <p>
 * The major point of this example is to show grid injection by Spring
 * framework. Grid bean is described in spring.xml file and instantiated
 * by Spring context. Once application completed its execution Spring will
 * apply grid bean destructor and stop the grid.
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
 * <h1 class="header">Spring Configuration</h1>
 * This example uses spring.xml file which one can find in the same directory.
 * Configuration file has three configuration examples. One that uses
 * {@link GridConfiguration} is uncommented by default. You can modify it or
 * uncomment another one to use.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridSpringBeanHelloWorldExample {
    /**
     * Ensure singleton.
     */
    private GridSpringBeanHelloWorldExample() {
        // No-op.
    }

    /**
     * Executes simple {@code HelloWorld} example on the grid (without splitting).
     *
     * @param args Command line arguments, none of them are used.
     * @throws Exception If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        AbstractApplicationContext ctx = new ClassPathXmlApplicationContext(
            "org/gridgain/examples/helloworld/spring/spring.xml");

        // Get Grid from Spring.
        GridProjection g = (GridProjection)ctx.getBean("mySpringBean");

        ExecutorService exec = g.executor();

        Future<String> res = exec.submit(new GridHelloWorldCallable());

        // Wait for callable completion.
        res.get();

        X.println(">>>");
        X.println(">>> Finished executing Grid \"Spring bean\" example.");
        X.println(">>> You should see printed out of 'Hello world' on one of the nodes.");
        X.println(">>> Check all nodes for output (this node is also part of the grid).");
        X.println(">>>");

        ctx.destroy();
    }

    /**
     * Callable that prints out <tt>Hello world!</tt> statement.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static final class GridHelloWorldCallable implements Callable<String>, Serializable {
        /**
         * Prints out <tt>Hello world!</tt> statement.
         *
         * @return {@code null}
         */
        @Nullable @Override public String call() {
            X.println("Hello world!");

            return null;
        }
    }
}

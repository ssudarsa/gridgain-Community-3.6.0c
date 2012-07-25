// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.task;

import org.gridgain.examples.helloworld.gridify.spring.*;
import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.gridify.aop.spring.*;
import org.gridgain.grid.typedef.*;

/**
 * Demonstrates a simple use of GridGain grid with
 * {@code Gridify} annotation and custom grid task.
 * <p>
 * String "Hello World" is passed as an argument to
 * {@link #sayIt(String)} method. Since this method is annotated with
 * {@code Gridify} annotation it is automatically grid-enabled. The
 * {@link GridifyHelloWorldTask} task specified in {@link Gridify} annotation
 * will split the passed in string argument into words and send each word
 * as job argument for execution on separate nodes. As an outcome,
 * two participating nodes will print out a single word from "Hello World"
 * string. One node will print out "Hello" and the other will print out
 * "World". If there is only one node participating, then it will
 * print out both words.
 * <p>
 * Grid task {@link GridifyHelloWorldTask} handles actual splitting
 * into sub-jobs, remote execution, and result reduction (see {@link GridTask}).
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
 * <p>
 * <h1 class="header">AOP Configuration</h1>
 * In order for this example to execute on the grid, any of the following
 * AOP configurations must be provided (only on the task initiating node).
 * <h2 class="header">Jboss AOP</h2>
 * The following configuration needs to be applied to enable JBoss byte code
 * weaving. Note that GridGain is not shipped with JBoss and necessary
 * libraries will have to be downloaded separately (they come standard
 * if you have JBoss installed already):
 * <ul>
 * <li>
 *      The following JVM configuration must be present:
 *      <ul>
 *      <li>{@code -javaagent:[path to jboss-aop-jdk50-4.x.x.jar]}</li>
 *      <li>{@code -Djboss.aop.class.path=[path to gridgain.jar]}</li>
 *      <li>{@code -Djboss.aop.exclude=org,com -Djboss.aop.include=org.gridgain.examples}</li>
 *      </ul>
 * </li>
 * <li>
 *      The following JARs should be in a classpath:
 *      <ul>
 *      <li>{@code javassist-3.x.x.jar}</li>
 *      <li>{@code jboss-aop-jdk50-4.x.x.jar}</li>
 *      <li>{@code jboss-aspect-library-jdk50-4.x.x.jar}</li>
 *      <li>{@code jboss-common-4.x.x.jar}</li>
 *      <li>{@code trove-1.0.2.jar}</li>
 *      </ul>
 * </li>
 * </ul>
 * <p>
 * <h2 class="header">AspectJ AOP</h2>
 * The following configuration needs to be applied to enable AspectJ byte code
 * weaving.
 * <ul>
 * <li>
 *      JVM configuration should include:
 *      {@code -javaagent:${GRIDGAIN_HOME}/libs/aspectjweaver-1.6.8.jar}
 * </li>
 * <li>
 *      Classpath should contain the {@code ${GRIDGAIN_HOME}/config/aop/aspectj} folder.
 * </li>
 * </ul>
 * <p>
 * <h2 class="header">Spring AOP</h2>
 * Spring AOP framework is based on dynamic proxy implementation and doesn't require
 * any specific runtime parameters for online weaving. All weaving is on-demand and should
 * be performed by calling method {@link GridifySpringEnhancer#enhance(Object)} for the object
 * that has method with {@link Gridify} annotation.
 * <p>
 * Note that this method of weaving is rather inconvenient and AspectJ or JBoss AOP are
 * more recommended. Spring AOP can be used in situation when code augmentation is
 * undesired and cannot be used. It also allows for very fine grained control of what gets
 * weaved.
 * <p>
 * NOTE: this example as is cannot be used with Spring AOP. To see an example of grid-enabling
 * with Spring AOP refer to {@link GridifySpringHelloWorldExample} example.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridifyHelloWorldTaskExample {
    /**
     * Ensure singleton.
     */
    private GridifyHelloWorldTaskExample() {
        // No-op.
    }

    /**
     * Method grid-enabled with {@link Gridify} annotation and will be
     * executed on the grid. Simply prints out the argument passed in and
     * returns its length.
     * <p>
     * Note that in this case instead of using default task, we provide
     * our own task which will split the passed in string into separate
     * words to be printed on remote nodes. The timeout parameter
     * tells grid that method execution should fail if no result
     * is received in 5 seconds.
     *
     * @param phrase Phrase string to print.
     * @return Number of characters in the phrase.
     */
    @Gridify(taskClass = GridifyHelloWorldTask.class, timeout = 5000)
    public static int sayIt(String phrase) {
        // Simply print out the argument.
        X.println(">>>");
        X.println(">>> Printing '" + phrase + "' on this node from grid-enabled method.");
        X.println(">>>");

        return phrase.length();
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
            // This method will be executed on a remote grid nodes.
            int phraseLen = sayIt("Hello World");

            X.println(">>>");
            X.println(">>> Finished executing Gridify \"Hello World\" example with custom task.");
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

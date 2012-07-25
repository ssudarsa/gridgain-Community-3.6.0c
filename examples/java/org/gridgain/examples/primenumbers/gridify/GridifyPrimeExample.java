// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.primenumbers.gridify;

import org.gridgain.examples.helloworld.gridify.spring.*;
import org.gridgain.examples.primenumbers.*;
import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.gridify.aop.spring.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Main bootstrap class for {@link Gridify} annotation based Prime Number
 * calculation on the grid. This class starts up GridGain Grid and executes
 * {@link GridPrimeChecker#checkPrime(long, long, long)} method for every
 * number we want to check for prime. As this method is annotated with
 * {@link Gridify @Gridify} annotation, it will be executed on the grid
 * by {@link GridifyPrimeTask}.
 * <p>
 * Refer to {@link GridifyPrimeTask} for documentation on how prime numbers
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
public final class GridifyPrimeExample {
    /**
     * Enforces singleton.
     */
    private GridifyPrimeExample() {
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
                // This method will be executed on the Grid as it is
                // annotated with @Gridify annotation.
                Long divisor = GridPrimeChecker.checkPrime(checkVal, 2, checkVal);

                // If divisor is null, then the number is prime.
                if (divisor == null) {
                    X.println("Value '" + checkVal + "'is a prime number.");
                }
                else {
                    X.println("Value '" + checkVal + "' is divisible by '" + divisor + '\'');
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

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.montecarlo.api20;

import org.gridgain.examples.helloworld.gridify.spring.*;
import org.gridgain.examples.montecarlo.*;
import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.gridify.aop.spring.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.spi.loadbalancing.adaptive.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * This is main class in this example. It basically starts grid locally (local node)
 * and executes credit risk calculation. If there are grid nodes available this
 * calculation will be gridified to other nodes. Information below describes
 * how to configure JVM parameters to enable AOP-based grid enabling.
 * <p>
 * <h1 class="header">Load Balancing</h1>
 * Please configure different {@link GridLoadBalancingSpi} implementations to use
 * various load balancers. For example, if you would like grid to automatically
 * adapt to the load, use {@link GridAdaptiveLoadBalancingSpi} SPI.
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
 * <p>
 * GridGain examples come with multiple configuration files you can try.
 * All configuration files are located under {@code GRIDGAIN_HOME/examples/config}
 * folder.
 * <p>
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
public final class GridCreditRiskExample {
    /**
     * Ensure singleton.
     */
    private GridCreditRiskExample() {
        // No-op.
    }

    /**
     * Method creates portfolio and calculates credit risks.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws Exception If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        // Start GridGain instance with default configuration.
        if (args.length == 0) {
            G.start();
        }
        else {
            G.start(args[0]);
        }

        try {
            // Create portfolio.
            GridCredit[] portfolio = new GridCredit[5000];

            Random rnd = new Random();

            // Generate some test portfolio items.
            for (int i = 0; i < portfolio.length; i++) {
                portfolio[i] = new GridCredit(
                    50000 * rnd.nextDouble(), // Credit amount.
                    rnd.nextInt(1000), // Credit term in days.
                    rnd.nextDouble() / 10, // APR.
                    rnd.nextDouble() / 20 + 0.02 // EDF.
                );
            }

            // Forecast horizon in days.
            int horizon = 365;

            // Percentile.
            double percentile = 0.95;

            // Number of Monte-Carlo iterations.
            int iter = 10000;

            // Instance of credit risk manager.
            GridCreditRiskManager riskMgr = new GridCreditRiskManager();

            // Mark the stopwatch.
            long start = System.currentTimeMillis();

            // Calculate credit risk and print it out.
            // As you can see the grid enabling is completely hidden from the caller
            // and it is fully transparent to him. In fact, the caller is never directly
            // aware if method was executed just locally on the 100s of grid nodes.
            // Credit risk crdRisk is the minimal amount that creditor has to have
            // available to cover possible defaults.
            double crdRisk = riskMgr.calculateCreditRiskMonteCarlo(portfolio, horizon, iter, percentile);

            X.println("Credit risk [crdRisk=" + crdRisk + ", duration=" +
                (System.currentTimeMillis() - start) + "ms]");
        }
        // We specifically don't do any error handling here to
        // simplify the example. Real application may want to
        // add error handling and application specific recovery.
        finally {
            // Stop GridGain instance.
            G.stop(true);
        }
    }
}

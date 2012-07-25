// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.gridify;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.gridify.aop.spring.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Demonstrates a use of {@code @GridifySetToValue} and {@code GridifySetToSet} annotations with
 * Spring proxy-based AOP.
 * <p>
 * Collection with numbers is passed as an argument to
 * {@link GridifySpringMathematics#findMaximum(Collection)} and to
 * {@link GridifySpringMathematics#findPrimes(Collection)} method.
 * Since this methods is annotated with appropriate annotation
 * ({@code @GridifySetToValue} and {@code GridifySetToSet}) it is
 * automatically grid-enabled and will be executed on few remote nodes.
 * Note, that the only thing user had to do is annotate method
 * with grid-enabled annotation, everything else is taken care of by the system.
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
 * NOTE: This example is slightly modified version of {@link GridifyMathematicsExample}
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
public final class GridifySpringMathematicsExample {
    /**
     * Ensure singleton.
     */
    private GridifySpringMathematicsExample() {
        // No-op.
    }

    /**
     * Execute {@code GridifyMathematicsExample} example grid-enabled with {@code GridifySetToValue} and
     * {@code GridifySetToSet} annotation.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CIX1 -> GridInClosureX
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        G.in(args.length == 0 ? null : args[0], new CIX1<Grid>() {
            @Override public void applyx(Grid g) {
                // Simple example stateful instance to demonstrate
                // how object state can be handled with grid-enabled methods.
                GridifySpringMathematics math = new GridifySpringMathematicsImpl();

                // Required step with proxy-based Spring AOP.
                // The 'enhance' method will make sure that all methods
                // with Gridify annotation will be grid-enabled.
                math = GridifySpringEnhancer.enhance(math);

                // This method will be executed on a remote grid nodes.
                Long max = math.findMaximum(Arrays.asList(1L, 2L, 3L, 4L));

                Collection<Long> primesInSet = math.findPrimes(Arrays.asList(2L, 3L, 4L, 6L));

                X.println(">>>");
                X.println(">>> Finished executing Gridify examples.");
                X.println(">>> Maximum in collection '" + max + "'.");
                X.println(">>> Prime numbers in set '" + primesInSet + "'.");
                X.println(">>>");
            }
        });
    }
}

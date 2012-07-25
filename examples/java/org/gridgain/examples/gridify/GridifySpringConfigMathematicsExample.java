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
import org.gridgain.grid.typedef.internal.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;
import java.util.*;

/**
 * Demonstrates a use of {@code @GridifySetToValue} and {@code GridifySetToSet} annotations with
 * Spring AOP configuration.
 * <p>
 * Collection with numbers is passed as an argument to
 * {@link GridifySpringAnnotatedMathematics#findMaximum(Collection)} and to
 * {@link GridifySpringAnnotatedMathematics#findPrimes(Iterable)} method.
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
 * You should run the following sample with Spring XML configuration file shipped
 * with GridGain and located {@code ${GRIDGAIN_HOME}/examples/config/spring-aop-config.xml}.
 * You should pass a path to Spring XML configuration file as 1st command line
 * argument into this example.
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
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifySpringConfigMathematicsExample {
    /**
     * Ensure singleton.
     */
    private GridifySpringConfigMathematicsExample() {
        // No-op.
    }

    /**
     * Execute {@code GridifyMathematicsExample} example grid-enabled with {@code GridifySetToValue} and
     * {@code GridifySetToSet} annotation.
     *
     * @param args Command line arguments (optional).
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        final GenericApplicationContext springCtx;

        try {
            springCtx = new GenericApplicationContext();

            new XmlBeanDefinitionReader(springCtx).loadBeanDefinitions(new UrlResource(U.resolveGridGainUrl(
                args.length == 0 ? "examples/config/spring-aop-config.xml" : args[0])));

            springCtx.refresh();
        }
        catch (BeansException e) {
            throw new GridException("Failed to instantiate Spring XML application context: " + e.getMessage(), e);
        }

        // Typedefs:
        // ---------
        // G -> GridFactory
        // CIX1 -> GridInClosureX
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        G.in(springCtx, new CIX1<Grid>() {
            @Override public void applyx(Grid g) {
                // Simple example stateful instance to demonstrate
                // how object state can be handled with grid-enabled methods.
                GridifySpringAnnotatedMathematics math =
                    (GridifySpringAnnotatedMathematics)springCtx.getBean("springMathematics");

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

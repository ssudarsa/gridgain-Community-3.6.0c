// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */
 
package org.gridgain.scalar.examples

import org.gridgain.scalar.scalar
import org.gridgain.grid.gridify.Gridify

/**
 * Demonstrates a simple use of GridGain grid in Scala with `Gridify`
 * annotation. 
 * <p>
 * String "Hello, World!" is passed as an argument to
 * `sayOnSomeNode(String)` method. Since this method is annotated with
 * `Gridify` annotation it is automatically grid-enabled and
 * will be executed on remote node. Note, that the only thing user had
 * to do is annotate method `sayOnSomeNode(String)` with {@link Gridify}
 * annotation, everything else is taken care of by the system.
 * <p>
 * <h1 class="header">Starting Remote Nodes</h1>
 * To try this example you should (but don't have to) start remote grid instances.
 * You can start as many as you like by executing the following script:
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh}</pre>
 * Once remote instances are started, you can execute this example from
 * Eclipse, Idea, or NetBeans (or any other IDE) by simply hitting run
 * button. You will witness that all nodes discover each other and
 * some of the nodes will participate in task execution (check node
 * output).
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
 *      <li>`-javaagent:[path to jboss-aop-jdk50-4.x.x.jar]`</li>
 *      <li>`-Djboss.aop.class.path=[path to gridgain.jar]`</li>
 *      <li>`-Djboss.aop.exclude=org,com -Djboss.aop.include=org.gridgain.examples`</li>
 *      </ul>
 * </li>
 * <li>
 *      The following JARs should be in a classpath:
 *      <ul>
 *      <li>`javassist-3.x.x.jar`</li>
 *      <li>`jboss-aop-jdk50-4.x.x.jar`</li>
 *      <li>`jboss-aspect-library-jdk50-4.x.x.jar`</li>
 *      <li>`jboss-common-4.x.x.jar`</li>
 *      <li>`trove-1.0.2.jar`</li>
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
 *      `-javaagent:${GRIDGAIN_HOME}/libs/aspectjweaver-1.6.8.jar`
 * </li>
 * <li>
 *      Classpath should contain the `${GRIDGAIN_HOME}/config/aop/aspectj` folder.
 * </li>
 * </ul>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarAopExample {
    /**
     * Main entry point to application.
     *
     * @param args Command like argument (not used).
     */
    def main(args: Array[String]) = scalar {
        sayOnSomeNode("Hello Cloud World!")
    }

    /**
     * This method mocks business logic for the purpose of this example.
     */
    @Gridify
    def sayOnSomeNode(msg: String) {
        println("\n" + msg + "\n")
    }
}

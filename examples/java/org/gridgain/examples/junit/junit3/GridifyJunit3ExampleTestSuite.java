// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.junit.junit3;

import junit.framework.*;
import org.gridgain.grid.test.*;

/**
 * Regular JUnit3 suite. Note that because of {@link GridifyTest} annotation,
 * all tests will execute in parallel on the grid.
 * <p>
 * Note that since {@link TestA} and {@link TestB} are added to this
 * suite from within another nested suite and not directly, they will
 * always execute sequentially, however still in parallel with other
 * tests.
 * <h1 class="header">Jboss AOP Configuration</h1>
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
 *      The following JARs should be in a classpath (all located under {@code ${GRIDGAIN_HOME}/libs} folder):
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
 * <h1 class="header">AspectJ AOP Configuration</h1>
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
 * When starting remote nodes for test execution, make sure to point
 * them to {@code "$GRIDGAIN_HOME/config/junit/junit-spring.xml"}
 * configuration file.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridifyJunit3ExampleTestSuite {
    /**
     * Enforces singleton.
     */
    private GridifyJunit3ExampleTestSuite() {
        // No-op.
    }

    /**
     * Standard JUnit3 static {@code suite()} method. Note we attach {@link GridifyTest}
     * annotation to it, so it will be grid-enabled.
     *
     * @return JUnit3 suite.
     */
    @GridifyTest
    public static Test suite() {
        TestSuite suite = new TestSuite("Example Test Suite");

        // Nested test suite to run tests A and B sequentially.
        TestSuite nested = new TestSuite("Example Nested Sequential Suite");

        nested.addTestSuite(TestA.class);
        nested.addTestSuite(TestB.class);

        // Add tests A and B.
        suite.addTest(nested);

        // Add other tests.
        suite.addTestSuite(TestC.class);
        suite.addTestSuite(TestD.class);

        return suite;
    }
}

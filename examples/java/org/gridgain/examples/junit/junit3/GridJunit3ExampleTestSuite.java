// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.junit.junit3;

import junit.framework.*;
import org.gridgain.grid.test.junit3.*;

/**
 * Distributed JUnit3 test. All tests added to it will execute in parallel
 * on the grid.
 * <p>
 * Note that since {@link TestA} and {@link TestB} are added to this
 * suite from within another nested suite and not directly, they will
 * always execute sequentially, however still in parallel with other
 * tests.
 * <p>
 * Also note, that since {@link TestC} is added as a part of local
 * suite (see {@link GridJunit3LocalTestSuite}), it will always execute
 * on local node, however still in parallel with other tests.
 * <p>
 * When starting remote nodes for test execution, make sure to point
 * them to {@code "$GRIDGAIN_HOME/config/junit/junit-spring.xml"}
 * configuration file.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridJunit3ExampleTestSuite {
    /**
     * Enforces singleton.
     */
    private GridJunit3ExampleTestSuite() {
        // No-op.
    }

    /**
     * Standard JUnit3 static suite method.
     *
     * @return JUnit3 suite.
     */
    public static Test suite() {
        TestSuite suite = new GridJunit3TestSuite("Example Grid Test Suite");

        // Nested test suite to run tests A and B sequentially.
        TestSuite nested = new TestSuite("Example Nested Sequential Suite");

        nested.addTestSuite(TestA.class);
        nested.addTestSuite(TestB.class);

        // Add tests A and B.
        suite.addTest(nested);

        // Add TestC to execute always on the local node but still in
        // parallel with other tests.
        suite.addTest(new GridJunit3LocalTestSuite(TestC.class, "Local suite"));

        // Add other tests.
        suite.addTestSuite(TestD.class);

        return suite;
    }
}

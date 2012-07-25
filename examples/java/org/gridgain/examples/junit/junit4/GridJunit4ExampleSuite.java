// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.junit.junit4;

import org.gridgain.grid.test.junit4.*;
import org.junit.runner.*;
import org.junit.runners.Suite.*;

/**
 * Distributed JUnit4 suite. It will execute all tests added to it
 * in parallel on the grid.
 * <p>
 * Note that since {@link TestA} and {@link TestB} are added to this
 * suite from within another nested suite and not directly, they will
 * always execute sequentially, however still in parallel with other
 * tests.
 * <p>
 * Also note, that since {@link TestC} is added as a part of local
 * suite (see {@link GridJunit4LocalSuite}), it will always execute
 * on local node, however still in parallel with other tests.
 * <p>
 * When starting remote nodes for test execution, make sure to point
 * them to {@code "$GRIDGAIN_HOME/config/junit/junit-spring.xml"}
 * configuration file.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@RunWith(GridJunit4Suite.class)
@SuiteClasses({
    GridJunit4ExampleNestedSuite.class, // Nested suite that will execute tests A and B added to it sequentially.
    GridJunit4ExampleNestedLocalSuite.class, // Local suite that will execute its test C locally.
    TestD.class // TestD will run in parallel with (A and B) and C tests.
})
public class GridJunit4ExampleSuite {
    // No-op.
}

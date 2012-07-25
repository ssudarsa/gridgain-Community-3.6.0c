// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import junit.framework.*;
import java.io.*;

/**
 * TODO: add file description.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
interface GridJunit3SerializableTest extends Serializable {
    /**
     * @return Test name.
     */
    String getName();

    /**
     * @return Test to run.
     */
    Test getTest();

    /**
     * @return Test class.
     */
    Class<? extends Test> getTestClass();

    /**
     * @param t Test case to find.
     * @return Found test case or {@code null} if test case was not found.
     */
    @SuppressWarnings({"ClassReferencesSubclass"})
    GridJunit3SerializableTestCase findTestCase(TestCase t);

    /**
     * @param res JUnit test.
     */
    void setResult(GridJunit3SerializableTest res);
}

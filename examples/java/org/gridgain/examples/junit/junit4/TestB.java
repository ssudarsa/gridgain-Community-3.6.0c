// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.junit.junit4;

import org.gridgain.grid.typedef.*;
import org.junit.*;

/**
 * Regular JUnit4 test used for JUnit4 example.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@SuppressWarnings({"ProhibitedExceptionThrown"})
public class TestB {
    /**
     * Set up logic.
     *
     * @throws Exception Thrown in case of any error.
     */
    @Before
    public void beforeTest() throws Exception {
        X.println("Preparing for test execution: " + getClass().getSimpleName());
    }

    /**
     * Tear down logic.
     *
     * @throws Exception Thrown in case of any error.
     */
    @After
    public void afterTest() throws Exception {
        X.println("Tearing down test execution: " + getClass().getSimpleName());
    }

    /**
     * Example test method.
     */
    @Test
    public void testMethod1() {
        X.println("Output from TestB.testMethod1().");
    }

    /**
     * Example test method.
     */
    @Test
    public void testMethod2() {
        X.println("Output from TestB.testMethod2().");
    }

    /**
     * Example test method.
     */
    @Test
    public void testMethod3() {
        X.println("Output from TestB.testMethod3().");

        Assert.assertTrue("Failed assertion from TestB.testMethod3().", false);
    }

    /**
     * Example test method.
     */
    @Test
    public void testMethod4() {
        X.println("Output from TestB.testMethod4().");

        throw new RuntimeException("Failed exception from TestB.testMethod4().");
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.interceptor;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import java.lang.annotation.*;
import java.lang.management.*;

/**
 * This interceptor will grid-enable methods only if local node's heap
 * memory usage is above 80% of all heap memory available.
 * <p>
 * A method declaration grid-enabled with this interceptor would look
 * like this:
 * <pre name="code" class="java">
 *      // Method grid-enabled with 'Gridify' annotation. Simply prints
 *      // out the argument passed in.
 *      // Note that the custom context factory used ensured that the method will
 *      // be grid-enabled only if used heap memory is above 80% of maximum heap
 *      // memory available.
 *      &#64;Gridify(interceptor = GridifyHelloWorldInterceptor.class)
 *      public static void helloWorld(String arg) {
 *         // Simply print out the argument.
 *         $.println("&gt;&gt;&gt; Node " + G.grid().localNode().getPhysicalAddress() + " is executing '" +
 *             arg + "' &lt;&lt;&lt;");
 *      }
 * </pre>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifyHelloWorldInterceptor implements GridifyInterceptor {
    /**
     * Grid-enables method only if heap utilized over 80%. Otherwise, method
     * method will proceed with local execution without calling grid at all.
     *
     * @param gridify {@inheritDoc}
     * @param arg {@inheritDoc}
     * @return {@inheritDoc}
     * @throws GridException {@inheritDoc}
     */
    @Override public boolean isGridify(Annotation gridify, GridifyArgument arg) throws GridException {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        // If heap memory for the local node is above 80% of maximum heap
        // memory available, return true which means that method will
        // be grid-enabled. Otherwise, return false, which means that
        // method will execute locally without grid.
        return heap.getUsed() > 0.8 * heap.getMax();
    }
}

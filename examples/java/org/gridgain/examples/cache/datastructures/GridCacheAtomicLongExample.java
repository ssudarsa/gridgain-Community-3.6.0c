// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache.datastructures;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.typedef.*;

import java.util.*;

import static org.gridgain.grid.GridClosureCallMode.*;

/**
 * Demonstrates a simple usage of distributed atomic long. Note that atomic long is only
 * available in <b>Enterprise Edition</b>.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache configuration, e.g. {@code 'ggstart.sh examples/config/spring-cache.xml'}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridCacheAtomicLongExample {
    /** Cache name. */
    //private static final String CACHE_NAME = "replicated";
    private static final String CACHE_NAME = "partitioned";

    /** Number of retries */
    private static final int RETRIES = 20;

    /**
     * Executes this example on the grid.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        Grid grid = G.start("examples/config/spring-cache.xml");

        try {
            if (!grid.isEnterprise()) {
                System.err.println("This example works only in Enterprise Edition.");

                return;
            }

            print("Starting atomic long example on nodes: " + grid.nodes().size());

            // Number nodes in grid.
            int nodes = grid.size();

            // Make name for atomic long (by which it will be known in the grid).
            final String atomicName = UUID.randomUUID().toString();

            // Initialize atomic long in grid.
            GridCacheAtomicLong atomicLong = grid.cache(CACHE_NAME).atomicLong(atomicName);

            print("Atomic long initial size : " + atomicLong.get() + '.');

            // Try increment atomic long from all grid nodes.
            // Note that this node is also part of the grid.
            grid.run(BROADCAST, new CAX() {
                @Override public void applyx() throws GridException {
                    GridCacheAtomicLong atomicLong = G.grid().cache(CACHE_NAME).atomicLong(atomicName);

                    for (int i = 0; i < RETRIES; i++)
                        print("AtomicLong value has been incremented " + atomicLong.incrementAndGet() + '.');
                }
            });

            print("");
            print("AtomicLong after incrementing [expected=" + (nodes * RETRIES) + ", actual=" + atomicLong.get() + ']');
            print("Finished atomic long example...");
            print("Check all nodes for output (this node is also part of the grid).");
            print("");
        }
        finally {
            G.stop(true);
        }
    }

    /**
     * Prints out given object to standard out.
     *
     * @param o Object to print.
     */
    private static void print(Object o) {
        X.println(">>> " + o);
    }

    /**
     * Ensure singleton.
     */
    private GridCacheAtomicLongExample() {
        // No-op.
    }
}

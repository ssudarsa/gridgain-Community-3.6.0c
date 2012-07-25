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
 * Demonstrates a simple usage of distributed atomic reference. Note that atomic reference is
 * only available in <b>Enterprise Edition</b>.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache configuration, e.g. {@code 'ggstart.sh examples/config/spring-cache.xml'}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridCacheAtomicReferenceExample {
    /** Cache name. */
    // private static final String CACHE_NAME = "replicated";
    private static final String CACHE_NAME = "partitioned";

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

            print("Starting atomic reference example on nodes: " + grid.nodes().size());

            // Make name of atomic reference.
            final String referenceName = UUID.randomUUID().toString();

            // Make value of atomic reference.
            String value = UUID.randomUUID().toString();

            // Initialize atomic reference in grid.
            GridCacheAtomicReference<String> ref = grid.cache(CACHE_NAME).
                atomicReference(referenceName, value, false);

            print("Atomic reference initial value : " + ref.get() + '.');

            // Make closure for checking atomic reference value on grid.
            Runnable c = new CAX() {
                @Override public void applyx() throws GridException {
                    GridCacheAtomicReference<String> ref = G.grid().cache(CACHE_NAME).
                        atomicReference(referenceName);

                    print("Atomic reference value is " + ref.get() + '.');
                }
            };

            // Check atomic reference on all grid nodes.
            grid.run(BROADCAST, c);

            // Make new value of atomic reference.
            String newValue = UUID.randomUUID().toString();

            print("Try to change value of atomic reference with wrong expected value.");

            ref.compareAndSet("WRONG EXPECTED VALUE", newValue); // Won't change.

            // Check atomic reference on all grid nodes.
            // Atomic reference value shouldn't be changed.
            grid.run(BROADCAST, c);

            print("Try to change value of atomic reference with correct expected value.");

            ref.compareAndSet(value, newValue);

            // Check atomic reference on all grid nodes.
            // Atomic reference value should be changed.
            grid.run(BROADCAST, c);
        }
        finally {
            G.stop(true);
        }

        print("");
        print("Finished atomic reference example...");
        print("Check all nodes for output (this node is also part of the grid).");
        print("");
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
    private GridCacheAtomicReferenceExample() {
        // No-op.
    }
}

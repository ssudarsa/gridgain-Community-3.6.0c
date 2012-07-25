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
 * Demonstrates a simple usage of distributed atomic sequence. Note that atomic long is only
 * available in <b>Enterprise Edition</b>.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache configuration, e.g. {@code 'ggstart.sh examples/config/spring-cache.xml'}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridCacheAtomicSequenceExample {
    /** Cache name. */
    // private static final String CACHE_NAME = "replicated";
    private static final String CACHE_NAME = "partitioned";

    /** Number of retries */
    private static final int RETRIES = 20;

    /**
     * Executes this example on the grid.
     * <p>
     * Note that atomic sequence reserves on each node region of ids for best performance.
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

            print("Starting atomic sequence example on nodes: " + grid.nodes().size());

            // Make name of sequence.
            final String seqName = UUID.randomUUID().toString();

            // Initialize atomic sequence in grid.
            GridCacheAtomicSequence seq = grid.cache(CACHE_NAME).atomicSequence(seqName);

            // First value of atomic sequence on this node.
            long firstVal = seq.get();

            print("Sequence initial value: " + firstVal);

            // Try increment atomic sequence on all grid nodes. Note that this node is also part of the grid.
            grid.run(BROADCAST, new CAX() {
                @Override public void applyx() throws GridException {
                    GridCacheAtomicSequence seq = G.grid().cache(CACHE_NAME).atomicSequence(seqName);

                    for (int i = 0; i < RETRIES; i++)
                        print("Sequence [currentValue=" + seq.get() + ", afterIncrement=" + seq.incrementAndGet() + ']');
                }
            });

            print("Sequence after incrementing [expected=" + (firstVal + RETRIES) + ", actual=" + seq.get() + ']');

        }
        finally {
            G.stop(true);
        }

        print("");
        print("Finished atomic sequence example...");
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
    private GridCacheAtomicSequenceExample() {
        // No-op.
    }
}

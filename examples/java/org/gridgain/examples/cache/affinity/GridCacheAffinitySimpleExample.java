// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache.affinity;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.typedef.*;

/**
 * This example demonstrates the simplest code that populates the distributed cache
 * and co-locates simple closure execution with each key. The goal of this particular
 * example is to provide the simplest code example of this logic.
 * <p>
 * Note that other examples in this package provide more detailed examples
 * of affinity co-location.
 * <p>
 * Note also that for Enterprise Edition affinity routing is enabled for all caches. In
 * Community Edition affinity routing works only if the cache is configured locally.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache: {@code 'ggstart.sh examples/config/spring-cache.xml'}. Local node can
 * be started with or without cache depending on whether community or enterprise
 * edition is used respectively.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridCacheAffinitySimpleExample {
    /** Number of keys. */
    private static final int KEY_CNT = 20;

    /** Grid instance. */
    private static Grid g;

    /**
     * Enforces singleton.
     */
    private GridCacheAffinitySimpleExample() {
        // No-op.
    }

    /**
     * Executes cache affinity example.
     * <p>
     * Note that in case of {@code LOCAL} configuration,
     * since there is no distribution, values may come back as {@code nulls}.
     *
     * @param args Command line arguments
     * @throws Exception If failed.
     */
    public static void main(String[] args) throws Exception {
        g = G.start("examples/config/spring-cache.xml");

        try {
            GridCache<Integer, String> cache = g.cache("partitioned");

            // If you run this example multiple times - make sure
            // to comment this call in order not to re-populate twice.
            populate(cache);

            // Co-locates closures with data in data grid.
            visit(cache);
        }
        finally {
            G.stop(true);
        }
    }

    /**
     * Visits every data grid entry on the remote node it resides by co-locating visiting
     * closure with the cache key.
     *
     * @param c Cache to use.
     * @throws GridException Thrown in case of any cache error.
     */
    private static void visit(final GridCache<Integer, String> c) throws GridException {
        for (int i = 0; i < KEY_CNT; i++) {
            // Affinity key is cache key for this example.
            final int key = i;

            g.affinityRun("partitioned", key, new CA() {
                // This closure will execute on the remote node where
                // data with the 'key' is located. Since it will be co-located
                // we can use local 'peek' operation safely.
                @Override public void apply() {
                    // Value should never be 'null' as we are co-located and using local 'peek'
                    // access method.
                    System.out.println("Co-located [key= " + key + ", value=" + c.peek(key) + ']');
                }
            });
        }
    }

    /**
     * Populates given cache.
     *
     * @param c Cache to populate.
     * @throws GridException Thrown in case of any cache error.
     */
    private static void populate(GridCache<Integer, String> c) throws GridException {
        for (int i = 0; i < KEY_CNT; i++)
            c.put(i, Integer.toString(i));
    }
}

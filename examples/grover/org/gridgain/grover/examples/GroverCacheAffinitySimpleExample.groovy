// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.examples

import org.gridgain.grid.*
import org.gridgain.grid.cache.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*

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
@Typed
@Use(GroverProjectionCategory)
class GroverCacheAffinitySimpleExample {
    /** Number of keys. */
    private static final int KEY_CNT = 20

    /** Grid instance. */
    private static Grid g

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
        g = start("examples/config/spring-cache.xml")

        try {
            def cache = g.<Integer, String>cache("partitioned")

            // If you run this example multiple times - make sure
            // to comment this call in order not to re-populate twice.
            populate(cache)

            // Co-locates closures with data in data grid.
            visit(cache)
        }
        finally {
            stop()
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
        (0 ..< KEY_CNT).each { key ->
            g.affinityRunOneKey("partitioned", key) { ->
                println("Co-located [key= " + key + ", value=" + c.peek(key) + ']')
            }
        }
    }

    /**
     * Populates given cache.
     *
     * @param c Cache to populate.
     * @throws GridException Thrown in case of any cache error.
     */
    private static void populate(GridCache<Integer, String> c) throws GridException {
        (0 ..< KEY_CNT).each { c.put(it, it.toString()) }
    }
}

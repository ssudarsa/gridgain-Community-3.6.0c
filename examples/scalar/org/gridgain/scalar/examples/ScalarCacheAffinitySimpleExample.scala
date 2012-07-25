// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */

package org.gridgain.scalar.examples

import org.gridgain.scalar.scalar
import scalar._
import org.gridgain.grid.cache.GridCache

/**
 * This example demonstrates the simplest code that populates the distributed cache
 * and co-locates simple closure execution with each key. The goal of this particular
 * example is to provide the simplest code example of this logic.
 *
 * Note that other examples in this package provide more detailed examples
 * of affinity co-location.
 *
 * Note also that for Enterprise Edition affinity routing is enabled for all caches. In
 * Community Edition affinity routing works only if the cache is configured locally.
 *
 * Remote nodes should always be started with configuration file which includes
 * cache: `'ggstart.sh examples/config/spring-cache.xml'`. Local node can
 * be started with or without cache depending on whether community or enterprise
 * edition is used respectively.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarCacheAffinitySimpleExample {
    /** Number of keys. */
    private val KEY_CNT = 20

    /**
     * Example entry point. No arguments required.
     *
     * Note that in case of `LOCAL` configuration,
     * since there is no distribution, values may come back as `nulls`.
     */
    def main(args: Array[String]) {
        scalar("examples/config/spring-cache.xml") {
            val c = grid$.cache[Int, String]("partitioned")

            populate(c)
            visit(c)
        }
    }

    /**
     * Visits every data grid entry on the remote node it resides by co-locating visiting
     * closure with the cache key.
     *
     * @param c Cache to use.
     */
    private def visit(c: GridCache[Int, String]) {
        (0 until KEY_CNT).foreach(i =>
            grid$.affinityRun("partitioned", i,
                () => println("Co-located [key= " + i + ", value=" + c.peek(i) + ']'))
        )
    }

    /**
     * Populates given cache.
     *
     * @param c Cache to populate.
     */
    private def populate(c: GridCache[Int, String]) {
        (0 until KEY_CNT).foreach(i => c += (i -> i.toString))
    }
}

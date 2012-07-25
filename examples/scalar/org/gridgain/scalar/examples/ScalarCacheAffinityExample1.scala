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
import org.gridgain.grid._
import cache.affinity.GridCacheAffinityMapped
import cache.GridCacheName
import GridClosureCallMode._
import lang.{GridCallable, GridFunc => F}
import org.jetbrains.annotations.Nullable

/**
 * Example of how to collocate computations and data in GridGain using
 * `GridCacheAffinityMapped` annotation as opposed to direct API calls. This
 * example will first populate cache on some node where cache is available, and then
 * will send jobs to the nodes where keys reside and print out values for those
 * keys.
 *
 * Remote nodes should always be started with configuration file which includes
 * cache: `'ggstart.sh examples/config/spring-cache.xml'`. Local node can
 * be started with or without cache depending on whether community or enterprise
 * edition is used respectively.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarCacheAffinityExample1 {
    /** Configuration file name. */
    //private val CONFIG = "examples/config/spring-cache-none.xml" // Enterprise Edition.
    private val CONFIG = "examples/config/spring-cache.xml" // Community Edition.

    /** Name of cache specified in spring configuration. */
    private val NAME = "partitioned"

    /**
     * Example entry point. No arguments required.
     *
     * Note that in case of `LOCAL` configuration,
     * since there is no distribution, values may come back as `nulls`.
     */
    def main(args: Array[String]) {
        scalar(CONFIG) {
            var keys = Seq.empty[String]

            ('A' to 'Z').foreach(keys :+= _.toString)

            populateCache(grid$, keys)

            var results = Map.empty[String, String]

            keys.foreach(key => {
                val result = grid$.call(
                    BALANCE,
                    new GridCallable[String] {
                        @GridCacheAffinityMapped
                        def affinityKey(): String = key

                        @GridCacheName
                        def cacheName(): String = NAME

                        @Nullable def call: String = {
                            println(">>> Executing affinity job for key: " + key)

                            val cache = cache$[String, String](NAME)

                            if (!cache.isDefined) {
                                println(">>> Cache not found [nodeId=" + grid$.localNode.id +
                                    ", cacheName=" + NAME + ']')

                                "Error"
                            }
                            else
                                cache.get.peek(key)
                        }
                    }
                )

                results += (key -> result)
            })

            results.foreach(e => println(">>> Affinity job result for key '" + e._1 + "': " + e._2))
        }
    }

    /**
     * Populates cache with given keys. This method accounts for the case when
     * cache is not started on local node. In that case a job which populates
     * the cache will be sent to the node where cache is started.
     *
     * @param g Grid.
     * @param keys Keys to populate.
     */
    private def populateCache(g: Grid, keys: Seq[String]) {
        var prj = g.projectionForPredicate(F.cacheNodesForNames(NAME))

        // Give preference to local node.
        if (prj.nodes().contains(g.localNode))
            prj = g.localNode

        // Populate cache on some node (possibly this node) which has cache with given name started.
        prj.run(
            UNICAST,
            (keys: Seq[String]) => {
                println(">>> Storing keys in cache: " + keys)

                val c = cache$[String, String](NAME).get

                keys.foreach(key => c += (key -> key.toLowerCase))
            },
            keys
        )
    }
}

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
import org.gridgain.grid.cache.affinity.*
import org.gridgain.grid.typedef.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*
import static org.gridgain.grid.GridClosureCallMode.*

/**
 * This example works only on <b>Enterprise Edition.</b>
 * <p>
 * Demonstrates how to collocate computations and data in GridGain using
 * direct API calls as opposed to {@link GridCacheAffinityMapped} annotation. This
 * example will first populate cache on some nodes where cache is available, and then
 * will send jobs to the nodes where keys reside and print out values for those keys.
 * <p>
 * Note that for Enterprise Edition affinity routing is enabled for all caches. In
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
class GroverCacheAffinityExample2 {
    /** Configuration file name. */
    //private static final String CONFIG = "examples/config/spring-cache-none.xml" // Enterprise Edition.
    private static final String CONFIG = "examples/config/spring-cache.xml" // Community Edition.

    /** Name of cache specified in spring configuration. */
    private static final String NAME = "partitioned"

    /** Ensure singleton. */
    private GridCacheAffinityExample2() {
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
    static void main(String[] args) throws Exception {
        grover(CONFIG) { Grid g ->
            if (!g.isEnterprise() && g.cache(NAME) == null)
                throw new GridException("This example without having cache '" + NAME + "' started locally " +
                    "works only in Enterprise Edition.")

            def keys = 'A' .. 'Z'

            // Populate cache with keys.
            populateCache(g, keys)

            // Map all keys to nodes. Note that community edition requires that
            // cache with given name is started on this node. Otherwise, use
            // enterprise edition to find out mapping on nodes that don't have
            // cache running.
            def mappings = g.mapKeysToNodes(NAME, keys)

            // If on community edition, we have to get mappings from GridCache
            // directly as affinity mapping without cache started
            // is not supported on community edition.
            if (mappings == null)
                mappings = g.<String, String>cache(NAME).mapKeysToNodes(keys)

            for (Map.Entry<GridRichNode, Collection<String>> mapping : mappings.entrySet()) {
                def node = mapping.key

                def mappedKeys = mapping.value

                if (node != null) {
                    // Bring computations to the nodes where the data resides (i.e. collocation).
                    // Note that this code does not account for node crashes, in which case you
                    // would get an exception and would have to remap the keys assigned to this node.
                    node.run { ->
                        println(">>> Executing affinity job for keys: " + mappedKeys)

                        // Get cache.
                        GridCache<String, String> cache = g.cache(NAME)

                        // If cache is not defined at this point then it means that
                        // job was not routed by affinity.
                        if (cache == null) {
                            println(">>> Cache not found [nodeId=" + g.localNode().id() +
                                ", cacheName=" + NAME + ']')

                            return
                        }

                        // Check cache without loading the value.
                        for (String key : mappedKeys)
                            println(">>> Peeked at: " + cache.peek(key))
                    }
                }
            }
        }
    }

    /**
     * Populates cache with given keys. This method accounts for the case when
     * cache is not started on local node. In that case a job which populates
     * the cache will be sent to the node where cache is started.
     *
     * @param g Grid.
     * @param keys Keys to populate.
     * @throws GridException If failed.
     */
    private static void populateCache(final Grid g, Collection<String> keys) throws GridException {
        GridProjection prj = g.projectionForPredicate(F.cacheNodesForNames(NAME))

        // Give preference to local node.
        if (prj.nodes().contains(g.localNode()))
            prj = g.localNode()

        // Populate cache on some node (possibly this node) which has cache with given name started.
        // Note that CIX1 is a short type alias for GridInClosureX class. If you
        // find it too cryptic, you can use GridInClosureX class directly.
        prj.run$(
            UNICAST,
            { Collection<String> ks ->
                println(">>> Storing keys in cache: " + ks)

                GridCache<String, String> c = g.cache(NAME)

                for (String k : ks)
                    c.put(k, k.toLowerCase())
            },
            keys
        )
    }
}

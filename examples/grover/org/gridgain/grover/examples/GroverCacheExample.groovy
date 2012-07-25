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
import static org.gridgain.grid.GridClosureCallMode.*
import static org.gridgain.grid.GridEventType.*
import org.gridgain.grid.cache.*
import org.gridgain.grid.lang.*
import static org.gridgain.grover.Grover.*

/**
 * Demonstrates basic Data Grid (a.k.a cache) operations with Grover.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
class GroverCacheExample {
    /**
     * Example entry point. No arguments required.
     */
    static void main(String[] args) {
        grover("examples/config/spring-cache.xml") { ->
            registerListener()

            // Create cache predicate-based projection (all values > 30).
            def c = cache$("partitioned").<Integer, Integer>projection(Integer.class, Integer.class).
                projection { Integer k, Integer v -> v < 30 }

            // Add few values.
            c.put(1, 1)
            c.put(2, 2)

            // Update values.
            c.put(1, 11)
            c.put(2, 22)

            // These should be filtered out by projection.
            c.put(1, 31)
            c.put(2, 32)

            // Put one more value.
            c.put(3, 11)

            GridPredicate<GridCacheEntry<Integer, Integer>> gt10 = {
                e -> e.peek() > 10
            }

            // These should pass the predicate.
            // Note that the predicate checks current state of entry, not the new value.
            c.put(3, 9, gt10)

            // These should not pass the predicate
            // because value less then 10 was put on previous step.
            c.put(3, 8, gt10)
            c.put(3, 12, gt10)

            // Print all projection values.
            c.values().each { v ->
                println(v)
            }
        }
    }

    /**
     * This method will register listener for cache events on all nodes,
     * so we can actually see what happens underneath locally and remotely.
     */
    private static def registerListener() {
        def g = grid$

        g.run(BROADCAST) {
            GridLocalEventListener lsnr = { GridEvent e ->
                println(e.shortDisplay())
            }

            if (g.<String, Object>nodeLocal().putIfAbsent("lsnr", lsnr) == null) {
                g.addLocalEventListener(lsnr,
                    EVT_CACHE_OBJECT_PUT,
                    EVT_CACHE_OBJECT_READ,
                    EVT_CACHE_OBJECT_REMOVED)

                println("Listener is registered.")
            }
        }
    }
}

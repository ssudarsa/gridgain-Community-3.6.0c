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
import org.gridgain.grid.cache._
import org.gridgain.grid.GridEventType._
import org.gridgain.grid._
import org.gridgain.grid.GridClosureCallMode._
import collection.JavaConversions._

/**
 * Demonstrates basic Data Grid (a.k.a cache) operations with Scalar.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarCacheExample {
    /**
     * Example entry point. No arguments required.
     */
    def main(args: Array[String]) {
        scalar("examples/config/spring-cache.xml") {
            registerListener()

            basicOperations()
            twoViewsOneCache()
        }
    }

    /**
     * Demos basic cache operations.
     */
    def basicOperations() {
        // Create cache predicate-based projection (all values > 30).
        val c = cache$("partitioned").get.viewByType(classOf[Int], classOf[Int]).
            viewByKv((k: Int, v: Int) => v < 30)

        // Add few values.
        c += (1 -> 1)
        c += (2 -> 2)

        // Update values.
        c += (1 -> 11)
        c += (2 -> 22)

        // These should be filtered out by projection.
        c += (1 -> 31)
        c += (2 -> 32)
        c += ((2, 32))

        // Remove couple of keys (if any).
        c -= (11, 22)

        // Put one more value.
        c += (3 -> 11)

        val gt10 = (e: GridCacheEntry[Int, Int]) => e.peek() > 10

        // These should pass the predicate.
        // Note that the predicate checks current state of entry, not the new value.
        c += (3 -> 9, gt10)

        // These should not pass the predicate
        // because value less then 10 was put on previous step.
        c += (3 -> 8, gt10)
        c += (3 -> 12, gt10)

        // Get with option...
        c.opt(44) match {
            case Some(v) => sys.error("Should never happen.")
            case None => println("Correct")
        }

        // Print all projection values.
        c.values foreach println
    }

    /**
     * Demos basic type projections.
     */
    def twoViewsOneCache() {
        // Create two typed views on the same cache.
        val view1 = cache$("partitioned").get.viewByType(classOf[String], classOf[Int])
        val view2 = cache$("partitioned").get.viewByType(classOf[Int], classOf[String])

        view1 += ("key1" -> 1)
        view1 += ("key2" -> 2)

        // Attempt to update with predicate (will not update due to predicate failing).
        view1 += ("key2" -> 3, (k: String, v: Int) => v != 2)

        view2 += (1 -> "val1")
        view2 += (2 -> "val2")

        println("Values in view1:")
        view1.values foreach println
        println("view1 size is: " + view1.size)

        println("Values in view2:")
        view2.values foreach println
        println("view2 size is: " + view2.size)
    }

    /**
     * This method will register listener for cache events on all nodes,
     * so we can actually see what happens underneath locally and remotely.
     */
    def registerListener() {
        val g = grid$

        g *< (BROADCAST, () => {
            val lsnr = new GridLocalEventListener {
                def onEvent(e: GridEvent) {
                    println(e.shortDisplay)
                }
            }

            if (g.nodeLocal[String, AnyRef].putIfAbsent("lsnr", lsnr) == null) {
                g.addLocalEventListener(lsnr,
                    EVT_CACHE_OBJECT_PUT,
                    EVT_CACHE_OBJECT_READ,
                    EVT_CACHE_OBJECT_REMOVED)

                println("Listener is registered.")
            }
        })
    }
}

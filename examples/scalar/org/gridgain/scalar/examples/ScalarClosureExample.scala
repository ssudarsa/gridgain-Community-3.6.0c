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
import org.gridgain.grid.lang.{GridFunc => F}
import org.gridgain.grid.GridClosureCallMode._
import org.gridgain.grid._

/**
 * Demonstrates various closure executions on the cloud using Scalar.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarClosureExample {
    /**
     * Example entry point. No arguments required.
     */
    def main(args: Array[String]) {
        scalar {
            topology()
            helloWorld()
            broadcast()
            unicast()
            println("Count of non-whitespace is: " + count("Scalar is cool!"))
            greetRemotes()
            greetRemotesAgain()
        }
    }

    /**
     * Prints grid topology.
     */
    def topology() {
        grid$ foreach (n => println("Node: " + n.id8))
    }

    /**
     *  Obligatory example - cloud enabled Hello World! 
     */
    def helloWorld() {
        // Notice the example usage of Java-side closure 'F.println(...)' and method 'scala'
        // that explicitly converts Java side object to a proper Scala counterpart.
        // This method is required since implicit conversion won't be applied here.
        grid$ run$ (SPREAD, for (w <- "Hello World!".split(" ")) yield F.println(w).scala)
    }

    /**
     * One way to execute closures on the grid.
     */
    def broadcast() {
        grid$ *< (BROADCAST, () => println("Broadcasting!!!"))
    }

    /**
     * One way to execute closures on the grid.
     */
    def unicast() {
        // Note Java-based closure usage (implicit conversion will apply).
        grid$.localNode *< (UNICAST, F.println("Howdy!"))
    }

    /**
     * Count non-whitespace characters by spreading workload to the cloud and reducing
     * on the local node.
     */
    // Same as 'count2' but with for-expression.
    def count(msg: String): Int =
        grid$ reduce$ (SPREAD, for (w <- msg.split(" ")) yield () => w.length, (_: Seq[Int]).sum)

    /**
     * Count non-whitespace characters by spreading workload to the cloud and reducing
     * on the local node.
     */
    // Same as 'count' but without for-expression.
    // Note that map's parameter type inference doesn't work in 2.9.0.
    def count2(msg: String): Int =
        grid$ @< (SPREAD, msg.split(" ") map ((s: String) => () => s.length), (_: Seq[Int]).sum)

    /**
     *  Greats all remote nodes only.
     */
    def greetRemotes() {
        val me = grid$.localNode.id

        // Note that usage Java-based closure.
        grid$.remoteProjection() match {
            case p if p.isEmpty => println("No remote nodes!")
            case p => p *< (BROADCAST, F.println("Greetings from: " + me))
        }
    }

    /**
     * Same as previous greetings for all remote nodes but remote projection is created manually.
     */
    def greetRemotesAgain() {
        val me = grid$.localNode.id

        // Just show that we can create any projections we like...
        // Note that usage of Java-based closure via 'F' typedef.
        grid$.projectionForPredicate((n: GridRichNode) => n.id != me) match {
            case p if p.isEmpty => println("No remote nodes!")
            case p => p *< (BROADCAST, F.println("Greetings again from: " + me))
        }
    }
}

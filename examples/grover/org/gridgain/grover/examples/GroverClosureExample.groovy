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
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*

/**
 * Demonstrates various closure executions on the cloud using Grover.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
@Use(GroverProjectionCategory)
class GroverClosureExample {
    /**
     * Example entry point. No arguments required.
     */
    static void main(String[] args) {
        grover { ->
            topology()
            helloWorld()
            broadcast()
            unicast()
            println("Count of non-whitespace is: " + count("Grover is cool!"))
            greetRemotes()
            greetRemotesAgain()
        }
    }

    /**
     * Prints grid topology.
     */
    static topology() {
        grid$.each { println("Node: " + it.id8()) }
    }

    /**
     *  Obligatory example - cloud enabled Hello World!
     */
    static helloWorld() {
        grid$.run(SPREAD, "Hello World!".split(" ").collect { { -> println(it) } } )
    }

    /**
     * One way to execute closures on the grid.
     */
    static broadcast() {
        grid$.run(BROADCAST) { println("Broadcasting!!!") }
    }

    /**
     * One way to execute closures on the grid.
     */
    static unicast() {
        grid$.localNode().run(UNICAST) { println("Howdy!") }
    }

    /**
     * Count non-whitespace characters by spreading workload to the cloud and reducing
     * on the local node.
     */
    static count(String msg) {
        grid$.reduce$(
            SPREAD,
            msg.split(" ").collect { { -> it.length() } },
            { it.sum() }
        )
    }

    /**
     *  Greats all remote nodes only.
     */
    static greetRemotes() {
        def me = grid$.localNode().id()

        grid$.remoteProjection().runSafe(
            BROADCAST,
            { println("Greetings from: " + me) },
            { println("No remote nodes!") }
        )
    }

    /**
     * Same as previous greetings for all remote nodes but remote projection is created manually.
     */
    static greetRemotesAgain() {
        def me = grid$.localNode().id()

        def p = grid$.projectionForPredicate { GridRichNode n -> n.id() != me }

        p.runSafe(
            BROADCAST,
            { println("Greetings from: " + me) },
            { println("No remote nodes!") }
        )
    }
}

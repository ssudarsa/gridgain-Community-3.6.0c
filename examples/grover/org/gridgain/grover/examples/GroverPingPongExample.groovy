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

import java.util.concurrent.*
import org.gridgain.grid.*
import static org.gridgain.grover.Grover.*
import static org.gridgain.grid.GridClosureCallMode.UNICAST

/**
 * Demonstrates simple protocol-based exchange in playing a ping-pong between
 * two nodes. It is analogous to {@code GridMessagingPingPongExample} on Java side.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
class GroverPingPongExample {
    static void main(String[] args) {
        grover { ->
            pingPong()
//            pingPong2()
        }
    }

    /**
     * Implements Ping Pong example between local and remote node.
     */
    static void pingPong() {
        def g = grid$

        if (g.nodes().size() < 2) {
            System.err << "I need a partner to play a ping pong!"

            return
        }

        // Pick first remote node as a partner.
        def loc = g.localNode()
        def rmt = g.remoteNodes().asList().first()

        // Set up remote player: configure remote node 'rmt' to listen
        // for messages from local node 'loc'.
        rmt.remoteListenAsync(loc, new GridListenActor<String>() {
            @Override void receive(UUID nodeId, String msg) {
                println(msg)

                switch(msg) {
                    case "PING":
                        respond("PONG")
                        break
                    case "STOP":
                        stop()
                }
            }
        }).get()

        def latch = new CountDownLatch(10)

        // Set up local player: configure local node 'loc'
        // to listen for messages from remote node 'rmt'.
        rmt.listen(new GridListenActor<String>() {
            @Override void receive(UUID nodeId, String msg) {
                println(msg)

                if (latch.getCount() == 1)
                    stop("STOP")
                else // We know it's 'PONG'.
                    respond("PING")

                latch.countDown()
            }
        })

        // Serve!
        rmt.send("PING")

        // Wait til the match is over.
        latch.await()
    }

    /**
     * Implements Ping Pong example between two remote nodes.
     */
    static void pingPong2() {
        def g = grid$

        if (g.remoteNodes().size() < 2) {
            System.err << "I need at least two remote nodes!"

            return
        }

        def nodes = g.remoteNodes().asList()

        // Pick two remote nodes.
        def n1 = nodes.first()
        def n2 = nodes.tail().first()

        // Configure remote node 'n1' to receive messages from 'n2'.
        n1.remoteListenAsync(n2, new GridListenActor<String>() {
            @Override void receive(UUID nid, String msg) {
                println(msg)

                switch(msg) {
                    case "PING":
                        respond("PONG")
                        break
                    case "STOP":
                        stop()
                }
            }
        }).get()

        // Configure remote node 'n2' to receive messages from 'n1'.
        n2.remoteListenAsync(n1, new GridListenActor<String>() {
            // Get local count down latch.
            @Override void receive(UUID nid, String msg) {
                println(msg)

                CountDownLatch latch = g.<String, CountDownLatch>nodeLocal().get("latch")

                switch(latch.count) {
                    case 1:
                        stop("STOP")
                        break
                    default:
                        respond("PING")
                }

                latch.countDown()
            }
        }).get()


        // 1. Sets latch into node local storage so that local actor could use it.
        // 2. Sends first 'PING' to 'n1'.
        // 3. Waits until all messages are exchanged between two remote nodes.
        n2.run(UNICAST) {
            def latch = new CountDownLatch(10)

            g.<String, CountDownLatch>nodeLocal().put("latch", latch)

            println("Latch set: ${g.nodeLocal().get("latch")}")

            n1.send("PING")

            latch.await()
        }
    }
}

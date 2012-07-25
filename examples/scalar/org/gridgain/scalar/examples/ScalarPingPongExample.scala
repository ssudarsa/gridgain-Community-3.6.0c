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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import org.gridgain.grid._
import GridClosureCallMode._

/**
 * Demonstrates simple protocol-based exchange in playing a ping-pong between
 * two nodes. It is analogous to `GridMessagingPingPongExample` on Java side.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarPingPongExample {
    def main(args: Array[String]) {
        scalar {
            pingPong()
            //pingPong2()
        }
    }

    /**
     * Implements Ping Pong example between local and remote node.
     */
    def pingPong() {
        val g = grid$

        if (g.nodes().size < 2)
            sys.error("I need a partner to play a ping pong!")
        else {
            // Pick first remote node as a partner.
            val loc = g.localNode
            val rmt = g.remoteNodes$().head

            // Set up remote player: configure remote node 'rmt' to listen
            // for messages from local node 'loc'.
            rmt.remoteListenAsync(loc, new GridListenActor[String]() {
                def receive(nodeId: UUID, msg: String) {
                    println(msg)

                    msg match {
                        case "PING" => respond("PONG")
                        case "STOP" => stop()
                    }
                }
            }).get

            val latch = new CountDownLatch(10)

            // Set up local player: configure local node 'loc'
            // to listen for messages from remote node 'rmt'.
            rmt listen new GridListenActor[String]() {
                def receive(nodeId: UUID, msg: String) {
                    println(msg)

                    if (latch.getCount == 1)
                        stop("STOP")
                    else // We know it's 'PONG'.
                        respond("PING")

                    latch.countDown()
                }
            }

            // Serve!
            rmt !< "PING"

            // Wait til the match is over.
            latch.await()
        }
    }

    /**
     * Implements Ping Pong example between two remote nodes.
     */
    def pingPong2() {
        val g = grid$

        if (g.remoteNodes().size() < 2)
            sys.error("I need at least two remote nodes!")
        else {
            // Pick two remote nodes.
            val n1 = g.remoteNodes$().head
            val n2 = g.remoteNodes$().tail.head

            // Configure remote node 'n1' to receive messages from 'n2'.
            n1.remoteListenAsync(n2, new GridListenActor[String] {
                def receive(nid: UUID, msg: String) {
                    println(msg)

                    msg match {
                        case "PING" => respond("PONG")
                        case "STOP" => stop()
                    }
                }
            }).get

            // Configure remote node 'n2' to receive messages from 'n1'.
            n2.remoteListenAsync(n1, new GridListenActor[String] {
                // Get local count down latch.
                private lazy val latch: CountDownLatch = g.nodeLocal.get("latch")

                def receive(nid: UUID, msg: String) {
                    println(msg)

                    latch.getCount match {
                        case 1 => stop("STOP")
                        case _ => respond("PING")
                    }

                    latch.countDown()
                }
            }).get


            // 1. Sets latch into node local storage so that local actor could use it.
            // 2. Sends first 'PING' to 'n1'.
            // 3. Waits until all messages are exchanged between two remote nodes.
            n2 *< (UNICAST, () => {
                val latch = new CountDownLatch(10)

                g.nodeLocal[String, CountDownLatch].put("latch", latch)

                n1 !< "PING"

                latch.await()
            })
        }
    }
}

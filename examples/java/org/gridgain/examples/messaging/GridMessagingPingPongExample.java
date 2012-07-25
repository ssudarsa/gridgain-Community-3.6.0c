// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.messaging;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Demonstrates various messaging APIs.
 * <p>
 * <h1 class="header">Starting Remote Nodes</h1>
 * To try this example you need to start at least one remote grid instance.
 * You can start as many as you like by executing the following script:
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh}</pre>
 * Once remote instances are started, you can execute this example from
 * Eclipse, IntelliJ IDEA, or NetBeans (and any other Java IDE) by simply hitting run
 * button. You will see that all nodes discover each other and
 * some of the nodes will participate in task execution (check node
 * output).
 * <p>
 * <h1 class="header">XML Configuration</h1>
 * If no specific configuration is provided, GridGain will start with
 * all defaults. For information about GridGain default configuration
 * refer to {@link GridFactory} documentation. If you would like to
 * try out different configurations you should pass a path to Spring
 * configuration file as 1st command line argument into this example.
 * The path can be relative to {@code GRIDGAIN_HOME} environment variable.
 * You should also pass the same configuration file to all other
 * grid nodes by executing startup script as follows (you will need
 * to change the actual file name):
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh} examples/config/specific-config-file.xml</pre>
 * <p>
 * GridGain examples come with multiple configuration files you can try.
 * All configuration files are located under {@code GRIDGAIN_HOME/examples/config}
 * folder.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridMessagingPingPongExample {
    /**
     * Enforces singleton.
     */
    private GridMessagingPingPongExample() {
        // No-op.
    }

    /**
     * This example demonstrates simple protocol-based exchange in playing a ping-pong between
     * two nodes.
     *
     * @param args Command line arguments (none required).
     * @throws GridException Thrown in case of any errors.
     */
    public static void main(String[] args) throws GridException {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        // Game is played over the default grid.
        G.in(args.length == 0 ? null : args[0], new CIX1<Grid>() {
            @Override public void applyx(Grid g) throws GridException {
                if (g.nodes().size() < 2) {
                    System.err.println("I need a partner to play a ping pong!");

                    return;
                }

                // Pick random remote node as a partner.
                GridRichNode nodeA = g.localNode();
                GridRichNode nodeB = F.rand(g.remoteNodes());

                // Note that both nodeA and nodeB will always point to
                // same nodes regardless of whether they were implicitly
                // serialized and deserialized on another node as part of
                // anonymous closure's state during its remote execution.

                // Set up remote player.
                nodeB.remoteListenAsync(nodeA, new GridListenActor<String>() {
                    @Override public void receive(UUID nodeId, String msg) throws GridException {
                        X.println(msg);

                        if ("PING".equals(msg))
                            respond("PONG");
                        else if ("STOP".equals(msg))
                            stop();
                    }
                }).get();

                int MAX_PLAYS = 10;

                final CountDownLatch cnt = new CountDownLatch(MAX_PLAYS);

                // Set up local player.
                nodeB.listen(new GridListenActor<String>() {
                    @Override protected void receive(UUID nodeId, String msg) throws GridException {
                        X.println(msg);

                        if (cnt.getCount() == 1)
                            stop("STOP");
                        else if ("PONG".equals(msg))
                            respond("PING");

                        cnt.countDown();
                    }
                });

                // Serve!
                nodeB.send("PING");

                // Wait til the game is over.
                try {
                    cnt.await();
                }
                catch (InterruptedException e) {
                    System.err.println("Hm... let us finish the game!\n" + e);
                }
            }
        });
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.messaging;

import org.gridgain.examples.helloworld.gridify.session.*;
import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Example that demonstrates how to exchange messages between nodes. Use such
 * functionality for cases when you need to communicate to other nodes outside
 * of grid task. In such cases all your message classes must be in system
 * class path as they will not be peer-loaded.
 * <p>
 * Note that in most cases you will usually have to exchange state within
 * task execution scope, between task and the jobs it spawned. In such case
 * you would use {@link GridTaskSession} to distribute state. See
 * {@link GridifyHelloWorldSessionExample} for example on how to use
 * task session.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridMessagingExample {
    /**
     * Ensure singleton.
     */
    private GridMessagingExample() {
        // No-op.
    }

    /**
     * Sends a sample message to local node and receives it via listener.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws Exception If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            G.start();
        }
        else {
            G.start(args[0]);
        }

        try {
            Grid grid = G.grid();

            grid.listen(new P2<UUID, Object>() {
                @Override public boolean apply(UUID nodeId, Object msg) {
                    X.println(">>> Received new message [msg=" + msg + ", originatingNodeId=" + nodeId + ']');

                    return !"stop".equals(msg);
                }
            });

            GridRichNode loc = grid.localNode();

            // Send messages to local node.
            loc.send("MSG-1");
            loc.send("MSG-2");
            loc.send("MSG-3");

            // Allow time for messages to be received.
            Thread.sleep(1000);

            loc.send("stop"); // Unregister listener.

            // Allow time for messages to be received.
            Thread.sleep(1000);

            X.println(">>>");
            X.println(">>> Finished executing Grid Messaging Example.");
            X.println(">>> Check local node output.");
            X.println(">>>");
        }
        finally {
            G.stop(true);
        }
    }
}

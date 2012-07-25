// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.multispi;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import org.springframework.context.support.*;

/**
 * Demonstrates a simple use of GridGain multiple topology SPI feature.
 * <p>
 * Two tasks {@link GridSegmentATask} and {@link GridSegmentBTask}
 * configured to be executed on certain nodes. {@link GridSegmentATask} should
 * be executed on nodes that have attribute {@code segment} with value {@code A}
 * and {@link GridSegmentBTask} should be executed on nodes that have attribute
 * {@code segment} with value {@code B}.
 * <p>
 * The node attributes are configured in {@code nodeA.xml} and {@code nodeB.xml}
 * files located in the same package. {@code NodeA} is configured with attribute
 * {@code segmentA}, and {@code NodeB} is configured with attribute {@code segmentB}.
 * <p>
 * The {@code master.xml} configuration represents the master node. It has two
 * Topology SPIs configured: one includes only nodes with {@code SegmentA} attribute
 * and the other includes only nodes with {@code SegmentB} attribute. The
 * {@link GridSegmentATask} and {@link GridSegmentBTask} tasks specify which Topology
 * SPI they should use via {@link GridTaskSpis} annotation. Once you run this example,
 * the master node with provided {@code master.xml} configuration will be started.
 * <p>
 * <h1 class="header">Starting Remote Nodes</h1>
 * To try this example you have to start two remote grid instances.
 * One should be started with configuration {@code nodeA.xml} that can be found
 * in the same package where example is. Another node should be started
 * with {@code nodeB.xml} configuration file. Here is an example of
 * starting node with {@code nodeA.xml} configuration file.
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh} /path_to_configuration/nodeA.xml</pre>
 * Once remote instances are started, you can execute this example from
 * Eclipse, IntelliJ IDEA, or NetBeans (and any other Java IDE) by simply hitting run
 * button. You will see that all nodes discover each other and
 * some of the nodes will participate in task execution (check node
 * output).
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridMultipleTopologyExample {
    /**
     * Ensure singleton.
     */
    private GridMultipleTopologyExample() {
        // No-op.
    }

    /**
     * Executes {@link GridSegmentATask} and {@link GridSegmentBTask} tasks on the grid.
     *
     * @param args Command line arguments, none required or used.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        AbstractApplicationContext ctx =
            new ClassPathXmlApplicationContext("org/gridgain/examples/multispi/master.xml");

        // Get configuration from Spring.
        GridConfiguration cfg = (GridConfiguration)ctx.getBean("grid.cfg", GridConfiguration.class);

        G.start(cfg);

        try {
            Grid grid = G.grid();

            // Execute task on segment "A".
            GridTaskFuture<Integer> futA = grid.execute(GridSegmentATask.class, null);

            // Execute task on segment "B".
            GridTaskFuture<Integer> futB = grid.execute(GridSegmentBTask.class, null);

            // Wait for task completion.
            futA.get();
            futB.get();

            X.println(">>>");
            X.println(">>> Finished executing Grid \"Multiple Topology\" example with custom tasks.");
            X.println(">>> You should see print out of 'Executing job on node that is from segment A.'" +
                "on node that has attribute \"segment=A\"");
            X.println(">>> and 'Executing job on node that is from segment B.' on node that has " +
                "attribute \"segment=B\"");
            X.println(">>> Check all nodes for output (this node is not a part of the grid).");
            X.println(">>>");
        }
        finally {
            G.stop(true);
        }
    }
}

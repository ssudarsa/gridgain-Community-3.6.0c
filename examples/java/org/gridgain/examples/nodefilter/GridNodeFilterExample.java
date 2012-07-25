// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.nodefilter;

import org.gridgain.examples.multispi.*;
import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.spi.topology.nodefilter.*;
import java.io.*;
import java.util.*;

/**
 * Demonstrates use of node filter API.
 * <p>
 * Two different node instances are started in this example, and only one has
 * user attribute defined. Then we are using node filter API to receive only one node
 * with right user attribute.
 * <p>
 * For example of how to use node filter for filter-based
 * {@link GridNodeFilterTopologySpi}
 * refer to {@link GridMultipleTopologyExample} documentation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridNodeFilterExample {
    /**
     * Ensure singleton.
     */
    private GridNodeFilterExample() {
        // No-op.
    }

    /**
     * Execute {@code Node Filter} example on the grid.
     *
     * @param args Command line arguments, none required.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        try {
            // Start first grid instance.
            Grid grid = G.start();

            // Create custom configuration for another grid instance.
            GridConfigurationAdapter cfg = new GridConfigurationAdapter();

            // Map to store user attributes.
            Map<String, Serializable> attrs = new HashMap<String, Serializable>();

            // Add some attribute to have ability to find this node later.
            attrs.put("custom_attribute", "custom_value");

            // Put created attributes to configuration.
            cfg.setUserAttributes(attrs);

            // Define another grid name to have ability to start two grid instance in one JVM.
            cfg.setGridName("OneMoreGrid");

            // Start second grid instance.
            G.start(cfg);

            // Now, when we have two grid instance, we want to find only second one,
            // that has user attribute defined.

            Collection<GridRichNode> nodes = grid.nodes(
                new GridJexlPredicate<GridRichNode>(
                    "node.attributes().get('custom_attribute') == 'custom_value'", "node"));

            // All available nodes.
            Collection<GridRichNode> allNodes = grid.nodes();

            if (nodes.size() == 1) {
                X.println(">>>");
                X.println(">>> Finished executing Grid \"Node Filter\" example.");
                X.println(">>> Total number of found nodes is " + nodes.size() + " from " + allNodes.size()
                    + " available.");
                X.println(">>> We found only one node that has necessary attribute defined.");
                X.println(">>>");
            }
            else {
                throw new GridException("Found " + nodes.size() + " nodes by node filter, but only 1 was expected.");
            }
        }
        finally {
            G.stop(true);
            G.stop("OneMoreGrid", true);
        }
    }
}

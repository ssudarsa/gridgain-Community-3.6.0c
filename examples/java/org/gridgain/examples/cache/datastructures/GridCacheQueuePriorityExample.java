// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache.datastructures;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.GridClosureCallMode.*;
import static org.gridgain.grid.cache.datastructures.GridCacheQueueType.*;

/**
 * Grid cache distributed queue example. This example demonstrates {@code PRIORITY}
 * unbounded cache queue. Note that distributed queue is only available in <b>Enterprise Edition</b>.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache: {@code 'ggstart.sh examples/config/spring-cache.xml'}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheQueuePriorityExample {
    /** Cache name. */
    //private static final String CACHE_NAME = "replicated";
    private static final String CACHE_NAME = "partitioned";

    /** Number of retries */
    private static final int RETRIES = 5;

    /** Grid instance. */
    private static Grid grid;

    /** Queue instance. */
    private static GridCacheQueue<SampleItem> queue;

    /** Ensure singleton. */
    private GridCacheQueuePriorityExample() { /* No-op. */ }

    /**
     * Executes this example on the grid.
     *
     * @param args Command line arguments, none required.
     * @throws Exception If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        grid = G.start("examples/config/spring-cache.xml");

        try {
            if (!grid.isEnterprise()) {
                System.err.println("This example works only in Enterprise Edition.");

                return;
            }

            print("Priority queue example started on nodes: " + grid.nodes().size());

            // Make queue name.
            String queueName = UUID.randomUUID().toString();

            queue = initializeQueue(queueName);

            readFromQueue();

            writeToQueue();

            clearAndRemoveQueue();
        }
        finally {
            GridFactory.stop(true);
        }

        print("Priority queue example finished.");
    }

    /**
     * Initialize queue.
     *
     * @param queueName Name of queue.
     * @return Queue.
     * @throws GridException If execution failed.
     */
    private static GridCacheQueue<SampleItem> initializeQueue(String queueName) throws GridException {
        // Initialize new priority queue.
        GridCacheQueue<SampleItem> queue = grid.cache(CACHE_NAME).queue(queueName, PRIORITY);

        // Initialize queue items.
        // We will be use blocking operation and queue size must be appropriated.
        for (int i = 0; i < grid.nodes().size() * RETRIES * 2; i++)
            queue.put(new SampleItem(i, grid.localNode().id().toString()));

        print("Queue size after initializing: " + queue.size());

        return queue;
    }

    /**
     * Read items from head and tail of queue.
     *
     * @throws GridException If execution failed.
     */
    private static void readFromQueue() throws GridException {
        final String queueName = queue.name();

        // Read queue items on each node.
        grid.run(BROADCAST, new CAX() {
            @Override public void applyx() throws GridException {
                GridCacheQueue<SampleItem> queue = G.grid().cache(CACHE_NAME).queue(queueName);

                // Take items from queue head.
                for (int i = 0; i < RETRIES; i++)
                    print("Queue item has been read from queue head: " + queue.poll().toString());

                // Take items from queue tail.
                for (int i = 0; i < RETRIES; i++)
                    print("Queue item has been read from queue tail: " + queue.pollLast());
            }
        });

        print("Queue size after reading [expected=0, actual=" + queue.size() + ']');
    }

    /**
     * Write items into queue.
     *
     * @throws GridException If execution failed.
     */
    private static void writeToQueue() throws GridException {
        final String queueName = queue.name();

        // Write queue items on each node.
        grid.run(BROADCAST, new CAX() {
            // IDEA doesn't understand distributed queries yet :)
            @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
            @Override public void applyx() throws GridException {
                GridCacheQueue<SampleItem> queue = G.grid().cache(CACHE_NAME).queue(queueName);

                for (int i = 0; i < RETRIES; i++) {
                    String item = G.grid().localNode().id() + "_" + Integer.toString(i);

                    queue.put(new SampleItem(i, G.grid().localNode().id().toString()));

                    print("Queue item has been added: " + item);
                }
            }
        });

        print("Queue size after writing [expected=" + grid.nodes().size() * RETRIES +
            ", actual=" + queue.size() + ']');

        print("Iterate over queue.");

        // Iterate over queue.
        for (SampleItem item : queue)
            print("Queue item: " + item);
    }

    /**
     * Clear and remove queue.
     *
     * @throws GridException If execution failed.
     */
    private static void clearAndRemoveQueue() throws GridException {
        print("Queue size before clearing: " + queue.size());

        // Clear queue.
        queue.clear();

        print("Queue size after clearing: " + queue.size());

        // Remove queue from cache.
        grid.cache(CACHE_NAME).removeQueue(queue.name());

        // Try to work with removed queue.
        try {
            queue.get();
        }
        catch (GridException expected) {
            print("Expected exception - " + expected.getMessage());
        }
    }

    /**
     * Prints out given object to standard out.
     *
     * @param o Object to print.
     */
    private static void print(Object o) {
        X.println(">>> " + o);
    }

    /**
     * Queue item class with priority field.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class SampleItem implements Serializable {
        /** Priority field*/
        @GridCacheQueuePriority
        private final int priority;

        /** Node id */
        private final String nodeId;

        /**
         * @param priority Item priority.
         * @param nodeId Id of node where object was instantiated.
         */
        private SampleItem(int priority, String nodeId) {
            this.priority = priority;
            this.nodeId = nodeId;
        }

        /**
         * @return Priority.
         */
        int priority() {
            return priority;
        }

        /**
         * @return Node id.
         */
        String nodeId() {
            return nodeId;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SampleItem.class, this);
        }
    }
}

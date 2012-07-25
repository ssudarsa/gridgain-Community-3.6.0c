// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.rich;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Rich entity processor.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridRichProcessor extends GridProcessorAdapter {
    /** */
    private static final long SLEEP_INTERVAL = 2 * 60 * 1000;

    /** Rich node cache. */
    private ConcurrentMap<UUID, GridRichNode> nodeCache = new ConcurrentHashMap<UUID, GridRichNode>();

    /** Cache cleaning worker. */
    private GcWorker gcWorker = new GcWorker();

    /** GC thread. */
    private GridThread gcThread;

    /**
     * Base class for rich processor closures that takes care about correct externalization.
     *
     * @param <T> Type of object to be converted.
     * @param <R> Type of result object.
     */
    private abstract static class RichClosure<T, R> extends C1<T, R> implements Externalizable {
        /** */
        protected transient GridRichProcessor proc;

        /**
         * Required by {@link Externalizable} contract.
         */
        protected RichClosure() {
            // No-op.
        }

        /**
         * @param proc Rich processor.
         */
        protected RichClosure(GridRichProcessor proc) {
            this.proc = proc;
        }

        /** @{inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            U.writeString(out, proc.ctx.gridName());
        }

        /** @{inheritDoc} */
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            proc = ((GridKernal)G.grid(U.readString(in))).context().rich();
        }
    }

    /**
     * Converting closure for grid node.
     */
    private static class RichNodeClosure extends RichClosure<GridNode, GridRichNode> {
        /**
         * Required by {@link Externalizable} contract.
         */
        public RichNodeClosure() {
            // No-op.
        }

        /**
         * @param proc Rich processor.
         */
        RichNodeClosure(GridRichProcessor proc) {
            super(proc);
        }

        /** @{inheritDoc} */
        @Override @Nullable public GridRichNode apply(GridNode node) {
            return proc.rich(node);
        }
    }

    /** */
    private final GridClosure<GridNode, GridRichNode> richNodeClosure = new RichNodeClosure(this);

    /**
     * @param ctx Kernal context.
     */
    public GridRichProcessor(GridKernalContext ctx) {
        super(ctx);
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        gcThread = new GridThread(ctx.config().getGridName(), "rich-processor-gc-worker", gcWorker);

        gcThread.start();

        if (log.isDebugEnabled())
            log.debug("Started rich processor.");
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        // Stop and wait cache cleaning worker.
        U.interrupt(gcThread);

        U.join(gcThread, log);

        gcThread = null;

        if (log.isDebugEnabled())
            log.debug("Stopped rich processor.");
    }

    /**
     * Gets converting closure from grid node to rich node.
     *
     * @return Converting closure from grid node to rich node.
     */
    public GridClosure<GridNode, GridRichNode> richNode() {
        return richNodeClosure;
    }

    /**
     * Converts node into rich node.
     *
     * @param node Grid node to convert.
     * @return Rich node.
     */
    @Nullable public GridRichNode rich(@Nullable GridNode node) {
        if (node == null)
            return null;

        if (node instanceof GridRichNode)
            return (GridRichNode)node;

        UUID id = node.id();

        GridRichNode rich = nodeCache.get(id);

        if (rich == null) {
            GridRichNode cur = nodeCache.putIfAbsent(id, rich = new GridRichNodeImpl(ctx, node));

            if (cur != null)
                rich = cur;
        }

        return rich;
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        X.println(">>>");
        X.println(">>> Rich processor memory stats [grid=" + ctx.gridName() + ']');
        X.println(">>>  nodeCacheSize: " + nodeCache.size());
    }

    /**
     * Worker for cleaning caches from stale objects.
     */
    private class GcWorker extends GridWorker {
        /** Constructs worker. */
        private GcWorker() {
            super(ctx.config().getGridName(), "rich-processor-gc-worker", log);
        }

        /** {@inheritDoc} */
        @SuppressWarnings( {"BusyWait"})
        @Override protected void body() throws InterruptedException {
            while (!isCancelled()) {
                cleanNodeCache();

                Thread.sleep(SLEEP_INTERVAL);
            }
        }

        /**
         * Clean cache of nodes (remove stale objects).
         */
        private void cleanNodeCache() {
            for (Iterator<Map.Entry<UUID, GridRichNode>> it = nodeCache.entrySet().iterator(); it.hasNext();)
                if (isStaleNode(it.next().getKey()))
                    it.remove();
        }

        /**
         * Checks if node is stale.
         *
         * @param nodeId Node id.
         * @return {@code true} if stale, {@code false} otherwise.
         */
        private boolean isStaleNode(UUID nodeId) {
            assert nodeId != null;

            return ctx.discovery().node(nodeId) == null;
        }
    }
}

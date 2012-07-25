// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.nodestart;

import org.gridgain.grid.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Host group starter.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridHostRunnable implements Runnable {
    /** Thread pool. */
    private final ExecutorService pool;

    /** Node starters. */
    private final Collection<GridNodeRunnable> nodeRuns;

    /** Maximum number of nodes to start in parallel. */
    private final int maxNodes;

    /**
     * Constructor.
     *
     * @param pool Thread pool.
     * @param nodeRuns Node starters.
     * @param maxNodes Maximum number of nodes to start in parallel.
     */
    public GridHostRunnable(ExecutorService pool, Collection<GridNodeRunnable> nodeRuns, int maxNodes) {
        assert pool != null;
        assert nodeRuns != null;
        assert maxNodes > 0;

        this.pool = pool;
        this.nodeRuns = nodeRuns;
        this.maxNodes = maxNodes;
    }

    /** {@inheritDoc} */
    @Override public void run() {
        Collection<Future<?>> futs = new ArrayList<Future<?>>(maxNodes);

        try {
            for (GridNodeRunnable run : nodeRuns) {
                futs.add(pool.submit(run));

                if (futs.size() == maxNodes) {
                    for (Future<?> fut : futs)
                        fut.get();

                    futs.clear();
                }
            }

            if (!futs.isEmpty())
                for (Future<?> fut : futs)
                    fut.get();
        }
        catch (Exception e) {
            throw new GridRuntimeException(e);
        }
    }
}

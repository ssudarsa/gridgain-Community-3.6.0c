// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.multispi;

import org.gridgain.grid.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;

/**
 * This class defines grid task for this example. Grid task is responsible for
 * splitting the task into jobs. This particular implementation creates single job
 * that later should be executed on node from segment "A" according to the topology
 * assigned to the task.
 * <p>
 * This task explicitly specifies that it should use Topology SPI named
 * {@code 'topologyA'} via {@link GridTaskSpis} annotation attached to
 * the task class definition.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridTaskSpis(topologySpi="topologyA")
public class GridSegmentATask extends GridTaskSplitAdapter<String, Integer> {
    /**
     * Creates single job that should be executed on node from segment "A".
     *
     * @param gridSize Number of nodes in the grid.
     * @param arg Any string.
     * @return Created grid jobs for remote execution.
     * @throws GridException If split failed.
     */
    @Override public Collection<? extends GridJob> split(int gridSize, String arg) throws GridException {
        return Collections.singletonList(new GridJobAdapterEx() {
            /** Injected grid instance. */
            @GridInstanceResource
            private Grid grid;

            /*
             * Simply checks that node where job is being executed is from segment "A"
             * and prints message that node is really from expected segment.
             */
            @Nullable
            @Override public Serializable execute() throws GridException {
                assert grid != null;

                String segVal = (String)grid.localNode().attribute("segment");

                if (segVal == null || !"A".equals(segVal))
                    throw new GridException("Wrong node \"segment\" attribute value. Expected \"A\" got " + segVal);

                X.println(">>>");
                X.println(">>> Executing job on node that is from segment A.");
                X.println(">>>");

                return null;
            }
        });
    }

    /**
     * Ignores job results.
     *
     * @param results Job results.
     * @return {@code null}.
     */
    @Nullable
    @Override public Integer reduce(List<GridJobResult> results) {
        return null;
    }
}

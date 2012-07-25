// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.checkpoint;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import java.io.*;
import java.util.*;

/**
 * This grid task demonstrates some basic usage of task session checkpoints and failover.
 * It does the following:
 * <ol>
 * <li>Save checkpoint with key '{@code fail}' and value '{@code true}'.</li>
 * <li>Pass the passed in string as an argument into remote job for execution.</li>
 * <li>
 *   The job will check the value of checkpoint with key '{@code fail}'. If it
 *   is {@code true}, then it will set it to {@code false} and throw
 *   exception to simulate a failure. If it is {@code false}, then
 *   it will execute the grid-enabled method.
 * </li>
 * </ol>
 * Note that when job throws an exception it will be treated as a failure
 * by {@link #result(GridJobResult,List)} method which will return
 * {@link GridJobResultPolicy#FAILOVER} policy. This will cause the job to
 * automatically failover to another node for execution. The new job will
 * simply print out the argument passed in.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifyHelloWorldCheckpointTask extends GridifyTaskSplitAdapter<Integer> {
    /** Injected task session. */
    @GridTaskSessionResource
    private GridTaskSession taskSes;

    /**
     * Creates job which throws an exception and it will be treated as a failure.
     * This will cause the job to automatically failover to another node for execution.
     * The new job will simply print out the argument passed in.
     *
     * @param gridSize Number of nodes in the grid.
     * @param arg Task execution argument.
     * @return Created grid jobs for remote execution.
     * @throws GridException If split failed.
     */
    @Override public Collection<? extends GridJob> split(int gridSize, GridifyArgument arg) throws GridException {
        // Make reasonably unique checkpoint key.
        final String cpKey = getClass().getName() + arg;

        taskSes.saveCheckpoint(cpKey, true);

        String phrase = ((String)arg.getMethodParameters()[0]);

        return Collections.singletonList(new GridJobAdapterEx(phrase) {
            /** Injected distributed task session. */
            @GridTaskSessionResource
            private GridTaskSession jobSes;

            /** Injected grid logger. */
            @GridLoggerResource
            private GridLogger log;

            /**
             * The job will check the checkpoint with key '{@code fail}' and if
             * it's {@code true} it will throw exception to simulate a failure.
             * Otherwise, it will execute the grid-enabled method.
             */
            @Override public Serializable execute() throws GridException {
                Serializable cp = jobSes.loadCheckpoint(cpKey);

                if (cp == null) {
                    log.warning("Checkpoint was not found. Make sure that Checkpoint SPI on all nodes has the same " +
                        "configuration. The 'directoryPath' configuration parameter for GridSharedFsCheckpointSpi " +
                        "on all nodes should point to the same location.");

                    return -1;
                }

                boolean fail = (Boolean)cp;

                if (fail) {
                    jobSes.saveCheckpoint(cpKey, false);

                    throw new GridException("Example job exception.");
                }

                // Execute gridified method.
                // Note that since we are calling this method from within the grid job
                // AOP-based grid enabling will not cross-cut it and method will just
                // execute normally.
                return GridifyHelloWorldCheckpointExample.sayIt(this.<String>argument(0));
            }
        });
    }

    /**
     * To facilitate example's logic, returns {@link GridJobResultPolicy#FAILOVER}
     * policy in case of any exception.
     *
     * @param result Job result.
     * @param received All previously received results.
     * @throws GridException {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override public GridJobResultPolicy result(GridJobResult result, List<GridJobResult> received) throws GridException {
        return result.getException() != null ? GridJobResultPolicy.FAILOVER : GridJobResultPolicy.WAIT;
    }

    /**
     * Sums up all characters from all jobs and returns a
     * total number of characters in the initial phrase.
     *
     * @param results Job results.
     * @return Number of letters for the phrase passed into
     *      {@link GridifyHelloWorldCheckpointExample#sayIt(String)} method.
     * @throws GridException If reduce failed.
     */
    @Override public Integer reduce(List<GridJobResult> results) throws GridException {
        // We only had one job in the split. Therefore, we only have one result.
        return  results.get(0).getData();
    }
}

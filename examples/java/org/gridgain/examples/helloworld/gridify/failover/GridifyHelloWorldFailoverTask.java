// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.failover;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.resources.*;
import java.io.*;
import java.util.*;

/**
 * This grid task demonstrates some basic usage of task session and failover. It does the following:
 * <ol>
 * <li>Set session attribute '{@code fail=true}'.</li>
 * <li>Pass the passed in string as an argument into remote job for execution.</li>
 * <li>
 *   The job will check the value of '{@code fail}' attribute. If it
 *   is {@code true}, then it will set it to {@code false} and throw
 *   exception to simulate a failure. If it is {@code false}, then
 *   it will execute the grid-enabled method.
 * </li>
 * </ol>
 * Note that when job throws an exception it will be treated as a failure
 * by {@link #result(GridJobResult, List)} method
 * which will return {@link GridJobResultPolicy#FAILOVER} policy. This will
 * cause the job to automatically failover to another node for execution.
 * The new job will simply print out the argument passed in.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifyHelloWorldFailoverTask extends GridifyTaskSplitAdapter<Integer> {
    /** Grid task session is injected here. */
    @GridTaskSessionResource
    private GridTaskSession ses;

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
        // Set initial value for 'fail' attribute to 'true'.
        ses.setAttribute("fail", true);

        // We know that 1st parameter of our 'sayIt' method is a string.
        String words = ((String)arg.getMethodParameters()[0]);

        // Return just one job here.
        return Collections.singletonList(new GridJobAdapterEx(words) {
            /*
             * The job will check the 'fail' session attribute and if
             * it's 'true' it will throw exception to simulate a failure.
             * Otherwise, it will execute the grid-enabled method.
             */
            @Override public Serializable execute() throws GridException {
                boolean fail;

                try {
                    // Wait and get 'fail' attribute from session when it becomes available.
                    // In our example - we'll get it immediately since we set it up front
                    // in the 'split' method above.
                    fail = (Boolean)ses.waitForAttribute("fail");
                }
                catch (InterruptedException e) {
                    throw new GridException("Got interrupted while waiting for attribute to be set.", e);
                }

                // First time 'fail' attribute will be 'true' since
                // that's what we initialized it to during 'split'.
                if (fail) {
                    // Reset this attribute to 'false'.
                    // Next time we get this attribute we'll get 'false' value.
                    ses.setAttribute("fail", false);

                    // Throw exception to simulate error condition.
                    // The task 'result' method will see this exception
                    // and failover the job.
                    throw new GridException("Example job exception.");
                }

                // Execute gridified method.
                // Note that since we are calling this method from within the grid job
                // AOP-based grid enabling will not cross-cut it and method will just
                // execute normally.
                return GridifyHelloWorldFailoverExample.sayIt(this.<String>argument(0));
            }
        });
    }

    /**
     * To facilitate example's logic, returns {@link GridJobResultPolicy#FAILOVER}
     * policy in case of any exception.
     *
     * @param result {@inheritDoc}
     * @param received {@inheritDoc}
     * @throws GridException {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override public GridJobResultPolicy result(GridJobResult result, List<GridJobResult> received)
        throws GridException {
        return result.getException() != null ? GridJobResultPolicy.FAILOVER : GridJobResultPolicy.WAIT;
    }

    /**
     * Sums up all characters from all jobs and returns a
     * total number of characters in the initial phrase.
     *
     * @param results Job results.
     * @return Number of letters for the phrase passed into
     *      {@link GridifyHelloWorldFailoverExample#sayIt(String)} method.
     * @throws GridException If reduce failed.
     */
    @Override public Integer reduce(List<GridJobResult> results) throws GridException {
        // We only had one job in the split. Therefore,
        // we only have one result.
        return  results.get(0).getData();
    }
}

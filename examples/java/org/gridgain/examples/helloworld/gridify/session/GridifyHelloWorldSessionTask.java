// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.session;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Grid task for {@link GridifyHelloWorldSessionExample} example. It handles spiting
 * this example into multiple jobs for execution on remote nodes.
 * <p>
 * Every job will do the following:
 * <ol>
 * <li>Execute grid-enabled method with argument passed in.</li>
 * <li>Add its argument to the session.</li>
 * <li>Wait for other jobs to add their arguments to the session.</li>
 * <li>Execute grid-enabled method with all session attributes concatenated into one string as an argument.</li>
 * </ol>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifyHelloWorldSessionTask extends GridifyTaskSplitAdapter<Integer> {
    /** Grid task session will be injected. */
    @GridTaskSessionResource
    private GridTaskSession ses;

    /**
     * Splits the passed in phrase into words and creates a job for every
     * word. Every job will print out full phrase, the word and return
     * number of letters in that word.
     *
     * @param gridSize Number of nodes in the grid.
     * @param arg Task execution argument.
     * @return Created grid jobs for remote execution.
     * @throws GridException If split failed.
     */
    @Override protected Collection<? extends GridJob> split(int gridSize, GridifyArgument arg) throws GridException {
        String[] words = ((String)arg.getMethodParameters()[0]).split(" ");

        Collection<GridJobAdapterEx> jobs = new ArrayList<GridJobAdapterEx>(words.length);

        for (String word : words) {
            jobs.add(new GridJobAdapterEx(word) {
                /** Job context will be injected. */
                @GridJobContextResource
                private GridJobContext jobCtx;

                /**
                 * Executes grid-enabled method once with all
                 * session attributes concatenated into string
                 * as an argument and again with passed in argument.
                 */
                @Override public Object execute() throws GridException {
                    String word = argument(0);

                    // Set session attribute with value of this job's word.
                    ses.setAttribute(jobCtx.getJobId(), word);

                    try {
                        // Wait for all other jobs within this task to set their attributes on
                        // the session.
                        for (GridJobSibling sibling : ses.getJobSiblings()) {
                            // Waits for attribute with sibling's job ID as a key.
                            if (ses.waitForAttribute(sibling.getJobId()) == null) {
                                throw new GridException("Failed to get session attribute from job: " +
                                    sibling.getJobId());
                            }
                        }
                    }
                    catch (InterruptedException e) {
                        throw new GridException("Got interrupted while waiting for session attributes.", e);
                    }

                    // Create a string containing all attributes set by all jobs
                    // within this task (in this case an argument from every job).
                    StringBuilder msg = new StringBuilder();

                    // Formatting.
                    msg.append("All session attributes [ ");

                    for (Object jobArg : ses.getAttributes().values()) {
                        msg.append(jobArg).append(' ');
                    }

                    // Formatting.
                    msg.append(']');

                    // For the purpose of example, we simply log session attributes.
                    X.println(msg.toString());

                    // Execute gridified method and return the number
                    // characters in the passed in word.
                    // NOTE: since we are calling this method from within the grid job
                    // AOP-based grid enabling will not cross-cut it and method will just
                    // execute normally.
                    return GridifyHelloWorldSessionExample.sayIt(word);
                }
            });
        }

        return jobs;
    }


    /**
     * Sums up all characters from all jobs and returns a
     * total number of characters in the initial phrase.
     *
     * @param results Job results.
     * @return Number of letters for the word passed into
     *      {@link GridifyHelloWorldSessionExample#sayIt(String)} method.
     * @throws GridException If reduce failed.
     */
    @Override public Integer reduce(List<GridJobResult> results) throws GridException {
        return results.size() - 1 + F.sum(F.<Integer>jobResults(results));
    }
}

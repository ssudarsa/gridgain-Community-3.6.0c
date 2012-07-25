// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.state;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.typedef.*;
import java.io.*;
import java.util.*;

/**
 * This grid task is responsible for splitting the string state of
 * {@link GridifyHelloWorld} into separate words and then passing
 * each word into its own grid job as an argument for execution on
 * remote nodes. When job receives such argument on remote node,
 * it will set it as state into new {@link GridifyHelloWorld} instance
 * to prepare the instance for execution and then execute grid-enabled
 * {@link GridifyHelloWorld#sayIt()} method on it.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifyHelloWorldStateTask extends GridifyTaskSplitAdapter<Integer> {
    /**
     * Splits the passed in phrase into words and creates a job for every
     * word. Every job will print out the word and return number of letters in that
     * word.
     *
     * @param gridSize Number of nodes in the grid.
     * @param arg Task execution argument.
     * @return Created grid jobs for remote execution.
     * @throws GridException If split failed.
     */
    @Override protected Collection<? extends GridJob> split(int gridSize, GridifyArgument arg) throws GridException {
        // Get target instance on which grid-enabled method was invoked.
        GridifyHelloWorld hw = (GridifyHelloWorld)arg.getTarget();

        // Split internal string state of GridifyHelloWorld into separate words.
        String[] words = hw.getState().split(" ");

        Collection<GridJobAdapterEx> jobs = new ArrayList<GridJobAdapterEx>(words.length);

        for (String word : words) {
            // Every job gets its own word as an argument.
            jobs.add(new GridJobAdapterEx(word) {
                /*
                 * Simply executes 'GridifyHelloWorld#sayIt(String)' method
                 * with passed in state.
                 */
                @Override public Serializable execute() {
                    GridifyHelloWorld hw0 = new GridifyHelloWorld();

                    // Initialize GridifyHelloWorld for execution by setting
                    // necessary state into it.
                    hw0.setState(this.<String>argument(0));

                    // Execute gridified method.
                    // Note that since we are calling this method from within the grid job
                    // AOP-based grid enabling will not cross-cut it and method will just
                    // execute normally.
                    return hw0.sayIt();
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
     * @return Number of characters for the 'phrase' passed into
     *      {@link GridifyHelloWorld#setState(String)} method.
     * @throws GridException If reduce failed.
     */
    @Override public Integer reduce(List<GridJobResult> results) throws GridException {
        return results.size() - 1 + F.sum(F.<Integer>jobResults(results));
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.task;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * This grid task is responsible for splitting the passed in string into
 * separate words and then passing each word into its own grid job
 * for execution on remote nodes.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifyHelloWorldTask extends GridifyTaskSplitAdapter<Integer> {
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
        return F.outJobs(F.yield(((String)arg.getMethodParameters()[0]).split(" "), new C1<String, Integer>() {
            @Override public Integer apply(String e) {
                // Note that since we are calling this method from within the grid job
                // AOP-based grid enabling will not cross-cut it and method will just
                // execute normally.
                return GridifyHelloWorldTaskExample.sayIt(e);
            }
        }));
    }

    /**
     * Sums up all characters from all jobs and returns a
     * total number of characters in the initial phrase.
     *
     * @param results Job results.
     * @return Number of characters for the 'phrase' passed into
     *      {@link GridifyHelloWorldTaskExample#sayIt(String)} method.
     * @throws GridException If reduce failed.
     */
    @Override public Integer reduce(List<GridJobResult> results) throws GridException {
        // Assuming only 1 space between words => starting with 'results.size() - 1'.
        return results.size() - 1 + F.sum(F.<Integer>jobResults(results));
    }
}

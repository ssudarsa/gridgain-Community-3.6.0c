// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.api20;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * This class defines grid task for this example. Grid task is responsible for
 * splitting the task into jobs. This particular implementation splits given
 * string into individual words and creates grid jobs for each word. Every job
 * will print the word passed into it and return the number of letters in that
 * word.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridHelloWorldTask extends GridTaskSplitAdapter<String, Integer> {
    /**
     * Splits the passed in phrase into words and creates a job for every
     * word. Every job will print out the word and return number of letters in that
     * word.
     *
     * @param gridSize Number of nodes in the grid.
     * @param phrase Any phrase (for this example we pass in {@code "Hello World"}).
     * @return Created grid jobs for remote execution.
     * @throws GridException If split failed.
     */
    @Override public Collection<? extends GridJob> split(int gridSize, String phrase) throws GridException {
         return F.outJobs(F.yield(phrase.split(" "), new C1<String, Integer>() {
             @Override public Integer apply(String word) {
                 X.println(">>>");
                 X.println(">>> Printing '" + word + "' on this node from grid job.");
                 X.println(">>>");

                 // Return number of letters in the word.
                 return word.length();
             }
         }));
    }

    /**
     * Sums up all characters returns from all jobs and returns a
     * total number of characters in the phrase.
     *
     * @param results Job results.
     * @return Number of characters for the phrase passed into
     *      {@code split(gridSize, phrase)} method above.
     * @throws GridException If reduce failed.
     */
    @Override public Integer reduce(List<GridJobResult> results) throws GridException {
        return results.size() - 1 + F.sum(F.<Integer>jobResults(results));
    }
}

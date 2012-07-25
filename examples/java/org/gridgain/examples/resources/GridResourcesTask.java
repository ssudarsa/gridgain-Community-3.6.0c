// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.resources;

import org.gridgain.grid.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import java.io.*;
import java.util.*;

/**
 * This class defines grid task for this example.
 * Grid task is responsible for splitting the task into jobs. This particular
 * implementation splits given string into individual words and creates
 * grid jobs for each word. Every job will send data through context injected in
 * job as user resource.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridResourcesTask extends GridTaskSplitAdapter<String, Integer> {
    /**
     * Splits the passed in phrase into words and creates a job for every
     * word. Every job will print out the word and return number of letters in that
     * word. Job use context to store data through injected context.
     *
     * @param gridSize Number of nodes in the grid.
     * @param phrase Any phrase.
     * @return Created grid jobs for remote execution.
     * @throws GridException If split failed.
     */
    @SuppressWarnings("unused")
    @Override public Collection<? extends GridJob> split(int gridSize, String phrase) throws GridException {
        // Split the passed in phrase into multiple words separated by spaces.
        String[] words = phrase.split(" ");

        Collection<GridJob> jobs = new ArrayList<GridJob>(words.length);

        for (String word : words) {
            // Every job gets its own word as an argument.
            jobs.add(new GridJobAdapterEx(word) {
                @GridUserResource
                private transient GridResourcesContext ctx;

                /*
                 * Simply prints the word passed into the job and
                 * returns number of letters in that word.
                 */
                @Override public Serializable execute() {
                    String word = argument(0);

                    assert word != null;

                    X.println(">>>");
                    X.println(">>> Printing '" + word + "' on this node from grid job.");
                    X.println(">>>");

                    try {
                        ctx.sendData(word);
                    }
                    catch (Exception e) {
                        e.printStackTrace(System.err);
                    }

                    // Return number of letters in the word.
                    return word.length();
                }
            });
        }

        return jobs;
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

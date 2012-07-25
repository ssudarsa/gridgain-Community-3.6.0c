// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.split;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * This grid task is responsible for splitting the passed in string into
 * separate words and printing them on the remote nodes.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifySplitHelloWorldTask extends GridifyTaskSplitAdapter<Object> {
    /** {@inheritDoc} */
    @Override protected Collection<? extends GridJob> split(int gridSize, GridifyArgument arg) throws GridException {
        Collection<GridJob> jobs = new LinkedList<GridJob>();

        for (final String word : ((String)arg.getMethodParameters()[0]).split(" ")) {
            jobs.add(new GridJobAdapterEx() {
                @Nullable
                @Override public Object execute() {
                    GridifySplitHelloWorldExample.sayIt(word);

                    return null;
                }
            });
        }

        return jobs;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public Object reduce(List<GridJobResult> results) throws GridException {
        return null; // Nothing to reduce.
    }
}

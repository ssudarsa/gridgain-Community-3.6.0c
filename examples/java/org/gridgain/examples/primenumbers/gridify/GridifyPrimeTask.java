// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.primenumbers.gridify;

import org.gridgain.examples.primenumbers.*;
import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Task responsible for checking passed in value for prime. It creates
 * as many jobs as there are nodes in the grid and sends this job to
 * grid nodes for execution (note, that local node also participates
 * in execution).
 * <p>
 * Every grid job gets a range of divisors to check. The lower and
 * upper boundaries of this range are passed into job as arguments.
 * The jobs invoke {@link GridPrimeChecker} to check if the value
 * passed in is divisible by any of the divisors in the range.
 * Refer to {@link GridPrimeChecker} for algorithm specifics (it is
 * very unsophisticated).
 * <p>
 * Upon receiving results from every job {@link #reduce(List)} method
 * is invoked. In this method we determine if any divisor was found.
 * If we found a divisor, then we know that a number is not a prime
 * number and there is no need to wait for other job results. In
 * this case, the method will return {@link GridJobResultPolicy#REDUCE}
 * policy in order to start aggregation of results. All remaining jobs
 * will be cancelled by the system. However, note that it is responsibility
 * of {@link GridPrimeChecker} to constantly check if it was cancelled
 * (via thread interruption) and abort when needed.
 * <p>
 * Aggregation of results happens in {@link #reduce(List)} method. In
 * this method we determine if any of the remote jobs returned a divisor.
 * If divisor is found, then we return it, otherwise we return {@code null}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifyPrimeTask  extends GridifyTaskSplitAdapter<Long> {
    /** {@inheritDoc} */
    @Override protected Collection<? extends GridJob> split(int gridSize, GridifyArgument arg) {
        // Grid-enabled method parameters.
        Object[] args = arg.getMethodParameters();

        // Get parameters from grid-enabled Java method. These are the parameters
        // passed into 'GridSimplePrimeChecker.checkPrime(long, long, long)' method.
        final long val = (Long)args[0];
        long taskMinRange = (Long)args[1];

        long numbersPerTask = val / gridSize < 10 ? 10 : val / gridSize;

        Collection<GridJobAdapterEx> jobs = new ArrayList<GridJobAdapterEx>(gridSize);

        long jobMaxRange = 0;

        // In this loop we create as many grid jobs as
        // there are nodes in the grid.
        for (int i = 0; jobMaxRange < val ; i++) {
            long jobMinRange = i * numbersPerTask + taskMinRange;

            jobMaxRange = (i + 1) * numbersPerTask + taskMinRange - 1;

            if (jobMaxRange > val) {
                jobMaxRange = val;
            }

            final long min = jobMinRange;
            final long max = jobMaxRange;

            // Pass in value to check, and minimum/maximum range boundaries
            // into job as arguments.
            jobs.add(new GridJobAdapterEx() {
                /**
                 * Check if the value passed in is divisible by
                 * any of the divisors in the range. If so,
                 * return the first divisor found, otherwise
                 * return {@code null}.
                 *
                 * @return First divisor found or {@code null} if no
                 *      divisor was found.
                 */
                @Nullable
                @Override public Long execute() {
                    // Return first divisor found or null if no
                    // divisor was found.
                    return GridPrimeChecker.checkPrime(val, min, max);
                }
            });
        }

        // List of jobs to be executed on the grid.
        return jobs;
    }

    /** {@inheritDoc} */
    @Override public GridJobResultPolicy result(GridJobResult result, List<GridJobResult> received) throws GridException {
        // If devisor is found then complete right away, otherwise, keep waiting.
        if(result.getData() != null) {
            // Start reducing. All jobs that are still running
            // will be cancelled automatically.
            return GridJobResultPolicy.REDUCE;
        }

        return super.result(result, received);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public Long reduce(List<GridJobResult> results) {
        for (GridJobResult res : results) {
            if (res.getData() != null) {
                return res.getData();
            }
        }

        // No divisor was found, the value is 'prime'.
        return null;
    }
}

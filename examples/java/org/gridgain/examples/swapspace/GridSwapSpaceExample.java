// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.swapspace;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.typedef.*;

/**
 * Example that shows using of {@link GridSwapSpaceSpi}.
 */
public final class GridSwapSpaceExample {
    /**
     * Ensure singleton.
     */
    private GridSwapSpaceExample() {
        /* No-op. */
    }

    /**
     * Execute <tt>SwapSpace</tt> example on the grid.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      <tt>"examples/config/"</tt> for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CIX1 -> GridInClosureX
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        G.in(args.length == 0 ? null : args[0], new CIX1<Grid>() {
            @Override public void applyx(Grid g) throws GridException {
                String testData = "TestSwapSpaceData";

                // Execute SwapSpace task.
                g.execute(GridSwapSpaceTask.class, testData).get();
            }
        });
    }
}

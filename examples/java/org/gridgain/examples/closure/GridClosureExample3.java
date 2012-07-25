// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.closure;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

import static org.gridgain.grid.GridClosureCallMode.*;

/**
 * Demonstrates new functional APIs.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridTaskExample3
 */
public class GridClosureExample3 {
    /**
     * Ensures singleton.
     */
    private GridClosureExample3() {
        /* No-op. */
    }

    /**
     * Executes broadcasting message example with closures.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CI1 -> GridInClosure
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        G.in(args.length == 0 ? null : args[0], new CIX1<Grid>() {
            @Override public void applyx(Grid g) throws GridException {
                // Broadcasts message to all nodes.
                g.run(BROADCAST,
                    new CA() {
                        @Override public void apply() {
                            X.println(">>>>>");
                            X.println(">>>>> Hello Node! :)");
                            X.println(">>>>>");
                        }
                    }
                );

                // Prints.
                X.println(">>>>> Check all nodes for hello message output.");
            }
        });
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.closure;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Demonstrates new functional APIs.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridTaskExample4
 */
public class GridClosureExample4 {
    /**
     * Ensures singleton.
     */
    private GridClosureExample4() {
        /* No-op. */
    }

    /**
     * Executes information gathering example with closures.
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
                // Broadcast closure to all nodes for gathering their system information.
                String res = g.reduce(GridClosureCallMode.BROADCAST,
                    Collections.<GridOutClosure<String>>singleton(
                        new CO<String>() {
                            @Override public String apply() {
                                StringBuilder buf = new StringBuilder();

                                buf.append("OS: ").append(System.getProperty("os.name"))
                                    .append(" ").append(System.getProperty("os.version"))
                                    .append(" ").append(System.getProperty("os.arch"))
                                    .append("\nUser: ").append(System.getProperty("user.name"))
                                    .append("\nJRE: ").append(System.getProperty("java.runtime.name"))
                                    .append(" ").append(System.getProperty("java.runtime.version"));

                                return buf.toString();
                            }
                        }
                    ),
                    new R1<String, String>() {
                        private StringBuilder buf = new StringBuilder();

                        @Override public boolean collect(String s) {
                            buf.append("\n").append(s).append("\n");

                            return true;
                        }

                        @Override public String apply() {
                            return buf.toString();
                        }
                    }
                );

                // Print result.
                X.println("Nodes system information:");
                X.println(res);
            }
        });
    }
}

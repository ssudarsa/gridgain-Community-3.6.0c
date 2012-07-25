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
import java.util.*;

import static org.gridgain.grid.GridClosureCallMode.*;

/**
 * Demonstrates new functional APIs.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridTaskExample2
 */
public class GridClosureExample2 {
    /**
     * Ensures singleton.
     */
    private GridClosureExample2() {
        /* No-op. */
    }

    /**
     * Executes example of calculation of the greatest common divisor (GCD) and
     * the lowest common multiple (LCM) for each generated pair of integers
     * using new functional APIs.
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
                // Bound for random numbers.
                int bound = 100;

                // Collection size.
                int size = 10;

                // Initialises collection of pair random numbers.
                Collection<int[]> pairs = new ArrayList<int[]>(size);

                // Fills collection.
                for (int i = 0; i < size; i++) {
                    pairs.add(new int[] {
                        GridNumberUtilExample.getRand(bound), GridNumberUtilExample.getRand(bound)
                    });
                }

                // Calculates and prints GCD and LCM for each pair of numbers with closure.
                g.run(SPREAD,
                    F.yield(pairs, new CI1<int[]>() {
                        @Override public void apply(int[] pair) {
                            int gcd = GridNumberUtilExample.getGCD(pair[0], pair[1]);
                            int lcm = GridNumberUtilExample.getLCM(pair[0], pair[1]);

                            System.out.printf(">>>>> Numbers: %d and %d. GCD: %d. LCM: %d.%n", pair[0], pair[1], gcd, lcm);
                        }
                    })
                );

                // Prints.
                X.println(">>>>> Check all nodes for numbers and their GCD and LCM output.");
            }
        });
    }
}

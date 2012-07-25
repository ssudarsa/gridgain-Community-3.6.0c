// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.functional;

import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Demonstrates various functional APIs from {@link org.gridgain.grid.lang.GridFunc} class.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridFunctionFoldExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionFoldExample() {
        /* No-op. */
    }

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     */
    public static void main(String[] args) {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CI1 -> GridInClosure
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        // Data initialisation.
        Random rand = new Random();

        int size = 10;

        Collection<Integer> nums = new ArrayList<Integer>(size);

        // Generate list of random integers.
        for (int i = 0; i < size; i++) {
            nums.add(rand.nextInt(size));
        }

        // Print generated list.
        X.println("Generated list:");

        F.forEach(nums, F.<Integer>print("", " "));

        // Note that the same method 'fold(...)' can be used to find
        // extremes as well as calculable values like sum.

        // Search max value.
        int max = F.fold(nums, F.first(nums), new C2<Integer, Integer, Integer>() {
            @Override public Integer apply(Integer n, Integer max) {
                return Math.max(n, max);
            }
        });

        // Search min value.
        int min = F.fold(nums, F.first(nums), new C2<Integer, Integer, Integer>() {
            @Override public Integer apply(Integer n, Integer min) {
                return Math.min(n, min);
            }
        });

        // Calculate sum of all numbers.
        int sum = F.fold(nums, 0, new C2<Integer, Integer, Integer>() {
            @Override public Integer apply(Integer n, Integer sum) {
                return sum + n;
            }
        });

        // Print result.
        X.println("\nMax value: " + max);
        X.println("Min value: " + min);
        X.println("Sum: " + sum);
    }
}

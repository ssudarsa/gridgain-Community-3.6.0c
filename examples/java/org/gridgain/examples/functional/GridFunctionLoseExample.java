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
public class GridFunctionLoseExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionLoseExample() {
        /* No-op. */
    }

    /**
     * Execute function example.
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

        final int size = 20;

        Collection<Integer> nums = new ArrayList<Integer>(size);

        // Generate list of random integers.
        for (int i = 0; i < size; i++) {
            nums.add(rand.nextInt(size));
        }

        // Print generated list.
        X.println("Generated list:");

        for (Integer i : nums) {
            System.out.print(i + " ");
        }

        // Lose all elements which value is low than half generated list size.
        Collection<Integer> res = F.lose(nums, true, new P1<Integer>() {
            @Override public boolean apply(Integer i) {
                return i < size / 2;
            }
        });

        // Print result.
        X.println("\nResult list:");

        for (Integer i : res) {
            System.out.print(i + " ");
        }

        // Lose first half of result list.
        res = F.lose(res, true, res.size() / 2);

        // Print result.
        X.println("\nResult list:");

        for (Integer i : res) {
            System.out.print(i + " ");
        }
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.functional;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Demonstrates various functional APIs from {@link org.gridgain.grid.lang.GridFunc} class.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridFunctionWhileDoExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionWhileDoExample() {
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
        final Random rand = new Random();

        final int size = 5;

        final Collection<Integer> primeNums = new ArrayList<Integer>(size);

        // Fills collection with prime numbers.
        F.whileDo(
            // Create closure which will generate prime numbers.
            new CA() {
                private int range = 100;

                @Override public void apply() {
                    int num = rand.nextInt(range);

                    if (num > 1) {
                        boolean prime = true;

                        for (int i = 2; i * i <= num; i++) {
                            if (num % i == 0) {
                                prime = false;

                                break;
                            }
                        }
                        if (prime) {
                            primeNums.add(num);
                        }
                    }
                }
            },
            // Create predicate which will check, whether the collection till the certain size is filled.
            new GridAbsPredicate() {
                @Override public boolean apply() {
                    return primeNums.size() < size;
                }
            }
        );

        // Print result.
        X.println("Prime numbers:");

        F.forEach(primeNums, F.<Integer>print("", " "));
    }
}

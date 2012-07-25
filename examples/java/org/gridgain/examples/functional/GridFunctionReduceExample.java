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
public class GridFunctionReduceExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionReduceExample() {
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
        
        // Create sample menu with function GridFunc.asMap from functional API.
        final Map<String, Item> menu = F.asMap(
            "Juice", new Item(15, 10),
            "Pie", new Item(20, 10),
            "Salad", new Item(25, 15),
            "Tea", new Item(10, 5)
        );

        // Create sample order.
        Map<String, Integer> order = F.asMap("Juice", 2, "Tea", 2, "Pie", 2, "Salad", 1);

        // Calculate order cost.
        double cost = F.reduce(order, new R2<String, Integer, Double>() {
            private double cost;

            @Override public boolean collect(String name, Integer cnt) {
                Item item = menu.get(name);

                double costWithoutCharge = item.cost * cnt;

                cost += costWithoutCharge + costWithoutCharge * item.extraCharge / 100f;

                return true;
            }

            @Override public Double apply() {
                return cost;
            }
        });

        // Print result.
        X.println("Order cost: " + cost);
    }

    /**
     * This class represents a sample menu item.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class Item {
        /** Menu item cost. */
        private double cost;

        /** Menu item extra charge. */
        private double extraCharge;

        /**
         * @param cost Menu item cost.
         * @param extraCharge Menu item extra charge.
         */
        Item(double cost, double extraCharge) {
            this.cost = cost;
            this.extraCharge = extraCharge;
        }
    }
}

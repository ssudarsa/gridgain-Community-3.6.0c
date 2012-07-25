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
public class GridFunctionForEachExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionForEachExample() {
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
        
        // Create sample menu.
        Map<String, Item> menu = F.asMap(
            "Juice", new Item("Juice", 15, 10),
            "Pie", new Item("Pie", 20, 10),
            "Salad", new Item("Salad", 25, 15),
            "Tea", new Item("Tea", 10, 5)
        );

        // Calculate full cost.
        F.forEach(menu.values(), new CI1<Item>() {
            @Override public void apply(Item item) {
                item.fullCost = item.cost + item.cost * item.extraCharge / 100.;
            }
        });

        // Print result.
        F.forEach(menu.values(), F.<Item>println());
    }

    /**
     * This class represents a sample menu item.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class Item {
        /** Menu item name. */
        private String name;

        /** Menu item cost. */
        private double cost;

        /** Menu item extra charge. */
        private double extraCharge;

        /** Menu item full cost. */
        private double fullCost;

        /**
         * @param name Menu item name.
         * @param cost Menu item cost.
         * @param extraCharge Menu item extra charge.
         */
        Item(String name, double cost, double extraCharge) {
            this.name = name;
            this.cost = cost;
            this.extraCharge = extraCharge;
        }

        /** {@inheritDoc}} */
        @Override public String toString() {
            return "Item [name=" + name + ", cost=" + cost + ", extra charge=" + extraCharge + ", full cost=" +
                fullCost + "]";
        }
    }
}

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
public class GridFunctionCopyExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionCopyExample() {
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

        // Create collection of goods.
        Collection<Item> goods = Arrays.asList(
            new Item("Table", false, 100),
            new Item("Chair", false, 50),
            new Item("Sofa", true, 200),
            null,
            new Item("Case", false, 300),
            new Item("Phone", true, 150),
            null
        );

        // Create collection which will contains copies.
        Collection<Item> res = new LinkedList<Item>();

        // Copy elements from goods which are not NULL and whose price lower than 150 or which are novelty.
        F.copy(res, goods,
            F.<Item>and(
                F.<Item>notNull(),
                F.<Item>or(
                    new P1<Item>() {
                        @Override public boolean apply(Item item) {
                            return item.novelty;
                        }
                    },
                    new P1<Item>() {
                        @Override public boolean apply(Item item) {
                            return item.price < 150;
                        }
                    }
                )
            )
        );

        F.forEach(res, F.<Item>println());
    }

    /**
     * This class simply represents a goods information.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class Item {
        /** Item name. */
        private String name;

        /** Is novelty goods? */
        private boolean novelty;

        /** Item price. */
        private double price;

        /**
         * @param name Item name.
         * @param novelty Is novelty goods.
         * @param price Item price.
         */
        Item(String name, boolean novelty, double price) {
            this.name = name;
            this.novelty = novelty;
            this.price = price;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "Item [name=" + name + ", price=" + price + ", novelty=" + (novelty ? "yes" : "no" + "]");
        }
    }
}

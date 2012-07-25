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
public class GridFunctionRetainExample2 {
    /**
     * Ensures singleton.
     */
    private GridFunctionRetainExample2() {
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
        // P1 -> GridPredicate

        // Create collection of cities.
        Collection<City> cities = Arrays.asList(
            new City("New York", 8140993, 1214, "USA"),
            new City("Los Angeles", 3975590, 1290, "USA"),
            new City("Moscow", 10562099, 1081, "Russia"),
            new City("Chicago", 2862244, 606, "USA"),
            new City("St. Petersburg", 4600310, 1439, "Russia"),
            new City("Sydney", 4114710, 1212, "Australia"),
            new City("London", 7581052, 1580, "Great Britain"),
            new City("Melbourne", 3528690, 881, "Australia"),
            new City("Istanbul", 16767433, 2106, "Turkey"),
            new City("Tokyo", 15570000, 2187, "Japan"));

        /*
         * Filter by newly created predicate and print result collection.
         * Filtered collection will contain cities whose population greater than 5kk and area lower than 1500 or
         * country is USA. Of course we can create only one predicate but this simple sample represent power of
         * functional API.
         */
        F.forEach(
            F.retain(cities, true,
                F.<City>or(
                    F.<City>and(
                        new P1<City>(){ @Override public boolean apply(City c) { return c.population > 5000000; }},
                        new P1<City>() { @Override public boolean apply(City c) { return c.area < 1500; }}
                    ),
                    new P1<City>() { @Override public boolean apply(City c) { return F.eq(c.country, "USA"); }}
                )
            ),
           F.<City>println());
    }

    /**
     * This class simply represents a city information.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class City {
        /** City name. */
        private String name;

        /** City population. */
        private int population;

        /** City area. */
        private int area;

        /** City country. */
        private String country;

        /**
         * @param name City name.
         * @param population City population.
         * @param area City area.
         * @param country City country.
         */
        City(String name, int population, int area, String country) {
            this.name = name;
            this.population = population;
            this.area = area;
            this.country = country;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "City [name=" + name + ", population=" + population + ", area=" + area + ", country=" + country +
                "]";
        }
    }
}

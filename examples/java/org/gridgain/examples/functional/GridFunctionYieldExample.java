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
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Demonstrates various functional APIs from {@link org.gridgain.grid.lang.GridFunc} class.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridFunctionYieldExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionYieldExample() {
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
        
        // Create sample array of temperature indicators.
        String[] strs = {
            "-20.2 C", "300 K", "60 F", "80.5 F", "99F"
        };

        final TemperatureFactory factory = new TemperatureFactory();

        // Generate collection of closures which in the future will help to receive converted indicators.
        Collection<GridOutClosure<Temperature>> res = F.yield(strs, new C1<String, Temperature>() {
            @Nullable
            @Override public Temperature apply(String str) {
                return factory.getTemperature(str);
            }
        });

        // Print result.
        F.forEach(res, new CI1<GridOutClosure<Temperature>>() {
            @Override public void apply(GridOutClosure<Temperature> closure) {
                try {
                    Temperature tmp = closure.apply();

                    System.out.printf(Locale.US, "Temperature [fahrenheit=%.2f F, celsius=%.2f C, kelvin=%.2f K]%n",
                        tmp.fahrenheit, tmp.celsius, tmp.kelvin);
                }
                catch (Exception e) {
                    X.println("Couldn't receive converted temperature indicator. Reason: " + e.getMessage());
                }
            }
        });
    }

    /**
     * This class represents a temperature indicator with various formats.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class Temperature {
        /** Temperature value in Fahrenheit. */
        private double fahrenheit;

        /** Temperature value in Celsius. */
        private double celsius;

        /** Temperature value in Kelvin. */
        private double kelvin;

        /**
         * @param fahrenheit Temperature value in Fahrenheit.
         * @param celsius Temperature value in Celsius.
         * @param kelvin Temperature value in Kelvin.
         */
        Temperature(double fahrenheit, double celsius, double kelvin) {
            this.fahrenheit = fahrenheit;
            this.celsius = celsius;
            this.kelvin = kelvin;
        }
    }

    /**
     * This class is intended for building {@link Temperature} instances from {@code String} values.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class TemperatureFactory {
        /**
         * Get {@code Temperature} instance from {@code String} value.
         *
         * @param str {@code String} temperature representation.
         * @return {@code Temperature} instance.
         * @throws IllegalArgumentException If parameter value is in wrong format.
         */
        @Nullable
        Temperature getTemperature(String str) throws IllegalArgumentException {
            if (!str.matches("-?\\d{1,3}(\\.\\d{1,2})? (F|C|K)")) {
                throw new IllegalArgumentException("Invalid temperature string format: " + str);
            }

            char c = str.charAt(str.length() - 1);

            double degree = Double.parseDouble(str.substring(0, str.length() - 2));

            Temperature tmp = null;

            switch (c) {
                case 'F':
                    tmp = buildByFahrenheit(degree);
                    break;
                case 'C':
                    tmp = buildByCelsius(degree);
                    break;
                case 'K':
                    tmp = buildByKelvin(degree);
                    break;
            }

            return tmp;
        }

        /**
         * Build {@code Temperature} instance by Fahrenheit.
         *
         * @param degree Temperature on the Fahrenheit scale.
         * @return {@code Temperature} instance.
         */
        Temperature buildByFahrenheit(double degree) {
            double celsius = (degree - 32) / 1.8;
            double kelvin = (degree + 459.67) / 1.8;

            return new Temperature(degree, celsius, kelvin);
        }

        /**
         * Build {@code Temperature} instance by Celsius.
         *
         * @param degree Temperature on the Celsius scale.
         * @return {@code Temperature} instance.
         */
        Temperature buildByCelsius(double degree) {
            double fahrenheit = 1.8 * degree + 32;
            double kelvin = degree + 273.15;

            return new Temperature(fahrenheit, degree, kelvin);
        }

        /**
         * Build {@code Temperature} instance by Kelvin.
         *
         * @param degree Temperature on the Kelvin scale.
         * @return {@code Temperature} instance.
         */
        Temperature buildByKelvin(double degree) {
            double fahrenheit = 1.8 * degree - 459.67;
            double celsius = degree - 273.15;

            return new Temperature(fahrenheit, celsius, degree);
        }
    }
}

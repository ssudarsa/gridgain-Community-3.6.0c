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
public class GridFunctionPartitionExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionPartitionExample() {
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
        
        // Create sample list of persons.
        Collection<Person> persons = Arrays.asList(
            new Person("Joel", 35),
            new Person("Don", 23),
            new Person("Zach", 16),
            new Person("Hugo", 17),
            new Person("Bill", 22),
            new Person("Marcus", 20)
        );

        // Get two collections of persons with function GridFunc.partition.
        // The first contains persons whose age greater than 18, the second - all others.
        GridTuple2<Collection<Person>, Collection<Person>> res = F.partition(persons,
            new P1<Person>() {
                @Override public boolean apply(Person person) {
                    return person.age > 18;
                }
            }
        );

        // Print result with GridFunc.forEach.
        X.println("Persons whose age greater than 18: ");
        F.forEach(res.get1(), F.<Person>println());

        X.println("\nPersons whose age lower or equal 18: ");
        F.forEach(res.get2(), F.<Person>println());
    }

    /**
     * This class represents a person information.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class Person {
        /** Person name. */
        private String name;

        /** Person age. */
        private int age;

        /**
         * @param name Person name.
         * @param age Person age.
         */
        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "Person [name=" + name +  ", age=" + age + "]";
        }
    }
}

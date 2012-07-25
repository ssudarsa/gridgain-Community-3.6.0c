// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.examples

import org.gridgain.grid.*
import org.gridgain.grid.cache.*
import org.gridgain.grid.cache.affinity.*
import org.gridgain.grid.cache.query.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*
import org.gridgain.grover.lang.*

/**
 * Demonstrates cache ad-hoc queries with Grover.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
@Use(GroverCacheProjectionCategory)
class GroverCacheQueryExample {
    /** Cache name. */
    private static String CACHE_NAME = "partitioned" // "replicated"

    /**
     * Example entry point. No arguments required.
     */
    static void main(String[] args) {
        grover("examples/config/spring-cache.xml") { ->
            example(grid$)
        }
    }

    /**
     * Runs the example.
     *
     * @param g Grid instance to use.
     */
    private static void example(Grid g) {
        if (!g.isEnterprise())
            println(">>> NOTE: in Community Edition all queries will be run localy.");

        // Populate cache.
        initialize()

        // Cache instance shortcut.
        def cache = this.<GridCacheAffinityKey<UUID>, Person>mkCache()

        // Distributed queries only supported by Enterprise Edition.
        // In Community Edition we'll use local node projection.
        def prj = g.isEnterprise() ? g : g.localNode()

        // Example for SQL-based querying employees based on salary ranges.
        // Gets all persons with 'salary > 1000'.
        print("People with salary more than 1000: ", cache.sql(prj, Person, "salary > 1000").
            collect { it._2 })

        // Example for TEXT-based querying for a given string in people resumes.
        // Gets all persons with 'Bachelor' degree.
        print("People with Bachelor degree: ", cache.lucene(prj, Person, "Bachelor").
            collect { it._2 })

        // Example for SQL-based querying with custom remote transformer to make sure
        // that only required data without any overhead is returned to caller.
        // Gets last names of all 'GridGain' employees.
        print("Last names of all 'GridGain' employees: ",
            cache.sqlTransform(
                prj,
                Person.class,
                "from Person, Organization where Person.orgId = Organization.id " +
                    "and Organization.name = 'GridGain'",
                { Person p -> p.lastName }
            ).collect { it._2 }
        )

        // Example for SQL-based querying with custom remote and local reducers
        // to calculate average salary among all employees within a company.
        // Gets average salary of persons with 'Master' degree.
        print("Average salary of people with Master degree: ",
            cache.luceneReduce(
                prj,
                Person.class,
                "Master",
                { Collection<GroverTuple<GridCacheAffinityKey<UUID>, Person>> c ->
                    def sum = c.collect { it._2.salary }.sum() ?: 0

                    new GroverTuple(sum, c.size())
                },
                { Collection<GroverTuple<Double, Integer>> c ->
                    (c.collect { it._1 }.sum() as double) / (c.collect { it._2 }.sum() as int)
                }
            )
        )
    }

    /**
     * Gets instance of typed cache view to use.
     *
     * @return Cache to use.
     */
    private static <K, V> GridCacheProjection<K, V> mkCache() {
        // In Community Edition queries work only for 'local' cache.
        // Distributed queries aren't support in Community Edition.
        cache$(grid$.isEnterprise() ? CACHE_NAME : "local").flagsOn(GridCacheFlag.SYNC_COMMIT)
    }

    /**
     * Populates cache with test data.
     */
    private static void initialize() {
        // Organization cache projection.
        def orgCache = this.<UUID, Organization>mkCache()

        // Organizations.
        def org1 = new Organization("GridGain")
        def org2 = new Organization("Other")

        orgCache.put(org1.id, org1)
        orgCache.put(org2.id, org2)

        // Person cache projection.
        def prnCache = this.<GridCacheAffinityKey<UUID>, Person>mkCache()

        // People.
        def p1 = new Person(org1, "John", "Doe", 2000, "John Doe has Master Degree.")
        def p2 = new Person(org1, "Jane", "Doe", 1000, "Jane Doe has Bachelor Degree.")
        def p3 = new Person(org2, "John", "Smith", 1500, "John Smith has Bachelor Degree.")
        def p4 = new Person(org2, "Jane", "Smith", 2500, "Jane Smith has Master Degree.")

        // Note that in this example we use custom affinity key for Person objects
        // to ensure that all persons are collocated with their organizations.
        prnCache.put(p1.key, p1)
        prnCache.put(p2.key, p2)
        prnCache.put(p3.key, p3)
        prnCache.put(p4.key, p4)
    }

    /**
     * Prints object or collection of objects to standard out.
     *
     * @param msg Message to print before object is printed.
     * @param o Object to print, can be `Iterable`.
     */
    private static void print(String msg, Object o) {
        assert msg != null
        assert o != null

        println(">>> ${msg}")

        switch(o) {
            case Iterable:
                o.each { println(">>>     ${it}") }

                break
            default:
                println(">>>     ${o}")
        }
    }

    /**
     * Organization class.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class Organization {
        @GridCacheQuerySqlField
        final String name

        @GridCacheQuerySqlField
        final UUID id = UUID.randomUUID()

        Organization(String name) {
            this.name = name
            this.id = id
        }
    }

    /**
     * Person class.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class Person {
        final Organization org
        final String firstName
        final String lastName
        @GridCacheQuerySqlField final Double salary
        @GridCacheQueryLuceneField final String resume
        final UUID id
        @GridCacheQuerySqlField final UUID orgId
        final GridCacheAffinityKey<UUID> key

        Person(Organization org, String firstName, String lastName, Double salary, String resume) {
            this.org = org
            this.firstName = firstName
            this.lastName = lastName
            this.salary = salary
            this.resume = resume
            id = UUID.randomUUID()
            orgId = org.id
            key = new GridCacheAffinityKey<UUID>(id, org.id)
        }

        /**
         * `toString` implementation.
         */
        @Override String toString() {
            "${firstName} ${lastName} [salary: ${salary}, resume: ${resume}]"
        }
    }
}

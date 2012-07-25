// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache.store;

import org.gridgain.examples.cache.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.typedef.*;

import java.util.*;

/**
 * This example demonstrates JDBC and Hibernate-based implementations
 * of persistent store functionality in cache.
 * <p>
 * You can execute this example with or without remote nodes.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache with cache store enabled:
 * <ul>
 *     <li>{@code 'ggstart.sh examples/config/spring-cache-store-jdbc.xml'}</li>
 *     <li>{@code 'ggstart.sh examples/config/spring-cache-store-hibernate.xml'}</li>
 * </ul>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheStoreExample {
    /** Global person ID to use across entire example. */
    private static final UUID PERSON_ID = UUID.randomUUID();

    /**
     * Ensure singleton.
     */
    private GridCacheStoreExample() { /* No-op. */ }

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        // To start grid with desired configuration uncomment the appropriate line.
        G.start("examples/config/spring-cache-store-jdbc.xml");
        // G.start("examples/config/spring-cache-store-hibernate.xml");

        try {
            commitExample();

            rollbackExample();
        }
        finally {
            GridFactory.stop(true);
        }
    }

    /**
     * Store commit example.
     *
     * @throws GridException If commit example fails.
     */
    private static void commitExample() throws GridException {
        X.println(">>>");
        X.println(">>> Starting store commit example.");
        X.println(">>>");

        GridCache<UUID, Person> cache = G.grid().cache();

        GridCacheTx tx = cache.txStart();

        try {
            Person val = cache.get(PERSON_ID);

            X.println("Read value: " + val);

            val = cache.put(PERSON_ID, person(PERSON_ID, "Isaac", "Newton", "Famous scientist"));

            X.println("Overwrote old value: " + val);

            val = cache.get(PERSON_ID);

            X.println("Read value: " + val);

            tx.commit();
        }
        finally {
            tx.end();
        }

        X.println("Read value after commit: " + cache.get(PERSON_ID));
    }

    /**
     * Store rollback example.
     *
     * @throws GridException If example fails.
     */
    private static void rollbackExample() throws GridException {
        X.println(">>>");
        X.println(">>> Starting store rollback example.");
        X.println(">>>");

        GridCache<UUID, Person> cache = G.grid().cache();

        GridCacheTx tx = cache.txStart();

        try {
            Person val = cache.get(PERSON_ID);

            X.println("Read value: " + val);

            // Put doomed value to cache.
            val = cache.put(PERSON_ID, person(PERSON_ID, "James", "Maxwell", "Famous scientist"));

            X.println("Overwrote old value: " + val);

            val = cache.get(PERSON_ID);

            X.println("Read value: " + val);
        }
        finally {
            tx.end();
        }

        X.println("Key value after rollback: " + cache.get(PERSON_ID));
    }

    /**
     * Creates person.
     *
     * @param id ID.
     * @param firstName First name.
     * @param lastName Last name.
     * @param resume Resume.
     * @return Newly created person.
     */
    private static Person person(UUID id, String firstName, String lastName, String resume) {
        Person person = new Person(id);

        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setResume(resume);

        return person;
    }
}

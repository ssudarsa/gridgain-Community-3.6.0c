// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache.tx;

import org.gridgain.examples.cache.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.typedef.*;

import java.util.*;

import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * This example demonstrates some examples of how to use cache transactions.
 * You can execute this example with or without remote nodes.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache: {@code 'ggstart.sh examples/config/spring-cache.xml'}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheTransactionExample {
    // Typedefs:
    // ---------
    // G -> GridFactory
    // CIX1 -> GridInClosureX
    // P2 -> GridPredicate2

    /** Ensure singleton. */
    private GridCacheTransactionExample() { /* No-op. */ }

    /** Cache. */
    private static GridCache<UUID, Object> cache;

    /**
     * Puts data to cache and then queries them.
     *
     * @param args Command line arguments, none required.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        Grid grid = G.start("examples/config/spring-cache.xml");

        try {
            // Uncomment any configured cache instance to observe
            // different cache behavior for different cache modes.
            cache = grid.cache("partitioned");
//            cache = grid.cache("replicated");
//            cache = grid.cache("local");

            print("Cache transaction example started.");

            transactions1();
            transactions2();
            transactions3();

            print("Cache transaction example finished");
        }
        finally {
            G.stop(true);
        }
    }

    /**
     * Demonstrates direct transaction demarcation.
     *
     * @throws GridException If failed.
     */
    private static void transactions1() throws GridException {
        Organization org = new Organization("GridGain");
        Person p = new Person(org, "Jon", "Doe", 1000, "I have a 'Master Degree'");

        GridCacheTx tx = cache.txStart(OPTIMISTIC, REPEATABLE_READ);

        try {
            assert cache.get(org.getId()) == null;
            assert cache.get(p.getId()) == null;

            cache.put(org.getId(), org);
            cache.put(p.getId(), p);

            // Since transaction is started with REPEATABLE_READ isolation,
            // 'get()' operation within transaction will return the same values
            // as were 'put' within the same transaction.
            assert cache.get(org.getId()) == org;
            assert cache.get(p.getId()) == p;

            // Commit transaction.
            tx.commit();

            // Get values from cache outside transaction and make sure that they are stored.
            assert org.equals(cache.get(org.getId()));
            assert p.equals(cache.get(p.getId()));
        }
        finally {
            // This call will rollback transaction
            // if it has not committed yet.
            tx.end();
        }

        print("Completed example 'transactions1'");
    }

    /**
     * Demonstrates transactional closures.
     *
     * @throws GridException If failed.
     */
    private static void transactions2() throws GridException {
        final Organization org = new Organization("GridGain");
        final Person p = new Person(org, "Jon", "Doe", 1000, "I have a 'Master Degree'");

        cache.inTx(3000, new CIX1<GridCacheProjection<UUID, Object>>() {
            @Override public void applyx(GridCacheProjection<UUID, Object> cache) throws GridException {
                assert cache.get(org.getId()) == null;
                assert cache.get(p.getId()) == null;

                cache.put(org.getId(), org);
                cache.put(p.getId(), p);
            }
        });

        // Get values from cache outside transaction and make sure that they are stored.
        assert org.equals(cache.get(org.getId()));
        assert p.equals(cache.get(p.getId()));

        print("Completed example 'transactions2'");
    }

    /**
     * Demonstrates custom cache projection in conjunction with transactional closure.
     *
     * @throws GridException If failed.
     */
    private static void transactions3() throws GridException {
        final Organization org = new Organization("GridGain");
        final Person jon = new Person(org, "Jon", "Doe", 1000, "I have a 'Master Degree'");
        final Person jane = new Person(org, "Jane", "Doe", 2000, "I have a 'Bachelor Degree'");

        cache.inTx(3000, new CIX1<GridCacheProjection<UUID, Object>>() {
            @Override public void applyx(GridCacheProjection<UUID, Object> cache) throws GridException {
                // Create convenience cache projection for accessing objects of type Person with
                // salaries greater than 2000.
                GridCacheProjection<UUID, Person> richPersons =
                    cache.<UUID, Person>projection(UUID.class, Person.class).projection(
                        // Predicate for filtering salaries greater than 2000.
                        new P2<UUID, Person>() {
                            @Override public boolean apply(UUID id, Person p) {
                                return p.getSalary() >= 2000;
                            }
                        });

                assert cache.get(org.getId()) == null;

                assert cache.get(jon.getId()) == null;
                assert cache.get(jane.getId()) == null;

                cache.put(org.getId(), org);

                // Jon's salary is less than 2000 so he's not considered rich.
                assert !richPersons.putx(jon.getId(), jon);

                // Jane's salary is 2000, she's rich.
                assert richPersons.putx(jane.getId(), jane);
            }
        });

        // Get values from cache outside transaction and make sure that organization
        // and Jane (as a rich person) are stored.
        assert org.equals(cache.get(org.getId()));

        assert cache.get(jon.getId()) == null;
        assert jane.equals(cache.get(jane.getId()));

        print("Completed example 'transactions3'");
    }

    /**
     * Prints out given object to standard out.
     *
     * @param o Object to print.
     */
    private static void print(Object o) {
        X.println(">>> " + o);
    }
}

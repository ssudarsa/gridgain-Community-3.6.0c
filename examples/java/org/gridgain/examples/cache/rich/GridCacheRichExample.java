// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache.rich;

import org.gridgain.examples.cache.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;

import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridClosureCallMode.*;
import static org.gridgain.grid.GridEventType.*;

/**
 * This example demonstrates some of the cache rich API capabilities.
 * You can execute this example with or without remote nodes.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache: {@code 'ggstart.sh examples/config/spring-cache.xml'}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheRichExample {
    /** Ensure singleton. */
    private GridCacheRichExample() { /* No-op. */ }

    /**
     * Put data to cache and then queries them.
     *
     * @param args Command line arguments, none required.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        Grid grid = G.start("examples/config/spring-cache.xml");

        try {
            print("Cache rich API example started.");

            // Register remote event handler.
            registerEvents();

            // Uncomment any configured cache instance to observe
            // different cache behavior for different cache modes.
            GridCache<UUID, Object> cache = grid.cache("partitioned");
            // GridCache<UUID, Object> cache = grid.cache("replicated");
            // GridCache<UUID, Object> cache = grid.cache("local");

            // Demonstrates concurrent-map-like operations on cache.
            concurrentMap(cache);

            // Demonstrates visitor-like operations on cache.
            visitors(cache);

            // Demonstrates iterations over cached collections.
            collections(cache);

            print("Cache rich API example finished.");
        }
        finally {
            G.stop(true);
        }
    }

    /**
     * This method will register listener for cache events on all nodes,
     * so we can actually see what happens underneath locally and remotely.
     *
     * @throws GridException If failed.
     */
    private static void registerEvents() throws  GridException {
        // Execute this runnable on all grid nodes, local and remote.
        G.grid().run(BROADCAST, new Runnable() {
            @Override public void run() {
                // Event listener which will print out cache events, so we
                // can visualize what happens on remote nodes.
                GridLocalEventListener lsnr = new GridLocalEventListener() {
                    @Override public void onEvent(GridEvent e) {
                        print(e.shortDisplay());
                    }
                };

                // Local node store.
                ConcurrentMap<String, GridLocalEventListener> nodeLocal = G.grid().nodeLocal();

                // GridNodeLocal is a ConcurrentMap attached to every grid node.
                GridLocalEventListener prev = nodeLocal.remove("lsnr");

                // Make sure that we always unsubscribe the old listener regardless
                // of how many times we run the example.
                if (prev != null)
                    G.grid().removeLocalEventListener(prev);

                nodeLocal.put("lsnr", lsnr);

                G.grid().addLocalEventListener(lsnr,
                    EVT_CACHE_OBJECT_PUT,
                    EVT_CACHE_OBJECT_READ,
                    EVT_CACHE_OBJECT_REMOVED);
             }
        });
    }

    /**
     * Demonstrates cache operations similar to {@link ConcurrentMap} API. Note that
     * cache API is a lot richer than the JDK {@link ConcurrentMap} as it supports
     * functional programming and asynchronous mode.
     *
     * @param cache Cache to use.
     * @throws GridException If failed.
     */
    private static void concurrentMap(GridCacheProjection<UUID,Object> cache) throws GridException {
        X.println(">>>");
        X.println(">>> ConcurrentMap Example.");
        X.println(">>>");

        // Organizations.
        Organization org1 = new Organization("GridGain");
        Organization org2 = new Organization("Other");

        // People.
        final Person p1 = new Person(org1, "Jon", "Doe", 1000, "I have a 'Master Degree'");
        Person p2 = new Person(org2, "Jane", "Doe", 2000, "I have a 'Master Degree'");
        Person p3 = new Person(org1, "Jon", "Smith", 3000, "I have a 'Bachelor Degree'");

        /*
         * Convenience projections for type-safe cache views.
         */
        GridCacheProjection<UUID, Organization> orgCache = cache.projection(UUID.class, Organization.class);
        GridCacheProjection<UUID, Person> peopleCache = cache.projection(UUID.class, Person.class);

        /*
         * Basic put.
         */
        orgCache.put(org1.getId(), org1);
        orgCache.put(org2.getId(), org2);

        /*
         * PutIfAbsent.
         */
        Person prev = peopleCache.putIfAbsent(p1.getId(), p1);

        assert prev == null;

        boolean ok = peopleCache.putxIfAbsent(p1.getId(), p1);

        assert !ok; // Second putIfAbsent for p1 must not go through.

        /*
         * Asynchronous putIfAbsent
         */
        GridFuture<Boolean> fut2 = peopleCache.putxIfAbsentAsync(p2.getId(), p2);
        GridFuture<Boolean> fut3 = peopleCache.putxIfAbsentAsync(p3.getId(), p3);

        // Both asynchronous putIfAbsent should be successful.
        assert fut2.get();
        assert fut3.get();

        /*
         * getAll with predicate.
         */
        Map<UUID, Person> people = peopleCache.getAll(new P1<GridCacheEntry<UUID, Person>>() {
            @Override public boolean apply(GridCacheEntry<UUID, Person> e) {
                // Only retrieve p1.
                return e.getKey().equals(p1.getId());
            }
        });

        // Only p1 should have been returned.
        assert people.size() == 1;
        assert people.values().iterator().next().equals(p1);

        /*
         * Replace operations.
         */

        // Replace p1 with p2 only if p1 is present in cache.
        Person p = peopleCache.replace(p1.getId(), p2);

        assert p!= null && p.equals(p1);

        // Put p1 back.
        ok = peopleCache.replace(p1.getId(), p2, p1);

        assert ok;

        /**
         * Remove operation that matches both, key and value.
         * This method is not available on projection, so we
         * call cache directly.
         */
        ok = peopleCache.cache().remove(p3.getId(), p3);

        assert ok;

        // Make sure that remove succeeded.
        assert peopleCache.peek(p3.getId()) == null;

        /*
         * Put operation with a filter.
         */
        ok = peopleCache.putx(p3.getId(), p3, new P1<GridCacheEntry<UUID, Person>>() {
            @Override public boolean apply(GridCacheEntry<UUID, Person> e) {
                return !e.hasValue(); // Only put if currently no value (this should succeed).
            }
        });

        assert ok;

        print("Finished concurrentMap operations example on cache.");
    }

    /**
     * Demonstrates visitor operations on cache, in particular {@code forEach(..)},
     * {@code forAll(..)} and {@code reduce(..)} methods.
     *
     * @param cache Cache to use.
     * @throws GridException If failed.
     */
    private static void visitors(GridCacheProjection<UUID, Object> cache) throws GridException {
        X.println(">>>");
        X.println(">>> Visitors Example.");
        X.println(">>>");

        // We only care about Person objects, therefore,
        // let's get projection to filter only Person instances.
        GridCacheProjection<UUID, Person> people = cache.projection(UUID.class, Person.class);

        // Visit by IDs.
        people.forEach(new CI1<GridCacheEntry<UUID, Person>>() {
            @Override public void apply(GridCacheEntry<UUID, Person> e) {
                print("Visited forEach person: " + e);
            }
        });

        // Asynchronously test each entry.
        GridFuture<Boolean> fut = people.forAllAsync(new P1<GridCacheEntry<UUID, Person>>() {
            // Visit count.
            private int cnt;

            @Override public boolean apply(GridCacheEntry<UUID, Person> e) {
                print("Visited forAll person: " + e);

                return ++cnt < 2; // As example, only visit first 2 elements.
            }
        });

        // Predicate should return false, as there are more than 2 people in cache.
        assert !fut.get();

        // Calculate total salary budget.
        Integer salarySum = people.reduce(new GridReducer<GridCacheEntry<UUID, Person>, Integer>() {
            private int sum;

            @Override public boolean collect(GridCacheEntry<UUID, Person> e) {
                Person p = e.peek();

                if (p != null) {
                    sum += p.getSalary(); // Calculate sum of all salaries.
                }

                return true;
            }

            @Override public Integer apply() {
                return sum;
            }
        });

        assert salarySum != null && salarySum == 6000 : "Invalid salary budget: " + salarySum;

        print("Total salary budget for all employees: " + salarySum);

        print("Finished cache visitor operations.");
    }

    /**
     * Demonstrates collection operations on cache.
     *
     * @param cache Cache to use.
     */
    private static void collections(GridCacheProjection<UUID, Object> cache) {
        X.println(">>>");
        X.println(">>> Collections Example.");
        X.println(">>>");

        // We only care about Person objects, therefore,
        // let's get projection to filter only Person instances.
        GridCacheProjection<UUID, Person> people = cache.projection(UUID.class, Person.class);

        // Iterate only over keys of people with name "Jon".
        for (UUID id : people.keySet(
            new P1<GridCacheEntry<UUID, Person>>() {
                @Override public boolean apply(GridCacheEntry<UUID, Person> e) {
                    Person p = e.peek();

                    return p != null && "Jon".equals(p.getFirstName());
                }
            })) {
            // Print out keys.
            print("Cached ID for person named 'Jon' from keySet: " + id);
        }

        // Delete all people with name "Jane".
        Collection<Person> janes = people.values(new P1<GridCacheEntry<UUID, Person>>() {
            @Override public boolean apply(GridCacheEntry<UUID, Person> e) {
                Person p = e.peek();

                return p != null && "Jane".equals(p.getFirstName());
            }
        });

        assert janes.size() == 1 : "Incorrect 'Janes' size: " + janes.size();

        int cnt = 0;

        for (Iterator<Person> it = janes.iterator(); it.hasNext(); ) {
            Person p = it.next();

            // Make sure that we are deleting "Jane".
            assert "Jane".equals(p.getFirstName()) : "First name is not 'Jane': " + p.getFirstName();

            // Remove all Janes.
            it.remove();

            cnt++;

            print("Removed Jane from cache: " + p);
        }

        assert cnt == 1;

        // Make sure that no Jane is present in cache.
        for (Person p : people.values()) {
            // Person cannot be "Jane".
            assert !"Jane".equals(p.getFirstName());

            print("Person still present in cache: " + p);
        }

        print("Finished collection operations on cache.");
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

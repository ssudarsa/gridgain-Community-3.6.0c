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
import org.gridgain.grid.resources.*
import org.gridgain.grid.typedef.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.GroverProjectionCategory

/**
 * Example shows how can GridGain be used to count triangles in undirectional graph.
 * <p>
 * It first creates adjacency list graph representation and puts it in cache.
 * Adjacency list for each vertex is referenced by vertex number.
 * <p>
 * Then computation and data collocation is used to process each vertex without
 * unnecessary data transfers. For each vertex we know all its neighbors (stored in
 * adjacency list), so we can generate a list of all possible edges that can complete
 * a triangle. If edge exists, triangle exists too (e.g., if vertex '3' is connected to
 * vertices '5' and '8' and edge (5 -> 8) exists, they form a triangle).
 * <p>
 * Reduce step is trivial - we just summarize results for all vertices to get
 * total number of triangles in graph.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache: {@code 'ggstart.sh examples/config/spring-cache.xml'}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
@Use(GroverProjectionCategory)
class GroverCountGraphTrianglesExample {
    /** Cache name. */
    private static String CACHE_NAME = "partitioned"

    /**
     * @param args Command line arguments (none required).
     */
    static void main(String[] args) {
        grover("examples/config/spring-cache.xml") { ->
            // Create example graph.
            def vertices = createGraph(grid$)

            // Count triangles.
            def trianglesCnt = count(grid$, vertices)

            println(">>>")
            println(">>> Number of triangles: " + trianglesCnt)
            println(">>>")
        }
    }

    /**
     * Creates adjacency list graph representation in cache.
     * Created graph has {@code 3} triangles.
     *
     * @param grid Grid.
     * @return Collection of vertices.
     * @throws GridException In case of error.
     */
    private static Collection<Integer> createGraph(Grid grid) throws GridException {
        assert grid != null

        def cache = cache(grid)

        // Put adjacency list for each vertex to cache.
        cache.put(1, Arrays.asList(2, 3))
        cache.put(2, Arrays.asList(1, 3, 4))
        cache.put(3, Arrays.asList(1, 2))
        cache.put(4, Arrays.asList(2, 5, 7))
        cache.put(5, Arrays.asList(4, 6, 7, 8))
        cache.put(6, Arrays.asList(5))
        cache.put(7, Arrays.asList(4, 5, 8))
        cache.put(8, Arrays.asList(5, 7))

        // Return collection of vertices.
        def vertices = new ArrayList<Integer>(8)

        for (int i = 1; i <= 8; i++)
            vertices.add(i)

        vertices
    }

    /**
     * Counts triangles in graph.
     *
     * @param grid Grid.
     * @param vertices Collection of vertices.
     * @return Triangles quantity.
     * @throws GridException In case of error.
     */
    private static int count(final Grid grid, Collection<Integer> vertices) throws GridException {
        assert grid != null

        // Run algorithm closure co-located with each vertex.
        // For each vertex we take all its neighbors (directly from adjacency list),
        // generate the list of all possible edges between neighbors and check their existence.
        // Existence of the edge means existence of triangle.
        def counts = grid.affinityCall(CACHE_NAME, vertices, new AlgorithmClosure())

        // Reduce and return total number of triangles in graph.
        counts.sum()
    }

    /**
     * Gets cache referenced by {@link #CACHE_NAME}.
     *
     * @param grid Grid.
     * @return Cache.
     */
    private static GridCache<Integer, List<Integer>> cache(Grid grid) {
        assert grid != null

        grid.cache(CACHE_NAME)
    }

    /**
     * Main algorithm closure.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    @Typed
    private static class AlgorithmClosure extends COX<Integer> {
        /** Grid. */
        @GridInstanceResource
        private Grid grid

        /** Job context. */
        @GridJobContextResource
        private GridJobContext ctx

        /** {@inheritDoc} */
        @Override public Integer applyx() throws GridException {
            // Get currently processed vertex from job context.
            Integer keyVertex = ctx.affinityKey()

            println(">>> Processing vertex #" + keyVertex)

            // Get neighbors of the vertex.
            def list = GroverCountGraphTrianglesExample.cache(grid).peek(keyVertex)

            // We used 'peek' method to get neighbors, but it should never
            // be 'null', because computations are co-located with data.
            // We never transfer data to computation node.
            assert list != null

            // Triangles counter.
            def cnt = 0

            // Loop through all neighbors.
            for (final def i : list) {
                // We include only neighbors that have larger number than current vertex.
                // This is done to count edges only once (e.g., we count edge (3 -> 5), but
                // not (5 -> 3), even if both of them are found in adjacency lists).
                if (i > keyVertex) {
                    // Nested loop to create all possible pairs of vertices (i.e. edges).
                    for (final def j : list) {
                        // Again, we count each edge only once.
                        if (j > i) {
                            // Check if edge (i -> j) exists. To do this, we run a closure on the
                            // node that stores adjacency list for vertex 'i' and check whether
                            // vertex 'j' is found among its neighbors.
                            def exists = grid.affinityCallOneKey(CACHE_NAME, i) {
                                def l = GroverCountGraphTrianglesExample.cache(grid).peek(i)

                                assert l != null

                                l.contains(j)
                            }

                            // If edge exists, increment triangles counter.
                            if (exists)
                                cnt++
                        }
                    }
                }
            }

            cnt
        }
    }
}

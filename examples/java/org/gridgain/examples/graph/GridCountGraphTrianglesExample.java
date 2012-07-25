// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.graph;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;

import java.util.*;

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
public class GridCountGraphTrianglesExample {
    /** Cache name. */
    private static final String CACHE_NAME = "partitioned";

    /** Ensure singleton. */
    private GridCountGraphTrianglesExample() {
        // No-op
    }

    /**
     * @param args Command line arguments (none required).
     * @throws GridException In case of error.
     */
    public static void main(String[] args) throws GridException {
        G.in("examples/config/spring-cache.xml", new CIX1<Grid>() {
            @Override public void applyx(Grid grid) throws GridException {
                // Create example graph.
                Collection<Integer> vertices = createGraph(grid);

                // Count triangles.
                int trianglesCnt = count(grid, vertices);

                X.println(">>>");
                X.println(">>> Number of triangles: " + trianglesCnt);
                X.println(">>>");
            }
        });
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
        assert grid != null;

        GridCache<Integer, List<Integer>> cache = cache(grid);

        // Put adjacency list for each vertex to cache.
        cache.put(1, Arrays.asList(2, 3));
        cache.put(2, Arrays.asList(1, 3, 4));
        cache.put(3, Arrays.asList(1, 2));
        cache.put(4, Arrays.asList(2, 5, 7));
        cache.put(5, Arrays.asList(4, 6, 7, 8));
        cache.put(6, Arrays.asList(5));
        cache.put(7, Arrays.asList(4, 5, 8));
        cache.put(8, Arrays.asList(5, 7));

        // Return collection of vertices.
        Collection<Integer> vertices = new ArrayList<Integer>(8);

        for (int i = 1; i <= 8; i++)
            vertices.add(i);

        return vertices;
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
        assert grid != null;

        // Run algorithm closure co-located with each vertex.
        // For each vertex we take all its neighbors (directly from adjacency list),
        // generate the list of all possible edges between neighbors and check their existence.
        // Existence of the edge means existence of triangle.
        Collection<Integer> counts = grid.affinityCall(CACHE_NAME, vertices, new COX<Integer>() {
            /** Job context. */
            @GridJobContextResource
            private GridJobContext ctx;

            /** {@inheritDoc} */
            @Override public Integer applyx() throws GridException {
                // Get currently processed vertex from job context.
                Integer keyVertex = ctx.affinityKey();

                X.println(">>> Processing vertex #" + keyVertex);

                // Get neighbors of the vertex.
                List<Integer> list = cache(grid).peek(keyVertex);

                // We used 'peek' method to get neighbors, but it should never
                // be 'null', because computations are co-located with data.
                // We never transfer data to computation node.
                assert list != null;

                // Triangles counter.
                int cnt = 0;

                // Loop through all neighbors.
                for (final int i : list) {
                    // We include only neighbors that have larger number than current vertex.
                    // This is done to count edges only once (e.g., we count edge (3 -> 5), but
                    // not (5 -> 3), even if both of them are found in adjacency lists).
                    if (i > keyVertex) {
                        // Nested loop to create all possible pairs of vertices (i.e. edges).
                        for (final int j : list) {
                            // Again, we count each edge only once.
                            if (j > i) {
                                // Check if edge (i -> j) exists. To do this, we run a closure on the
                                // node that stores adjacency list for vertex 'i' and check whether
                                // vertex 'j' is found among its neighbors.
                                boolean exists = grid.affinityCall(CACHE_NAME, i, new CO<Boolean>() {
                                    @Override public Boolean apply() {
                                        List<Integer> list = cache(grid).peek(i);

                                        assert list != null;

                                        return list.contains(j);
                                    }
                                });

                                // If edge exists, increment triangles counter.
                                if (exists)
                                    cnt++;
                            }
                        }
                    }
                }

                return cnt;
            }
        });

        // Reduce and return total number of triangles in graph.
        return F.sum(counts);
    }

    /**
     * Gets cache referenced by {@link #CACHE_NAME}.
     *
     * @param grid Grid.
     * @return Cache.
     */
    private static GridCache<Integer, List<Integer>> cache(Grid grid) {
        assert grid != null;

        return grid.cache(CACHE_NAME);
    }
}

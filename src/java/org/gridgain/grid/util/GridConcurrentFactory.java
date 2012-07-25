// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;

import java.util.concurrent.*;

import static org.gridgain.grid.GridSystemProperties.*;

/**
 * Concurrent map factory.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridConcurrentFactory {
    /** Default concurrency level. */
    private static final int CONCURRENCY_LEVEL;

    /**
     * Initializes concurrency level.
     */
    static {
        int dfltLevel = 256;

        String s = X.getSystemOrEnv(GG_MAP_CONCURRENCY_LEVEL, Integer.toString(dfltLevel));

        int level;

        try {
            level = Integer.parseInt(s);
        }
        catch (NumberFormatException ignore) {
            level = dfltLevel;
        }

        CONCURRENCY_LEVEL = level;
    }

    /**
     * Ensure singleton.
     */
    private GridConcurrentFactory() {
        // No-op.
    }

    /**
     * Creates concurrent map with default concurrency level.
     *
     * @return New concurrent map.
     */
    public static <K, V> ConcurrentMap<K, V> newMap() {
        return new ConcurrentHashMap<K, V>(16 * CONCURRENCY_LEVEL, 0.75f, CONCURRENCY_LEVEL);
    }

    /**
     * Creates concurrent map with default concurrency level and given {@code initialCapacity}.
     *
     * @param initialCapacity Initial capacity.
     * @return New concurrent map.
     */
    public static <K, V> ConcurrentMap<K, V> newMap(int initialCapacity) {
        return new ConcurrentHashMap<K, V>(initialSize(initialCapacity, CONCURRENCY_LEVEL), 0.75f, CONCURRENCY_LEVEL);
    }

    /**
     * Creates concurrent map with given concurrency level and initialCapacity.
     *
     * @param initialCapacity Initial capacity.
     * @param concurrencyLevel Concurrency level.
     * @return New concurrent map.
     */
    public static <K, V> ConcurrentMap<K, V> newMap(int initialCapacity, int concurrencyLevel) {
        return new ConcurrentHashMap<K, V>(initialSize(initialCapacity, concurrencyLevel), 0.75f, concurrencyLevel);
    }

    /**
     * Creates concurrent set with default concurrency level.
     *
     * @return New concurrent map.
     */
    public static <V> GridConcurrentHashSet<V> newSet() {
        return new GridConcurrentHashSet<V>(16 * CONCURRENCY_LEVEL, 0.75f, CONCURRENCY_LEVEL);
    }

    /**
     * Creates concurrent set with default concurrency level and given {@code initialCapacity}.
     *
     * @param initialCapacity Initial capacity.
     * @return New concurrent map.
     */
    public static <V> GridConcurrentHashSet<V> newSet(int initialCapacity) {
        return new GridConcurrentHashSet<V>(initialSize(initialCapacity, CONCURRENCY_LEVEL), 0.75f, CONCURRENCY_LEVEL);
    }

    /**
     * Creates concurrent set with given concurrency level and initialCapacity.
     *
     * @param initialCapacity Initial capacity.
     * @param concurrencyLevel Concurrency level.
     * @return New concurrent map.
     */
    public static <V> GridConcurrentHashSet<V> newSet(int initialCapacity, int concurrencyLevel) {
        return new GridConcurrentHashSet<V>(initialSize(initialCapacity, concurrencyLevel), 0.75f, concurrencyLevel);
    }

    /**
     * @param cap Capacity.
     * @param concurrencyLevel Concurrency level.
     * @return Calculated size.
     */
    private static int initialSize(int cap, int concurrencyLevel) {
        return cap / concurrencyLevel < 16 ? 16 * concurrencyLevel : cap;
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Caches class loaders for classes.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridClassLoaderCache {
    /** Class loader. */
    private static final ClassLoader CLS_LDR = GridClassLoaderCache.class.getClassLoader();

    /** Maximum cache size. */
    private static final int MAX = 1024;

    /** Cache. */
    private static volatile GridTuple2<ConcurrentHashMap<Class<?>, ClassLoader>, AtomicInteger> ldrCache =
        F.t(new ConcurrentHashMap<Class<?>, ClassLoader>(MAX), new AtomicInteger());

    /**
     * Gets cached ClassLoader for efficiency since class loader detection has proven to be slow.
     *
     * @param cls Class.
     * @return ClassLoader for the class.
     */
    public static ClassLoader classLoader(Class<?> cls) {
        GridTuple2<ConcurrentHashMap<Class<?>, ClassLoader>, AtomicInteger> t = ldrCache;

        ConcurrentHashMap<Class<?>, ClassLoader> cache = t.get1();
        AtomicInteger size = t.get2();

        ClassLoader cached = cache.get(cls);

        if (cached == null) {
            ClassLoader old = cache.putIfAbsent(cls, cached = detectClassLoader(cls));

            if (old != null)
                cached = old;
            else if (size.incrementAndGet() == MAX)
                ldrCache = F.t(new ConcurrentHashMap<Class<?>, ClassLoader>(MAX), new AtomicInteger());
        }

        return cached;
    }

    /**
     * Detects class loader for given class.
     * <p>
     * This method will first check if {@link Thread#getContextClassLoader()} is appropriate.
     * If yes, then context class loader will be returned, otherwise
     * the {@link Class#getClassLoader()} will be returned.
     *
     * @param cls Class to find class loader for.
     * @return Class loader for given class (never {@code null}).
     */
    private static ClassLoader detectClassLoader(Class<?> cls) {
        ClassLoader ldr = Thread.currentThread().getContextClassLoader();

        ClassLoader clsLdr = cls.getClassLoader();

        if (clsLdr == null)
            clsLdr = CLS_LDR;

        if (ldr != null) {
            if (ldr == clsLdr)
                return ldr;

            try {
                // Check if context loader is wider than direct object class loader.
                Class<?> c = Class.forName(cls.getName(), true, ldr);

                if (c == cls)
                    return ldr;
            }
            catch (ClassNotFoundException ignored) {
                // No-op.
            }
        }

        ldr = clsLdr;

        return ldr;
    }

    /**
     * Ensure singleton.
     */
    private GridClassLoaderCache() {
        // No-op.
    }
}

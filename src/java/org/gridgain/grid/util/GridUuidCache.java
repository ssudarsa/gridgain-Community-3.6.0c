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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 *
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridUuidCache {
    /** Maximum cache size. */
    private static final int MAX = 1024;

    /** Cache. */
    private static volatile GridTuple2<ConcurrentHashMap<UUID, UUID>, AtomicInteger> uidCache =
        F.t(new ConcurrentHashMap<UUID, UUID>(MAX), new AtomicInteger());

    /**
     * Gets cached UUID to preserve memory.
     *
     * @param id Read UUID.
     * @return Cached UUID equivalent to the read one.
     */
    public static UUID onGridUuidRead(UUID id) {
        GridTuple2<ConcurrentHashMap<UUID, UUID>, AtomicInteger> t = uidCache;

        ConcurrentHashMap<UUID, UUID> cache = t.get1();
        AtomicInteger size = t.get2();

        UUID cached = cache.get(id);

        if (cached == null) {
            UUID old = cache.putIfAbsent(id, cached = id);

            if (old != null)
                cached = old;
            else if (size.incrementAndGet() == MAX)
                uidCache = F.t(new ConcurrentHashMap<UUID, UUID>(MAX), new AtomicInteger());
        }

        return cached;
    }

    /**
     * Ensure singleton.
     */
    private GridUuidCache() {
        // No-op.
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.never;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.typedef.internal.*;

import static org.gridgain.grid.cache.GridCachePeekMode.*;

/**
 * Cache eviction policy that does not do anything. This eviction policy can be used
 * whenever it is known that cache size is constant and won't change or grow infinitely.
 * <p/>
 * By default this policy allows empty ({@code null}-valued) entries. However, one can enable
 * this policy to evict empty entries either by creating this policy with constructor
 * {@link #GridCacheNeverEvictionPolicy(boolean)} or by calling {@link #setAllowEmptyEntries(boolean)}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheNeverEvictionPolicy<K, V> implements GridCacheEvictionPolicy<K, V>,
    GridCacheNeverEvictionPolicyMBean {
    /** Flag indicating whether to allow empty entries. */
    private volatile boolean allowEmptyEntries = true;

    /**
     * Constructs {@code GridCacheNeverEvictionPolicy} that allows empty entries.
     */
    public GridCacheNeverEvictionPolicy() {
        // No-op.
    }

    /**
     * Constructs {@code GridCacheNeverEvictionPolicy} with allow empty entries
     * flag specified.
     *
     * @param allowEmptyEntries If {@code false}, empty entries will be evicted immediately.
     */
    public GridCacheNeverEvictionPolicy(boolean allowEmptyEntries) {
        this.allowEmptyEntries = allowEmptyEntries;
    }

    /** {@inheritDoc} */
    @Override public boolean isAllowEmptyEntries() {
        return allowEmptyEntries;
    }

    /** {@inheritDoc} */
    @Override public void setAllowEmptyEntries(boolean allowEmptyEntries) {
        this.allowEmptyEntries = allowEmptyEntries;
    }

    /** {@inheritDoc} */
    @Override public void onEntryAccessed(boolean rmv, GridCacheEntry<K, V> entry) {
        if (!allowEmptyEntries && empty(entry))
            entry.evict();
    }

    /**
     * Checks entry for empty value.
     *
     * @param entry Entry to check.
     * @return {@code True} if entry is empty.
     */
    private boolean empty(GridCacheEntry<K, V> entry) {
        try {
            return !entry.hasValue(GLOBAL);
        }
        catch (GridException e) {
            U.error(null, e.getMessage(), e);

            assert false : "Should never happen: " + e;

            return false;
        }
    }
}

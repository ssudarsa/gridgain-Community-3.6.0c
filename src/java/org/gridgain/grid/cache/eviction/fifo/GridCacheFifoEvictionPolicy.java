// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.fifo;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.lang.utils.GridQueue.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;

import static org.gridgain.grid.cache.GridCachePeekMode.*;

/**
 * Eviction policy based on {@code First In First Out (FIFO)} algorithm. This
 * implementation is very efficient since it is lock-free and does not
 * create any additional table-like data structures. The {@code FIFO} ordering
 * information is maintained by attaching ordering metadata to cache entries.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheFifoEvictionPolicy<K, V> implements GridCacheEvictionPolicy<K, V>,
    GridCacheFifoEvictionPolicyMBean {
    /** Tag. */
    private final String meta = UUID.randomUUID().toString();

    /** Maximum size. */
    private volatile int max = -1;

    /** Flag indicating whether empty entries are allowed. */
    private volatile boolean allowEmptyEntries = true;

    /** FIFO queue. */
    private final GridQueue<GridCacheEntry<K, V>> queue = new GridQueue<GridCacheEntry<K, V>>();

    /**
     * Constructs LRU eviction policy with all defaults.
     */
    public GridCacheFifoEvictionPolicy() {
        // No-op.
    }

    /**
     * Constructs FIFO eviction policy with maximum size. Empty entries are allowed.
     *
     * @param max Maximum allowed size of cache before entry will start getting evicted.
     */
    public GridCacheFifoEvictionPolicy(int max) {
        A.ensure(max > 0, "max > 0");

        this.max = max;
    }

    /**
     * Constructs FIFO eviction policy with maximum size and allow empty entries flag specified.
     *
     * @param max Maximum allowed size of cache before entry will start getting evicted.
     * @param allowEmptyEntries If false, {@code false} empty entries will be evicted immediately.
     */
    public GridCacheFifoEvictionPolicy(int max, boolean allowEmptyEntries) {
        this(max);

        this.allowEmptyEntries = allowEmptyEntries;
    }

    /**
     * Gets maximum allowed size of cache before entry will start getting evicted.
     *
     * @return Maximum allowed size of cache before entry will start getting evicted.
     */
    @Override public int getMaxSize() {
        return max;
    }

    /**
     * Sets maximum allowed size of cache before entry will start getting evicted.
     *
     * @param max Maximum allowed size of cache before entry will start getting evicted.
     */
    @Override public void setMaxSize(int max) {
        A.ensure(max > 0, "max > 0");

        this.max = max;
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
    @Override public int getCurrentSize() {
        return queue.size();
    }

    /** {@inheritDoc} */
    @Override public String getMetaAttributeName() {
        return meta;
    }

    /**
     * Gets read-only view on internal {@code FIFO} queue in proper order.
     *
     * @return Read-only view ono internal {@code 'FIFO'} queue.
     */
    public Collection<GridCacheEntry<K, V>> queue() {
        return Collections.unmodifiableCollection(queue);
    }

    /** {@inheritDoc} */
    @Override public void onEntryAccessed(boolean rmv, GridCacheEntry<K, V> entry) {
        if (!rmv) {
            if (!allowEmptyEntries && empty(entry)) {
                Node<GridCacheEntry<K, V>> node = entry.removeMeta(meta);

                if (node != null)
                    queue.unlink(node);

                if (!entry.evict())
                    touch(entry);
            }
            else
                touch(entry);
        }
        else {
            Node<GridCacheEntry<K, V>> node = entry.removeMeta(meta);

            if (node != null)
                queue.unlink(node);
        }

        shrink();
    }

    /**
     * @param entry Entry to touch.
     */
    private void touch(GridCacheEntry<K, V> entry) {
        Node<GridCacheEntry<K, V>> n = entry.meta(meta);

        // Don't reorder existing entries.
        if (n == null) {
            Node<GridCacheEntry<K, V>> old = entry.addMeta(meta, queue.offerx(entry));

            assert old == null : "Node was enqueued by another thread: " + old;
        }
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

    /**
     * Shrinks FIFO queue to maximum allowed size.
     */
    private void shrink() {
        int max = this.max;

        int startSize = queue.size();

        for (int i = 0; i < startSize && queue.size() > max; i++) {
            GridCacheEntry<K, V> entry = queue.poll();

            assert entry != null;

            Node<GridCacheEntry<K, V>> old = entry.removeMeta(meta);

            assert old != null;

            if (!entry.evict())
                touch(entry);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheFifoEvictionPolicy.class, this);
    }
}

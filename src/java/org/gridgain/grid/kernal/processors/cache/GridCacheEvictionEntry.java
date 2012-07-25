// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCachePeekMode.*;

/**
 * Entry wrapper that never obscures obsolete entries from user.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheEvictionEntry<K, V> implements GridCacheEntry<K, V>, Externalizable {
    /** Static logger to avoid re-creation. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Logger. */
    @GridToStringExclude
    protected final GridLogger log;

    /** Cached entry. */
    @GridToStringInclude
    protected GridCacheEntryEx<K, V> cached;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCacheEvictionEntry() {
        log = null;
    }

    /**
     * @param cached Cached entry.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    protected GridCacheEvictionEntry(GridCacheEntryEx<K, V> cached) {
        this.cached = cached;

        log = U.logger(cached.context().kernalContext(), logRef, this);
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> parent() {
        return cached.context().cache();
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheFlag> flags() {
        return cached.context().cache().flags();
    }

    /** {@inheritDoc} */
    @Override public K getKey() throws IllegalStateException {
        return cached.key();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public V getValue() throws IllegalStateException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Nullable @Override public V setValue(V val) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public Object version() {
        try {
            return cached.version();
        }
        catch (GridCacheEntryRemovedException ignore) {
            return cached.obsoleteVersion();
        }
    }

    /** {@inheritDoc} */
    @Override public long expirationTime() {
        return cached.rawExpireTime();
    }

    /** {@inheritDoc} */
    @Override public GridCacheMetrics metrics() {
        return GridCacheMetricsAdapter.copyOf(cached.metrics0());
    }

    /** {@inheritDoc} */
    @Override public boolean primary() {
        GridCacheContext<K, V> ctx = cached.context();

        return ctx.config().getCacheMode() != PARTITIONED ||
            ctx.nodeId().equals(CU.primary(ctx.affinity(cached.key(), CU.allNodes(ctx))).id());
    }

    /** {@inheritDoc} */
    @Override public boolean backup() {
        GridCacheContext<K, V> ctx = cached.context();

        return ctx.config().getCacheMode() == PARTITIONED &&
            F.viewReadOnly(CU.backups(ctx.affinity(cached.key(), CU.allNodes(ctx))), F.node2id()).
                contains(ctx.nodeId());
    }

    /** {@inheritDoc} */
    @Override public int partition() {
        return cached.partition();
    }

    /** {@inheritDoc} */
    @Override public V peek() {
        try {
            return peek(SMART);
        }
        catch (GridException e) {
            // Should never happen.
            throw new GridRuntimeException("Unable to perform entry peek() operation.", e);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V peek(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        try {
            return peek0(SMART, filter, cached.context().tm().localTxx());
        }
        catch (GridException e) {
            // Should never happen.
            throw new GridRuntimeException("Unable to perform entry peek() operation.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable GridCachePeekMode[] modes) throws GridException {
        return peek(F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(@Nullable final Collection<GridCachePeekMode> modes) {
        GridCacheContext<K, V> ctx = cached.context();

        final GridCacheTxEx<K, V> tx = ctx.tm().localTx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<V>() {
            @Nullable @Override public V call() {
                try {
                    return peek0(modes, CU.<K, V>empty(), tx);
                }
                catch (GridException e) {
                    throw new GridClosureException(e);
                }
            }
        }), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(@Nullable GridCachePeekMode[] modes) {
        return peekAsync(F.asList(modes));
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable @Override public V peek(@Nullable GridCachePeekMode mode) throws GridException {
        return peek0(mode, CU.<K, V>empty(), cached.context().tm().localTxx());
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable Collection<GridCachePeekMode> modes) throws GridException {
        return peek0(modes, CU.<K, V>empty(), cached.context().tm().localTxx());
    }

    /**
     * @param mode Peek mode.
     * @param filter Optional entry filter.
     * @param tx Transaction to peek at (if mode is TX).
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peek0(@Nullable GridCachePeekMode mode,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter, @Nullable GridCacheTxEx<K, V> tx)
        throws GridException {
        assert tx == null || tx.local();

        if (mode == null)
            mode = SMART;

        try {
            return cached.peek0(false, mode, filter, tx);
        }
        catch (GridCacheEntryRemovedException ignore) {
            return null;
        }
        catch (GridCacheFilterFailedException e) {
            e.printStackTrace();

            assert false;

            return null;
        }
    }

    /**
     * @param modes Peek modes.
     * @param filter Optional entry filter.
     * @param tx Transaction to peek at (if modes contains TX value).
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @Nullable private V peek0(@Nullable Collection<GridCachePeekMode> modes,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter, GridCacheTxEx<K, V> tx) throws GridException {
        if (F.isEmpty(modes))
            return peek0(SMART, filter, tx);

        assert modes != null;

        for (GridCachePeekMode mode : modes) {
            V val = peek0(mode, filter, tx);

            if (val != null)
                return val;
        }

        return null;
    }

    /**
     * @return Unsupported exception.
     */
    private RuntimeException unsupported() {
        return new UnsupportedOperationException("Operation not supported during eviction.");
    }

    /** {@inheritDoc} */
    @Nullable @Override public V reload(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> reloadAsync(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean evict(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheContext<K, V> ctx = cached.context();

        try {
            return ctx.evicts().evict(cached, null, false, filter);
        }
        catch (GridException e) {
            U.error(log, "Failed to evict entry from cache: " + cached, e);

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean clear(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean compact(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Nullable @Override public V get(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> getAsync(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Nullable @Override public V set(V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> setAsync(V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean setx(V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> setxAsync(V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Nullable @Override public V replace(V val) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> replaceAsync(V val) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean replace(V oldVal, V newVal) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replaceAsync(V oldVal, V newVal) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public long timeToLive() {
        return cached.rawTtl();
    }

    /** {@inheritDoc} */
    @Override public void timeToLive(long ttl) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Nullable @Override public V setIfAbsent(V val) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> setIfAbsentAsync(V val) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean setxIfAbsent(V val) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> setxIfAbsentAsync(V val) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean replacex(V val) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replacexAsync(V val) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection() {
        return cached.context().cache().gridProjection(Arrays.asList(cached.key()));
    }

    /** {@inheritDoc} */
    @Override public boolean hasValue(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return peek(filter) != null;
    }

    /** {@inheritDoc} */
    @Override public boolean hasValue(@Nullable GridCachePeekMode mode) throws GridException {
        return peek(mode) != null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public V remove(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> removeAsync(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean removex(GridPredicate<GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removexAsync(GridPredicate<GridCacheEntry<K, V>>[] filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean remove(V val) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removeAsync(V val) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public <V> V addMeta(String name, V val) {
        return cached.addMeta(name, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> V meta(String name) {
        return (V)cached.meta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> V removeMeta(String name) {
        return (V)cached.removeMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V> Map<String, V> allMeta() {
        return cached.allMeta();
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return cached.hasMeta(name);
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name, Object val) {
        return cached.hasMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V putMetaIfAbsent(String name, V val) {
        return cached.putMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V putMetaIfAbsent(String name, Callable<V> c) {
        return cached.putMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, V val) {
        return cached.addMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, Callable<V> c) {
        return cached.addMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean replaceMeta(String name, V curVal, V newVal) {
        return cached.replaceMeta(name, curVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        cached.copyMeta(from);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        cached.copyMeta(data);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean removeMeta(String name, V val) {
        return cached.removeMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public boolean isLocked() {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean isLockedByThread() {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean lock(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public boolean lock(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public void unlock(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        throw unsupported();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(cached.context());
        out.writeObject(cached.key());
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        GridCacheContext<K, V> ctx = (GridCacheContext<K, V>)in.readObject();
        K key = (K)in.readObject();

        cached = ctx.cache().entryEx(key);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return cached.key().hashCode();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (obj instanceof GridCacheEvictionEntry) {
            GridCacheEvictionEntry<K, V> other = (GridCacheEvictionEntry<K, V>)obj;

            V v1 = peek();
            V v2 = other.peek();

            return
                cached.key().equals(other.cached.key()) &&
                F.eq(cached.context().cache().name(), other.cached.context().cache().name()) &&
                F.eq(v1, v2);
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheEvictionEntry.class, this);
    }
}

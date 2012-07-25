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
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Cache entry wrapper.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheEntryWrapper<K, V> implements GridCacheEntry<K, V> {
    /** Wrapped entry. */
    protected final GridCacheEntry<K, V> e;

    /**
     * @param e Wrapped entry.
     */
    public GridCacheEntryWrapper(GridCacheEntry<K, V> e) {
        assert e != null;

        this.e = e;
    }

    /**
     * @return Wrapped entry.
     */
    public GridCacheEntry<K, V> wrapped() {
        return e;
    }

    /** {@inheritDoc} */
    @Override public boolean clear(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.clear(filter);
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheFlag> flags() {
        return e.flags();
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> parent() {
        return e.parent();
    }

    /** {@inheritDoc} */
    @Override public boolean hasValue(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.hasValue(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean hasValue(@Nullable GridCachePeekMode mode) throws GridException {
        return e.hasValue(mode);
    }

    /** {@inheritDoc} */
    @Override public V peek() {
        return e.peek();
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.peek(filter);
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable GridCachePeekMode mode) throws GridException {
        return e.peek(mode);
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable GridCachePeekMode... modes) throws GridException {
        return e.peek(modes);
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable Collection<GridCachePeekMode> modes) throws GridException {
        return e.peek(modes);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(@Nullable GridCachePeekMode... modes) {
        return e.peekAsync(modes);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(@Nullable Collection<GridCachePeekMode> modes) {
        return e.peekAsync(modes);
    }

    /** {@inheritDoc} */
    @Override public V reload(GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException {
        return e.reload(filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> reloadAsync(GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.reloadAsync(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean isLocked() {
        return e.isLocked();
    }

    /** {@inheritDoc} */
    @Override public boolean isLockedByThread() {
        return e.isLockedByThread();
    }

    /** {@inheritDoc} */
    @Override public Object version() {
        return e.version();
    }

    /** {@inheritDoc} */
    @Override public long expirationTime() {
        return e.expirationTime();
    }

    /** {@inheritDoc} */
    @Override public long timeToLive() {
        return e.timeToLive();
    }

    /** {@inheritDoc} */
    @Override public void timeToLive(long ttl) {
        e.timeToLive(ttl);
    }

    /** {@inheritDoc} */
    @Override public GridCacheMetrics metrics() {
        return e.metrics();
    }

    /** {@inheritDoc} */
    @Override public boolean primary() {
        return e.primary();
    }

    /** {@inheritDoc} */
    @Override public boolean backup() {
        return e.backup();
    }

    /** {@inheritDoc} */
    @Override public int partition() {
        return e.partition();
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection() {
        return e.gridProjection();
    }

    /** {@inheritDoc} */
    @Override public V getValue() {
        return e.getValue();
    }

    /** {@inheritDoc} */
    @Override public V get(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException {
        return e.get(filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> getAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.getAsync(filter);
    }

    /** {@inheritDoc} */
    @Override public V setValue(V val) {
        return e.setValue(val);
    }

    /** {@inheritDoc} */
    @Override public V set(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException {
        return e.set(val, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> setAsync(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.setAsync(val, filter);
    }

    /** {@inheritDoc} */
    @Override public V setIfAbsent(V val) throws GridException {
        return e.setIfAbsent(val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> setIfAbsentAsync(V val) {
        return e.setIfAbsentAsync(val);
    }

    /** {@inheritDoc} */
    @Override public boolean setx(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException {
        return e.setx(val, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> setxAsync(V val,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.setxAsync(val, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean setxIfAbsent(@Nullable V val) throws GridException {
        return e.setxIfAbsent(val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> setxIfAbsentAsync(V val) {
        return e.setxIfAbsentAsync(val);
    }

    /** {@inheritDoc} */
    @Override public V replace(V val) throws GridException {
        return e.replace(val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> replaceAsync(V val) {
        return e.replaceAsync(val);
    }

    /** {@inheritDoc} */
    @Override public boolean replacex(V val) throws GridException {
        return e.replacex(val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replacexAsync(V val) {
        return e.replacexAsync(val);
    }

    /** {@inheritDoc} */
    @Override public boolean replace(V oldVal, V newVal) throws GridException {
        return e.replace(oldVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replaceAsync(V oldVal, V newVal) {
        return e.replaceAsync(oldVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public V remove(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException {
        return e.remove(filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> removeAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.removeAsync(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean removex(@Nullable GridPredicate<GridCacheEntry<K, V>>... filter) throws GridException {
        return e.removex(filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removexAsync(@Nullable GridPredicate<GridCacheEntry<K, V>>... filter) {
        return e.removexAsync(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean remove(V val) throws GridException {
        return e.remove(val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removeAsync(V val) {
        return e.removeAsync(val);
    }

    /** {@inheritDoc} */
    @Override public boolean evict(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.evict(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException {
        return e.invalidate(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean compact(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException {
        return e.compact(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean lock(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException {
        return e.lock(filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.lockAsync(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean lock(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException {
        return e.lock(timeout, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return e.lockAsync(timeout, filter);
    }

    /** {@inheritDoc} */
    @Override public void unlock(GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException {
        e.unlock(filter);
    }

    /** {@inheritDoc} */
    @Override public K getKey() {
        return e.getKey();
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        e.copyMeta(from);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        e.copyMeta(data);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> T addMeta(String name, T val) {
        return e.<T>addMeta(name, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> T putMetaIfAbsent(String name, T val) {
        return e.<T>putMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> T putMetaIfAbsent(String name, Callable<T> c) {
        return e.<T>putMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> T addMetaIfAbsent(String name, T val) {
        return e.<T>addMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> T addMetaIfAbsent(String name, @Nullable Callable<T> c) {
        return e.<T>addMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> T meta(String name) {
        return e.<T>meta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> T removeMeta(String name) {
        return e.<T>removeMeta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> boolean removeMeta(String name, T val) {
        return e.<T>removeMeta(name, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> Map<String, T> allMeta() {
        return e.<T>allMeta();
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return e.hasMeta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> boolean hasMeta(String name, T val) {
        return e.<T>hasMeta(name, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> boolean replaceMeta(String name, T curVal, T newVal) {
        return e.<T>replaceMeta(name, curVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return e.toString();
    }
}

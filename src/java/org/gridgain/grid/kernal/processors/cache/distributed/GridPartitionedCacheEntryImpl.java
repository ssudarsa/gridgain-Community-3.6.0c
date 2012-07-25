// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.cache.GridCachePeekMode.*;

/**
 * Partitioned cache entry public API.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridPartitionedCacheEntryImpl<K, V> extends GridCacheEntryImpl<K, V> {
    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridPartitionedCacheEntryImpl() {
        // No-op.
    }

    /**
     * @param nearPrj Parent projection or {@code null} if entry belongs to default cache.
     * @param ctx Near cache context.
     * @param key key.
     * @param cached Cached entry (either from near or dht cache map).
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public GridPartitionedCacheEntryImpl(GridCacheProjectionImpl<K, V> nearPrj, GridCacheContext<K, V> ctx, K key,
        @Nullable GridCacheEntryEx<K, V> cached) {
        super(nearPrj, ctx, key, cached);

        assert !this.ctx.isDht();
    }

    /**
     * @return Dht cache.
     */
    public GridDhtCache<K, V> dht() {
        return ctx.near().dht();
    }

    /**
     * @return Near cache.
     */
    public GridNearCache<K, V> near() {
        return ctx.near();
    }

    /**
     * @param filter Optional filter.
     * @return {@code True} if evicted.
     */
    public boolean evictNearOnly(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return ctx.near().evictNearOnly(key, filter);
    }

    /**
     * @param filter Filter.
     * @return {@code True} if evicted.
     */
    public boolean evictDhtOnly(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return ctx.near().dht().evict(key, filter);
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
    @Override public V peek(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        V val = super.peek(filter);

        if (val == null)
            val = peekDht(filter);

        return val;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable @Override public V peek(@Nullable GridCachePeekMode mode) throws GridException {
        if (mode == NEAR_ONLY)
            return peekNear0(GLOBAL, CU.<K, V>empty());

        V val = null;

        if (mode != PARTITIONED_ONLY)
            val = super.peek(mode);

        if (val == null)
            val = peekDht0(mode, CU.<K, V>empty());

        return val;
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable Collection<GridCachePeekMode> modes) throws GridException {
        if (modes.contains(NEAR_ONLY))
            return peekNear0(modes, CU.<K, V>empty());

        V val = null;

        if (!modes.contains(PARTITIONED_ONLY))
            val = super.peek(modes);

        if (val == null)
            val = peekDht0(modes, CU.<K, V>empty());

        return val;
    }

    /**
     * @param filter Filter.
     * @return Peeked value.
     */
    @Nullable public V peekDht(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        try {
            return peekDht0(SMART, filter);
        }
        catch (GridException e) {
            // Should never happen.
            throw new GridRuntimeException("Unable to perform entry peek() operation.", e);
        }
    }

    /**
     * @param modes Peek modes.
     * @param filter Optional entry filter.
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @Nullable private V peekNear0(@Nullable Collection<GridCachePeekMode> modes,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (F.isEmpty(modes))
            return peekNear0(SMART, filter);

        assert modes != null;

        for (GridCachePeekMode mode : modes) {
            V val = peekNear0(mode, filter);

            if (val != null)
                return val;
        }

        return null;
    }

    /**
     * @param mode Peek mode.
     * @param filter Optional entry filter.
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peekNear0(@Nullable GridCachePeekMode mode,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (mode == null)
            mode = SMART;

        while (true) {
            GridCacheProjectionImpl<K, V> prjPerCall =
                (prj instanceof GridCacheProjectionImpl) ? ((GridCacheProjectionImpl)prj) : null;

            if (prjPerCall != null)
                filter = ctx.vararg(F.and(ctx.vararg(prj.entryPredicate()), filter));

            GridCacheProjectionImpl<K, V> prev = ctx.gate().enter(prjPerCall);

            try {
                GridCacheEntryEx<K, V> entry = near().peekEx(key);

                return entry == null ? null : ctx.cloneOnFlag(entry.peek0(false, mode, filter, ctx.tm().localTxx()));
            }
            catch (GridCacheEntryRemovedException ignore) {
                // No-op.
            }
            catch (GridCacheFilterFailedException e) {
                e.printStackTrace();

                assert false;

                return null;
            }
            finally {
                ctx.gate().leave(prev);
            }
        }
    }

    /**
     * @param modes Peek modes.
     * @param filter Optional entry filter.
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @Nullable private V peekDht0(@Nullable Collection<GridCachePeekMode> modes,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (F.isEmpty(modes))
            return peekDht0(SMART, filter);

        assert modes != null;

        for (GridCachePeekMode mode : modes) {
            V val = peekDht0(mode, filter);

            if (val != null)
                return val;
        }

        return null;
    }

    /**
     * @param mode Peek mode.
     * @param filter Optional entry filter.
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peekDht0(@Nullable GridCachePeekMode mode,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (mode == null)
            mode = SMART;

        while (true) {
            GridCacheProjectionImpl<K, V> prjPerCall =
                (prj instanceof GridCacheProjectionImpl) ? ((GridCacheProjectionImpl)prj) : null;

            if (prjPerCall != null)
                filter = ctx.vararg(F.and(ctx.vararg(prj.entryPredicate()), filter));

            GridCacheProjectionImpl<K, V> prev = ctx.gate().enter(prjPerCall);

            try {
                GridCacheEntryEx<K, V> entry = dht().peekEx(key);

                return entry == null ? null : ctx.cloneOnFlag(entry.peek0(false, mode, filter, ctx.tm().localTxx()));
            }
            catch (GridCacheEntryRemovedException ignore) {
                // No-op.
            }
            catch (GridCacheFilterFailedException e) {
                e.printStackTrace();

                assert false;

                return null;
            }
            finally {
                ctx.gate().leave(prev);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(@Nullable final Collection<GridCachePeekMode> modes) {
        final GridCacheTxEx<K, V> tx = ctx.tm().localTxx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<V>() {
            @Nullable @Override public V call() throws GridException {
                ctx.tm().txContext(tx);

                return peek(modes);
            }
        }), true);
    }

    /** {@inheritDoc} */
    @Override protected GridCacheEntryEx<K, V> entryEx() {
        try {
            return ctx.belongs(key, ctx.localNode()) ? dht().entryEx(key) : near().entryEx(key);
        }
        catch (GridDhtInvalidPartitionException ignore) {
            return near().entryEx(key);
        }
    }

    /** {@inheritDoc} */
    @Override protected GridCacheEntryEx<K, V> peekEx() {
        try {
            return ctx.belongs(key, ctx.localNode()) ? dht().peekEx(key) : near().peekEx(key);
        }
        catch (GridDhtInvalidPartitionException ignore) {
            return near().peekEx(key);
        }
    }

    /** {@inheritDoc} */
    @Override public <V1> V1 addMeta(String name, V1 val) {
        V1 v = null;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            v = de.addMeta(name, val);

        GridNearCacheEntry<K, V> ne = de != null ? near().peekExx(key) : near().entryExx(key);

        if (ne != null) {
            V1 v1 = ne.addMeta(name, val);

            if (v == null)
                v = v1;
        }

        return v;
    }

    /** {@inheritDoc} */
    @Override public <V1> V1 addMetaIfAbsent(String name, Callable<V1> c) {
        V1 v = null;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            v = de.addMetaIfAbsent(name, c);

        GridNearCacheEntry<K, V> ne = de != null ? near().peekExx(key) : near().entryExx(key);

        if (ne != null) {
            V1 v1 = ne.addMetaIfAbsent(name, c);

            if (v == null)
                v = v1;
        }

        return v;
    }

    /** {@inheritDoc} */
    @Override public <V1> V1 addMetaIfAbsent(String name, V1 val) {
        V1 v = null;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            v = de.addMetaIfAbsent(name, val);

        GridNearCacheEntry<K, V> ne = de != null ? near().peekExx(key) : near().entryExx(key);

        if (ne != null) {
            V1 v1 = ne.addMetaIfAbsent(name, val);

            if (v == null)
                v = v1;
        }

        return v;
    }

    /** {@inheritDoc} */
    @Override public <V1> Map<String, V1> allMeta() {
        Map<String, V1> m = null;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            m = de.allMeta();

        GridNearCacheEntry<K, V> ne = near().peekExx(key);

        if (ne != null) {
            Map<String, V1> m1 = ne.allMeta();

            if (m == null)
                m = m1;
            else if (!m1.isEmpty()) {
                for (Map.Entry<String, V1> e1 : m1.entrySet())
                    if (!m.containsKey(e1.getKey())) // Give preference to DHT.
                        m.put(e1.getKey(), e1.getValue());
            }
        }

        return m;
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        boolean b = false;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            b = de.hasMeta(name);

        GridNearCacheEntry<K, V> ne = near().peekExx(key);

        if (ne != null)
            b |= ne.hasMeta(name);

        return b;
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name, Object val) {
        boolean b = false;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            b = de.hasMeta(name, val);

        GridNearCacheEntry<K, V> ne = near().peekExx(key);

        if (ne != null)
            b |= ne.hasMeta(name, val);

        return b;
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantCast"})
    @Override public <V1> V1 meta(String name) {
        V1 v = null;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            v = (V1)de.meta(name);

        GridNearCacheEntry<K, V> ne = near().peekExx(key);

        if (ne != null) {
            V1 v1 = (V1)ne.meta(name);

            if (v == null)
                v = v1;
        }

        return v;
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantCast"})
    @Override public <V1> V1 putMetaIfAbsent(String name, Callable<V1> c) {
        V1 v = null;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            v = (V1)de.putMetaIfAbsent(name, c);

        GridNearCacheEntry<K, V> ne = de != null ? near().peekExx(key) : near().entryExx(key);

        if (ne != null) {
            V1 v1 = (V1)ne.putMetaIfAbsent(name, c);

            if (v == null)
                v = v1;
        }

        return v;
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantCast"})
    @Override public <V1> V1 putMetaIfAbsent(String name, V1 val) {
        V1 v = null;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            v = (V1)de.putMetaIfAbsent(name, val);

        GridNearCacheEntry<K, V> ne = de != null ? near().peekExx(key) : near().entryExx(key);

        if (ne != null) {
            V1 v1 = (V1)ne.putMetaIfAbsent(name, val);

            if (v == null)
                v = v1;
        }

        return v;
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantCast"})
    @Override public <V1> V1 removeMeta(String name) {
        V1 v = null;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            v = (V1)de.removeMeta(name);

        GridNearCacheEntry<K, V> ne = near().peekExx(key);

        if (ne != null) {
            V1 v1 = (V1)ne.removeMeta(name);

            if (v == null)
                v = v1;
        }

        return v;
    }

    /** {@inheritDoc} */
    @Override public <V1> boolean removeMeta(String name, V1 val) {
        boolean b = false;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            b = de.removeMeta(name, val);

        GridNearCacheEntry<K, V> ne = near().peekExx(key);

        if (ne != null)
            b |= ne.removeMeta(name, val);

        return b;
    }

    /** {@inheritDoc} */
    @Override public <V1> boolean replaceMeta(String name, V1 curVal, V1 newVal) {
        boolean b = false;

        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            b = de.replaceMeta(name, curVal, newVal);

        GridNearCacheEntry<K, V> ne = near().peekExx(key);

        if (ne != null)
            b |= ne.replaceMeta(name, curVal, newVal);

        return b;
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            de.copyMeta(data);

        GridNearCacheEntry<K, V> ne = de != null ? near().peekExx(key) : near().entryExx(key);

        if (ne != null)
            ne.copyMeta(data);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        GridDhtCacheEntry<K, V> de = dht().peekExx(key);

        if (de != null)
            de.copyMeta(from);

        GridNearCacheEntry<K, V> ne = de != null ? near().peekExx(key) : near().entryExx(key);

        if (ne != null)
            ne.copyMeta(from);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridPartitionedCacheEntryImpl.class, this, super.toString());
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 *
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridNearGetFuture<K, V> extends GridCompoundIdentityFuture<Map<K, V>>
    implements GridCacheFuture<Map<K, V>> {
    /** Logger reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Context. */
    private GridCacheContext<K, V> cctx;

    /** Keys. */
    private Collection<? extends K> keys;

    /** Reload flag. */
    private boolean reload;

    /** Future ID. */
    private GridUuid futId;

    /** Version. */
    private GridCacheVersion ver;

    /** Transaction. */
    private GridCacheTxLocalEx<K, V> tx;

    /** Filters. */
    private GridPredicate<? super GridCacheEntry<K, V>>[] filters;

    /** Logger. */
    private GridLogger log;

    /** Trackable flag. */
    private boolean trackable = true;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearGetFuture() {
        // No-op.
    }

    /**
     * @param cctx Context.
     * @param keys Keys.
     * @param reload Reload flag.
     * @param tx Transaction.
     * @param filters Filters.
     */
    public GridNearGetFuture(GridCacheContext<K, V> cctx, Collection<? extends K> keys, boolean reload,
        @Nullable GridCacheTxLocalEx<K, V> tx, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filters) {
        super(cctx.kernalContext(), CU.<K, V>mapsReducer(keys.size()));

        assert cctx != null;
        assert !F.isEmpty(keys);

        this.cctx = cctx;
        this.keys = keys;
        this.reload = reload;
        this.filters = filters;
        this.tx = tx;

        futId = GridUuid.randomUuid();

        ver = tx == null ? cctx.versions().next() : tx.xidVersion();

        log = U.logger(ctx, logRef, GridNearGetFuture.class);
    }

    /**
     * Initializes future.
     */
    void init() {
        map(keys, Collections.<GridRichNode, LinkedHashMap<K, Boolean>>emptyMap());

        markInitialized();
    }

    /** {@inheritDoc} */
    @Override public boolean trackable() {
        return trackable; // TODO: can be optimized to return false for local mappings.
    }

    /** {@inheritDoc} */
    @Override public void markNotTrackable() {
        trackable = false;
    }

    /**
     * @return Keys.
     */
    Collection<? extends K> keys() {
        return keys;
    }

    /** {@inheritDoc} */
    @Override public GridUuid futureId() {
        return futId;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return ver;
    }

    /** {@inheritDoc} */
    @Override public Collection<? extends GridNode> nodes() {
        return
            F.viewReadOnly(futures(), new GridClosure<GridFuture<Map<K, V>>, GridRichNode>() {
                @Nullable @Override public GridRichNode apply(GridFuture<Map<K, V>> f) {
                    if (isMini(f))
                        return ((MiniFuture)f).node();

                    return cctx.rich().rich(cctx.discovery().localNode());
                }
            });
    }

    /** {@inheritDoc} */
    @Override public boolean onNodeLeft(UUID nodeId) {
        for (GridFuture<Map<K, V>> fut : futures())
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.node().id().equals(nodeId)) {
                    f.onResult(new GridTopologyException("Remote node left grid (will retry): " + nodeId));

                    return true;
                }
            }

        return false;
    }

    /**
     * @param nodeId Sender.
     * @param res Result.
     */
    void onResult(UUID nodeId, GridNearGetResponse<K, V> res) {
        for (GridFuture<Map<K, V>> fut : futures())
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.futureId().equals(res.miniId())) {
                    assert f.node().id().equals(nodeId);

                    f.onResult(res);
                }
            }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(Map<K, V> res, Throwable err) {
        if (super.onDone(res, err)) {
            // Don't forget to clean up.
            cctx.mvcc().removeFuture(this);

            return true;
        }

        return false;
    }

    /**
     * @param f Future.
     * @return {@code True} if mini-future.
     */
    private boolean isMini(GridFuture<Map<K, V>> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     * @param keys Keys.
     * @param mapped Mappings to check for duplicates.
     */
    private void map(Collection<? extends K> keys, Map<GridRichNode, LinkedHashMap<K, Boolean>> mapped) {
        long topVer = tx == null ? -1 : tx.topologyVersion();

        Collection<GridRichNode> nodes = CU.allNodes(cctx, topVer);

        Map<GridRichNode, LinkedHashMap<K, Boolean>> mappings =
            new HashMap<GridRichNode, LinkedHashMap<K, Boolean>>(nodes.size());

        // Assign keys to primary nodes.
        for (K key : keys)
            map(key, mappings, nodes, mapped);

        if (isDone())
            return;

        // Create mini futures.
        for (Map.Entry<GridRichNode, LinkedHashMap<K, Boolean>> entry : mappings.entrySet()) {
            final GridRichNode n = entry.getKey();

            final LinkedHashMap<K, Boolean> mappedKeys = entry.getValue();

            assert !mappedKeys.isEmpty();

            // If this is the primary or backup node for the keys.
            if (n.isLocal()) {
                final GridDhtFuture<Collection<GridCacheEntryInfo<K, V>>> fut =
                    dht().getDhtAsync(n.id(), -1, mappedKeys, reload, topVer, filters);

                final Collection<Integer> invalidParts = fut.invalidPartitions();

                if (!F.isEmpty(invalidParts)) {
                    Collection<K> remapKeys = new LinkedList<K>(F.view(keys, new P1<K>() {
                        @Override public boolean apply(K key) {
                            return invalidParts.contains(cctx.partition(key));
                        }
                    }));

                    // Remap recursively.
                    map(remapKeys, mappings);
                }

                // Add new future.
                add(new GridEmbeddedFuture<Map<K, V>, Collection<GridCacheEntryInfo<K, V>>>(
                    cctx.kernalContext(),
                    fut,
                    new C2<Collection<GridCacheEntryInfo<K, V>>, Exception, Map<K, V>>() {
                        @Override public Map<K, V> apply(Collection<GridCacheEntryInfo<K, V>> infos, Exception e) {
                            if (e != null) {
                                U.error(log, "Failed to get values from dht cache [fut=" + fut + "]", e);

                                onDone(e);

                                return Collections.emptyMap();
                            }

                            return loadEntries(n.id(), mappedKeys.keySet(), infos);
                        }
                    })
                );
            }
            else {
                MiniFuture fut = new MiniFuture(n, mappedKeys);

                GridCacheMessage<K, V> req = new GridNearGetRequest<K, V>(futId, fut.futureId(), ver, mappedKeys,
                    reload, topVer, filters);

                add(fut); // Append new future.

                try {
                    cctx.io().send(n, req);
                }
                catch (GridTopologyException e) {
                    fut.onResult(e);
                }
                catch (GridException e) {
                    // Fail the whole thing.
                    fut.onResult(e);
                }
            }
        }
    }

    /**
     * @param mappings Mappings.
     * @param key Key to map.
     * @param nodes Nodes.
     * @param mapped Previously mapped.
     */
    private void map(K key, Map<GridRichNode, LinkedHashMap<K, Boolean>> mappings,
        Collection<GridRichNode> nodes, Map<GridRichNode, LinkedHashMap<K, Boolean>> mapped) {
        GridCacheEntryEx<K, V> entry = cache().peekEx(key);

        while (true) {
            try {
                V v = null;

                boolean near = entry != null;

                // First we peek into near cache.
                if (entry != null)
                    v = entry.innerGet(tx, /*swap*/false, /*read-through*/false, /*fail-fast*/true,
                        true, true, filters);

                if (v == null) {
                    try {
                        GridDhtCache<K, V> dht = cache().dht();

                        entry = dht.context().isSwapEnabled() ? dht.entryEx(key) : dht.peekEx(key);

                        // If near cache does not have value, then we peek DHT cache.
                        if (entry != null) {
                            v = entry.innerGet(tx, /*swap*/true, /*read-through*/false, /*fail-fast*/true, true, !near,
                                filters);

                            // Entry was not in memory or in swap, so we remove it from cache.
                            if (v == null && entry.markObsolete(ver))
                                cache().dht().removeIfObsolete(key);
                        }
                    }
                    catch (GridDhtInvalidPartitionException ignored) {
                        // No-op.
                    }
                }

                if (v != null && !reload)
                    add(new GridFinishedFuture<Map<K, V>>(cctx.kernalContext(), Collections.singletonMap(key, v)));
                else {
                    GridRichNode node = CU.primary0(cctx.affinity(key, nodes));

                    LinkedHashMap<K, Boolean> keys = mapped.get(node);

                    if (keys != null && keys.containsKey(key)) {
                        onDone(new GridException("Failed to remap key to a new node (key got remapped to the " +
                            "same node) [key=" + key + ", node=" + U.toShortString(node) + ", mappings=" +
                            mapped + ']'));

                        return;
                    }

                    // Don't add reader if transaction acquires lock anyway to avoid deadlock.
                    boolean addRdr = tx == null || tx.optimistic();

                    if (!addRdr && tx.readCommitted() && !tx.writeSet().contains(key))
                        addRdr = true;

                    LinkedHashMap<K, Boolean> old = mappings.get(node);

                    if (old == null)
                        mappings.put(node, old = new LinkedHashMap<K, Boolean>(3, 1f));

                    old.put(key, addRdr);
                }

                break;
            }
            catch (GridException e) {
                onDone(e);

                break;
            }
            catch (GridCacheEntryRemovedException ignored) {
                entry = cache().peekEx(key);
            }
            catch (GridCacheFilterFailedException e) {
                if (log.isDebugEnabled())
                    log.debug("Filter validation failed for entry: " + e);

                break;
            }
        }
    }

    /**
     * @return Near cache.
     */
    private GridNearCache<K, V> cache() {
        return (GridNearCache<K, V>)cctx.cache();
    }

    /**
     * @return DHT cache.
     */
    private GridDhtCache<K, V> dht() {
        return cache().dht();
    }

    /**
     * @param nodeId Node id.
     * @param keys Keys.
     * @param infos Entry infos.
     * @return Result map.
     */
    private Map<K, V> loadEntries(UUID nodeId, Collection<K> keys, Collection<GridCacheEntryInfo<K, V>> infos) {
        boolean empty = F.isEmpty(keys);

        Map<K, V> map = empty ? Collections.<K, V>emptyMap() : new GridLeanMap<K, V>(keys.size());

        if (!empty) {
            GridCacheVersion ver = F.isEmpty(infos) ? null : cctx.versions().next();

            for (GridCacheEntryInfo<K, V> info : infos) {
                // Entries available locally in DHT should not loaded into near cache for reading.
                if (!ctx.localNodeId().equals(nodeId)) {
                    while (true) {
                        try {
                            GridNearCacheEntry<K, V> entry = cache().entryExx(info.key());

                            // Load entry into cache.
                            entry.loadedValue(tx, nodeId, info.value(), info.valueBytes(), ver, info.ttl(),
                                info.expireTime(), true);

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry while processing get response (will retry");
                        }
                        catch (GridException e) {
                            // Fail.
                            onDone(e);

                            return Collections.emptyMap();
                        }
                    }
                }

                map.put(info.key(), info.value());
            }
        }

        return map;
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<Map<K, V>> {
        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** Node ID. */
        private GridRichNode node;

        /** Keys. */
        @GridToStringInclude
        private LinkedHashMap<K, Boolean> keys;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            // No-op.
        }

        /**
         * @param node Node.
         * @param keys Keys.
         */
        MiniFuture(GridRichNode node, LinkedHashMap<K, Boolean> keys) {
            super(cctx.kernalContext());

            this.node = node;
            this.keys = keys;
        }

        /**
         * @return Future ID.
         */
        GridUuid futureId() {
            return futId;
        }

        /**
         * @return Node ID.
         */
        public GridRichNode node() {
            return node;
        }

        /**
         * @return Keys.
         */
        public Collection<K> keys() {
            return keys.keySet();
        }

        /**
         * @param e Error.
         */
        void onResult(Throwable e) {
            if (log.isDebugEnabled())
                log.debug("Failed to get future result [fut=" + this + ", err=" + e + ']');

            // Fail.
            onDone(e);
        }

        void onResult(GridTopologyException e) {
            if (log.isDebugEnabled())
                log.debug("Remote node left grid while sending or waiting for reply (will retry): " + this);

            // Remap.
            map(keys.keySet(), F.t(node, keys));

            onDone(Collections.<K, V>emptyMap());
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridNearGetResponse<K, V> res) {
            final Collection<Integer> invalidParts = res.invalidPartitions();

            // Remap invalid partitions.
            if (!F.isEmpty(invalidParts)) {
                if (log.isDebugEnabled())
                    log.debug("Remapping mini get future [invalidParts=" + invalidParts + ", fut=" + this + ']');

                // This will append new futures to compound list.
                map(F.view(keys.keySet(),  new P1<K>() {
                    @Override public boolean apply(K key) {
                        return invalidParts.contains(cctx.partition(key));
                    }
                }), F.t(node, keys));
            }

            onDone(loadEntries(node.id(), keys.keySet(), res.entries()));
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this);
        }
    }
}

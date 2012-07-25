// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;

/**
 * Transaction created by system implicitly on remote nodes.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridDhtTxRemote<K, V> extends GridDistributedTxRemoteAdapter<K, V> {
    /** Near node ID. */
    private UUID nearNodeId;

    /** Remote future ID. */
    private GridUuid rmtFutId;

    /** Topology version. */
    private long topVer;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtTxRemote() {
        // No-op.
    }

    /**
     * @param nearNodeId Near node ID.
     * @param rmtFutId Remote future ID.
     * @param ldr Class loader.
     * @param nodeId Node ID.
     * @param rmtThreadId Remote thread ID.
     * @param topVer Topology version.
     * @param xidVer XID version.
     * @param commitVer Commit version.
     * @param concurrency Concurrency level (should be pessimistic).
     * @param isolation Transaction isolation.
     * @param invalidate Invalidate flag.
     * @param timeout Timeout.
     * @param writes Write entries.
     * @param ctx Cache context.
     * @throws GridException If unmarshalling failed.
     */
    public GridDhtTxRemote(
        UUID nearNodeId,
        GridUuid rmtFutId,
        ClassLoader ldr,
        UUID nodeId,
        long rmtThreadId,
        long topVer,
        GridCacheVersion xidVer,
        GridCacheVersion commitVer,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        boolean invalidate,
        long timeout,
        Collection<GridCacheTxEntry<K, V>> writes,
        GridCacheContext<K, V> ctx) throws GridException {
        super(ctx, nodeId, rmtThreadId, xidVer, commitVer, concurrency, isolation, invalidate, timeout);

        assert nearNodeId != null;
        assert rmtFutId != null;

        this.topVer = topVer;
        this.nearNodeId = nearNodeId;
        this.rmtFutId = rmtFutId;

        readMap = Collections.emptyMap();

        writeMap = new GridConcurrentLinkedHashMap<K, GridCacheTxEntry<K, V>>(
            writes != null ? writes.size() : 0, 1.0f);

        addWrites(writes, ldr);
    }

    /**
     * This constructor is meant for pessimistic transactions.
     *
     * @param nearNodeId Near node ID.
     * @param rmtFutId Remote future ID.
     * @param nodeId Node ID.
     * @param rmtThreadId Remote thread ID.
     * @param topVer Topology version.
     * @param xidVer XID version.
     * @param commitVer Commit version.
     * @param concurrency Concurrency level (should be pessimistic).
     * @param isolation Transaction isolation.
     * @param invalidate Invalidate flag.
     * @param timeout Timeout.
     * @param key Key.
     * @param keyBytes Key bytes.
     * @param val Value.
     * @param valBytes Value bytes.
     * @param ctx Cache context.
     */
    public GridDhtTxRemote(
        UUID nearNodeId,
        GridUuid rmtFutId,
        UUID nodeId,
        long rmtThreadId,
        long topVer,
        GridCacheVersion xidVer,
        GridCacheVersion commitVer,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        boolean invalidate,
        long timeout,
        K key,
        byte[] keyBytes,
        V val,
        byte[] valBytes,
        GridCacheContext<K, V> ctx) {
        super(ctx, nodeId, rmtThreadId, xidVer, commitVer, concurrency, isolation, invalidate, timeout);

        assert nearNodeId != null;
        assert rmtFutId != null;

        this.topVer = topVer;
        this.nearNodeId = nearNodeId;
        this.rmtFutId = rmtFutId;

        readMap = Collections.emptyMap();
        writeMap = new GridConcurrentLinkedHashMap<K, GridCacheTxEntry<K, V>>(1, 1.0f);

        addWrite(key, keyBytes, val, valBytes);
    }

    /** {@inheritDoc} */
    @Override public boolean dht() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public UUID eventNodeId() {
        return nearNodeId();
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> masterNodeIds() {
        return Arrays.asList(nearNodeId, nodeId);
    }

    /** {@inheritDoc} */
    @Override public UUID otherNodeId() {
        return nearNodeId;
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> nodeIds() {
        return nodeId.equals(nearNodeId) ? Collections.singleton(nodeId) : F.asList(nodeId, nearNodeId);
    }

    /** {@inheritDoc} */
    @Override public boolean enforceSerializable() {
        return false; // Serializable will be enforced on primary mode.
    }

    /**
     * @return Near node ID.
     */
    UUID nearNodeId() {
        return nearNodeId;
    }

    /**
     * @return Remote future ID.
     */
    GridUuid remoteFutureId() {
        return rmtFutId;
    }

    /** {@inheritDoc} */
    @Override public void addInvalidPartition(int part) {
        super.addInvalidPartition(part);

        for (Iterator<GridCacheTxEntry<K, V>> it = writeMap.values().iterator(); it.hasNext();) {
            GridCacheTxEntry<K, V> e = it.next();

            GridCacheEntryEx<K, V> cached = e.cached();

            if (cached != null) {
                if (cached.partition() == part)
                    it.remove();
            }
            else if (cctx.partition(e.key()) == part)
                it.remove();
        }
    }

    /**
     * @param writes Write entries.
     * @param ldr Class loader.
     * @throws GridException If failed.
     */
    private void addWrites(Iterable<GridCacheTxEntry<K, V>> writes, ClassLoader ldr) throws GridException {
        if (isSystemInvalidate())
            return;

        if (!F.isEmpty(writes)) {
            for (GridCacheTxEntry<K, V> entry : writes) {
                entry.unmarshal(cctx, ldr);

                try {
                    GridDhtCacheEntry<K, V> cached = cctx.dht().entryExx(entry.key(), topVer);

                    checkInternal(entry.key());

                    // Initialize cache entry.
                    entry.cached(cached, entry.keyBytes());

                    writeMap.put(entry.key(), entry);

                    addExplicit(entry);
                }
                catch (GridDhtInvalidPartitionException e) {
                    addInvalidPartition(e.partition());
                }
            }
        }
    }

    /**
     * @param key Key to add to write set.
     * @param keyBytes Key bytes.
     * @param val Value.
     * @param valBytes Value bytes.
     */
    void addWrite(K key, byte[] keyBytes, @Nullable V val, @Nullable byte[] valBytes) {
        checkInternal(key);

        if (isSystemInvalidate())
            return;

        GridDhtCacheEntry<K, V> cached = cctx.dht().entryExx(key, topVer);

        GridCacheTxEntry<K, V> txEntry = new GridCacheTxEntry<K, V>(cctx, this, NOOP, val, 0, cached);

        txEntry.keyBytes(keyBytes);
        txEntry.valueBytes(valBytes);

        writeMap.put(key, txEntry);
    }

    /** {@inheritDoc} */
    @Override protected boolean isNotifyEvent() {
        return !cctx.nodeId().equals(nearNodeId);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridDhtTxRemote.class, this, "super", super.toString());
    }
}

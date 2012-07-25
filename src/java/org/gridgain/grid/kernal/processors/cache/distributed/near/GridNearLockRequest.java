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
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Near cache lock request.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridNearLockRequest<K, V> extends GridDistributedLockRequest<K, V> {
    /** Topology version. */
    private long topVer;

    /** Mini future ID. */
    private GridUuid miniId;

    /** Filter. */
    private byte[][] filterBytes;

    /** Filter. */
    private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

    /** Synchronous commit flag. */
    private boolean syncCommit;

    /** Synchronous rollback flag. */
    private boolean syncRollback;

    /** Implicit flag. */
    private boolean implicitTx;

    /** Implicit transaction with one key flag. */
    private boolean implicitSingleTx;

    /** Array of mapped DHT versions for this entry. */
    @GridToStringInclude
    private GridCacheVersion[] dhtVers;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearLockRequest() {
        // No-op.
    }

    /**
     * @param topVer Topology version.
     * @param nodeId Node ID.
     * @param threadId Thread ID.
     * @param futId Future ID.
     * @param lockVer Cache version.
     * @param isInTx {@code True} if implicit transaction lock.
     * @param implicitTx Flag to indicate that transaction is implicit.
     * @param implicitSingleTx Implicit-transaction-with-one-key flag.
     * @param isRead Indicates whether implicit lock is for read or write operation.
     * @param isolation Transaction isolation.
     * @param isInvalidate Invalidation flag.
     * @param timeout Lock timeout.
     * @param syncCommit Synchronous commit flag.
     * @param syncRollback Synchronous rollback flag.
     * @param keyCnt Number of keys.
     */
    public GridNearLockRequest(long topVer, UUID nodeId, long threadId, GridUuid futId, GridCacheVersion lockVer,
        boolean isInTx, boolean implicitTx, boolean implicitSingleTx, boolean isRead, GridCacheTxIsolation isolation,
        boolean isInvalidate, long timeout, boolean syncCommit, boolean syncRollback, int keyCnt) {
        super(nodeId, threadId, futId, lockVer, isInTx, isRead, isolation, isInvalidate, timeout, keyCnt);

        assert topVer > 0;

        this.topVer = topVer;
        this.implicitTx = implicitTx;
        this.implicitSingleTx = implicitSingleTx;
        this.syncCommit = syncCommit;
        this.syncRollback = syncRollback;

        dhtVers = new GridCacheVersion[keyCnt];
    }

    /**
     * @return Topology version.
     */
    public long topologyVersion() {
        return topVer;
    }

    /**
     * @return Implicit transaction flag.
     */
    public boolean implicitTx() {
        return implicitTx;
    }

    /**
     * @return Implicit-transaction-with-one-key flag.
     */
    public boolean implicitSingleTx() {
        return implicitSingleTx;
    }

    /**
     * @return Filter.
     */
    public GridPredicate<? super GridCacheEntry<K, V>>[] filter() {
        return filter;
    }

    /**
     * @param filter Filter.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void filter(GridPredicate<? super GridCacheEntry<K, V>>[] filter, GridCacheContext<K, V> ctx)
        throws GridException {
        prepareFilter(filter, ctx);

        this.filter = filter;
    }

    /**
     * @return Synchronous commit flag.
     */
    public boolean syncCommit() {
        return syncCommit;
    }

    /**
     * @return Synchronous rollback flag.
     */
    public boolean syncRollback() {
        return syncRollback;
    }

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @param miniId Mini future Id.
     */
    public void miniId(GridUuid miniId) {
        this.miniId = miniId;
    }

    /**
     * Adds a key.
     *
     * @param key Key.
     * @param retVal Flag indicating whether value should be returned.
     * @param keyBytes Key bytes.
     * @param cands Candidates.
     * @param dhtVer DHT version.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void addKeyBytes(K key, byte[] keyBytes, boolean retVal, Collection<GridCacheMvccCandidate<K>> cands,
        @Nullable GridCacheVersion dhtVer, GridCacheContext<K, V> ctx) throws GridException {
        dhtVers[idx] = dhtVer;

        // Delegate to super.
        addKeyBytes(key, keyBytes, retVal, cands, ctx);
    }

    /**
     * @param idx Index of the key.
     * @return DHT version for key at given index.
     */
    public GridCacheVersion dhtVersion(int idx) {
        return dhtVers[idx];
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (filterBytes == null)
            filterBytes = marshalFilter(filter, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (filter == null)
            filter = unmarshalFilter(filterBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeLong(topVer);
        out.writeBoolean(implicitTx);
        out.writeBoolean(implicitSingleTx);
        out.writeBoolean(syncCommit);
        out.writeBoolean(syncRollback);
        out.writeObject(filterBytes);

        U.writeArray(out, dhtVers);

        assert miniId != null;

        U.writeGridUuid(out, miniId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        topVer = in.readLong();
        implicitTx = in.readBoolean();
        implicitSingleTx = in.readBoolean();
        syncCommit = in.readBoolean();
        syncRollback = in.readBoolean();
        filterBytes = (byte[][])in.readObject();

        dhtVers = U.readArray(in, CU.versionArrayFactory());

        miniId = U.readGridUuid(in);

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearLockRequest.class, this, super.toString());
    }
}

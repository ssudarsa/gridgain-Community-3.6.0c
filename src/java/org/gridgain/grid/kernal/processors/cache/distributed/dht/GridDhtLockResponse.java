// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * DHT cache lock response.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridDhtLockResponse<K, V> extends GridDistributedLockResponse<K, V> {
    /** Evicted readers. */
    @GridToStringInclude
    private Collection<K> nearEvicted;

    /** Evicted reader key bytes. */
    private Collection<byte[]> nearEvictedBytes;

    /** Mini ID. */
    private GridUuid miniId;

    /** Invalid partitions. */
    @GridToStringInclude
    private Set<Integer> invalidParts = new GridLeanSet<Integer>();

    /**
     * Empty constructor (required by {@link Externalizable}).
     */
    public GridDhtLockResponse() {
        // No-op.
    }

    /**
     * @param lockVer Lock version.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param cnt Key count.
     */
    public GridDhtLockResponse(GridCacheVersion lockVer, GridUuid futId, GridUuid miniId, int cnt) {
        super(lockVer, futId, cnt);

        assert miniId != null;

        this.miniId = miniId;
    }

    /**
     * @param lockVer Lock ID.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param err Error.
     */
    public GridDhtLockResponse(GridCacheVersion lockVer, GridUuid futId, GridUuid miniId, Throwable err) {
        super(lockVer, futId, err);

        assert miniId != null;

        this.miniId = miniId;
    }

    /**
     * @return Evicted readers.
     */
    public Collection<K> nearEvicted() {
        return nearEvicted;
    }

    /**
     * @param nearEvicted Evicted readers.
     */
    public void nearEvicted(Collection<K> nearEvicted) {
        this.nearEvicted = nearEvicted;
    }

    /**
     * @param nearEvictedBytes Key bytes.
     */
    public void nearEvictedBytes(Collection<byte[]> nearEvictedBytes) {
        this.nearEvictedBytes = nearEvictedBytes;
    }

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @param part Invalid partition.
     */
    public void addInvalidPartition(int part) {
        invalidParts.add(part);
    }

    /**
     * @return Invalid partitions.
     */
    public Set<Integer> invalidPartitions() {
        return invalidParts;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (nearEvictedBytes == null)
            nearEvictedBytes = marshalCollection(nearEvicted, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        nearEvicted = unmarshalCollection(nearEvictedBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert miniId != null;

        U.writeCollection(out, nearEvictedBytes);
        U.writeGridUuid(out, miniId);
        U.writeIntCollection(out, invalidParts);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        nearEvictedBytes = U.readCollection(in);
        miniId = U.readGridUuid(in);
        invalidParts = U.readIntSet(in);

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtLockResponse.class, this, super.toString());
    }
}

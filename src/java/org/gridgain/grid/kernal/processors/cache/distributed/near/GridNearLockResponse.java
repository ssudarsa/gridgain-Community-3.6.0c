// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Near cache lock response.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridNearLockResponse<K, V> extends GridDistributedLockResponse<K, V> {
    /** Collection of versions that are pending and less than lock version. */
    @GridToStringInclude
    private Collection<GridCacheVersion> pending;

    /** */
    private GridUuid miniId;

    /** DHT versions. */
    @GridToStringInclude
    private GridCacheVersion[] dhtVers;

    /**
     * Empty constructor (required by {@link Externalizable}).
     */
    public GridNearLockResponse() {
        // No-op.
    }

    /**
     * @param lockVer Lock ID.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param cnt Count.
     * @param err Error.
     */
    public GridNearLockResponse(GridCacheVersion lockVer, GridUuid futId, GridUuid miniId, int cnt, Throwable err) {
        super(lockVer, futId, cnt, err);

        assert miniId != null;

        this.miniId = miniId;

        dhtVers = new GridCacheVersion[cnt];
    }

    /**
     * Gets pending versions that are less than {@link #version()}.
     *
     * @return Pending versions.
     */
    public Collection<GridCacheVersion> pending() {
        return pending;
    }

    /**
     * Sets pending versions that are less than {@link #version()}.
     *
     * @param pending Pending versions.
     */
    public void pending(Collection<GridCacheVersion> pending) {
        this.pending = pending;
    }

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @param idx Index.
     * @return DHT version.
     */
    public GridCacheVersion dhtVersion(int idx) {
        return dhtVers == null ? null : dhtVers[idx];
    }

    /**
     * @param val Value.
     * @param valBytes Value bytes (possibly {@code null}).
     * @param dhtVer DHT version.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void addValueBytes(@Nullable V val, @Nullable byte[] valBytes, @Nullable GridCacheVersion dhtVer,
        GridCacheContext<K, V> ctx)
        throws GridException {
        dhtVers[values().size()] = dhtVer;

        // Delegate to super.
        addValueBytes(val, valBytes, ctx);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeCollection(out, pending);
        U.writeArray(out, dhtVers);

        assert miniId != null;

        U.writeGridUuid(out, miniId);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        pending = U.readSet(in);
        dhtVers = U.readArray(in, CU.versionArrayFactory());
        miniId = U.readGridUuid(in);

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearLockResponse.class, this, super.toString());
    }
}

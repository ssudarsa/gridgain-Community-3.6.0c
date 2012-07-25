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
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Get response.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridNearGetResponse<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable,
    GridCacheVersionable {
    /** Future ID. */
    private GridUuid futId;

    /** Sub ID. */
    private GridUuid miniId;

    /** Version. */
    private GridCacheVersion ver;

    /** Result. */
    @GridToStringInclude
    private Collection<GridCacheEntryInfo<K, V>> entries;

    /** Keys to retry due to ownership shift. */
    @GridToStringInclude
    private Collection<Integer> invalidParts = new GridLeanSet<Integer>();

    /** Error. */
    private Throwable err;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearGetResponse() {
        // No-op.
    }

    /**
     * @param futId Future ID.
     * @param miniId Sub ID.
     * @param ver Version.
     */
    public GridNearGetResponse(GridUuid futId, GridUuid miniId, GridCacheVersion ver) {
        assert futId != null;
        assert miniId != null;
        assert ver != null;

        this.futId = futId;
        this.miniId = miniId;
        this.ver = ver;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @return Sub ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return ver;
    }

    /**
     * @return Entries.
     */
    public Collection<GridCacheEntryInfo<K, V>> entries() {
        return entries;
    }

    /**
     * @param entries Entries.
     */
    public void entries(Collection<GridCacheEntryInfo<K, V>> entries) {
        this.entries = entries;
    }

    /**
     * @return Failed filter set.
     */
    public Collection<Integer> invalidPartitions() {
        return invalidParts;
    }

    /**
     * @param invalidParts Partitions to retry due to ownership shift.
     */
    public void invalidPartitions(Collection<Integer> invalidParts) {
        this.invalidParts = invalidParts;
    }

    /**
     * @return Error.
     */
    public Throwable error() {
        return err;
    }

    /**
     * @param err Error.
     */
    public void error(Throwable err) {
        this.err = err;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        marshalInfos(entries, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        unmarshalInfos(entries, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert futId != null;
        assert miniId != null;
        assert ver != null;

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);
        U.writeCollection(out, entries);
        U.writeIntCollection(out, invalidParts);

        CU.writeVersion(out, ver);

        out.writeObject(err);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);
        entries = U.readCollection(in);
        invalidParts = U.readIntSet(in);

        ver = CU.readVersion(in);

        err = (Throwable)in.readObject();

        if (invalidParts == null)
            invalidParts = Collections.emptyList();

        assert futId != null;
        assert miniId != null;
        assert ver != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearGetResponse.class, this);
    }
}

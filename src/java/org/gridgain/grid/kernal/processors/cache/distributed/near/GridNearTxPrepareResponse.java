// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Near cache prepare response.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridNearTxPrepareResponse<K, V> extends GridDistributedTxPrepareResponse<K, V> {
    /** Future ID.  */
    private GridUuid futId;

    /** Mini future ID. */
    private GridUuid miniId;

    /** DHT version. */
    private GridCacheVersion dhtVer;

    /** */
    @GridToStringInclude
    private Collection<Integer> invalidParts;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridNearTxPrepareResponse() {
        // No-op.
    }

    /**
     * @param xid Xid version.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param dhtVer DHT version.
     * @param invalidParts Invalid partitions.
     * @param err Error.
     */
    public GridNearTxPrepareResponse(GridCacheVersion xid, GridUuid futId, GridUuid miniId, GridCacheVersion dhtVer,
        Collection<Integer> invalidParts, Throwable err) {
        super(xid, err);

        assert futId != null;
        assert miniId != null;
        assert dhtVer != null;

        this.futId = futId;
        this.miniId = miniId;
        this.dhtVer = dhtVer;
        this.invalidParts = invalidParts;
    }

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @return DHT version.
     */
    public GridCacheVersion dhtVersion() {
        return dhtVer;
    }

    /**
     * @return Invalid partitions.
     */
    public Collection<Integer> invalidPartitions() {
        return invalidParts;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert futId != null;
        assert miniId != null;
        assert dhtVer != null;

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);

        CU.writeVersion(out, dhtVer);

        U.writeIntCollection(out, invalidParts);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);

        dhtVer = CU.readVersion(in);

        invalidParts = U.readIntSet(in);

        assert futId != null;
        assert miniId != null;
        assert dhtVer != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxPrepareResponse.class, this, "super", super.toString());
    }
}

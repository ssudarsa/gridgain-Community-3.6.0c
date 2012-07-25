// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Cache eviction request.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class GridCacheEvictionRequest<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** Future id. */
    private long futId;

    /** Entries to clear from near and backup nodes. */
    @GridToStringInclude
    private Collection<GridTuple3<K, GridCacheVersion, Boolean>> entries;

    /** Serialized entries. */
    @GridToStringExclude
    private byte[] entriesBytes;

    /** Topology version. */
    private long topVer;

    /**
     * Required by {@link Externalizable}.
     */
    public GridCacheEvictionRequest() {
        // No-op.
    }

    /**
     * @param futId Future id.
     * @param size Size.
     * @param topVer Topology version.
     */
    GridCacheEvictionRequest(long futId, int size, long topVer) {
        assert futId > 0;
        assert size > 0;
        assert topVer > 0;

        this.futId = futId;

        entries = new ArrayList<GridTuple3<K, GridCacheVersion, Boolean>>(size);

        this.topVer = topVer;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (entries != null) {
            prepareObjects(entries, ctx);

            entriesBytes = U.marshal(ctx.marshaller(), entries).getEntireArray();
        }
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (entriesBytes != null)
            entries = U.unmarshal(ctx.marshaller(), new GridByteArrayList(entriesBytes), ldr);
    }

    /**
     * @return Future id.
     */
    long futureId() {
        return futId;
    }

    /**
     * @return Entries - {{Key, Version, Boolean (near or not)}, ...}.
     */
    Collection<GridTuple3<K, GridCacheVersion, Boolean>> entries() {
        return entries;
    }

    /**
     * @return Topology version.
     */
    long topologyVersion() {
        return topVer;
    }

    /**
     * Add key to request.
     *
     * @param key Key to evict.
     * @param ver Entry version.
     * @param near {@code true} if key should be evicted from near cache.
     */
    void addKey(K key, GridCacheVersion ver, boolean near) {
        assert key != null;
        assert ver != null;

        entries.add(F.t(key, ver, near));
    }

    /** {@inheritDoc} */
    @Override public boolean ignoreClassErrors() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeLong(futId);

        U.writeByteArray(out, entriesBytes);

        out.writeLong(topVer);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        futId = in.readLong();

        entriesBytes = U.readByteArray(in);

        topVer = in.readLong();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheEvictionRequest.class, this);
    }
}

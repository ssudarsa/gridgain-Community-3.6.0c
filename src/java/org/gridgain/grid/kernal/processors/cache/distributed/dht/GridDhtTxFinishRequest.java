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
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Near transaction finish request.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridDhtTxFinishRequest<K, V> extends GridDistributedTxFinishRequest<K, V> {
    /** Near node ID. */
    private UUID nearNodeId;

    /** Transaction isolation. */
    private GridCacheTxIsolation isolation;

    /** Near writes. */
    private Collection<GridCacheTxEntry<K, V>> nearWrites;

    /** Mini future ID. */
    private GridUuid miniId;

    /** System invalidation flag. */
    private boolean sysInvalidate;

    /** Topology version. */
    private long topVer;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtTxFinishRequest() {
        // No-op.
    }

    /**
     * @param nearNodeId Near node ID.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param topVer Topology version.
     * @param xidVer Transaction ID.
     * @param threadId Thread ID.
     * @param commitVer Commit version.
     * @param isolation Transaction isolation.
     * @param commit Commit flag.
     * @param invalidate Invalidate flag.
     * @param sysInvalidate System invalidation flag.
     * @param baseVer Base version.
     * @param committedVers Committed versions.
     * @param rolledbackVers Rolled back versions.
     * @param writes Write entries.
     * @param nearWrites Near cache writes.
     * @param reply Reply flag.
     */
    public GridDhtTxFinishRequest(
        UUID nearNodeId,
        GridUuid futId,
        GridUuid miniId,
        long topVer,
        GridCacheVersion xidVer,
        GridCacheVersion commitVer,
        long threadId,
        GridCacheTxIsolation isolation,
        boolean commit,
        boolean invalidate,
        boolean sysInvalidate,
        GridCacheVersion baseVer, Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers,
        Collection<GridCacheTxEntry<K, V>> writes,
        Collection<GridCacheTxEntry<K, V>> nearWrites,
        boolean reply) {
        super(xidVer, futId, commitVer, threadId, commit, invalidate, baseVer, committedVers, rolledbackVers, writes,
            reply);

        assert miniId != null;
        assert nearNodeId != null;
        assert isolation != null;

        this.topVer = topVer;
        this.nearNodeId = nearNodeId;
        this.isolation = isolation;
        this.nearWrites = nearWrites;
        this.miniId = miniId;
        this.sysInvalidate = sysInvalidate;
    }

    /** {@inheritDoc} */
    @Override public boolean allowForStartup() {
        return true;
    }

    /**
     * @return Near writes.
     */
    public Collection<GridCacheTxEntry<K, V>> nearWrites() {
        return nearWrites == null ? Collections.<GridCacheTxEntry<K, V>>emptyList() : nearWrites;
    }

    /**
     * @return Mini ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @return Transaction isolation.
     */
    public GridCacheTxIsolation isolation() {
        return isolation;
    }

    /**
     * @return Near node ID.
     */
    public UUID nearNodeId() {
        return nearNodeId;
    }

    /**
     * @return System invalidate flag.
     */
    public boolean isSystemInvalidate() {
        return sysInvalidate;
    }

    /**
     * @return Topology version.
     */
    public long topologyVersion() {
        return topVer;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        marshalTx(nearWrites, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        unmarshalTx(nearWrites, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert miniId != null;

        U.writeUuid(out, nearNodeId);
        U.writeGridUuid(out, miniId);
        U.writeCollection(out, nearWrites);

        out.writeLong(topVer);
        out.writeByte(isolation.ordinal());
        out.writeBoolean(sysInvalidate);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        nearNodeId = U.readUuid(in);
        miniId = U.readGridUuid(in);
        nearWrites = U.readCollection(in);

        topVer = in.readLong();
        isolation = GridCacheTxIsolation.fromOrdinal(in.readByte());
        sysInvalidate = in.readBoolean();

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtTxFinishRequest.class, this, super.toString());
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Force keys response. Contains absent keys.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridDhtForceKeysResponse<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** Future ID. */
    private GridUuid futId;

    /** Mini-future ID. */
    private GridUuid miniId;

    /** */
    private Collection<byte[]> missedKeyBytes;

    /** Missed (not found) keys. */
    @GridToStringInclude
    private transient Collection<K> missedKeys;

    /** Cache entries in serialized form. */
    @GridToStringExclude
    private List<byte[]> infoBytes;

    /** Cache entries. */
    @GridToStringInclude
    private transient List<GridCacheEntryInfo<K, V>> infos;

    /**
     * Required by {@link Externalizable}.
     */
    public GridDhtForceKeysResponse() {
        // No-op.
    }

    /**
     * @param futId Request id.
     * @param miniId Mini-future ID.
     */
    public GridDhtForceKeysResponse(GridUuid futId, GridUuid miniId) {
        assert futId != null;
        assert miniId != null;

        this.futId = futId;
        this.miniId = miniId;
    }

    /** {@inheritDoc} */
    @Override public boolean allowForStartup() {
        return true;
    }

    /**
     * @return Keys.
     */
    public Collection<K> missedKeys() {
        return missedKeys == null ? Collections.<K>emptyList() : missedKeys;
    }

    /**
     * @return Forced entries.
     */
    public Collection<GridCacheEntryInfo<K, V>> forcedInfos() {
        return infos == null ? Collections.<GridCacheEntryInfo<K,V>>emptyList() : infos;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @return Mini-future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @param key Key.
     */
    public void addMissed(K key) {
        if (missedKeys == null)
            missedKeys = new LinkedList<K>();

        missedKeys.add(key);
    }

    /**
     * @param info Entry info to add.
     */
    public void addInfo(GridCacheEntryInfo<K, V> info) {
        assert info != null;

        if (infos == null)
            infos = new LinkedList<GridCacheEntryInfo<K, V>>();

        infos.add(info);
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (missedKeys != null)
            missedKeyBytes = marshalCollection(missedKeys, ctx);

        if (infos != null) {
            for (GridCacheEntryInfo<K, V> e : infos)
                marshalInfo(e, ctx);

            infoBytes = marshalCollection(infos, ctx);
        }
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (missedKeyBytes != null)
            missedKeys = unmarshalCollection(missedKeyBytes, ctx, ldr);

        if (infoBytes != null) {
            infos = unmarshalCollection(infoBytes, ctx, ldr);

            unmarshalInfos(infos, ctx, ldr);
        }
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);
        U.writeCollection(out, missedKeyBytes);
        U.writeCollection(out, infoBytes);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);
        missedKeyBytes = U.readCollection(in);
        infoBytes = U.readList(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtForceKeysResponse.class, this, super.toString());
    }
}

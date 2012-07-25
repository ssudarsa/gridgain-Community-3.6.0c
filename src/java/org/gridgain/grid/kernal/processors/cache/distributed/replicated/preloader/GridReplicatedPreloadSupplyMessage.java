// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Preload supply message.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class GridReplicatedPreloadSupplyMessage<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** Last flag. */
    private boolean last;

    /** Worker ID. */
    private int workerId = -1;

    /** Failed flag. {@code True} if sender cannot supply. */
    private boolean failed;

    /** Cache preload entries in serialized form. */
    @GridToStringExclude
    private Collection<byte[]> entryBytes = new LinkedList<byte[]>();

    /** Message size. */
    private int msgSize;

    /** Cache entries. */
    @GridToStringExclude
    private List<GridCacheEntryInfo<K, V>> entries = new LinkedList<GridCacheEntryInfo<K, V>>();

    /**
     * @param workerId Worker ID.
     */
    GridReplicatedPreloadSupplyMessage(int workerId) {
        this.workerId = workerId;
    }

    /**
     * @param workerId Worker ID.
     * @param failed Failed flag.
     */
    GridReplicatedPreloadSupplyMessage(int workerId, boolean failed) {
        this.workerId = workerId;
        this.failed = failed;
    }

    /**
     * Required by {@link Externalizable}.
     */
    public GridReplicatedPreloadSupplyMessage() {
        /* No-op. */
    }

    /** {@inheritDoc} */
    @Override public boolean allowForStartup() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean ignoreClassErrors() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        // NOTE: we don't need to prepare entryBytes here since we do it
        // iteratively using method addSerializedEntry().
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        entries = unmarshalCollection(entryBytes, ctx, ldr);

        unmarshalInfos(entries, ctx, ldr);
    }

    /**
     * @param info Entry to add.
     * @param ctx Cache context.
     * @throws GridException If failed.
     */
    void addEntry(GridCacheEntryInfo<K, V> info, GridCacheContext<K, V> ctx) throws GridException {
        assert info != null;

        marshalInfo(info, ctx);

        byte[] bytes = CU.marshal(ctx, info).getEntireArray();

        msgSize += bytes.length;

        entryBytes.add(bytes);
    }

    /**
     * @return {@code true} if this is the last batch..
     */
    boolean last() {
        return last;
    }

    /**
     * @param last {@code true} if this is the last batch.
     */
    void last(boolean last) {
        this.last = last;
    }

    /**
     * @return Worker ID.
     */
    int workerId() {
        return workerId;
    }

    /**
     * @return {@code True} if preloading from this node failed.
     */
    boolean failed() {
        return failed;
    }

    /**
     * @return Entries.
     */
     Collection<GridCacheEntryInfo<K, V>> entries() {
        return entries;
    }

    /**
     * @return Message size in bytes.
     */
    int size() {
        return msgSize;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(workerId);
        out.writeBoolean(last);
        out.writeBoolean(failed);

        U.writeCollection(out, entryBytes);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        workerId = in.readInt();
        last = in.readBoolean();
        failed = in.readBoolean();

        entryBytes = U.readList(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridReplicatedPreloadSupplyMessage.class, this, "size", size());
    }
}

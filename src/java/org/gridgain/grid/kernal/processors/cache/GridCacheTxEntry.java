// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;

/**
 * Transaction entry. Note that it is essential that this class does not override
 * {@link #equals(Object)} method, as transaction entries should use referential
 * equality.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheTxEntry<K, V> implements GridPeerDeployAware, Externalizable {
    /** Owning transaction. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridToStringExclude
    private GridCacheTxEx<K, V> tx;

    /** Cache key. */
    @GridToStringInclude
    private transient K key;

    /** Key bytes. */
    private byte[] keyBytes;

    /** Cache value. */
    @GridToStringInclude
    private transient V val;

    /** Value bytes. */
    private byte[] valBytes;

    /** Filter bytes. */
    private byte[] filterBytes;

    /** Cache operation. */
    @GridToStringInclude
    private GridCacheOperation op;

    /** Time to live. */
    private long ttl;

    /** Time to live. */
    @GridToStringInclude
    private long expireTime;

    /** Explicit lock version if there is one. */
    @GridToStringInclude
    private GridCacheVersion explicitVer;

    /** DHT version. */
    private transient volatile GridCacheVersion dhtVer;

    /** Put filters. */
    @GridToStringInclude
    private GridPredicate<? super GridCacheEntry<K, V>>[] filters;

    /** Underlying cache entry. */
    private transient volatile GridCacheEntryEx<K, V> entry;

    /** Cache registry. */
    private transient GridCacheContext<K, V> ctx;

    /** Committing flag. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private transient AtomicBoolean committing = new AtomicBoolean(false);

    /** Assigned node ID (required only for partitioned cache). */
    private transient UUID nodeId;

    /** Marks entry as ready-to-be-read. */
    private boolean marked;

    /**
     * Required by {@link Externalizable}
     */
    public GridCacheTxEntry() {
        /* No-op. */
    }

    /**
     * @param ctx Cache registry.
     * @param tx Owning transaction.
     * @param op Operation.
     * @param val Value.
     * @param ttl Time to live.
     * @param entry Cache entry.
     */
    public GridCacheTxEntry(GridCacheContext<K, V> ctx, GridCacheTxEx<K, V> tx, GridCacheOperation op, V val,
        long ttl, GridCacheEntryEx<K, V> entry) {
        assert ctx != null;
        assert tx != null;
        assert op != null;
        assert entry != null;

        this.ctx = ctx;
        this.tx = tx;
        this.op = op;
        this.val = val;
        this.entry = entry;
        this.ttl = ttl;

        key = entry.key();
        keyBytes = entry.keyBytes();

        expireTime = toExpireTime(ttl);
    }

    /**
     * @param ctx Cache registry.
     * @param tx Owning transaction.
     * @param op Operation.
     * @param val Value.
     * @param ttl Time to live.
     * @param entry Cache entry.
     * @param filters Put filters.
     */
    public GridCacheTxEntry(GridCacheContext<K, V> ctx, GridCacheTxEx<K, V> tx, GridCacheOperation op,
        V val, long ttl, GridCacheEntryEx<K,V> entry,
        GridPredicate<? super GridCacheEntry<K, V>>[] filters) {
        assert ctx != null;
        assert tx != null;
        assert op != null;
        assert entry != null;

        this.ctx = ctx;
        this.tx = tx;
        this.op = op;
        this.val = val;
        this.entry = entry;
        this.ttl = ttl;
        this.filters = filters;

        key = entry.key();
        keyBytes = entry.keyBytes();

        expireTime = toExpireTime(ttl);
    }

    /**
     * @param ctx Context.
     * @return Clean copy of this entry.
     */
    public GridCacheTxEntry<K, V> cleanCopy(GridCacheContext<K, V> ctx) {
        GridCacheTxEntry<K, V> cp = new GridCacheTxEntry<K, V>();

        cp.key = key;
        cp.ctx = ctx;

        cp.val = val;
        cp.keyBytes = keyBytes;
        cp.valBytes = valBytes;
        cp.op = op;
        cp.filters = filters;
        cp.val = val;
        cp.ttl = ttl;
        cp.expireTime = expireTime;
        cp.explicitVer = explicitVer;

        return cp;
    }

    /**
     * @return Node ID.
     */
    public UUID nodeId() {
        return nodeId;
    }

    /**
     * @param nodeId Node ID.
     */
    public void nodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * @return DHT version.
     */
    public GridCacheVersion dhtVersion() {
        return dhtVer;
    }

    /**
     * @param dhtVer DHT version.
     */
    public void dhtVersion(GridCacheVersion dhtVer) {
        this.dhtVer = dhtVer;
    }

    /**
     * Marks entry as committing.
     *
     * @return {@code True} if entry has been marked by this call.
     */
    public boolean markCommitting() {
        return committing.compareAndSet(false, true);
    }

    /**
     * @return {@code True} if committing flag is set.
     */
    public boolean committing() {
        return committing.get();
    }

    /**
     * @param val Value to set.
     */
    void setAndMark(V val) {
        this.val = val;

        mark();
    }

    /**
     * @param op Operation.
     * @param val Value to set.
     */
    void setAndMark(GridCacheOperation op, V val) {
        this.op = op;
        this.val = val;

        mark();
    }

    /**
     * Marks this entry as value-has-bean-read.
     */
    void mark() {
        marked = true;
    }

    /**
     * @return {@code True} if marked.
     */
    boolean marked() {
        return marked;
    }

    /**
     *
     * @param ttl Time to live.
     * @return Expire time.
     */
    private long toExpireTime(long ttl) {
        long expireTime = ttl == 0 ? 0 : System.currentTimeMillis() + ttl;

        if (expireTime <= 0)
            expireTime = 0;

        return expireTime;
    }

    /**
     * @return Entry key.
     */
    public K key() {
        return key;
    }

    /**
     *
     * @return Key bytes.
     */
    @Nullable public byte[] keyBytes() {
        byte[] bytes = keyBytes;

        if (bytes == null && entry != null) {
            bytes = entry.keyBytes();

            keyBytes = bytes;
        }

        return bytes;
    }

    /**
     * @param keyBytes Key bytes.
     */
    public void keyBytes(byte[] keyBytes) {
        initKeyBytes(keyBytes);
    }

    /**
     * @return Underlying cache entry.
     */
    public GridCacheEntryEx<K, V> cached() {
        return entry;
    }

    /**
     * @param entry Cache entry.
     * @param keyBytes Key bytes, possibly {@code null}.
     */
    public void cached(GridCacheEntryEx<K,V> entry, @Nullable byte[] keyBytes) {
        assert entry != null;

        this.entry = entry;

        initKeyBytes(keyBytes);
    }

    /**
     * Initialized key bytes locally and on the underlying entry.
     *
     * @param bytes Key bytes to initialize.
     */
    private void initKeyBytes(@Nullable byte[] bytes) {
        if (bytes != null) {
            keyBytes = bytes;

            while (true) {
                try {
                    if (entry != null)
                        entry.keyBytes(bytes);

                    break;
                }
                catch (GridCacheEntryRemovedException ignore) {
                    entry = ctx.cache().entryEx(key);
                }
            }
        }
        else if (entry != null) {
            bytes = entry.keyBytes();

            if (bytes != null)
                keyBytes = bytes;
        }
    }

    /**
     * @return Entry value.
     */
    public V value() {
        return val;
    }

    /**
     * @return Value bytes.
     */
    public byte[] valueBytes() {
        return valBytes;
    }

    /**
     * @param valBytes Value bytes.
     */
    public void valueBytes(byte[] valBytes) {
        this.valBytes = valBytes;
    }

    /**
     * @return Expire time.
     */
    public long expireTime() {
        return expireTime;
    }

    /**
     * @param expireTime Expiration time.
     */
    public void expireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * @return Time to live.
     */
    public long ttl() {
        return ttl;
    }

    /**
     * @param ttl Time to live.
     */
    public void ttl(long ttl) {
        this.ttl = ttl;
    }

    /**
     * @param val Entry value.
     * @return Old value.
     */
    public V value(@Nullable V val) {
        V oldVal = this.val;

        this.val = val;

        return oldVal;
    }

    /**
     * @return Cache operation.
     */
    public GridCacheOperation op() {
        return op;
    }

    /**
     * @param op Cache operation.
     */
    public void op(GridCacheOperation op) {
        this.op = op;
    }

    /**
     * @return {@code True} if read entry.
     */
    public boolean isRead() {
        return op() == READ;
    }

    /**
     * @param explicitVer Explicit version.
     */
    public void explicitVersion(GridCacheVersion explicitVer) {
        this.explicitVer = explicitVer;
    }

    /**
     * @return Explicit version.
     */
    public GridCacheVersion explicitVersion() {
        return explicitVer;
    }

    /**
     * @return Put filters.
     */
    public GridPredicate<? super GridCacheEntry<K, V>>[] filters() {
        return filters;
    }

    /**
     * @param filters Put filters.
     */
    public void filters(GridPredicate<? super GridCacheEntry<K, V>>[] filters) {
        this.filters = filters;
    }

    /**
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void marshal(GridCacheContext<K, V> ctx) throws GridException {
        if (keyBytes == null)
            keyBytes = entry.getOrMarshalKeyBytes();

        // Don't serialize values for read operations.
        if (op == READ || op == NOOP)
            valBytes = null;
        else if (valBytes == null && val != null)
            valBytes = CU.marshal(ctx, val).getArray();

        // Do not serialize filters if they are null.
        if (F.isEmpty(filters))
            filterBytes = null;
        else if (filterBytes == null)
            filterBytes = CU.marshal(ctx, filterBytes).getArray();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeByteArray(out, keyBytes);
        U.writeByteArray(out, valBytes);
        U.writeByteArray(out, filterBytes);

        out.writeByte(op.ordinal());

        out.writeLong(ttl);

        long remaining;

        // 0 means never expires.
        if (expireTime == 0)
            remaining = -1;
        else {
            remaining = expireTime - System.currentTimeMillis();

            if (remaining < 0)
                remaining = 0;
        }

        // Write remaining time.
        out.writeLong(remaining);

        CU.writeVersion(out, explicitVer);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        keyBytes = U.readByteArray(in);
        valBytes = U.readByteArray(in);
        filterBytes = U.readByteArray(in);

        if (filterBytes == null)
            filters(CU.<K, V>empty());

        op = fromOrdinal(in.readByte());

        ttl = in.readLong();

        long remaining = in.readLong();

        expireTime = remaining < 0 ? 0 : System.currentTimeMillis() + remaining;

        // Account for overflow.
        if (expireTime < 0)
            expireTime = 0;

        explicitVer = CU.readVersion(in);
    }

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        ClassLoader clsLdr = getClass().getClassLoader();

        V val = value();

        // First of all check classes that may be loaded by class loader other than application one.
        return key != null && !clsLdr.equals(key.getClass().getClassLoader()) ?
            key.getClass() : val != null ? val.getClass() : getClass();
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        return deployClass().getClassLoader();
    }

    /**
     * Unmarshalls entry.
     *
     * @param ctx Cache context.
     * @param clsLdr Class loader.
     * @throws GridException If un-marshalling failed.
     */
    @SuppressWarnings({"unchecked"})
    public void unmarshal(GridCacheContext<K, V> ctx, ClassLoader clsLdr) throws GridException {
        this.ctx = ctx;

        // Don't unmarshal more than once by checking key for null.
        if (key == null)
            key = (K)U.unmarshal(ctx.marshaller(), new ByteArrayInputStream(keyBytes), clsLdr);

        if (op != READ && valBytes != null && val == null)
            value((V)U.unmarshal(ctx.marshaller(), new ByteArrayInputStream(valBytes), clsLdr));

        if (filters == null && filterBytes != null) {
            filters = (GridPredicate<? super GridCacheEntry<K, V>>[])
                U.unmarshal(ctx.marshaller(), new ByteArrayInputStream(filterBytes), clsLdr);

            if (filters == null)
                filters = CU.empty();

            filters(filters);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridCacheTxEntry.class, this,
            "keyBytesSize", keyBytes == null ? "null" : Integer.toString(keyBytes.length),
            "valBytesSize", valBytes == null ? "null" : Integer.toString(valBytes.length),
            "xidVer", tx == null ? "null" : tx.xidVersion());
    }
}

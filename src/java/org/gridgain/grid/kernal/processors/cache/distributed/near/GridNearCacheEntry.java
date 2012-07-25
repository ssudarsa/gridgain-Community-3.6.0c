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
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Replicated cache entry.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "TooBroadScope"})
public class GridNearCacheEntry<K, V> extends GridDistributedCacheEntry<K, V> {
    /** ID of primary node from which this entry was last read. */
    private volatile UUID primaryNodeId;

    /** DHT version which caused the last update. */
    private GridCacheVersion dhtVer;

    /**
     * @param ctx Cache context.
     * @param key Cache key.
     * @param hash Key hash value.
     * @param val Entry value.
     * @param next Next entry in the linked list.
     * @param ttl Time to live.
     */
    public GridNearCacheEntry(GridCacheContext<K, V> ctx, K key, int hash, V val, GridCacheMapEntry<K, V> next,
        long ttl) {
        super(ctx, key, hash, val, next, ttl);
    }

    /** {@inheritDoc} */
    @Override public boolean isNear() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean valid() {
        UUID primaryNodeId = this.primaryNodeId;

        if (primaryNodeId == null)
            return false;

        if (cctx.discovery().node(primaryNodeId) == null) {
            this.primaryNodeId = null;

            return false;
        }

        // Make sure that primary node is alive before returning this value.
        GridRichNode primary = F.first(cctx.affinity(key(), CU.allNodes(cctx)));

        if (primary != null && primary.id().equals(primaryNodeId))
            return true;

        // Primary node changed.
        this.primaryNodeId = null;

        return false;
    }

    /**
     * @return {@code True} if this entry was initialized by this call.
     * @throws GridCacheEntryRemovedException If this entry is obsolete.
     */
    public boolean initializeFromDht() throws GridCacheEntryRemovedException {
        while (true) {
            GridDhtCacheEntry<K, V> entry = cctx.near().dht().peekExx(key);

            if (entry != null) {
                GridCacheEntryInfo<K, V> e = entry.info();

                if (e != null) {
                    lock();

                    try {
                        checkObsolete();

                        if (isNew()) {
                            // Version does not change for load ops.
                            update(e.value(), e.valueBytes(), e.expireTime(), e.ttl(),
                                e.isNew() ? ver : e.version(), e.metrics());

                            dhtVer = e.isNew() ? null : e.version();

                            return true;
                        }

                        return false;
                    }
                    finally {
                        unlock();
                    }
                }
            }
            else
                return false;
        }
    }

    /**
     * This method should be called only when lock is owned on this entry.
     *
     * @param val Value.
     * @param valBytes Value bytes.
     * @param ver Version.
     * @param dhtVer DHT version.
     * @param primaryNodeId Primary node ID.
     * @return {@code True} if reset was done.
     * @throws GridCacheEntryRemovedException If obsolete.
     * @throws GridException If failed.
     */
    @SuppressWarnings( {"RedundantTypeArguments"})
    public boolean resetFromPrimary(V val, byte[] valBytes, GridCacheVersion ver, GridCacheVersion dhtVer,
        UUID primaryNodeId) throws GridCacheEntryRemovedException, GridException {
        assert dhtVer != null;

        cctx.versions().onReceived(primaryNodeId, dhtVer);

        if (valBytes != null && val == null) {
            GridCacheVersion curDhtVer = dhtVersion();

            if (!F.eq(dhtVer, curDhtVer))
                val = U.<V>unmarshal(cctx.marshaller(), new GridByteArrayList(valBytes), cctx.deploy().globalLoader());
        }

        lock();

        try {
            checkObsolete();

            this.primaryNodeId = primaryNodeId;

            if (!F.eq(this.dhtVer, dhtVer)) {
                this.val = val;
                this.valBytes = isStoreValueBytes() ? valBytes : null;
                this.ver = ver;
                this.dhtVer = dhtVer;

                return true;
            }
        }
        finally {
            unlock();
        }

        return false;
    }

    /**
     * This method should be called only when lock is owned on this entry.
     *
     * @param dhtVer DHT version.
     * @param val Value associated with version.
     * @param valBytes Value bytes.
     * @param expireTime Expire time.
     * @param ttl Time to live.
     * @param primaryNodeId Primary node ID.
     */
    public void updateOrEvict(GridCacheVersion dhtVer, @Nullable V val, @Nullable byte[] valBytes, long expireTime,
        long ttl, UUID primaryNodeId) {
        assert dhtVer != null;

        cctx.versions().onReceived(primaryNodeId, dhtVer);

        lock();

        try {
            if (!obsolete()) {
                // Don't set DHT version to null until we get a match from DHT remote transaction.
                if (F.eq(this.dhtVer, dhtVer))
                    this.dhtVer = null;

                // If we are here, then we already tried to evict this entry.
                // If cannot evict, then update.
                if (this.dhtVer == null) {
                    if (!markObsolete(dhtVer, true)) {
                        this.val = val;
                        this.valBytes = isStoreValueBytes() ? valBytes : null;
                        this.expireTime = expireTime;
                        this.ttl = ttl;
                        this.primaryNodeId = primaryNodeId;
                    }
                }
            }
        }
        finally {
            unlock();
        }
    }

    /**
     * @return DHT version for this entry.
     * @throws GridCacheEntryRemovedException If obsolete.
     */
    @Nullable public GridCacheVersion dhtVersion() throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            return dhtVer;
        }
        finally {
            unlock();
        }
    }

    /**
     * @return Tuple with version and value of this entry.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
    @Nullable public GridTuple3<GridCacheVersion, V, byte[]> versionedValue() throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            return dhtVer == null ? null : F.t(dhtVer, val, valBytes);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isNew() throws GridCacheEntryRemovedException {
        assert isHeldByCurrentThread();

        checkObsolete();

        return startVer == ver || !valid();
    }

    /** {@inheritDoc} */
    @Override public boolean isNewLocked() throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            return startVer == ver || !valid();
        }
        finally {
            unlock();
        }
    }

    /**
     * @return ID of primary node from which this value was loaded.
     */
    UUID nodeId() {
        return primaryNodeId;
    }

    /** {@inheritDoc} */
    @Override protected void recordNodeId(UUID primaryNodeId) {
        assert isHeldByCurrentThread();

        this.primaryNodeId = primaryNodeId;
    }

    /**
     * This method should be called only when committing optimistic transactions.
     *
     * @param dhtVer DHT version to record.
     */
    public void recordDhtVersion(GridCacheVersion dhtVer) {
        // Version manager must be updated separately, when adding DHT version
        // to transaction entries.
        lock();

        try {
            this.dhtVer = dhtVer;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override protected void refreshAhead(GridCacheTx tx, K key, GridCacheVersion matchVer) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override protected V readThrough(GridCacheTx tx, K key, boolean reload,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cctx.near().loadAsync(F.asList(key), reload, filter).get().get(key);
    }

    /**
     * @param tx Transaction.
     * @param primaryNodeId Primary node ID.
     * @param val New value.
     * @param valBytes Value bytes.
     * @param ver Version to use.
     * @param ttl Time to live.
     * @param expireTime Expiration time.
     * @param evt Event flag.
     * @return {@code True} if initial value was set.
     * @throws GridException In case of error.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    public boolean loadedValue(@Nullable GridCacheTx tx, UUID primaryNodeId, V val, byte[] valBytes,
        GridCacheVersion ver, long ttl, long expireTime, boolean evt) throws GridException,
        GridCacheEntryRemovedException {
        if (valBytes != null && val == null && isNewLocked())
            val = U.<V>unmarshal(cctx.marshaller(), new GridByteArrayList(valBytes), cctx.deploy().globalLoader());

        try {
            lock();

            try {
                checkObsolete();

                if (metrics == null)
                    metrics = new GridCacheMetricsAdapter();

                metrics.onRead(false);

                if (isNew() || !valid()) {
                    this.primaryNodeId = primaryNodeId;

                    isRefreshing = false;

                    // Version does not change for load ops.
                    update(val, valBytes, expireTime, ttl, ver, metrics);

                    updateIndex(val);

                    return true;
                }

                return false;
            }
            finally {
                unlock();
            }
        }
        finally {
            if (evt)
                cctx.events().addEvent(partition(), key, tx, null, EVT_CACHE_OBJECT_READ, val, null);
        }
    }

    /** {@inheritDoc} */
    @Override protected void updateIndex(V val) throws GridException {
        // No-op: queries are disabled for near cache.
    }

    /** {@inheritDoc} */
    @Override protected boolean clearIndex() throws GridException {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean hasLockCandidate(long threadId) throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            return mvcc.remoteCandidate(cctx.nodeId(), threadId) != null;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(long threadId) throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c!= null && c.threadId() == threadId;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThreadUnsafe(long threadId) {
        lock();

        try {
            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c!= null && c.threadId() == threadId;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(GridCacheVersion exclude) throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c!= null && c.threadId() == Thread.currentThread().getId() &&
                (exclude == null || !c.version().equals(exclude));
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> localOwner() throws GridCacheEntryRemovedException {
        lock();

        try {
            return mvcc.remoteOwner();
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheMvccCandidate<K>> localCandidates(GridCacheVersion... exclude)
        throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            return remoteMvccSnapshot(exclude);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedLocally(GridUuid lockId) throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c != null && c.version().id().equals(lockId);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(long threadId, GridCacheVersion exclude)
        throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c != null && c.threadId() == threadId && (exclude == null || !c.version().equals(exclude));
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedLocallyByIdOrThread(GridUuid lockId, long threadId)
        throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c != null && (c.version().id().equals(lockId) || c.threadId() == threadId);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> candidate(UUID nodeId, long threadId)
        throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            return mvcc.remoteCandidate(nodeId, threadId);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> localCandidate(long threadId) throws GridCacheEntryRemovedException {
        return candidate(cctx.nodeId(), threadId);
    }

    /** {@inheritDoc} */
    @Override public boolean lockedLocallyUnsafe(GridUuid lockId) {
        lock();

        try {
            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c != null && c.version().id().equals(lockId);
        }
        finally {
            unlock();
        }
    }

    /**
     * @param baseVer Base version.
     * @param owned Owned versions.
     * @throws GridCacheEntryRemovedException If removed.
     */
    public void orderOwned(GridCacheVersion baseVer, Collection<GridCacheVersion> owned)
        throws GridCacheEntryRemovedException {
        if (!F.isEmpty(owned)) {
            GridCacheMvccCandidate<K> prev;
            GridCacheMvccCandidate<K> owner;

            V val;

            lock();

            try {
                checkObsolete();

                prev = mvcc.anyOwner();

                boolean emptyBefore = mvcc.isEmpty();

                owner = mvcc.orderOwned(baseVer, owned);

                boolean emptyAfter = mvcc.isEmpty();

                checkCallbacks(emptyBefore, emptyAfter);

                val = this.val;
            }
            finally {
                unlock();
            }

            // This call must be made outside of synchronization.
            checkOwnerChanged(prev, owner, val);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> addLocal(long threadId, GridCacheVersion ver, long timeout,
        boolean reenter, boolean ec, boolean tx, boolean implicitSingle) throws GridCacheEntryRemovedException {
        return addNearLocal(null, threadId, ver, timeout, reenter, ec, tx, implicitSingle);
    }

    /**
     * Add near local candidate.
     *
     * @param dhtNodeId DHT node ID.
     * @param threadId Owning thread ID.
     * @param ver Lock version.
     * @param timeout Timeout to acquire lock.
     * @param reenter Reentry flag.
     * @param ec Eventually consistent flag.
     * @param tx Transaction flag.
     * @param implicitSingle Implicit flag.
     * @return New candidate.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    @Nullable public GridCacheMvccCandidate<K> addNearLocal(@Nullable UUID dhtNodeId, long threadId, GridCacheVersion ver,
        long timeout, boolean reenter, boolean ec, boolean tx, boolean implicitSingle)
        throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;
        GridCacheMvccCandidate<K> cand;

        V val;

        UUID locId = cctx.nodeId();

        lock();

        try {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteCandidate(locId, threadId);

            if (c != null)
                return reenter ? c.reenter() : null;

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            // Lock could not be acquired.
            if (timeout < 0 && !emptyBefore)
                return null;

            // Local lock for near cache is a remote lock.
            cand = mvcc.addRemote(this, locId, dhtNodeId, threadId, ver, timeout, ec, tx, implicitSingle,
                /*near-local*/true);

            owner = mvcc.anyOwner();

            boolean emptyAfter = mvcc.isEmpty();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;

            refreshRemotes();
        }
        finally {
            unlock();
        }

        // This call must be outside of synchronization.
        checkOwnerChanged(prev, owner, val);

        return cand;
    }

    /**
     * @param ver Version to set DHT node ID for.
     * @param dhtNodeId DHT node ID.
     * @return {@code true} if candidate was found.
     * @throws GridCacheEntryRemovedException If entry is removed.
     */
    @Nullable public GridCacheMvccCandidate<K> dhtNodeId(GridCacheVersion ver, UUID dhtNodeId)
        throws GridCacheEntryRemovedException {
        lock();

        try {
            checkObsolete();

            GridCacheMvccCandidate<K> cand = mvcc.candidate(ver);

            if (cand == null)
                return null;

            cand.otherNodeId(dhtNodeId);

            return cand;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> readyLock(GridCacheMvccCandidate<K> cand)
        throws GridCacheEntryRemovedException {
        lock();

        // Essentially no-op as locks are acquired on primary nodes.
        try {
            checkObsolete();

            return mvcc.anyOwner();
        }
        finally {
            unlock();
        }
    }

    /**
     * Unlocks local lock.
     *
     * @return Removed candidate, or <tt>null</tt> if thread still holds the lock.
     */
    @Override @Nullable public GridCacheMvccCandidate<K> removeLock() {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        UUID locId = cctx.nodeId();

        lock();

        try {
            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            GridCacheMvccCandidate<K> cand = mvcc.remoteCandidate(locId, Thread.currentThread().getId());

            if (cand != null && cand.nearLocal() && cand.owner() && cand.used()) {
                // If a reentry, then release reentry. Otherwise, remove lock.
                GridCacheMvccCandidate<K> reentry = cand.unenter();

                if (reentry != null) {
                    assert reentry.reentry();

                    return reentry;
                }

                mvcc.remove(cand.version());

                owner = mvcc.anyOwner();

                refreshRemotes();
            }
            else
                return null;

            boolean emptyAfter = mvcc.isEmpty();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;
        }
        finally {
            unlock();
        }

        assert owner != prev;

        if (log.isDebugEnabled())
            log.debug("Released local candidate from entry [owner=" + owner + ", prev=" + prev +
                ", entry=" + this + ']');

        if (prev != null && owner != prev)
            checkThreadChain(prev);

        // This call must be outside of synchronization.
        checkOwnerChanged(prev, owner, val);

        return owner != prev ? prev : null;
    }

    /** {@inheritDoc} */
    @Override public GridCacheEntry<K, V> wrap(boolean prjAware) {
        GridCacheProjectionImpl<K, V> prjPerCall = cctx.projectionPerCall();

        if (prjPerCall != null && prjAware)
            return new GridPartitionedCacheEntryImpl<K, V>(prjPerCall, cctx, key, this);

        GridCacheEntryImpl<K, V> wrapper = this.wrapper;

        if (wrapper == null)
            this.wrapper = wrapper = new GridPartitionedCacheEntryImpl<K, V>(null, cctx, key, this);

        return wrapper;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearCacheEntry.class, this, "super", super.toString());
    }
}

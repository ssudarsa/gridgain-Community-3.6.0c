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
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.kernal.processors.cache.distributed.dht.GridDhtPartitionState.*;

/**
 * Key partition.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridDhtLocalPartition<K, V> implements Comparable<GridDhtLocalPartition> {
    /** Static logger to avoid re-creation. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Partition ID. */
    private final int id;

    /** State. */
    @GridToStringExclude
    private AtomicStampedReference<GridDhtPartitionState> state =
        new AtomicStampedReference<GridDhtPartitionState>(MOVING, 0);

    /** Rent future. */
    @GridToStringExclude
    private final GridFutureAdapter<?> rent;

    /** Entries map. */
    private final ConcurrentMap<K, GridDhtCacheEntry<K, V>> map = new ConcurrentHashMap<K, GridDhtCacheEntry<K,V>>();

    /** Context. */
    private final GridCacheContext<K, V> cctx;

    /** Logger. */
    private final GridLogger log;

    /** Create time. */
    @GridToStringExclude
    private final long createTime = System.currentTimeMillis();

    /** Eviction history. */
    private volatile Map<K, GridCacheVersion> evictHist = new HashMap<K, GridCacheVersion>();

    /** Lock. */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @param cctx Context.
     * @param id Partition ID.
     */
    GridDhtLocalPartition(GridCacheContext<K, V> cctx, int id) {
        assert cctx != null;

        this.id = id;
        this.cctx = cctx;

        log = U.logger(cctx.kernalContext(), logRef, this);

        rent = new GridFutureAdapter<Object>(cctx.kernalContext());
    }

    /**
     * @return Partition ID.
     */
    public int id() {
        return id;
    }

    /**
     * @return Create time.
     */
    long createTime() {
        return createTime;
    }

    /**
     * @return Partition state.
     */
    public GridDhtPartitionState state() {
        return state.getReference();
    }

    /**
     * @return Reservations.
     */
    public int reservations() {
        return state.getStamp();
    }

    /**
     * @return Entries belonging to partition.
     */
    public Collection<GridDhtCacheEntry<K, V>> entries() {
        return map.values();
    }

    /**
     * @return {@code True} if partition is empty.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Note that this method has O(N) complexity.
     *
     * @return Number of entries in thsis partition.
     */
    public int size() {
        return map.size();
    }

    /**
     * @return If partition is moving or owning or renting.
     */
    public boolean valid() {
        GridDhtPartitionState state = state();

        return state == MOVING || state == OWNING || state == RENTING;
    }

    /**
     * @param entry Entry to add.
     */
    void onAdded(GridDhtCacheEntry<K, V> entry) {
        GridDhtPartitionState state = state();

        assert state != EVICTED : "Adding entry to invalid partition: " + this;

        map.put(entry.key(), entry);
    }

    /**
     * @param entry Entry to remove.
     */
    void onRemoved(GridDhtCacheEntry<K, V> entry) {
        assert entry.obsolete();

        // Make sure to remove exactly this entry.
        map.remove(entry.key(), entry);

        // Attempt to evict.
        tryEvict();
    }

    /**
     * Locks partition.
     */
    @SuppressWarnings( {"LockAcquiredButNotSafelyReleased"})
    public void lock() {
        lock.lock();
    }

    /**
     * Unlocks partition.
     */
    public void unlock() {
        lock.unlock();
    }

    /**
     * @param key Key.
     * @param ver Version.
     */
    public void onEntryEvicted(K key, GridCacheVersion ver) {
        assert key != null;
        assert ver != null;
        assert lock.isHeldByCurrentThread(); // Only one thread can enter this method at a time.

        if (state() != MOVING)
            return;

        Map<K, GridCacheVersion> evictHist0 = evictHist;

        if (evictHist0 != null ) {
            GridCacheVersion ver0 = evictHist0.get(key);

            if (ver0 == null || ver0.isLess(ver)) {
                GridCacheVersion ver1  = evictHist0.put(key, ver);

                assert ver1 == ver0;
            }
        }
    }

    /**
     * Cache preloader should call this method within partition lock.
     *
     * @param key Key.
     * @param ver Version.
     * @return {@code True} if preloading is permitted.
     */
    public boolean preloadingPermitted(K key, GridCacheVersion ver) {
        assert key != null;
        assert ver != null;
        assert lock.isHeldByCurrentThread(); // Only one thread can enter this method at a time.

        if (state() != MOVING)
            return false;

        Map<K, GridCacheVersion> evictHist0 = evictHist;

        if (evictHist0 != null)  {
            GridCacheVersion ver0 = evictHist0.get(key);

            // Permit preloading if version in history
            // is missing or less than passed in.
            return ver0 == null || ver0.isLess(ver);
        }

        return false;
    }

    /**
     * Reserves a partition so it won't be cleared.
     *
     * @return {@code True} if reserved.
     */
    public boolean reserve() {
        while (true) {
            int reservations = state.getStamp();

            GridDhtPartitionState s = state.getReference();

            if (s == EVICTED)
                return false;

            if (state.compareAndSet(s, s, reservations, reservations + 1))
                return true;
        }
    }

    /**
     * Releases previously reserved partition.
     */
    public void release() {
        while (true) {
            int reservations = state.getStamp();

            if (reservations == 0)
                return;

            GridDhtPartitionState s = state.getReference();

            assert s != EVICTED;

            // Decrement reservations.
            if (state.compareAndSet(s, s, reservations, --reservations)) {
                tryEvict();

                break;
            }
        }
    }

    /**
     * @return {@code True} if transitioned to OWNING state.
     */
    boolean own() {
        while (true) {
            int reservations = state.getStamp();

            GridDhtPartitionState s = state.getReference();

            if (s == RENTING || s == EVICTED)
                return false;

            if (s == OWNING)
                return true;

            assert s == MOVING;

            if (state.compareAndSet(MOVING, OWNING, reservations, reservations)) {
                if (log.isDebugEnabled())
                    log.debug("Owned partition: " + this);

                // No need to keep history any more.
                evictHist = null;

                return true;
            }
        }
    }

    /**
     * @return Future to signal that this node is no longer an owner or backup.
     */
    GridFuture<?> rent() {
        while (true) {
            int reservations = state.getStamp();

            GridDhtPartitionState s = state.getReference();

            if (s == RENTING || s == EVICTED)
                return rent;

            if (state.compareAndSet(s, RENTING, reservations, reservations)) {
                if (log.isDebugEnabled())
                    log.debug("Moved partition to RENTING state: " + this);

                rent.addWatch(cctx.stopwatch("PARTITION_RENT"));

                // Evict asynchronously, as the 'rent' method may be called
                // from within write locks on local partition.
                tryEvictAsync();

                break;
            }
        }

        return rent;
    }

    /**
     * @return Future for evict attempt.
     */
    private GridFuture<Boolean> tryEvictAsync() {
        if (map.isEmpty() && state.compareAndSet(RENTING, EVICTED, 0, 0)) {
            if (log.isDebugEnabled())
                log.debug("Evicted partition: " + this);

            rent.onDone();

            return new GridFinishedFuture<Boolean>(cctx.kernalContext(), true);
        }

        return cctx.closures().callLocalSafe(new GPC<Boolean>() {
            @Override public Boolean call() {
                return tryEvict();
            }
        }, /*system pool*/true);
    }

    /**
     * @return {@code True} if entry has been transitioned to state EVICTED.
     */
    private boolean tryEvict() {
        // Attempt to evict partition entries from cache.
        if (state.getReference() == RENTING && state.getStamp() == 0)
            clearAll();

        if (map.isEmpty() && state.compareAndSet(RENTING, EVICTED, 0, 0)) {
            if (log.isDebugEnabled())
                log.debug("Evicted partition: " + this);

            rent.onDone();

            return true;
        }

        return false;
    }

    /**
     *
     */
    void onUnlock() {
        tryEvict();
    }

    /**
     * @return {@code True} if local node is primary for this partition.
     */
    public boolean primary() {
        return cctx.primary(cctx.localNode(), id);
    }

    /**
     * Clears values for this partition.
     */
    private void clearAll() {
        GridCacheVersion clearVer = cctx.versions().next();

        for (Iterator<GridDhtCacheEntry<K, V>> it = map.values().iterator(); it.hasNext();) {
            GridDhtCacheEntry<K, V> cached = it.next();

            try {
                if (cached.clear(clearVer, cctx.isSwapEnabled(), true, CU.<K, V>empty()))
                    it.remove();
            }
            catch (GridException e) {
                U.error(log, "Failed to clear cache entry for evicted partition: " + cached, e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id;
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"OverlyStrongTypeCast"})
    @Override public boolean equals(Object obj) {
        return obj instanceof GridDhtLocalPartition && (obj == this || ((GridDhtLocalPartition)obj).id() == id);
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridDhtLocalPartition part) {
        if (part == null)
            return 1;

        return id == part.id() ? 0 : id > part.id() ? 1 : -1;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtLocalPartition.class, this,
            "state", state(),
            "reservations", reservations(),
            "empty", map.isEmpty(),
            "createTime", U.format(createTime));
    }
}

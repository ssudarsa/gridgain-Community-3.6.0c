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
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.cache.GridCacheFlag.*;

/**
 * Concurrent implementation of cache map.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheConcurrentMap<K, V> {
    /** Debug flag. */
    private static final boolean DEBUG = false;

    /** Random. */
    private static final Random RAND = new Random();

    /** The default load factor for this map. */
    private static final float DFLT_LOAD_FACTOR = 0.75f;

    /** The default concurrency level for this map. */
    private static final int DFLT_CONCUR_LEVEL = 2048;

    /**
     * The maximum capacity, used if a higher value is implicitly specified by either
     * of the constructors with arguments. Must be a power of two <= 1<<30 to ensure
     * that entries are indexable using integers.
     */
    private static final int MAX_CAP = 1 << 30;

    /** The maximum number of segments to allow. */
    private static final int MAX_SEGS = 1 << 16; // slightly conservative

    /**
     * Mask value for indexing into segments. The upper bits of a
     * key's hash code are used to choose the segment.
     */
    private final int segMask;

    /** Shift value for indexing within segments. */
    private final int segShift;

    /** The segments, each of which is a specialized hash table. */
    private final Segment[] segs;

    /** */
    private GridCacheMapEntryFactory<K, V> factory;

    /** Cache context. */
    private final GridCacheContext<K, V> ctx;

    /** */
    private final AtomicInteger mapPubSize = new AtomicInteger();

    /** */
    private final AtomicInteger mapSize = new AtomicInteger();

    /** Filters cache internal entry. */
    private static final P1<GridCacheEntry<?, ?>> NON_INTERNAL =
        new P1<GridCacheEntry<?, ?>>() {
            @Override public boolean apply(GridCacheEntry<?, ?> entry) {
                return !(entry.getKey() instanceof GridCacheInternal);
            }
        };

    /** Non-internal predicate array. */
    public static final GridPredicate[] NON_INTERNAL_ARR = new P1[] {NON_INTERNAL};

    /** Filters obsolete cache map entry. */
    private final GridPredicate<GridCacheMapEntry<K, V>> obsolete =
        new P1<GridCacheMapEntry<K, V>>() {
            @Override public boolean apply(GridCacheMapEntry<K, V> entry) {
                return entry.obsolete();
            }
        };

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because ConcurrentHashMap uses power-of-two length hash tables,
     * that otherwise encounter collisions for hashCodes that do not
     * differ in lower or upper bits.
     *
     * @param h Value to hash.
     * @return Hash value.
     */
    private static int hash(int h) {
        // Spread bits to regularize both segment and index locations,
        // using variant of single-word Wang/Jenkins hash.
        h += (h <<  15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h <<   3);
        h ^= (h >>>  6);
        h += (h <<   2) + (h << 14);

        return h ^ (h >>> 16);
    }

    /**
     * Returns the segment that should be used for key with given hash
     *
     * @param hash The hash code for the key.
     * @return The segment.
     */
    private Segment segmentFor(int hash) {
        return segs[(hash >>> segShift) & segMask];
    }

    /**
     * Creates a new, empty map with the specified initial
     * capacity, load factor and concurrency level.
     *
     * @param ctx Cache context.
     * @param initialCapacity the initial capacity. The implementation
     *      performs internal sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     *      Resizing may be performed when the average number of elements per
     *      bin exceeds this threshold.
     * @param concurrencyLevel the estimated number of concurrently
     *      updating threads. The implementation performs internal sizing
     *      to try to accommodate this many threads.
     * @throws IllegalArgumentException if the initial capacity is
     *      negative or the load factor or concurrencyLevel are
     *      non-positive.
     */
    @SuppressWarnings({"unchecked"})
    private GridCacheConcurrentMap(GridCacheContext<K, V> ctx, int initialCapacity, float loadFactor,
        int concurrencyLevel) {
        this.ctx = ctx;

        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();

        if (concurrencyLevel > MAX_SEGS)
            concurrencyLevel = MAX_SEGS;

        // Find power-of-two sizes best matching arguments
        int sshift = 0;
        int ssize = 1;

        while (ssize < concurrencyLevel) {
            ++sshift;
            ssize <<= 1;
        }

        segShift = 32 - sshift;
        segMask = ssize - 1;
        segs = (Segment[])Array.newInstance(Segment.class, ssize);

        if (initialCapacity > MAX_CAP)
            initialCapacity = MAX_CAP;

        int c = initialCapacity / ssize;

        if (c * ssize < initialCapacity)
            ++c;

        int cap = 1;

        while (cap < c)
            cap <<= 1;

        if (cap < 16)
            cap = 16;

        for (int i = 0; i < segs.length; ++i)
            segs[i] = new Segment(cap, loadFactor);
    }

    /**
     * Creates a new, empty map with the specified initial capacity
     * and load factor and with the default concurrencyLevel (16).
     *
     * @param ctx Cache context.
     * @param initialCapacity The implementation performs internal
     *      sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     *      Resizing may be performed when the average number of elements per
     *      bin exceeds this threshold.
     * @throws IllegalArgumentException if the initial capacity of
     *      elements is negative or the load factor is non-positive.
     */
    public GridCacheConcurrentMap(GridCacheContext<K, V> ctx, int initialCapacity, float loadFactor) {
        this(ctx, initialCapacity, loadFactor, DFLT_CONCUR_LEVEL);
    }

    /**
     * Creates a new, empty map with the specified initial capacity,
     * and with default load factor (0.75) and concurrencyLevel (16).
     *
     * @param ctx Cache context.
     * @param initialCapacity the initial capacity. The implementation
     *      performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     *      elements is negative.
     */
    public GridCacheConcurrentMap(GridCacheContext<K, V> ctx, int initialCapacity) {
        this(ctx, initialCapacity, DFLT_LOAD_FACTOR, DFLT_CONCUR_LEVEL);
    }

    /**
     * Sets factory for entries.
     *
     * @param factory Entry factory.
     */
    public void setEntryFactory(GridCacheMapEntryFactory<K, V> factory) {
        assert factory != null;

        this.factory = factory;
    }

    /**
     * @return Non-internal predicate.
     */
    private static <K, V> GridPredicate<? super GridCacheEntry<K, V>>[] nonInternal() {
        return (GridPredicate<? super GridCacheEntry<K,V>>[])NON_INTERNAL_ARR;
    }

    /**
     * @param filter Filter to add to non-internal-key filter.
     * @return Non-internal predicate.
     */
    private static <K, V> GridPredicate<? super GridCacheEntry<K, V>>[] nonInternal(
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return F.asArray(F.and(filter, (GridPredicate<? super GridCacheEntry<K, V>>)NON_INTERNAL));
    }

    /**
     * @return {@code True} if this map is empty.
     */
    public boolean isEmpty() {
        return mapSize.get() == 0;
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map.
     */
    public int size() {
        return mapSize.get();
    }

    /**
     * @return Public size.
     */
    public int publicSize() {
        return mapPubSize.get();
    }

    /**
     * @param key Key.
     * @return {@code True} if map contains mapping for provided key.
     */
    public boolean containsKey(Object key) {
        int hash = hash(key.hashCode());

        return segmentFor(hash).containsKey(key, hash);
    }

    /**
     * Collection of all (possibly {@code null}) values.
     *
     * @param filter Filter.
     * @return a collection view of the values contained in this map.
     */
    public Collection<V> allValues(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new Values<K, V>(this, filter);
    }

    /**
     * @return Random entry out of hash map.
     */
    @Nullable public GridCacheMapEntry<K, V> randomEntry() {
        while (true) {
            if (mapPubSize.get() == 0)
                return null;

            // Desired and current indexes.
            int segIdx = RAND.nextInt(segs.length);
            int i = 0;

            Segment seg = null;

            for (Segment s : segs) {
                if (s.publicSize() > 0) {
                    seg = s;

                    if (segIdx == i++)
                        break;
                }
            }

            if (seg == null)
                // It happened so that all public values had been removed from segments.
                return null;

            GridCacheMapEntry<K, V> entry = seg.randomEntry();

            if (entry == null)
                continue;

            assert !(entry.key() instanceof GridCacheInternal);

            return entry;
        }
    }

    /**
     * Returns the entry associated with the specified key in the
     * HashMap.  Returns null if the HashMap contains no mapping
     * for this key.
     *
     * @param key Key.
     * @return Entry.
     */
    @Nullable public GridCacheMapEntry<K, V> getEntry(Object key) {
        assert key != null;

        int hash = hash(key.hashCode());

        return segmentFor(hash).get(key, hash);
    }

    /**
     * @param topVer Topology version.
     * @param key Key.
     * @param val Value.
     * @param ttl Time to live.
     * @return Cache entry for corresponding key-value pair.
     */
    public GridCacheMapEntry<K, V> putEntry(long topVer, K key, @Nullable V val, long ttl) {
        assert key != null;

        int hash = hash(key.hashCode());

        return segmentFor(hash).put(key, hash, val, topVer, ttl);
    }

    /**
     * @param topVer Topology version.
     * @param key Key.
     * @param val Value.
     * @param ttl Time to live.
     * @param create Create flag.
     * @return Triple where the first element is current entry associated with the key,
     *      the second is created entry and the third is doomed (all may be null).
     */
    public GridTriple<GridCacheMapEntry<K, V>> putEntryIfObsoleteOrAbsent(long topVer, K key, @Nullable V val,
        long ttl, boolean create) {
        assert key != null;

        int hash = hash(key.hashCode());

        return segmentFor(hash).putIfObsolete(key, hash, val, topVer, ttl, create);
    }

    /**
     * Copies all of the mappings from the specified map to this map
     * These mappings will replace any mappings that
     * this map had for any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map.
     * @param ttl Time to live.
     * @throws NullPointerException If the specified map is null.
     */
    public void putAll(Map<? extends K, ? extends V> m, long ttl) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            putEntry(-1, e.getKey(), e.getValue(), ttl);
    }

    /**
     * Removes and returns the entry associated with the specified key
     * in the HashMap. Returns null if the HashMap contains no mapping
     * for this key.
     *
     * @param key Key.
     * @return Removed entry, possibly {@code null}.
     */
    @Nullable public GridCacheMapEntry<K, V> removeEntry(K key) {
        assert key != null;

        int hash = hash(key.hashCode());

        return segmentFor(hash).remove(key, hash, null);
    }

    /**
     * Removes passed in entry if it presents in the map.
     *
     * @param e Entry to remove.
     * @return {@code True} if remove happened.
     */
    public boolean removeEntry(GridCacheEntryEx<K, V> e) {
        assert e != null;

        K key = e.key();

        int hash = hash(key.hashCode());

        return segmentFor(hash).remove(key, hash, same(e)) != null;
    }

    /**
     * @param p Entry to check equality.
     * @return Predicate to filter the same (equal by ==) entry.
     */
    private GridPredicate<GridCacheMapEntry<K, V>> same(final GridCacheEntryEx<K, V> p) {
        return new P1<GridCacheMapEntry<K,V>>() {
            @Override public boolean apply(GridCacheMapEntry<K, V> e) {
                return e == p;
            }
        };
    }

    /**
     * Removes and returns the entry associated with the specified key
     * in the HashMap if entry is obsolete. Returns null if the HashMap
     * contains no mapping for this key.
     *
     * @param key Key.
     * @return Removed entry, possibly {@code null}.
     */
    @SuppressWarnings( {"unchecked"})
    @Nullable public GridCacheMapEntry<K, V> removeEntryIfObsolete(K key) {
        assert key != null;

        int hash = hash(key.hashCode());

        return segmentFor(hash).remove(key, hash, obsolete);
    }

    /**
     * Removes the mapping for this key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map.
     * @return previous value associated with specified key, or {@code null}
     *           if there was no mapping for key.  A {@code null} return can
     *           also indicate that the map previously associated {@code null}
     *           with the specified key.
     */
    @Nullable public V remove(K key) {
        GridCacheMapEntry<K, V> e = removeEntry(key);

        return (e == null ? null : e.rawGet());
    }

    /**
     * Entry wrapper set.
     *
     * @param filter Filter.
     * @return Entry wrapper set.
     */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    public Set<GridCacheEntryImpl<K, V>> wrappers(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return (Set<GridCacheEntryImpl<K, V>>)(Set<? extends GridCacheEntry<K, V>>)entries(filter);
    }

    /**
     * Entry wrapper set casted to projections.
     *
     * @param filter Filter to check.
     * @return Entry projections set.
     */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    public Set<GridCacheEntry<K, V>> projections(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return (Set<GridCacheEntry<K, V>>)(Set<? extends GridCacheEntry<K, V>>)wrappers(filter);
    }

    /**
     * Same as {@link #wrappers(GridPredicate[])}
     *
     * @param filter Filter.
     * @return Set of the mappings contained in this map.
     */
    @SuppressWarnings({"unchecked"})
    public Set<GridCacheEntry<K, V>> entries(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new EntrySet<K, V>(this, filter);
    }

    /**
     * Internal entry set, excluding {@link GridCacheInternal} entries.
     *
     * @return Set of the mappings contained in this map.
     */
    public Set<GridCacheEntryEx<K, V>> entries0() {
        return new Set0<K, V>(this, GridCacheConcurrentMap.<K, V>nonInternal());
    }

    /**
     * Gets all internal entry set, including {@link GridCacheInternal} entries.
     *
     * @return All internal entry set, including {@link GridCacheInternal} entries.
     */
    public Set<GridCacheEntryEx<K, V>> allEntries0() {
        return new Set0<K, V>(this, CU.<K, V>empty());
    }

    /**
     * Key set.
     *
     * @param filter Filter.
     * @return Set of the keys contained in this map.
     */
    public Set<K> keySet(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new KeySet<K, V>(this, filter);
    }

    /**
     * Collection of non-{@code null} values.
     *
     * @param filter Filter.
     * @return Collection view of the values contained in this map.
     */
    public Collection<V> values(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return F.view(allValues(filter), F.<V>notNull());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheConcurrentMap.class, this, "size", mapSize, "pubSize", mapPubSize);
    }

    /**
     *
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    void printDebugInfo() {
        for (Segment s : segs)
            s.lock();

        try {
            X.println(">>> Cache map debug info: " + ctx.namexx());

            for (int i = 0; i < segs.length; i++) {
                Segment seg = segs[i];

                X.println("    Segment [idx=" + i + ", size=" + seg.size() + ']');

                Bucket<K, V>[] tab = seg.table;

                for (int j = 0; j < tab.length; j++)
                    X.println("        Bucket [idx=" + j + ", bucket=" + tab[j] + ']');
            }

            checkConsistency();
        }
        finally {
            for (Segment s : segs)
                s.unlock();
        }
    }

    /**
     *
     */
    private void checkConsistency() {
        int size = 0;
        int pubSize = 0;

        for (Segment s : segs) {
            Bucket<K, V>[] tab = s.table;

            for (Bucket<K, V> b : tab) {
                if (b != null) {
                    HashEntry<K, V> e = b.entry();

                    assert e != null;

                    int cnt = 0;
                    int pubCnt = 0;

                    while (e != null) {
                        cnt++;

                        if (!(e.key instanceof GridCacheInternal))
                            pubCnt++;

                        e = e.next;
                    }

                    assert b.count() == cnt;
                    assert b.publicCount() == pubCnt;

                    size += cnt;
                    pubSize += pubCnt;
                }
            }
        }

        assert size() == size;
        assert publicSize() == pubSize;
    }

    /**
     * Hash bucket.
     */
    private static class Bucket<K, V> {
        /** */
        private volatile int cnt;

        /** */
        private volatile int pubCnt;

        /** */
        private volatile HashEntry<K, V> root;

        /**
         * @param root Entry.
         */
        Bucket(HashEntry<K, V> root) {
            assert root != null;

            this.root = root;

            for (HashEntry<K, V> e = root; e != null; e = e.next) {
                cnt++;

                if (!(root.key instanceof GridCacheInternal))
                    pubCnt++;
            }
        }

        /**
         *
         */
        Bucket() {
            // No-op.
        }

        /**
         * @return Bucket count.
         */
        int count() {
            return cnt;
        }

        /**
         * @return Bucket count.
         */
        int publicCount() {
            return pubCnt;
        }

        /**
         * @return Entry.
         */
        HashEntry<K, V> entry() {
            return root;
        }

        /**
         * @param root New root.
         */
        void onAdd(HashEntry<K, V> root) {
            assert root != null;

            this.root = root;

            cnt++;

            if (!(root.key instanceof GridCacheInternal))
                pubCnt++;
        }

        /**
         * @param root New root.
         * @param rmvPub {@code True} if public entry was removed from the bucket.
         */
        void onRemove(HashEntry<K, V> root, boolean rmvPub) {
            assert root != null;

            this.root = root;

            cnt--;

            if (rmvPub)
                pubCnt--;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(Bucket.class, this);
        }

        /**
         * @param i Size.
         * @return Bucket array.
         */
        @SuppressWarnings("unchecked")
        static <K, V> Bucket<K, V>[] newArray(int i) {
            return new Bucket[i];
        }
    }

    /**
     * ConcurrentHashMap list entry. Note that this is never exported
     * out as a user-visible Map.Entry.
     *
     * Because the value field is volatile, not final, it is legal wrt
     * the Java Memory Model for an unsynchronized reader to see null
     * instead of initial value when read via a data race.  Although a
     * reordering leading to this is not likely to ever actually
     * occur, the Segment.readValueUnderLock method is used as a
     * backup in case a null (pre-initialized) value is ever seen in
     * an unsynchronized access method.
     */
    private static final class HashEntry<K, V> {
        /** */
        @GridToStringInclude
        private final K key;

        /** */
        private final int hash;

        /** */
        @GridToStringExclude
        private final GridCacheMapEntry<K, V> val;

        /** */
        private final HashEntry<K, V> next;

        /**
         * @param key Key.
         * @param hash Hash.
         * @param next Next.
         * @param val Value.
         */
        HashEntry(K key, int hash, HashEntry<K, V> next, GridCacheMapEntry<K, V> val) {
            this.key = key;
            this.hash = hash;
            this.next = next;
            this.val = val;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(HashEntry.class, this);
        }
    }

    /**
     * Segments are specialized versions of hash tables.  This
     * subclasses from ReentrantLock opportunistically,
     * just to simplify some locking and avoid separate construction.
     */
    private class Segment extends ReentrantLock {
        /*
         * Segments maintain a table of entry lists that are ALWAYS
         * kept in a consistent state, so can be read without locking.
         * Next fields of nodes are immutable (final).  All list
         * additions are performed at the front of each bin. This
         * makes it easy to check changes, and also fast to traverse.
         * When nodes would otherwise be changed, new nodes are
         * created to replace them. This works well for hash tables
         * since the bin lists tend to be short. (The average length
         * is less than two for the default load factor threshold.)
         *
         * Read operations can thus proceed without locking, but rely
         * on selected uses of volatiles to ensure that completed
         * write operations performed by other threads are
         * noticed. For most purposes, the "count" field, tracking the
         * number of elements, serves as that volatile variable
         * ensuring visibility.  This is convenient because this field
         * needs to be read in many read operations anyway:
         *
         *   - All (unsynchronized) read operations must first read the
         *     "count" field, and should not look at table entries if
         *     it is 0.
         *
         *   - All (synchronized) write operations should write to
         *     the "count" field after structurally changing any bin.
         *     The operations must not take any action that could even
         *     momentarily cause a concurrent read operation to see
         *     inconsistent data. This is made easier by the nature of
         *     the read operations in Map. For example, no operation
         *     can reveal that the table has grown but the threshold
         *     has not yet been updated, so there are no atomicity
         *     requirements for this with respect to reads.
         *
         * As a guide, all critical volatile reads and writes to the
         * count field are marked in code comments.
         */

        /**
         * The number of elements in this segment's region.
         */
        private volatile int segSize;

        /**
         * The number of public elements in this segment's region.
         */
        private volatile int segPubSize;

        /**
         * The table is rehashed when its size exceeds this threshold.
         * (The value of this field is always <tt>(int)(capacity * loadFactor)</tt>.)
         */
        private int threshold;

        /** The per-segment table. */
        private volatile Bucket<K, V>[] table;

        /**
         * The load factor for the hash table. Even though this value
         * is same for all segments, it is replicated to avoid needing
         * links to outer object.
         * @serial
         */
        private final float loadFactor;

        /**
         * @param initCap Initial capacity.
         * @param lf Load factor.
         */
        Segment(int initCap, float lf) {
            loadFactor = lf;

            setTable(Bucket.<K, V>newArray(initCap));
        }

        /**
         * Sets table to new buckets array.
         * <p>
         * Call only while holding lock or in constructor.
         *
         * @param newTable New table.
         */
        void setTable(Bucket<K, V>[] newTable) {
            threshold = (int)(newTable.length * loadFactor);

            table = newTable;
        }

        /**
         * Returns properly casted first entry for given hash.
         *
         * @param hash Hash.
         * @return Entry for hash.
         */
        @Nullable HashEntry<K, V> getFirst(int hash) {
            Bucket<K, V>[] tab = table;

            Bucket<K, V> bucket = tab[hash & (tab.length - 1)];

            return bucket != null ? bucket.entry() : null;
        }

        /**
         * Reads value field of an entry under lock. Called if value
         * field ever appears to be null. This is possible only if a
         * compiler happens to reorder a HashEntry initialization with
         * its table assignment, which is legal under memory model
         * but is not known to ever occur.
         *
         * @param e Entry whose value to read.
         * @return Read value.
         */
        GridCacheMapEntry<K, V> readValueUnderLock(HashEntry<K, V> e) {
            lock();

            try {
                return e.val;
            }
            finally {
                unlock();
            }
        }

        /**
         * @param key Key.
         * @param hash Hash.
         * @return Value.
         */
        @Nullable GridCacheMapEntry<K, V> get(Object key, int hash) {
            if (segSize != 0) {
                HashEntry<K, V> e = getFirst(hash);

                while (e != null) {
                    if (e.hash == hash && key.equals(e.key)) {
                        GridCacheMapEntry<K, V> v = e.val;

                        if (v != null)
                            return v;

                        return readValueUnderLock(e); // recheck
                    }

                    e = e.next;
                }
            }

            return null;
        }

        /**
         * @param key Key.
         * @param hash Hash.
         * @return {@code True} if segment contains value.
         */
        boolean containsKey(Object key, int hash) {
            if (segSize != 0) {
                HashEntry<K, V> e = getFirst(hash);

                while (e != null) {
                    if (e.hash == hash && key.equals(e.key))
                        return true;

                    e = e.next;
                }
            }

            return false;
        }

        /**
         * @param key Key.
         * @param hash Hash.
         * @param val Value.
         * @param topVer Topology version.
         * @param ttl TTL.
         * @return Associated value.
         */
        @SuppressWarnings({"unchecked"})
        GridCacheMapEntry<K, V> put(K key, int hash, @Nullable V val, long topVer, long ttl) {
            lock();

            try {
                return put0(key, hash, val, topVer, ttl);
            }
            finally {
                unlock();
            }
        }

        /**
         * @param key Key.
         * @param hash Hash.
         * @param val Value.
         * @param topVer Topology version.
         * @param ttl TTL.
         * @return Associated value.
         */
        @SuppressWarnings({"unchecked"})
        private GridCacheMapEntry<K, V> put0(K key, int hash, V val, long topVer, long ttl) {
            int idx = -1;

            Bucket<K, V> bucket = null;

            Bucket<K, V>[] tab = null;

            try {
                int c = segSize;

                if (c++ > threshold) // Ensure capacity.
                    rehash();

                tab = table;

                idx = hash & (tab.length - 1);

                bucket = tab[idx];

                if (bucket == null)
                    tab[idx] = bucket = new Bucket<K, V>();

                HashEntry<K, V> first = bucket.entry();

                HashEntry<K, V> e = first;

                while (e != null && (e.hash != hash || !key.equals(e.key)))
                    e = e.next;

                GridCacheMapEntry<K, V> retVal;

                if (e != null) {
                    retVal = e.val;

                    e.val.rawPut(val, ttl);
                }
                else {
                    GridCacheMapEntry next = bucket.entry() != null ? bucket.entry().val : null;

                    GridCacheMapEntry newEntry = factory.create(ctx, topVer, key, hash, val, next, ttl);

                    retVal = newEntry;

                    HashEntry<K, V> newRoot = new HashEntry<K, V>(key, hash, first, newEntry);

                    bucket.onAdd(newRoot);

                    // Modify counters.
                    if (!(key instanceof GridCacheInternal)) {
                        mapPubSize.incrementAndGet();

                        segPubSize++;
                    }

                    mapSize.incrementAndGet();

                    segSize = c;
                }

                return retVal;
            }
            finally {
                if (bucket != null && bucket.count() == 0) {
                    // We cannot leave empty bucket in segment.
                    assert tab != null;
                    assert idx >= 0;

                    tab[idx] = null;
                }

                if (DEBUG)
                    checkConsistency();
            }
        }

        /**
         * @param key Key.
         * @param hash Hash.
         * @param val Value.
         * @param topVer Topology version.
         * @param ttl TTL.
         * @param create Create flag.
         * @return Triple where the first element is current entry associated with the key,
         *      the second is created entry and the third is doomed (all may be null).
         */
        @SuppressWarnings( {"unchecked"})
        GridTriple<GridCacheMapEntry<K, V>> putIfObsolete(K key, int hash, @Nullable V val,
            long topVer, long ttl, boolean create) {
            lock();

            try {
                Bucket<K, V>[] tab = table;

                int idx = hash & (tab.length - 1);

                Bucket<K, V> bucket = tab[idx];

                GridCacheMapEntry<K, V> cur = null;
                GridCacheMapEntry<K, V> created = null;
                GridCacheMapEntry<K, V> doomed = null;

                if (bucket == null) {
                    if (create)
                        cur = created = put(key, hash, val, topVer, ttl);

                    return new GridTriple<GridCacheMapEntry<K, V>>(cur, created, doomed);
                }

                HashEntry<K, V> e = bucket.entry();

                while (e != null && (e.hash != hash || !key.equals(e.key)))
                    e = e.next;

                if (e != null) {
                    if (e.val.obsolete()) {
                        doomed = remove(key, hash, null);

                        if (create)
                            cur = created = put(key, hash, val, topVer, ttl);
                    }
                    else
                        cur = e.val;
                }
                else if (create)
                    cur = created = put(key, hash, val, topVer, ttl);

                return new GridTriple<GridCacheMapEntry<K, V>>(cur, created, doomed);
            }
            finally {
                unlock();
            }
        }

        /**
         *
         */
        void rehash() {
            Bucket<K, V>[] oldTable = table;

            int oldCapacity = oldTable.length;

            if (oldCapacity >= MAX_CAP)
                return;

            /*
             * Reclassify nodes in each list to new Map.  Because we are
             * using power-of-two expansion, the elements from each bin
             * must either stay at same index, or move with a power of two
             * offset. We eliminate unnecessary node creation by catching
             * cases where old nodes can be reused because their next
             * fields won't change. Statistically, at the default
             * threshold, only about one-sixth of them need cloning when
             * a table doubles. The nodes they replace will be eligible for GC
             * as soon as they are no longer referenced by any
             * reader thread that may be in the midst of traversing table
             * right now.
             */
            Bucket<K, V>[] newTable = Bucket.newArray(oldCapacity << 1);

            threshold = (int)(newTable.length * loadFactor);

            int sizeMask = newTable.length - 1;

            for (int i = 0; i < oldCapacity ; i++) {
                // We need to guarantee that any existing reads of old Map can proceed.
                // So, we cannot yet null out each bin.
                Bucket<K, V> b1 = oldTable[i];

                if (b1 == null)
                    continue;

                HashEntry<K, V> e = b1.entry();

                assert e != null;

                HashEntry<K, V> next = e.next;

                int idx = e.hash & sizeMask;

                // Single node on list.
                if (next == null)
                    newTable[idx] = b1;
                else {
                    // Reuse trailing consecutive sequence at same slot.
                    HashEntry<K, V> lastRun = e;

                    int lastIdx = idx;

                    for (HashEntry<K, V> last = next; last != null; last = last.next) {
                        int k = last.hash & sizeMask;

                        if (k != lastIdx) {
                            lastIdx = k;
                            lastRun = last;
                        }
                    }

                    Bucket<K, V> b2 = new Bucket<K, V>(lastRun);

                    newTable[lastIdx] = b2;

                    // Clone all remaining nodes.
                    for (HashEntry<K, V> p = e; p != lastRun; p = p.next) {
                        int k = p.hash & sizeMask;

                        Bucket<K, V> b3 = newTable[k];

                        if (b3 == null)
                            newTable[k] = b3 = new Bucket<K, V>();

                        HashEntry<K, V> newRoot = new HashEntry<K, V>(p.key, p.hash, b3.entry(), p.val);

                        b3.onAdd(newRoot);
                    }
                }
            }

            table = newTable;

            if (DEBUG)
                checkSegmentConsistency();
        }

        /**
         * Remove; match on key only if value null, else match both.
         *
         * @param key Key.
         * @param hash Hash.
         * @param filter Optional predicate.
         * @return Removed value.
         */
        @Nullable GridCacheMapEntry<K, V> remove(Object key, int hash,
            @Nullable GridPredicate<GridCacheMapEntry<K, V>> filter) {
            lock();

            try {
                Bucket<K, V>[] tab = table;

                int idx = hash & (tab.length - 1);

                Bucket<K, V> bucket = tab[idx];

                if (bucket == null)
                    return null;

                HashEntry<K, V> first = bucket.entry();

                assert first != null;

                HashEntry<K, V> e = first;

                while (e != null && (e.hash != hash || !key.equals(e.key)))
                    e = e.next;

                GridCacheMapEntry<K, V> oldValue = null;

                if (e != null) {
                    oldValue = e.val;

                    if (filter != null && !filter.apply(oldValue))
                        return null;

                    // All entries following removed node can stay in list,
                    // but all preceding ones need to be cloned.
                    HashEntry<K, V> newFirst = e.next;

                    for (HashEntry<K, V> p = first; p != e; p = p.next)
                        newFirst = new HashEntry<K, V>(p.key, p.hash, newFirst, p.val);

                    // Is this public entry?
                    boolean rmvPub = !(e.key instanceof GridCacheInternal);

                    if (newFirst == null)
                        tab[idx] = null;
                    else {
                        Bucket<K, V> b = tab[idx];

                        b.onRemove(newFirst, rmvPub);
                    }

                    // Modify counters.
                    if (rmvPub) {
                        mapPubSize.decrementAndGet();

                        segPubSize--;
                    }

                    mapSize.decrementAndGet();

                    segSize--;
                }

                return oldValue;
            }
            finally {
                if (DEBUG)
                    checkSegmentConsistency();

                unlock();
            }
        }

        /**
         * @return Entries count within segment.
         */
        int size() {
            return segSize;
        }

        /**
         * @return Public entries count within segment.
         */
        int publicSize() {
            return segPubSize;
        }

        /**
         * @return Random cache map entry from this segment.
         */
        @Nullable GridCacheMapEntry<K, V> randomEntry() {
            Bucket<K, V>[] tab = table;

            Collection<HashEntry<K, V>> entries = new ArrayList<HashEntry<K, V>>(3);

            int pubCnt = 0;

            int start = RAND.nextInt(tab.length);

            for (int i = start; i < start + tab.length; i++) {
                Bucket<K, V> bucket = tab[i % tab.length];

                if (bucket == null || bucket.publicCount() == 0)
                    continue;

                entries.add(bucket.entry());

                pubCnt += bucket.publicCount();

                if (entries.size() == 3)
                    break;
            }

            if (entries.isEmpty())
                return null;

            if (pubCnt == 0)
                return null;

            // Desired and current indexes.
            int idx = RAND.nextInt(pubCnt);
            int i = 0;

            GridCacheMapEntry<K, V> retVal = null;

            for (HashEntry<K, V> e : entries) {
                for (; e != null; e = e.next) {
                    if (!(e.key instanceof GridCacheInternal)) {
                        // In case desired entry was deleted, we return the closest one from left.
                        retVal = e.val;

                        if (idx == i++)
                            break;
                    }
                }
            }

            return retVal;
        }

        /**
         *
         */
        void checkSegmentConsistency() {
            Bucket<K, V>[] tab = table;

            for (Bucket<K, V> b : tab) {
                if (b != null) {
                    HashEntry<K, V> e = b.entry();

                    assert e != null;

                    int cnt = 0;
                    int pubCnt = 0;

                    while (e != null) {
                        cnt++;

                        if (!(e.key instanceof GridCacheInternal))
                            pubCnt++;

                        e = e.next;
                    }

                    assert b.count() == cnt;
                    assert b.publicCount() == pubCnt;
                }
            }
        }
    }

    /**
     * Iterator over {@link GridCacheEntryEx} elements.
     *
     * @param <K> Key type.
     * @param <V> Value type.
     */
    private static class Iterator0<K, V> implements Iterator<GridCacheEntryEx<K, V>>, Externalizable {
        /** */
        private int nextSegmentIndex;

        /** */
        private int nextTableIndex;

        /** */
        private Bucket<K,V>[] curTable;

        /** */
        private HashEntry<K, V> nextEntry;

        /** Next entry to return. */
        private GridCacheMapEntry<K, V> next;

        /** Next value. */
        private V nextVal;

        /** Current value. */
        private V curVal;

        /** */
        private boolean isVal;

        /** Current entry. */
        private GridCacheMapEntry<K, V> cur;

        /** Iterator filter. */
        private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

        /** Outer cache map. */
        private GridCacheConcurrentMap<K, V> map;

        /** Cache context. */
        private GridCacheContext<K, V> ctx;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public Iterator0() {
            // No-op.
        }

        /**
         * @param map Cache map.
         * @param isVal {@code True} if value iterator.
         * @param filter Entry filter.
         */
        @SuppressWarnings({"unchecked"})
        Iterator0(GridCacheConcurrentMap<K, V> map, boolean isVal,
            GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            this.filter = filter;
            this.isVal = isVal;

            this.map = map;

            ctx = map.ctx;

            nextSegmentIndex = map.segs.length - 1;
            nextTableIndex = -1;

            advance();
        }

        /**
         *
         */
        @SuppressWarnings({"unchecked"})
        private void advance() {
            if (nextEntry != null && advanceInBucket(nextEntry, true))
                return;

            while (nextTableIndex >= 0) {
                Bucket<K, V> bucket = curTable[nextTableIndex--];

                if (bucket != null && advanceInBucket(bucket.entry(), false))
                    return;
            }

            while (nextSegmentIndex >= 0) {
                GridCacheConcurrentMap.Segment seg = map.segs[nextSegmentIndex--];

                if (seg.size() != 0) {
                    curTable = seg.table;

                    for (int j = curTable.length - 1; j >= 0; --j) {
                        Bucket<K, V> bucket = curTable[j];

                        if (bucket != null && advanceInBucket(bucket.entry(), false)) {
                            nextTableIndex = j - 1;

                            return;
                        }
                    }
                }
            }
        }

        /**
         * @param e Current next.
         * @param skipFirst {@code True} to skip check on first iteration.
         * @return {@code True} if advance succeeded.
         */
        @SuppressWarnings( {"unchecked"})
        private boolean advanceInBucket(@Nullable HashEntry<K, V> e, boolean skipFirst) {
            if (e == null)
                return false;

            nextEntry = e;

            do {
                if (!skipFirst) {
                    next = nextEntry.val;

                    if (isVal) {
                        nextVal = next.wrap(true).peek(CU.<K, V>empty());

                        if (nextVal == null)
                            continue;
                    }

                    if (next.visitable(filter))
                        return true;
                }

                // Perform checks in any case.
                skipFirst = false;
            }
            while ((nextEntry = nextEntry.next) != null);

            assert nextEntry == null;

            next = null;
            nextVal = null;

            return false;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return next != null && (!isVal || nextVal != null);
        }

        /**
         * @return Next value.
         */
        public V currentValue() {
            return curVal;
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public GridCacheEntryEx<K, V> next() {
            GridCacheMapEntry<K, V> e = next;
            V v = nextVal;

            if (e == null)
                throw new NoSuchElementException();

            advance();

            cur = e;
            curVal = v;

            return cur;
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            if (cur == null)
                throw new IllegalStateException();

            GridCacheMapEntry<K, V> e = cur;

            cur = null;
            curVal = null;

            try {
                ctx.cache().remove(e.key(), CU.<K, V>empty());
            }
            catch (GridException ex) {
                throw new GridRuntimeException(ex);
            }
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(ctx);
            out.writeObject(filter);
            out.writeBoolean(isVal);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            ctx = (GridCacheContext<K, V>)in.readObject();
            filter = (GridPredicate<? super GridCacheEntry<K, V>>[])in.readObject();
            isVal = in.readBoolean();
        }

        /**
         * Reconstructs object on demarshalling.
         *
         * @return Reconstructed object.
         * @throws ObjectStreamException Thrown in case of demarshalling error.
         */
        protected Object readResolve() throws ObjectStreamException {
            return new Iterator0<K, V>(ctx.cache().map(), isVal, filter);
        }
    }

    /**
     * Entry set.
     */
    @SuppressWarnings("unchecked")
    private static class Set0<K, V> extends AbstractSet<GridCacheEntryEx<K, V>> implements Externalizable {
        /** Filter. */
        private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

        /** Base map. */
        private GridCacheConcurrentMap<K, V> map;

        /** Context. */
        private GridCacheContext<K, V> ctx;

        /** */
        private GridCacheProjectionImpl prjPerCall;

        /** */
        private GridCacheFlag[] forcedFlags;

        /** */
        private boolean clone;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public Set0() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Filter.
         */
        private Set0(GridCacheConcurrentMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert map != null;

            this.map = map;
            this.filter = filter;

            ctx = map.ctx;

            prjPerCall = ctx.projectionPerCall();
            forcedFlags = ctx.forcedFlags();
            clone = ctx.hasFlag(CLONE);
        }

        /** {@inheritDoc} */
        @Override public boolean isEmpty() {
            return !iterator().hasNext();
        }

        /** {@inheritDoc} */
        @Override public Iterator<GridCacheEntryEx<K, V>> iterator() {
            return new Iterator0<K, V>(map, false, filter);
        }

        /**
         * @return Entry iterator.
         */
        Iterator<GridCacheEntry<K, V>> entryIterator() {
            return new EntryIterator<K, V>(map, filter, ctx, prjPerCall, forcedFlags);
        }

        /**
         * @return Key iterator.
         */
        Iterator<K> keyIterator() {
            return new KeyIterator<K, V>(map, filter);
        }

        /**
         * @return Value iterator.
         */
        Iterator<V> valueIterator() {
            return new ValueIterator<K, V>(map, filter, ctx, clone);
        }

        /**
         * Checks for key containment.
         *
         * @param k Key to check.
         * @return {@code True} if key is in the map.
         */
        boolean containsKey(K k) {
            GridCacheEntryEx<K, V> e = ctx.cache().peekEx(k);

            return e != null && !e.obsolete() && F.isAll(e.wrap(false), filter);
        }

        /**
         * @param v Checks if value is contained in
         * @return {@code True} if value is in the set.
         */
        boolean containsValue(V v) {
            A.notNull(v, "value");

            if (v == null)
                return false;

            for (Iterator<V> it = valueIterator(); it.hasNext(); ) {
                V v0 = it.next();

                if (F.eq(v0, v))
                    return true;
            }

            return false;
        }

        /** {@inheritDoc} */
        @Override public boolean contains(Object o) {
            if (!(o instanceof GridCacheEntryEx))
                return false;

            GridCacheEntryEx<K, V> e = (GridCacheEntryEx<K, V>)o;

            GridCacheEntryEx<K, V> cur = ctx.cache().peekEx(e.key());

            return cur != null && cur.equals(e);
        }

        /** {@inheritDoc} */
        @Override public boolean remove(Object o) {
            return o instanceof GridCacheEntry && removeKey(((Map.Entry<K, V>)o).getKey());
        }

        /**
         * @param k Key to remove.
         * @return If key has been removed.
         */
        boolean removeKey(K k) {
            try {
                return ctx.cache().remove(k, CU.<K, V>empty()) != null;
            }
            catch (GridException e) {
                throw new GridRuntimeException("Failed to remove cache entry for key: " + k, e);
            }
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return F.isEmpty(filter) ? map.publicSize() : F.size(iterator());
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            ctx.cache().clearAll(new KeySet<K, V>(map, filter));
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(ctx);
            out.writeObject(filter);
        }

        /** {@inheritDoc} */
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            ctx = (GridCacheContext<K, V>)in.readObject();
            filter = (GridPredicate<? super GridCacheEntry<K, V>>[])in.readObject();
        }

        /**
         * Reconstructs object on demarshalling.
         *
         * @return Reconstructed object.
         * @throws ObjectStreamException Thrown in case of demarshalling error.
         */
        protected Object readResolve() throws ObjectStreamException {
            return new Set0<K, V>(ctx.cache().map(), filter);
        }
    }

    /**
     * Iterator over hash table.
     * <p>
     * Note, class is static for {@link Externalizable}.
     */
    private static class EntryIterator<K, V> implements Iterator<GridCacheEntry<K, V>>, Externalizable {
        /** Base iterator. */
        private Iterator0<K, V> it;

        /** */
        private GridCacheContext<K, V> ctx;

        /** */
        private GridCacheProjectionImpl<K, V> prjPerCall;

        /** */
        private GridCacheFlag[] forcedFlags;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public EntryIterator() {
            // No-op.
        }

        /**
         * @param map Cache map.
         * @param filter Entry filter.
         * @param ctx Cache context.
         * @param prjPerCall Projection per call.
         * @param forcedFlags Forced flags.
         */
        EntryIterator(
            GridCacheConcurrentMap<K, V> map,
            GridPredicate<? super GridCacheEntry<K, V>>[] filter,
            GridCacheContext<K, V> ctx,
            GridCacheProjectionImpl<K, V> prjPerCall,
            GridCacheFlag[] forcedFlags) {
            it = new Iterator0<K, V>(map, false, filter);

            this.ctx = ctx;
            this.prjPerCall = prjPerCall;
            this.forcedFlags = forcedFlags;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return it.hasNext();
        }

        /** {@inheritDoc} */
        @Override public GridCacheEntry<K, V> next() {
            GridCacheProjectionImpl<K, V> oldPrj = ctx.projectionPerCall();

            ctx.projectionPerCall(prjPerCall);

            GridCacheFlag[] oldFlags = ctx.forceFlags(forcedFlags);

            try {
                return it.next().wrap(true);
            }
            finally {
                ctx.projectionPerCall(oldPrj);
                ctx.forceFlags(oldFlags);
            }
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            it.remove();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(it);
            out.writeObject(ctx);
            out.writeObject(prjPerCall);
            out.writeObject(forcedFlags);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            it = (Iterator0<K, V>)in.readObject();
            ctx = (GridCacheContext<K, V>)in.readObject();
            prjPerCall = (GridCacheProjectionImpl<K, V>)in.readObject();
            forcedFlags = (GridCacheFlag[])in.readObject();
        }
    }

    /**
     * Value iterator.
     * <p>
     * Note that class is static for {@link Externalizable}.
     */
    private static class ValueIterator<K, V> implements Iterator<V>, Externalizable {
        /** Hash table iterator. */
        private Iterator0<K, V> it;

        /** Context. */
        private GridCacheContext<K, V> ctx;

        /** */
        private boolean clone;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public ValueIterator() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Value filter.
         * @param ctx Cache context.
         * @param clone Clone flag.
         */
        private ValueIterator(
            GridCacheConcurrentMap<K, V> map,
            GridPredicate<? super GridCacheEntry<K, V>>[] filter,
            GridCacheContext<K, V> ctx,
            boolean clone) {
            it = new Iterator0<K, V>(map, true, filter);

            this.ctx = ctx;
            this.clone = clone;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return it.hasNext();
        }

        /** {@inheritDoc} */
        @Nullable @Override public V next() {
            it.next();

            // Cached value.
            V val = it.currentValue();

            try {
                return clone ? ctx.cloneValue(val) : val;
            }
            catch (GridException e) {
                throw new GridRuntimeException(e);
            }
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            it.remove();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(it);
            out.writeObject(ctx);
            out.writeBoolean(clone);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            it = (Iterator0)in.readObject();
            ctx = (GridCacheContext<K, V>)in.readObject();
            clone = in.readBoolean();
        }
    }

    /**
     * Key iterator.
     */
    private static class KeyIterator<K, V> implements Iterator<K>, Externalizable {
        /** Hash table iterator. */
        private Iterator0<K, V> it;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public KeyIterator() {
            // No-op.
        }

        /**
         * @param map Cache map.
         * @param filter Filter.
         */
        private KeyIterator(GridCacheConcurrentMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            it = new Iterator0<K, V>(map, false, filter);
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return it.hasNext();
        }

        /** {@inheritDoc} */
        @Override public K next() {
            return it.next().key();
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            it.remove();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(it);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            it = (Iterator0)in.readObject();
        }
    }

    /**
     * Key set.
     */
    private static class KeySet<K, V> extends AbstractSet<K> implements Externalizable {
        /** Base entry set. */
        private Set0<K, V> set;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public KeySet() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Key filter.
         */
        private KeySet(GridCacheConcurrentMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert map != null;

            set = new Set0<K, V>(map, nonInternal(filter));
        }

        /** {@inheritDoc} */
        @Override public Iterator<K> iterator() {
            return set.keyIterator();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return set.size();
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean contains(Object o) {
            return set.containsKey((K)o);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean remove(Object o) {
            return set.removeKey((K)o);
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            set.clear();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(set);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            set = (Set0<K, V>)in.readObject();
        }
    }

    /**
     * Value set.
     * <p>
     * Note that the set is static for {@link Externalizable} support.
     */
    private static class Values<K, V> extends AbstractCollection<V> implements Externalizable {
        /** Base entry set. */
        private Set0<K, V> set;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public Values() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Value filter.
         */
        private Values(GridCacheConcurrentMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert map != null;

            set = new Set0<K, V>(map, nonInternal(filter));
        }

        /** {@inheritDoc} */
        @Override public Iterator<V> iterator() {
            return set.valueIterator();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return set.size();
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean contains(Object o) {
            return set.containsValue((V)o);
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            set.clear();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(set);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            set = (Set0<K, V>)in.readObject();
        }
    }
    /**
     * Entry set.
     */
    private static class EntrySet<K, V> extends AbstractSet<GridCacheEntry<K, V>> implements Externalizable {
        /** Base entry set. */
        private Set0<K, V> set;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public EntrySet() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Key filter.
         */
        private EntrySet(GridCacheConcurrentMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert map != null;

            set = new Set0<K, V>(map, nonInternal(filter));
        }

        /** {@inheritDoc} */
        @Override public Iterator<GridCacheEntry<K, V>> iterator() {
            return set.entryIterator();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return set.size();
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean contains(Object o) {
            return o instanceof GridCacheEntryImpl && set.contains(((GridCacheEntryImpl<K, V>)o).unwrap());
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean remove(Object o) {
            return set.removeKey((K)o);
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            set.clear();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(set);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            set = (Set0<K, V>)in.readObject();
        }
    }
}

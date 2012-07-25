// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * A hash table supporting full concurrency of retrievals and
 * adjustable expected concurrency for updates. This class obeys the
 * same functional specification as {@link java.util.Hashtable}, and
 * includes versions of methods corresponding to each method of
 * <tt>Hashtable</tt>. However, even though all operations are
 * thread-safe, retrieval operations do <em>not</em> entail locking,
 * and there is <em>not</em> any support for locking the entire table
 * in a way that prevents all access.  This class is fully
 * interoperable with <tt>Hashtable</tt> in programs that rely on its
 * thread safety but not on its synchronization details.
 *
 * <p> Retrieval operations (including <tt>get</tt>) generally do not
 * block, so may overlap with update operations (including
 * <tt>put</tt> and <tt>remove</tt>). Retrievals reflect the results
 * of the most recently <em>completed</em> update operations holding
 * upon their onset.  For aggregate operations such as <tt>putAll</tt>
 * and <tt>clear</tt>, concurrent retrievals may reflect insertion or
 * removal of only some entries.  Similarly, Iterators and
 * Enumerations return elements reflecting the state of the hash table
 * at some point at or since the creation of the iterator/enumeration.
 * They do <em>not</em> throw {@link ConcurrentModificationException}.
 * However, iterators are designed to be used by only one thread at a time.
 *
 * <p> The allowed concurrency among update operations is guided by
 * the optional <tt>concurrencyLevel</tt> constructor argument
 * (default <tt>16</tt>), which is used as a hint for internal sizing.  The
 * table is internally partitioned to try to permit the indicated
 * number of concurrent updates without contention. Because placement
 * in hash tables is essentially random, the actual concurrency will
 * vary.  Ideally, you should choose a value to accommodate as many
 * threads as will ever concurrently modify the table. Using a
 * significantly higher value than you need can waste space and time,
 * and a significantly lower value can lead to thread contention. But
 * overestimates and underestimates within an order of magnitude do
 * not usually have much noticeable impact. A value of one is
 * appropriate when it is known that only one thread will modify and
 * all others will only read. Also, resizing this or any other kind of
 * hash table is a relatively slow operation, so, when possible, it is
 * a good idea to provide estimates of expected table sizes in
 * constructors.
 *
 * <p/> This implementation differs from
 * <tt>HashMap</tt> in that it maintains a doubly-linked list running through
 * all of its entries.  This linked list defines the iteration ordering,
 * which is normally the order in which keys were inserted into the map
 * (<i>insertion-order</i>).  Note that insertion order is not affected
 * if a key is <i>re-inserted</i> into the map.  (A key <tt>k</tt> is
 * reinserted into a map <tt>m</tt> if <tt>m.put(k, v)</tt> is invoked when
 * <tt>m.containsKey(k)</tt> would return <tt>true</tt> immediately prior to
 * the invocation.)
 *
 * <p/>A special constructor is provided to create a linked hash map whose order of iteration is the order
 * in which its entries were last accessed, from least-recently accessed to
 * most-recently (<i>access-order</i>).  This kind of map is well-suited to
 * building LRU caches.  Invoking the <tt>put</tt> or <tt>get</tt> method
 * results in an access to the corresponding entry (assuming it exists after
 * the invocation completes).  The <tt>putAll</tt> method generates one entry
 * access for each mapping in the specified map, in the order that key-value
 * mappings are provided by the specified map's entry set iterator.  <i>No
 * other methods generate entry accesses.</i> In particular, operations on
 * collection-views do <i>not</i> affect the order of iteration of the backing
 * map.
 *
 * <p>An optional {@link GridPredicate2} object may be passed to the map constructor to
 * impose a policy for removing stale mappings automatically when new mappings
 * are added to the map.
 *
 * <p/>When iterating over the key set in insertion order one should note that iterator
 * will see all removes done since the iterator was created, but will see <b>no</b>
 * inserts to map.
 *
 * <p>This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link Map} and {@link Iterator}
 * interfaces.
 *
 * <p> Like {@link Hashtable} but unlike {@link HashMap}, this class
 * does <em>not</em> allow <tt>null</tt> to be used as a key or value.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
// @SuppressWarnings( {"ALL"})
public class GridConcurrentLinkedHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    /*
     * The basic strategy is to subdivide the table among Segments,
     * each of which itself is a concurrently readable hash table.
     */

    /* ---------------- Constants -------------- */

    /**
     * The default initial capacity for this table,
     * used when not otherwise specified in a constructor.
     */
    public static final int DFLT_INIT_CAP = 16;

    /**
     * The default load factor for this table, used when not
     * otherwise specified in a constructor.
     */
    public static final float DFLT_LOAD_FACTOR = 0.75f;

    /**
     * The default concurrency level for this table, used when not
     * otherwise specified in a constructor.
     */
    public static final int DFLT_CONCUR_LVL = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly
     * specified by either of the constructors with arguments.  MUST
     * be a power of two <= 1<<30 to ensure that entries are indexable
     * using ints.
     */
    public static final int MAX_CAP_LIMIT = 1 << 30;

    /**
     * The maximum number of segments to allow; used to bound
     * constructor arguments.
     */
    public static final int MAX_SEGS = 1 << 16; // slightly conservative

    /**
     * Number of unsynchronized retries in {@link #size} and {@link #containsValue}
     * methods before resorting to locking. This is used to avoid
     * unbounded retries if tables undergo continuous modification
     * which would make it impossible to obtain an accurate result.
     */
    public static final int RETRIES_BEFORE_LOCK = 2;

    /* ---------------- Fields -------------- */

    /**
     * Mask value for indexing into segments. The upper bits of a
     * key's hash code are used to choose the segment.
     */
    private final int segmentMask;

    /** Shift value for indexing within segments. */
    private final int segmentShift;

    /** The segments, each of which is a specialized hash table. */
    private final Segment<K, V>[] segments;

    /** Key set. */
    private Set<K> keySet;

    /** Entry set */
    private Set<Map.Entry<K, V>> entrySet;

    /** Values collection. */
    private Collection<V> values;

    /** Queue containing order of entries. */
    private final GridConcurrentLinkedDeque<HashEntry<K, V>> entryQ =
        new GridConcurrentLinkedDeque<HashEntry<K, V>>();

    /** Atomic variable containing map size. */
    private final AtomicInteger size = new AtomicInteger();

    private final AtomicInteger modCnt = new AtomicInteger();

    /** Flag indicating if map is insert-ordered or access-ordered */
    private boolean accessOrder;

    /** Lock for access to {@link #entryQ} */
    private final ReadWriteLock queueLock = new ReentrantReadWriteLock();

    /** Predicate for eviction detection */
    private GridPredicate2<GridConcurrentLinkedHashMap<K,V>, HashEntry<K, V>> rmvPred;

    /* ---------------- Small Utilities -------------- */

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because ConcurrentHashMap uses power-of-two length hash tables,
     * that otherwise encounter collisions for hashCodes that do not
     * differ in lower or upper bits.
     *
     * @param h Input hash.
     * @return Hash.
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
     * Returns the segment that should be used for key with given hash.
     *
     * @param hash the hash code for the key
     * @return the segment
     */
    private Segment<K, V> segmentFor(int hash) {
        return segments[(hash >>> segmentShift) & segmentMask];
    }

    /* ---------------- Inner Classes -------------- */

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
    @SuppressWarnings({"PublicInnerClass"})
    public static final class HashEntry<K, V> {
        /** Key. */
        private final K key;

        /** Hash of the key after {@code hash()} method is applied. */
        private final int hash;

        /** Value. */
        private volatile V val;

        /** Reference to a node in queue for fast removal operations */
        private volatile GridConcurrentLinkedDeque.Node node;

        /** Modification count of the map for duplicates exclusion. */
        private volatile int modCnt;

        /** Link to the next entry in a bucket */
        private final HashEntry<K, V> next;

        /**
         * @param key Key.
         * @param hash Key hash.
         * @param next Link to next.
         * @param val Value.
         */
        HashEntry(K key, int hash, HashEntry<K, V> next, V val) {
            this.key = key;
            this.hash = hash;
            this.next = next;
            this.val = val;
        }

        /**
         * @param key Key.
         * @param hash Key hash.
         * @param next Link to next.
         * @param val Value.
         * @param node Queue node.
         */
        HashEntry(K key, int hash, HashEntry<K, V> next, V val, GridConcurrentLinkedDeque.Node node) {
            this.key = key;
            this.hash = hash;
            this.next = next;
            this.val = val;
            this.node = node;
        }

        /**
         * Returns key of this entry.
         *
         * @return Key.
         */
        public K getKey() {
            return key;
        }

        /**
         * Return value of this entry.
         *
         * @return Value.
         */
        public V getValue() {
            return val;
        }

        /**
         * Creates new array of entries.
         *
         * @param i Size of array.
         * @param <K> Key type.
         * @param <V> Value type.
         * @return Empty array.
         */
        @SuppressWarnings("unchecked")
        static <K, V> HashEntry<K, V>[] newArray(int i) {
            return new HashEntry[i];
        }
    }

    /**
     * Segments are specialized versions of hash tables.  This
     * subclasses from ReentrantLock opportunistically, just to
     * simplify some locking and avoid separate construction.
     */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final class Segment<K, V> extends ReentrantLock {
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
        private transient volatile int cnt;

        /**
         * Number of updates that alter the size of the table. This is
         * used during bulk-read methods to make sure they see a
         * consistent snapshot: If modCounts change during a traversal
         * of segments computing size or checking containsValue, then
         * we might have an inconsistent view of state so (usually)
         * must retry.
         */
        private transient int modCnt;

        /**
         * The table is rehashed when its size exceeds this threshold.
         * (The value of this field is always <tt>(int)(capacity *
         * loadFactor)</tt>.)
         */
        private transient int threshold;

        /**
         * The per-segment table.
         */
        private transient volatile HashEntry<K, V>[] table;

        /**
         * The load factor for the hash table.  Even though this value
         * is same for all segments, it is replicated to avoid needing
         * links to outer object.
         */
        private final float loadFactor;

        /** Atomic variable for fast size retreival. */
        private final AtomicInteger size;

        /**
         * @param initialCapacity Segment initial capacity.
         * @param loadFactor Segment load factor,
         * @param size Atmoic for size updates.
         */
        Segment(int initialCapacity, float loadFactor, AtomicInteger size) {
            this.loadFactor = loadFactor;
            this.size = size;

            setTable(HashEntry.<K, V>newArray(initialCapacity));
        }

        /**
         * Sets table to new HashEntry array.
         * Call only while holding lock or in constructor.
         *
         * @param newTable New hash table
         */
        void setTable(HashEntry<K, V>[] newTable) {
            threshold = (int)(newTable.length * loadFactor);
            table = newTable;
        }

        /**
         * Returns properly casted first entry of bin for given hash.
         *
         * @param hash Hash of the key.
         * @return Head of bin's linked list.
         */
        HashEntry<K, V> getFirst(int hash) {
            HashEntry<K, V>[] tab = table;

            return tab[hash & (tab.length - 1)];
        }

        /**
         * Reads value field of an entry under lock. Called if value
         * field ever appears to be null. This is possible only if a
         * compiler happens to reorder a HashEntry initialization with
         * its table assignment, which is legal under memory model
         * but is not known to ever occur.
         *
         * @param e Entry that needs to be read.
         * @return Value of entry.
         */
        V readValueUnderLock(HashEntry<K, V> e) {
            lock();

            try {
                return e.val;
            }
            finally {
                unlock();
            }
        }

        /* Specialized implementations of map methods */

        /**
         * Performs lock-free read of value for given key.
         *
         * @param key Key to be read.
         * @param hash Hash of the key
         * @return Stored value
         */
        V get(Object key, int hash) {
            if (cnt != 0) { // read-volatile
                HashEntry<K, V> e = getFirst(hash);

                while (e != null) {
                    if (e.hash == hash && key.equals(e.key)) {
                        V v = e.val;

                        if (v != null)
                            return v;

                        v = readValueUnderLock(e);

                        recordAccess(e);

                        return v; // recheck
                    }

                    e = e.next;
                }
            }

            return null;
        }

        /**
         * Performs lock-free check of key presence.
         *
         * @param key Key to lookup.
         * @param hash Hash of the key.
         * @return {@code true} if segment contains this key.
         */
        boolean containsKey(Object key, int hash) {
            if (cnt != 0) { // read-volatile
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
         * Performs lock-free check of value presence.
         *
         * @param val Value.
         * @return {@code true} if segment contains this key.
         */
        boolean containsValue(Object val) {
            if (cnt != 0) { // read-volatile
                HashEntry<K, V>[] tab = table;

                int len = tab.length;

                for (int i = 0 ; i < len; i++) {
                    for (HashEntry<K, V> e = tab[i]; e != null; e = e.next) {
                        V v = e.val;

                        if (v == null) // recheck
                            v = readValueUnderLock(e);

                        if (val.equals(v))
                            return true;
                    }
                }
            }

            return false;
        }

        /**
         * Performs value replacement for a given key with old value check.
         *
         * @param key Key to replace.
         * @param hash Hash of the key.
         * @param oldVal Old value.
         * @param newVal New value
         * @return {@code true} If value was replaced.
         */
        @SuppressWarnings({"unchecked"})
        boolean replace(K key, int hash, V oldVal, V newVal) {
            lock();

            boolean replaced = false;

            try {
                HashEntry<K, V> e = getFirst(hash);

                while (e != null && (e.hash != hash || !key.equals(e.key)))
                    e = e.next;

                if (e != null && oldVal.equals(e.val)) {
                    replaced = true;

                    e.val = newVal;
                }

                if (replaced)
                    recordAccess(e);
            }
            finally {
                unlock();
            }

            return replaced;
        }

        /**
         * Performs value replacement for a given key with old value check.
         *
         * @param key Key to replace.
         * @param hash Hash of the key.
         * @param oldVal Old value.
         * @param newVal New value
         * @return {@code oldVal}, if value was replaced, non-null object if map
         *         contained some other value and {@code null} if there were no such key.
         */
        @SuppressWarnings({"unchecked"})
        V replacex(K key, int hash, V oldVal, V newVal) {
            lock();

            V replaced = null;

            try {
                HashEntry<K, V> e = getFirst(hash);

                while (e != null && (e.hash != hash || !key.equals(e.key)))
                    e = e.next;

                if (e != null) {
                    if (oldVal.equals(e.val)) {
                        replaced = oldVal;

                        e.val = newVal;
                    } else
                        replaced = e.val;
                }

                if (replaced == oldVal)
                    recordAccess(e);
            }
            finally {
                unlock();
            }

            return replaced;
        }

        @SuppressWarnings({"unchecked"})
        V replace(K key, int hash, V newVal) {
            lock();

            V oldVal = null;

            try {
                HashEntry<K, V> e = getFirst(hash);

                while (e != null && (e.hash != hash || !key.equals(e.key)))
                    e = e.next;

                if (e != null) {
                    oldVal = e.val;

                    e.val = newVal;
                }

                if (oldVal != null)
                    recordAccess(e);
            }
            finally {
                unlock();
            }

            return oldVal;
        }

        @SuppressWarnings({"unchecked"})
        V put(K key, int hash, V val, boolean onlyIfAbsent) {
            lock();

            V oldVal;

            boolean added = false;

            try {
                int c = cnt;

                if (c++ > threshold) // ensure capacity
                    rehash();

                HashEntry<K, V>[] tab = table;

                int index = hash & (tab.length - 1);

                HashEntry<K, V> first = tab[index];

                HashEntry<K, V> e = first;

                while (e != null && (e.hash != hash || !key.equals(e.key)))
                    e = e.next;

                boolean modified = false;

                if (e != null) {
                    oldVal = e.val;

                    if (!onlyIfAbsent) {
                        e.val = val;

                        modified = true;
                    }
                }
                else {
                    oldVal = null;

                    ++modCnt;

                    size.incrementAndGet();

                    e = tab[index] = new HashEntry<K, V>(key, hash, first, val);

                    e.modCnt = GridConcurrentLinkedHashMap.this.modCnt.incrementAndGet();

                    cnt = c; // write-volatile

                    added = true;
                }

                assert !(added && modified);

                if (added)
                    recordInsert(e);

                if (modified)
                    recordAccess(e);

            }
            finally {
                unlock();
            }

            if (added && rmvPred != null)
                checkRemoveEldestEntry();

            return oldVal;
        }

        /**
         * This method is called under the segment lock.
         */
        void rehash() {
            HashEntry<K, V>[] oldTable = table;
            int oldCapacity = oldTable.length;

            if (oldCapacity >= MAX_CAP_LIMIT)
                return;

            /*
             * Reclassify nodes in each list to new Map.  Because we are
             * using power-of-two expansion, the elements from each bin
             * must either stay at same index, or move with a power of two
             * offset. We eliminate unnecessary node creation by catching
             * cases where old nodes can be reused because their next
             * fields won't change. Statistically, at the default
             * threshold, only about one-sixth of them need cloning when
             * a table doubles. The nodes they replace will be garbage
             * collectable as soon as they are no longer referenced by any
             * reader thread that may be in the midst of traversing table
             * right now.
             */

            HashEntry<K, V>[] newTable = HashEntry.newArray(oldCapacity<<1);

            threshold = (int)(newTable.length * loadFactor);

            int sizeMask = newTable.length - 1;

            for (int i = 0; i < oldCapacity ; i++) {
                // We need to guarantee that any existing reads of old Map can
                //  proceed. So we cannot yet null out each bin.
                HashEntry<K, V> e = oldTable[i];

                if (e != null) {
                    HashEntry<K, V> next = e.next;

                    int idx = e.hash & sizeMask;

                    //  Single node on list
                    if (next == null)
                        newTable[idx] = e;

                    else {
                        // Reuse trailing consecutive sequence at same slot
                        HashEntry<K, V> lastRun = e;

                        int lastIdx = idx;

                        for (HashEntry<K, V> last = next; last != null; last = last.next) {
                            int k = last.hash & sizeMask;

                            if (k != lastIdx) {
                                lastIdx = k;
                                lastRun = last;
                            }
                        }

                        newTable[lastIdx] = lastRun;

                        // Clone all remaining nodes
                        for (HashEntry<K, V> p = e; p != lastRun; p = p.next) {
                            int k = p.hash & sizeMask;

                            HashEntry<K, V> n = newTable[k];

                            newTable[k] = new HashEntry<K, V>(p.key, p.hash, n, p.val, p.node);
                        }
                    }
                }
            }

            table = newTable;
        }

        /**
         * Remove; match on key only if value null, else match both.
         *
         * @param key Key to be removed.
         * @param hash Hash of the key.
         * @param val Value to match.
         * @return Old value, if entry existed, {@code null} otherwise.
         */
        @SuppressWarnings({"unchecked"})
        V remove(Object key, int hash, @Nullable Object val) {
            lock();

            try {
                int c = cnt - 1;

                HashEntry<K, V>[] tab = table;

                int index = hash & (tab.length - 1);

                HashEntry<K, V> first = tab[index];

                HashEntry<K, V> e = first;

                while (e != null && (e.hash != hash || !key.equals(e.key)))
                    e = e.next;

                V oldVal = null;

                if (e != null) {
                    V v = e.val;

                    if (val == null || val.equals(v)) {
                        oldVal = v;

                        // All entries following removed node can stay
                        // in list, but all preceding ones need to be
                        // cloned.
                        ++modCnt;
                        GridConcurrentLinkedHashMap.this.modCnt.incrementAndGet();

                        HashEntry<K, V> newFirst = e.next;

                        for (HashEntry<K, V> p = first; p != e; p = p.next)
                            newFirst = new HashEntry<K, V>(p.key, p.hash, newFirst, p.val, p.node);

                        tab[index] = newFirst;

                        cnt = c; // write-volatile

                        size.decrementAndGet();
                    }
                }

                if (oldVal != null)
                    recordRemove(e);

                return oldVal;
            }
            finally {
                unlock();
            }
        }

        void clear() {
            if (cnt != 0) {
                lock();

                try {
                    HashEntry<K, V>[] tab = table;

                    for (int i = 0; i < tab.length ; i++)
                        tab[i] = null;

                    ++modCnt;

                    cnt = 0; // write-volatile
                }
                finally {
                    unlock();
                }
            }
        }
    }

    /* ---------------- Public operations -------------- */

    /**
     * Creates a new, empty map with the specified initial
     * capacity, load factor, concurrency level and access order.
     *
     * @param initialCapacity the initial capacity. The implementation
     *      performs internal sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     *      Resizing may be performed when the average number of elements per
     *      bin exceeds this threshold.
     * @param concurLvl the estimated number of concurrently
     *      updating threads. The implementation performs internal sizing
     *      to try to accommodate this many threads.
     * @param accessOrder indicates if entries should be ordered by access order,
     *      not by insertion order. {@code true} means access order.
     * @param rmvPred An optional predicate providing policy for removing stale
     *      entries from the map. This predicate is applied every time a new entry
     *      is inserted into the map by {@link #put(Object, Object)}. Predicate should
     *      return true if eldest entry should be removed from the map.
     * @throws IllegalArgumentException if the initial capacity is
     *      negative or the load factor or concurLvl are
     *      nonpositive.
     */
    @SuppressWarnings({"unchecked"})
    public GridConcurrentLinkedHashMap(int initialCapacity, float loadFactor, int concurLvl, boolean accessOrder,
        @Nullable GridPredicate2<GridConcurrentLinkedHashMap<K,V>, HashEntry<K,V>> rmvPred) {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurLvl <= 0)
            throw new IllegalArgumentException();

        if (concurLvl > MAX_SEGS)
            concurLvl = MAX_SEGS;

        this.accessOrder = accessOrder;
        this.rmvPred = rmvPred;

        // Find power-of-two sizes best matching arguments
        int sshift = 0;

        int ssize = 1;

        while (ssize < concurLvl) {
            ++sshift;
            ssize <<= 1;
        }

        segmentShift = 32 - sshift;

        segmentMask = ssize - 1;

        segments = new Segment[ssize];

        if (initialCapacity > MAX_CAP_LIMIT)
            initialCapacity = MAX_CAP_LIMIT;

        int c = initialCapacity / ssize;

        if (c * ssize < initialCapacity)
            ++c;

        int cap = 1;

        while (cap < c)
            cap <<= 1;

        for (int i = 0; i < segments.length; ++i) {
            segments[i] = new Segment<K, V>(cap, loadFactor, size);
        }
    }

    /**
     * Creates a new, empty map with the specified initial
     * capacity, load factor and concurrency level.
     *
     * @param initialCapacity the initial capacity. The implementation
     *      performs internal sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     *      Resizing may be performed when the average number of elements per
     *      bin exceeds this threshold.
     * @param concurLvl the estimated number of concurrently
     *      updating threads. The implementation performs internal sizing
     *      to try to accommodate this many threads.
     * @throws IllegalArgumentException if the initial capacity is
     *      negative or the load factor or concurLvl are
     *      nonpositive.
     */
    @SuppressWarnings({"unchecked"})
    public GridConcurrentLinkedHashMap(int initialCapacity, float loadFactor, int concurLvl) {
        this(initialCapacity, loadFactor, concurLvl, false, null);
    }

    /**
     * Creates a new, empty map with the specified initial capacity
     * and load factor and with the default concurrencyLevel (16).
     *
     * @param initialCapacity The implementation performs internal
     * sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     * Resizing may be performed when the average number of elements per
     * bin exceeds this threshold.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative or the load factor is nonpositive
     *
     * @since 1.6
     */
    public GridConcurrentLinkedHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, DFLT_CONCUR_LVL);
    }

    /**
     * Creates a new, empty map with the specified initial capacity,
     * and with default load factor (0.75) and concurrencyLevel (16).
     *
     * @param initialCapacity the initial capacity. The implementation
     * performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative.
     */
    public GridConcurrentLinkedHashMap(int initialCapacity) {
        this(initialCapacity, DFLT_LOAD_FACTOR, DFLT_CONCUR_LVL);
    }

    /**
     * Creates a new, empty map with a default initial capacity (16),
     * load factor (0.75) and concurrencyLevel (16).
     */
    public GridConcurrentLinkedHashMap() {
        this(DFLT_INIT_CAP, DFLT_LOAD_FACTOR, DFLT_CONCUR_LVL);
    }

     /**
     * Creates a new, empty map with specified order and default initial capacity (16),
     * load factor (0.75) and concurrencyLevel (16).
      *
      * @param accessOrder {@code true} if map should account access order.
      */
    public GridConcurrentLinkedHashMap(boolean accessOrder) {
        this(DFLT_INIT_CAP, DFLT_LOAD_FACTOR, DFLT_CONCUR_LVL, accessOrder, null);
    }

    /**
     * Creates a new map with the same mappings as the given map.
     * The map is created with a capacity of 1.5 times the number
     * of mappings in the given map or 16 (whichever is greater),
     * and a default load factor (0.75) and concurrencyLevel (16).
     *
     * @param m the map
     */
    public GridConcurrentLinkedHashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DFLT_LOAD_FACTOR) + 1, DFLT_INIT_CAP),
            DFLT_LOAD_FACTOR, DFLT_CONCUR_LVL);

        putAll(m);
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    @Override public boolean isEmpty() {
        Segment<K, V>[] segments = this.segments;
        /*
         * We keep track of per-segment modCounts to avoid ABA
         * problems in which an element in one segment was added and
         * in another removed during traversal, in which case the
         * table was never actually empty at any point. Note the
         * similar use of modCounts in the size() and containsValue()
         * methods, which are the only other methods also susceptible
         * to ABA problems.
         */
        int[] mc = new int[segments.length];
        int mcsum = 0;

        for (int i = 0; i < segments.length; ++i) {
            if (segments[i].cnt != 0)
                return false;
            else
                mcsum += mc[i] = segments[i].modCnt;
        }

        // If mcsum happens to be zero, then we know we got a snapshot
        // before any modifications at all were made.  This is
        // probably common enough to bother tracking.
        if (mcsum != 0) {
            for (int i = 0; i < segments.length; ++i) {
                if (segments[i].cnt != 0 ||
                    mc[i] != segments[i].modCnt)
                    return false;
            }
        }

        return true;
    }

    /**
     * Returns the number of key-value mappings in this map.  If the
     * map contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
     *
     * @return the number of key-value mappings in this map
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    @Override public int size() {
        Segment<K, V>[] segments = this.segments;
        long sum = 0;
        long check = 0;
        int[] mc = new int[segments.length];

        // Try a few times to get accurate count. On failure due to
        // continuous async changes in table, resort to locking.
        for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
            check = 0;
            sum = 0;
            int mcsum = 0;

            for (int i = 0; i < segments.length; ++i) {
                sum += segments[i].cnt;
                mcsum += mc[i] = segments[i].modCnt;
            }

            if (mcsum != 0) {
                for (int i = 0; i < segments.length; ++i) {
                    check += segments[i].cnt;

                    if (mc[i] != segments[i].modCnt) {
                        check = -1; // force retry

                        break;
                    }
                }
            }

            if (check == sum)
                break;
        }

        if (check != sum) { // Resort to locking all segments
            sum = 0;

            for (Segment<K, V> segment : segments)
                segment.lock();

            for (Segment<K, V> segment : segments)
                sum += segment.cnt;

            for (Segment<K, V> segment : segments)
                segment.unlock();
        }

        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)sum;
    }

    /**
     * @return The number of key-value mappings in this map (constant-time).
     */
    public int sizex() {
        return size.get();
    }

    /**
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    public boolean isEmptyx() {
        return sizex() == 0;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code key.equals(k)},
     * then this method returns {@code v}; otherwise it returns
     * {@code null}.  (There can be at most one such mapping.)
     *
     * @throws NullPointerException if the specified key is null
     */
    @Override public V get(Object key) {
        int hash = hash(key.hashCode());

        return segmentFor(hash).get(key, hash);
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param  key   possible key
     * @return <tt>true</tt> if and only if the specified object
     *         is a key in this table, as determined by the
     *         <tt>equals</tt> method; <tt>false</tt> otherwise.
     * @throws NullPointerException if the specified key is null
     */
    @Override public boolean containsKey(Object key) {
        int hash = hash(key.hashCode());

        return segmentFor(hash).containsKey(key, hash);
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value. Note: This method requires a full internal
     * traversal of the hash table, and so is much slower than
     * method <tt>containsKey</tt>.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     * @throws NullPointerException if the specified value is null
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    @Override public boolean containsValue(Object value) {
        if (value == null)
            throw new NullPointerException();

        // See explanation of modCount use above

        Segment<K, V>[] segments = this.segments;
        int[] mc = new int[segments.length];

        // Try a few times without locking
        for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
            int mcsum = 0;

            for (int i = 0; i < segments.length; ++i) {
                mcsum += mc[i] = segments[i].modCnt;

                if (segments[i].containsValue(value))
                    return true;
            }

            boolean cleanSweep = true;

            if (mcsum != 0) {
                for (int i = 0; i < segments.length; ++i) {
                    if (mc[i] != segments[i].modCnt) {
                        cleanSweep = false;

                        break;
                    }
                }
            }

            if (cleanSweep)
                return false;
        }

        // Resort to locking all segments
        for (Segment<K, V> segment : segments)
            segment.lock();

        boolean found = false;

        try {
            for (Segment<K, V> segment : segments) {
                if (segment.containsValue(value)) {
                    found = true;

                    break;
                }
            }
        } finally {
            for (Segment<K, V> segment : segments)
                segment.unlock();
        }

        return found;
    }

    /**
     * Legacy method testing if some key maps into the specified value
     * in this table.  This method is identical in functionality to
     * {@link #containsValue}, and exists solely to ensure
     * full compatibility with class {@link java.util.Hashtable},
     * which supported this method prior to introduction of the
     * Java Collections framework.

     * @param  value a value to search for
     * @return <tt>true</tt> if and only if some key maps to the
     *         <tt>value</tt> argument in this table as
     *         determined by the <tt>equals</tt> method;
     *         <tt>false</tt> otherwise
     * @throws NullPointerException if the specified value is null
     */
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * <p> The value can be retrieved by calling the <tt>get</tt> method
     * with a key that is equal to the original key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>
     * @throws NullPointerException if the specified key or value is null
     */
    @Override public V put(K key, V value) {
        if (value == null)
            throw new NullPointerException();

        int hash = hash(key.hashCode());

        return segmentFor(hash).put(key, hash, value, false);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or <tt>null</tt> if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @Override public V putIfAbsent(K key, V value) {
        if (value == null)
            throw new NullPointerException();

        int hash = hash(key.hashCode());

        return segmentFor(hash).put(key, hash, value, true);
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     */
    @Override public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * Removes the key (and its corresponding value) from this map.
     * This method does nothing if the key is not in the map.
     *
     * @param  key the key that needs to be removed
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>
     * @throws NullPointerException if the specified key is null
     */
    @Override public V remove(Object key) {
        int hash = hash(key.hashCode());

        return segmentFor(hash).remove(key, hash, null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    @Override public boolean remove(Object key, Object value) {
        int hash = hash(key.hashCode());

        return value != null && segmentFor(hash).remove(key, hash, value) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if any of the arguments are null
     */
    @Override public boolean replace(K key, V oldVal, V newVal) {
        if (oldVal == null || newVal == null)
            throw new NullPointerException();

        int hash = hash(key.hashCode());

        return segmentFor(hash).replace(key, hash, oldVal, newVal);
    }

     /**
     * Replaces the entry for a key only if currently mapped to a given value.
     * This is equivalent to
     * <pre>
     *   if (map.containsKey(key)) {
     *       if (map.get(key).equals(oldValue)) {
     *           map.put(key, newValue);
     *           return oldValue;
      *      } else
      *          return map.get(key);
     *   } else return null;</pre>
     * except that the action is performed atomically.
     *
     * @param key key with which the specified value is associated
     * @param oldVal value expected to be associated with the specified key
     * @param newVal value to be associated with the specified key
     * @return {@code oldVal}, if value was replaced, non-null previous value if map
      *        contained some other value and {@code null} if there were no such key.
     */
    public V replacex(K key, V oldVal, V newVal) {
        if (oldVal == null || newVal == null)
            throw new NullPointerException();

        int hash = hash(key.hashCode());

        return segmentFor(hash).replacex(key, hash, oldVal, newVal);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or <tt>null</tt> if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @Override public V replace(K key, V val) {
        if (val == null)
            throw new NullPointerException();

        int hash = hash(key.hashCode());

        return segmentFor(hash).replace(key, hash, val);
    }

    /**
     * Removes all of the mappings from this map.
     */
    @Override public void clear() {
        for (Segment<K, V> segment : segments)
            segment.clear();
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from this map,
     * via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    @Override public Set<K> keySet() {
        Set<K> ks = keySet;

        return (ks != null) ? ks : (keySet = new KeySet());
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  The collection
     * supports element removal, which removes the corresponding
     * mapping from this map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt>, and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    @Override public Collection<V> values() {
        Collection<V> vs = values;

        return (vs != null) ? vs : (values = new Values());
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from the map,
     * via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    @Override public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> es = entrySet;

        return (es != null) ? es : (entrySet = new EntrySet());
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return an enumeration of the keys in this table
     * @see #keySet()
     */
    public Enumeration<K> keys() {
        return new KeyIterator();
    }

    /**
     * Returns an enumeration of the values in this table.
     *
     * @return an enumeration of the values in this table
     * @see #values()
     */
    public Enumeration<V> elements() {
        return new ValueIterator();
    }

    /**
     * This method is called by hash map whenever a new entry is inserted into map.
     * <p/>
     * This method is called outside the segment-protection lock and may be called concurrently.
     *
     * @param e The new inserted entry.
     */
    @SuppressWarnings({"unchecked"})
    private void recordInsert(HashEntry e) {
        queueReadLock();

        try {
            e.node = entryQ.addx(e);
        }
        finally {
            queueReadUnlock();
        }
    }

    /**
     * This method is called by hash map whenever an already existing entry is accessed (read or written).
     * <p/>
     * This method is called both outside and inside the segment-protection lock. Concurrent access is possible
     * when value is read.
     *
     * @param e The new inserted entry.
     */
    @SuppressWarnings({"unchecked"})
    private void recordAccess(HashEntry e) {
        if (!accessOrder)
            return;

        queueReadLock();

        try {
            // e.node may be null when concurrent get call occurs with put or remove calls.
            // recordAccess is called before corresponding recordInsert or after corresponding recordDelete call.
            if (e.node != null) {
                entryQ.unlinkx(e.node);

                e.node = entryQ.addx(e);
            }
        }
        finally {
            queueReadUnlock();
        }
    }

    /**
     * This method is called by hash map whenever an already existing entry is removed.
     * <p/>
     * This method is called outside the segment-protection lock and may be called concurrently.
     *
     * @param e The new inserted entry.
     */
    @SuppressWarnings({"unchecked"})
    private void recordRemove(HashEntry e) {
        queueReadLock();

        try {
            entryQ.unlinkx(e.node);

            e.node = null;
        }
        finally {
            queueReadUnlock();
        }
    }

    /**
     * Tries to concurrently remove eldest entry from the map. If concurrent remove failed,
     * locks all segments and then performs remove.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    private void checkRemoveEldestEntry() {
        for (int i = 0; i < RETRIES_BEFORE_LOCK; i++) {
            HashEntry<K, V> e = entryQ.peekFirst();

            if (e == null)
                return;

            Segment<K, V> s = segmentFor(e.hash);

            s.lock();

            try {
                queueReadLock();

                try {
                    HashEntry<K, V> e1 = entryQ.peekFirst();

                    if (e != e1)
                        continue;

                    checkRemoveEldestEntry(s, e);

                    return;
                }
                finally {
                    queueReadUnlock();
                }
            }
            finally {
                s.unlock();
            }
        }

        // Concurrent removal failed, so lock all segments.
        for (Segment<K, V> s : segments) {
            s.lock();
        }

        try {
            queueReadLock();

            try {
                HashEntry<K, V> e = entryQ.peekFirst();

                if (e == null) {
                    return;
                }

                checkRemoveEldestEntry(segmentFor(e.hash), e);
            }
            finally {
                queueReadUnlock();
            }
        }
        finally {
            for (Segment<K, V> s : segments) {
                s.unlock();
            }
        }
    }

    /**
     * Removes entry from the map if needed.
     * <p/>
     * This method is called when thread holds locks on both segment and queue lock.
     * So far, parameter given is eldest entry in queue.
     *
     * @param s Segment in which this entry located.
     * @param e Eldest entry according to map order.
     */
    private void checkRemoveEldestEntry(Segment<K, V> s, HashEntry<K, V> e) {
        if (rmvPred.apply(this, e)) {
            s.remove(e.key, e.hash, null);
        }
    }

    /**
     * Acquires a lock on queue access read lock. Locking is moved to a separate method due to vision
     * that lock is not needed in insert-order mode.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    private void queueReadLock() {
        if (accessOrder)
            queueLock.readLock().lock();
    }

    /**
     * Releases queue access read lock. Locking is moved to a separate method due to vision
     * that lock is not needed in insert-order mode.
     */
    private void queueReadUnlock() {
        if (accessOrder)
            queueLock.readLock().unlock();
    }

    /**
     * Acquires a lock on queue access write lock. Locking is moved to a separate method due to vision
     * that lock is not needed in insert-order mode.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    private void queueWriteLock() {
        if (accessOrder)
            queueLock.writeLock().lock();
    }

    /**
     * Releases queue access write lock. Locking is moved to a separate method due to vision
     * that lock is not needed in insert-order mode.
     */
    private void queueWriteUnlock() {
        if (accessOrder)
            queueLock.writeLock().unlock();
    }

    /**
     * This method is intended for test purposes only.
     *
     * @return Queue.
     */
    GridConcurrentLinkedDeque<HashEntry<K, V>> queue() {
        return entryQ;
    }

    /**
     * Class implementing iteration over map entries.
     */
    private abstract class HashIterator {
        /** Underlying collection iterator. */
        private Iterator<HashEntry<K, V>> delegate;

        /** Last returned entry, used in {@link #remove()} method. */
        private HashEntry<K, V> lastReturned;

        /** Next entry to return */
        private HashEntry<K, V> nextEntry;

        /** The map modification count at the creation time. */
        private int modCnt;

        /** */
        HashIterator() {
            modCnt = GridConcurrentLinkedHashMap.this.modCnt.get();

            queueWriteLock();

            try {
                // In case of access order copy the whole entries list.
                delegate = accessOrder ? new ArrayList<HashEntry<K, V>>(entryQ).iterator() : entryQ.iterator();
            }
            finally {
                queueWriteUnlock();
            }

            advance();
        }

        /**
         * @return {@code true} If iterator has elements to iterate.
         */
        public boolean hasMoreElements() {
            return hasNext();
        }

        /**
         * @return {@code true} If iterator has elements to iterate.
         */
        public boolean hasNext() {
            return nextEntry != null;
        }

        /**
         * @return Next entry.
         */
        HashEntry<K, V> nextEntry() {
            if (nextEntry == null)
                throw new NoSuchElementException();

            lastReturned = nextEntry;

            advance();

            return lastReturned;
        }

        /**
         * Removes entry returned by {@link #nextEntry()}.
         */
        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();

            GridConcurrentLinkedHashMap.this.remove(lastReturned.key);

            lastReturned = null;
        }

        /**
         * Moves iterator to the next position.
         */
        private void advance() {
            nextEntry = null;

            while (delegate.hasNext()) {
                HashEntry<K, V> n = delegate.next();

                if (n.modCnt <= modCnt) {
                    nextEntry = n;

                    break;
                }
            }
        }
    }

    /**
     * Key iterator implementation.
     */
    private final class KeyIterator extends HashIterator implements Iterator<K>, Enumeration<K> {
        /** {@inheritDoc} */
        @Override public K next() {
            return nextEntry().key;
        }

        /** {@inheritDoc} */
        @Override public K nextElement() {
            return nextEntry().key;
        }
    }

    /**
     * Value iterator implementation.
     */
    private final class ValueIterator extends HashIterator implements Iterator<V>, Enumeration<V> {
        /** {@inheritDoc} */
        @Override public V next() {
            return nextEntry().val;
        }

        /** {@inheritDoc} */
        @Override public V nextElement() {
            return nextEntry().val;
        }
    }

    /**
     * Custom Entry class used by EntryIterator.next(), that relays
     * setValue changes to the underlying map.
     */
    private final class WriteThroughEntry extends AbstractMap.SimpleEntry<K, V> {
        /**
         * @param k Key
         * @param v Value
         */
        WriteThroughEntry(K k, V v) {
            super(k,v);
        }

        /**
         * Set our entry's value and write through to the map. The
         * value to return is somewhat arbitrary here. Since a
         * WriteThroughEntry does not necessarily track asynchronous
         * changes, the most recent "previous" value could be
         * different from what we return (or could even have been
         * removed in which case the put will re-establish). We do not
         * and cannot guarantee more.
         */
        @Override public V setValue(V value) {
            if (value == null)
                throw new NullPointerException();

            V v = super.setValue(value);

            put(getKey(), value);

            return v;
        }
    }

    /**
     * Entry iterator implementation.
     */
    private final class EntryIterator extends HashIterator implements Iterator<Entry<K, V>> {
        /** {@inheritDoc} */
        @Override public Map.Entry<K, V> next() {
            HashEntry<K, V> e = nextEntry();

            return new WriteThroughEntry(e.key, e.val);
        }
    }

    /**
     * Key set of the map.
     */
    private final class KeySet extends AbstractSet<K> {
        /** {@inheritDoc} */
        @Override public Iterator<K> iterator() {
            return new KeyIterator();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return GridConcurrentLinkedHashMap.this.size();
        }

        /** {@inheritDoc} */
        @Override public boolean contains(Object o) {
            return containsKey(o);
        }

        /** {@inheritDoc} */
        @Override public boolean remove(Object o) {
            return GridConcurrentLinkedHashMap.this.remove(o) != null;
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            GridConcurrentLinkedHashMap.this.clear();
        }
    }

    /**
     * Values collection of the map.
     */
    private final class Values extends AbstractCollection<V> {
        /** {@inheritDoc} */
        @Override public Iterator<V> iterator() {
            return new ValueIterator();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return GridConcurrentLinkedHashMap.this.size();
        }

        /** {@inheritDoc} */
        @Override public boolean contains(Object o) {
            return containsValue(o);
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            GridConcurrentLinkedHashMap.this.clear();
        }
    }

    /**
     * Entry set implementation.
     */
    private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        /** {@inheritDoc} */
        @Override public Iterator<Map.Entry<K, V>> iterator() {
            return new EntryIterator();
        }

        /** {@inheritDoc} */
        @Override public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;

            Map.Entry<?,?> e = (Map.Entry<?,?>)o;

            V v = get(e.getKey());

            return v != null && v.equals(e.getValue());
        }

        /** {@inheritDoc} */
        @Override public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;

            Map.Entry<?,?> e = (Map.Entry<?,?>)o;

            return GridConcurrentLinkedHashMap.this.remove(e.getKey(), e.getValue());
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return GridConcurrentLinkedHashMap.this.size();
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            GridConcurrentLinkedHashMap.this.clear();
        }
    }
}

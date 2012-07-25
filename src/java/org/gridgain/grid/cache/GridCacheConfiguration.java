// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.cloner.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.cache.eviction.lirs.*;
import org.gridgain.grid.cache.jta.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.lang.*;

import java.util.*;

/**
 * This interface defines grid cache configuration. This configuration is passed to
 * grid via {@link GridConfiguration#getCacheConfiguration()} method. It defines all configuration
 * parameters required to start a cache within grid instance. You can have multiple caches
 * configured with different names within one grid.
 * <p>
 * Note, that absolutely every configuration property in {@code GridCacheConfiguration} is optional.
 * One can simply create new instance of {@link GridCacheConfigurationAdapter}, for example,
 * and pass it to {@link GridConfiguration#getCacheConfiguration()} to start grid cache with
 * default configuration.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridCacheConfiguration {
    /** Default query log name. */
    public static final String DFLT_QUERY_LOGGER_NAME = "org.gridgain.cache.queries";

    /** DGC tracing logger name. */
    public static final String DGC_TRACE_LOGGER_NAME =
        "org.gridgain.grid.kernal.processors.cache.GridCacheDgcManager.trace";

    /** Default atomic sequence reservation size. */
    public static final int DFLT_ATOMIC_SEQUENCE_RESERVE_SIZE = 1000;

    /** Default size of preload thread pool. */
    public static final int DFLT_PRELOAD_THREAD_POOL_SIZE = 2;

    /**
     * Default time to live. The value is <tt>0</tt> which means that
     * cached objects never expire based on time.
     */
    public static final long DFLT_TIME_TO_LIVE = 0;

    /** Default caching mode. */
    public static final GridCacheMode DFLT_CACHE_MODE = GridCacheMode.REPLICATED;

    /** Default transaction timeout. */
    public static final long DFLT_TRANSACTION_TIMEOUT = 0;

    /** Default query timeout. */
    public static final long DFLT_QUERY_TIMEOUT = 0;

    /** Default lock timeout. */
    public static final long DFLT_LOCK_TIMEOUT = 0;

    /** Default concurrency mode. */
    public static final GridCacheTxConcurrency DFLT_TX_CONCURRENCY = GridCacheTxConcurrency.OPTIMISTIC;

    /** Default transaction isolation level. */
    public static final GridCacheTxIsolation DFLT_TX_ISOLATION = GridCacheTxIsolation.REPEATABLE_READ;

    /** Initial default cache size. */
    public static final int DFLT_START_SIZE = 1024;

    /** Default cache size to use with default eviction policy. */
    public static final int DFLT_CACHE_SIZE = 100000;

    /** Initial default near cache size. */
    public static final int DFLT_NEAR_START_SIZE = DFLT_START_SIZE / 4;

    /** Default near cache size to use with default near eviction policy. */
    public static final int DFLT_NEAR_SIZE = 10000;

    /** Default value for 'nearEnabled' flag. */
    public static final boolean DFLT_NEAR_ENABLED = true;

    /** Default value for 'nearEvictionEnabled' flag. */
    public static final boolean DFLT_NEAR_EVICTION_ENABLED = true;

    /** Default value for 'evictionEnabled' flag. */
    public static final boolean DFLT_EVICTION_ENABLED = true;

    /** Default value for 'txSerializableEnabled' flag. */
    public static final boolean DFLT_TX_SERIALIZABLE_ENABLED = false;

    /** Default value for 'txBatchUpdate' flag. */
    public static final boolean DFLT_TX_BATCH_UPDATE = true;

    /** Default value for 'invalidate' flag that indicates if this is invalidation-based cache. */
    public static final boolean DFLT_INVALIDATE = false;

    /** Default value for 'storeValueBytes' flag indicating if value bytes should be stored. */
    public static final boolean DFLT_STORE_VALUE_BYTES = true;

    /** Default preload mode for distributed cache. */
    public static final GridCachePreloadMode DFLT_PRELOAD_MODE = GridCachePreloadMode.ASYNC;

    /** Default preload batch size in bytes. */
    public static final int DFLT_PRELOAD_BATCH_SIZE = 512 * 1024; // 512K

    /** Default value for 'idxFixedTyping' flag. */
    public static final boolean DFLT_IDX_FIXED_TYPING = true;

    /**
     * Default value for 'idxCleanup' flag indicating if query index files
     * should be removed on node stop.
     */
    public static final boolean DFLT_IDX_CLEANUP = true;

    /**
     * Default value for 'idxMemoryOnly' flag indicating if query index
     * database should be in-memory.
     */
    public static final boolean DFLT_IDX_MEM_ONLY = true;

    /**
     * Default value for maximum memory used per single operation with query index
     * (store and remove), in bytes.
     */
    public static final int DFLT_IDX_MAX_OPERATIONAL_MEM = 0;

    /** Default frequency of running H2 "ANALYZE" command. */
    public static final long DFLT_IDX_ANALYZE_FREQ = 10 * 60 * 1000;

    /** Default number samples used to run H2 "ANALYZE" command. */
    public static final int DFLT_IDX_ANALYZE_SAMPLE_SIZE = 10000;

    /** Default distributed garbage collection frequency. */
    public static final long DFLT_DGC_FREQUENCY = 10000;

    /** Default timeout for lock not to be considered as suspicious. */
    public static final long DFLT_DGC_SUSPECT_LOCK_TIMEOUT = 10000;

    /** Default value for whether DGC should remove long running locks, or only report them. */
    public static final boolean DFLT_DGC_REMOVE_LOCKS = true;

    /** Default index parent folder name. */
    public static final String DFLT_IDX_PARENT_FOLDER_NAME = "work/cache/indexes";

    /** Default maximum eviction queue ratio. */
    public static final float DFLT_MAX_EVICTION_OVERFLOW_RATIO = 10;

    /** Default eviction synchronized flag. */
    public static final boolean DFLT_EVICT_SYNCHRONIZED = true;

    /** Default near nodes eviction synchronized flag. */
    public static final boolean DFLT_EVICT_NEAR_SYNCHRONIZED = true;

    /** Default eviction key buffer size for batching synchronized evicts. */
    public static final int DFLT_EVICT_KEY_BUFFER_SIZE = 1024;

    /** Default synchronous eviction timeout in milliseconds. */
    public static final long DFLT_EVICT_SYNCHRONOUS_TIMEOUT = 10000;

    /** Default synchronous eviction concurrency level. */
    public static final int DFLT_EVICT_SYNCHRONOUS_CONCURRENCY_LEVEL = 4;

    /** Default value for 'synchronousCommit' flag. */
    public static final boolean DFLT_SYNC_COMMIT = false;

    /** Default value for 'synchronousRollback' flag. */
    public static final boolean DFLT_SYNC_ROLLBACK = false;

    /** Default value for 'swapEnabled' flag. */
    public static final boolean DFLT_SWAP_ENABLED = false;

    /** Default value for 'storeEnabled' flag. */
    public static final boolean DFLT_STORE_ENABLED = true;

    /** Default value for 'writeFromBehindEnabled' flag. */
    public static final boolean DFLT_WRITE_FROM_BEHIND_ENABLED = false;

    /** Default flush size for write-from-behind cache store. */
    public static final int DFLT_WRITE_FROM_BEHIND_FLUSH_SIZE = 10240; // 10K

    /** Default critical size used when flush size is not specified. */
    public static final int DFLT_WRITE_FROM_BEHIND_CRITICAL_SIZE = 16384; // 16K

    /** Default flush frequency for write-from-behind cache store in milliseconds. */
    public static final long DFLT_WRITE_FROM_BEHIND_FLUSH_FREQUENCY = 5000;

    /** Default count of flush threads for write-from-behind cache store. */
    public static final int DFLT_WRITE_FROM_BEHIND_FLUSH_THREAD_CNT = 1;

    /** Default batch size for write-from-behind cache store. */
    public static final int DFLT_WRITE_FROM_BEHIND_BATCH_SIZE = 512;

    /**
     * Cache name. If not provided or {@code null}, then this will be considered a default
     * cache which can be accessed via {@link Grid#cache()} method. Otherwise, if name
     * is provided, the cache will be accessed via {@link Grid#cache(String)} method.
     *
     * @return Cache name.
     */
    public String getName();

    /**
     * Gets caching mode to use. You can configure cache either to be local-only,
     * fully replicated, partitioned, or near. If not provided, {@link GridCacheMode#REPLICATED}
     * mode will be used by default (defined by #DFLT_CACHE_MODE} constant).
     *
     * @return {@code True} if cache is local.
     */
    public GridCacheMode getCacheMode();

    /**
     * Gets time to live for all objects in cache. This value can be overridden for individual objects.
     * If not set, then value is {@code 0} which means that objects never expire.
     *
     * @return Time to live for all objects in cache.
     */
    public long getDefaultTimeToLive();

    /**
     * Gets cache eviction policy. By default, {@link GridCacheLirsEvictionPolicy}
     * will be used with default settings.
     *
     * @return Cache eviction policy.
     */
    public <K, V> GridCacheEvictionPolicy<K, V> getEvictionPolicy();

    /**
     * Gets eviction policy for {@code near} cache which is different from the one used for
     * {@code partitioned} cache. By default, {@link GridCacheLirsEvictionPolicy}
     * will be used with maximum size set to {@link #DFLT_NEAR_SIZE} value.
     *
     * @return Cache eviction policy.
     */
    public <K, V> GridCacheEvictionPolicy<K, V> getNearEvictionPolicy();

    /**
     * Gets eviction filter to specify which entries should not be evicted
     * (except explicit evict by calling {@link GridCacheEntry#evict(GridPredicate[])}).
     * If {@link GridCacheEvictionFilter#evictAllowed(GridCacheEntry)} method returns
     * {@code false} then eviction policy will not be notified and entry will
     * never be evicted.
     * <p>
     * If not provided, any entry may be evicted depending on
     * {@link #getEvictionPolicy() eviction policy} configuration.
     *
     * @return Eviction filter or {@code null}.
     */
    public <K, V> GridCacheEvictionFilter<K, V> getEvictionFilter();

    /**
     * Gets flag indicating whether eviction is synchronized between primary and
     * backup nodes. In case of replicated cache all nodes are synchronized. If
     * this parameter is {@code true} and swap is disabled then
     * {@link GridCacheProjection#evict(Object, GridPredicate[])} and all its
     * variations will involve all nodes where an entry is kept. For replicated
     * cache this is a group of nodes responsible for partition to which
     * corresponding key belongs. If this property is set to {@code false} then
     * eviction is done independently on cache nodes. Default value is {@code false}.
     * <p>
     * Note that it's not recommended to set this value to {@code true} if cache
     * store is configured since it will allow to significantly improve cache
     * performance.
     *
     * @return {@code true} If eviction is synchronized with backup nodes (or the
     *      rest of the nodes in case of replicated cache), {@code false} if not.
     */
    public boolean isEvictSynchronized();

    /**
     * Gets flag indicating whether eviction on primary node is synchronized with
     * near nodes where entry is kept. Default value is {@code true}.
     * <p>
     * Note that in most cases this property should be set to {@code true} to keep
     * cache consistency. But there may be the cases when user may use some
     * special near eviction policy to have desired control over near cache
     * entry set.
     *
     * @return {@code true} If eviction is synchronized with near nodes in
     *      partitioned cache, {@code false} if not.
     */
    public boolean isEvictNearSynchronized();

    /**
     * Gets size of the key buffer for synchronous evictions.
     * <p>
     * Default value is defined by {@link #DFLT_EVICT_KEY_BUFFER_SIZE}.
     *
     * @return Eviction key buffer size.
     */
    public int getEvictSynchronisedKeyBufferSize();

    /**
     * Gets synchronous eviction timeout.
     * <p>
     * Node that initiates eviction waits for responses
     * from remote nodes within this timeout.
     * <p>
     * Default value is defined by {@link #DFLT_EVICT_SYNCHRONOUS_TIMEOUT}.
     *
     * @return Synchronous eviction timeout.
     */
    public long getEvictSynchronizedTimeout();

    /**
     * Gets synchronous eviction concurrency level. This flag only makes sense
     * with {@link #isEvictNearSynchronized()} or {@link #isEvictSynchronized()} set
     * to {@code true}. When synchronous evictions are enabled, it is possible that
     * eviction policy will try to evict entries faster than they can be synchronized
     * with backup or near nodes. This value specifies how many concurrent synchronous
     * eviction sessions should be allowed before the system is forced to wait and let
     * synchronous evictions catch up with the eviction policy.
     * <p>
     * Note that if synchronous evictions start lagging, it is possible that you have either
     * too big or too small eviction key buffer size or small eviction timeout. In that case
     * you will need to adjust {@link #getEvictSynchronisedKeyBufferSize()} or {@link #getEvictSynchronizedTimeout()}
     * values as well.
     * <p>
     * Default value is defined by {@link #DFLT_EVICT_SYNCHRONOUS_CONCURRENCY_LEVEL}.
     *
     * @return Synchronous eviction concurrency level.
     */
    public int getEvictSynchronizedConcurrencyLevel();

    /**
     * This value denotes the maximum size of eviction queue in percents of cache
     * size in case of distributed cache (replicated and partitioned) and using
     * synchronized eviction (that is if {@link #isEvictSynchronized()} returns
     * {@code true}).
     * <p>
     * That queue is used internally as a buffer to decrease network costs for
     * synchronized eviction. Once queue size reaches specified value all required
     * requests for all entries in the queue are sent to remote nodes and the queue
     * is cleared.
     * <p>
     * Default value is defined by {@link #DFLT_MAX_EVICTION_OVERFLOW_RATIO} and
     * equals to {@code 10%}.
     *
     * @return Maximum size of eviction queue in percents of cache size.
     */
    public float getEvictMaxOverflowRatio();

    /**
     * Default cache transaction isolation to use when one is not explicitly
     * specified. Default value is defined by {@link #DFLT_TX_ISOLATION}.
     *
     * @return Default cache transaction isolation.
     * @see GridCacheTx
     */
    public GridCacheTxIsolation getDefaultTxIsolation();

    /**
     * Default cache transaction concurrency to use when one is not explicitly
     * specified. Default value is defined by {@link #DFLT_TX_CONCURRENCY}.
     *
     * @return Default cache transaction concurrency.
     * @see GridCacheTx
     */
    public GridCacheTxConcurrency getDefaultTxConcurrency();

    /**
     * Gets initial cache size which will be used to pre-create internal
     * hash table after start. Default value is defined by {@link #DFLT_START_SIZE}.
     *
     * @return Initial cache size.
     */
    public int getStartSize();

    /**
     * Gets initial cache size for near cache which will be used to pre-create internal
     * hash table after start. Default value is defined by {@link #DFLT_START_SIZE}.
     *
     * @return Initial near cache size.
     */
    public int getNearStartSize();

    /**
     * Gets flag indicating whether near cache is enabled in case of
     * {@link GridCacheMode#PARTITIONED PARTITIONED} mode. It's {@code true}
     * by default.
     *
     * @return Flag indicating whether near cache is enabled or not.
     */
    public boolean isNearEnabled();

    /**
     * Gets underlying persistent storage for read-through and write-through operations.
     * If not provided, cache will not exhibit read-through or write-through behavior.
     *
     * @return Underlying persistent storage for read-through and write-through operations.
     */
    public <K, V> GridCacheStore<K, V> getStore();

    /**
     * Gets key topology resolver to provide mapping from keys to nodes.
     *
     * @return Key topology resolver to provide mapping from keys to nodes.
     */
    public <K> GridCacheAffinity<K> getAffinity();

    /**
     * Gets flag to enable/disable {@link GridCacheTxIsolation#SERIALIZABLE} isolation
     * level for cache transactions. Serializable level does carry certain overhead and
     * if not used, should be disabled. Default value is {@code false}.
     *
     * @return {@code True} if serializable transactions are enabled, {@code false} otherwise.
     */
    public boolean isTxSerializableEnabled();

    /**
     * If {@code true}, then all transactional values will be written to persistent
     * storage at {@link GridCacheTx#commit()} phase. If {@code false}, then values
     * will be persisted after every operation. Default value is {@code true}.
     *
     * @return Flag indicating whether to persist once on commit, or after every
     *      operation.
     */
    public boolean isBatchUpdateOnCommit();

    /**
     * Gets default transaction timeout. Default value is defined by {@link #DFLT_TRANSACTION_TIMEOUT}
     * which is {@code 0} and means that transactions will never time out.
     *
     * @return Default transaction timeout.
     */
    public long getDefaultTxTimeout();

    /**
     * Gets default query timeout. Default value is defined by {@link #DFLT_QUERY_TIMEOUT}. {@code 0} (zero)
     * means that the query will never timeout and will wait for completion.
     *
     * @return Default query timeout, {@code 0} for never.
     */
    public long getDefaultQueryTimeout();

    /**
     * Gets default lock acquisition timeout. Default value is defined by {@link #DFLT_LOCK_TIMEOUT}
     * which is {@code 0} and means that lock acquisition will never timeout.
     *
     * @return Default lock timeout.
     */
    public long getDefaultLockTimeout();

    /**
     * Invalidation flag. If {@code true}, values will be invalidated (nullified) upon commit.
     *
     * @return Invalidation flag.
     */
    public boolean isInvalidate();

    /**
     * Flag indicating if cached values should be additionally stored in serialized form.
     * It's set to {@code true} by default.
     *
     * @return {@code true} if cached values should be additionally stored in
     *      serialized form, {@code false} otherwise.
     */
    public boolean isStoreValueBytes();

    /**
     * Gets refresh-ahead ratio. If non-zero, then entry will be preloaded in the background
     * whenever it's accessed and the refresh ratio of it's total time-to-live has passed.
     * This feature ensures that entries are always automatically re-cached whenever they are
     * nearing expiration.
     * <p>
     * For example, if refresh ratio is set to {@code 0.75} and entry's time-to-live is
     * {@code 1} minute, then if this entry is accessed any time after {@code 45} seconds
     * (which is 0.75 of a minute), the cached value will be immediately returned, but
     * entry will be automatically reloaded from persistent store in the background.
     *
     * @return Refresh-ahead ratio.
     */
    public double getRefreshAheadRatio();

    /**
     * Gets transaction manager finder for integration for JEE app servers.
     *
     * @return Transaction manager finder.
     */
    public GridCacheTmLookup getTransactionManagerLookup();

    /**
     * Gets preload mode for distributed cache.
     * <p>
     * Default is defined by {@link #DFLT_PRELOAD_MODE}.
     *
     * @return Preload mode.
     */
    public GridCachePreloadMode getPreloadMode();

    /**
     * Gets size (in number bytes) to be loaded within a single preload message.
     * Preloading algorithm will split total data set on every node into multiple
     * batches prior to sending data. Default value is defined by
     * {@link #DFLT_PRELOAD_BATCH_SIZE}.
     *
     * @return Size in bytes of a single preload message.
     */
    public int getPreloadBatchSize();

    /**
     * Gets size of preloading thread pool. Note that size serves as a hint and implementation
     * may create more threads for preloading than specified here (but never less threads).
     * <p>
     * Default value is {@link #DFLT_PRELOAD_THREAD_POOL_SIZE}.
     *
     * @return Size of preloading thread pool.
     */
    public int getPreloadThreadPoolSize();

    /**
     * Gets query types to use to auto index values of boxed and unboxed primitive types,
     * Strings and Dates.
     *
     * @return Query types to use to auto index values of primitives, strings, and dates.
     */
    public Collection<GridCacheQueryType> getAutoIndexQueryTypes();

    /**
     * Absolute or relative to {@code GRIDGAIN_HOME} path for storing query indexes on disk
     * (if they are configured to be stored on disk). If not provided, by default indexes will be
     * stored under default folder defined by {@link #DFLT_IDX_PARENT_FOLDER_NAME} constant.
     *
     * @return Absolute or relative to {@code GRIDGAIN_HOME} path for storing query indexes on disk.
     */
    public String getIndexPath();

    /**
     * Flag indicating whether full class names, i.e. {@link Class#getName()} values, or
     * simple class names, i.e. {@link Class#getSimpleName()} values should be used in
     * queries.
     * <p>
     * Default value is {@code false}.
     *
     * @return Use full class names for index tables or short.
     */
    public boolean isIndexFullClassName();

    /**
     * This flag indicates that the same key object can only be associated with the same value
     * type and a value type can only be associated with keys of the same type.
     * <p>
     * For example, let's assume that you have keys {@code K1} and {@code K2} of types
     * {@code Kt1} and {@code Kt2}, and values {@code V1} and {@code V2} of type {@code Vt1}
     * and {@code Vt2}. If this flag is set to {@code true}, then once key {@code K1} is
     * associated with value of type {@code Vt1}, this {@code K1} can never be associated
     * with a value of type {@code Vt2}. Also, once a value of type {@code Vt1} is associated with
     * a key of type {@code Kt1}, all values of type {@code Vt1} will have to be associated with
     * keys of type {@code Kt1} and can never be associated with keys of type {@code Kt2}.
     * <p>
     * The behavior described above is how we usually operate with data. However, in certain
     * cases it may be desired to associate a key with values of different types over time and
     * in that case you should set this flag to {@code false}.
     * <p>
     * Setting this flag to {@code true}, which is default, allows cache implementation to
     * perform performance optimizations for queries.
     * <p>
     * Note that if this flag is {@code false} then it is impossible to run sql queries
     * containing any conditions on key field (which is '_key') since it is of binary type
     * in this case.
     *
     * @return {@code True} for fixed typing, {@code false} otherwise.
     */
    public boolean isIndexFixedTyping();

    /**
     * Flag indicating whether query storage should be deleted or not upon start
     * (default is {@code true}).
     *
     * @return If {@code true}, cache indexes will be cleaned up upon start.
     */
    public boolean isIndexCleanup();

    /**
     * Flag indicating whether query index should be stored only in memory (not on disk).
     * <p>
     * Note that cache queries with {@link GridCacheQueryType#LUCENE LUCENE} type cannot
     * be used in case of in-memory index database, i.e. if this property is {@code true}.
     * <p>
     * It is reasonable to configure this property with opposite value of {@link #isSwapEnabled()}
     * property. If swap is enabled, then most likely indexes will not fit in memory and
     * it is reasonable to overflow them to disk. Otherwise, if swap id disabled,
     * then indexes should fit in memory.
     * <p>
     * Default is {@code true} and is defined by {@link #DFLT_IDX_MEM_ONLY} constant.
     *
     * @return {@code True} if index should be stored only in memory (not on disk).
     */
    public boolean isIndexMemoryOnly();

    /**
     * Gets the maximum memory used per single operation with query index
     * (store and remove), in bytes. Operations that use more memory are buffered
     * to disk, slowing down the operation. The default max size is 100000. 0 means no limit.
     *
     * @return Maximum memory used for a single query index operation in bytes. 0 means no limit.
     */
    public int getIndexMaxOperationMemory();

    /**
     *
     * @return Addition options to H2 database (query storage).
     */
    public String getIndexH2Options();

    /**
     * Gets frequency of running H2 "ANALYZE" command in order to update
     * selectivity statistics of H2 database tables. Default value is
     * defined by {@link #DFLT_IDX_ANALYZE_FREQ} and equals to 10 minutes.
     * <p>
     * To disable query analyzing, set to {@code 0}
     *
     * @return Frequency (in milliseconds) for running H2 "ANALYZE" command.
     */
    public long getIndexAnalyzeFrequency();

    /**
     * Gets number of samples used to run H2 "ANALYZE" command in order to update
     * selectivity statistics of H2 database tables. In other words, this value
     * means the number of rows to scan for each db table. Default value is defined
     * by {@link #DFLT_IDX_ANALYZE_SAMPLE_SIZE} and equals to 10000.
     *
     * @return Frequency (in milliseconds) for running H2 "ANALYZE" command.
     */
    public long getIndexAnalyzeSampleSize();

    /**
     * Optional user name for index store.
     *
     * @return Optional user name for index store.
     */
    public String getIndexUsername();

    /**
     * Optional password for index store.
     *
     * @return Optional password for index store.
     */
    public String getIndexPassword();

    /**
     * Gets frequency at which distributed garbage collector will
     * check other nodes if there are any zombie locks left over.
     * <p>
     * If not provided, default value is {@link GridCacheConfiguration#DFLT_DGC_FREQUENCY}.
     *
     * @return Frequency of distributed GC in milliseconds ({@code 0} to disable GC).
     */
    public long getDgcFrequency();

    /**
     * Gets timeout after which locks are considered to be suspicious.
     * <p>
     * If not provided, default value is {@link GridCacheConfiguration#DFLT_DGC_SUSPECT_LOCK_TIMEOUT}.
     *
     * @return Distributed GC suspect lock timeout.
     */
    public long getDgcSuspectLockTimeout();

    /**
     * Gets system-wide flag indicating whether DGC manager should remove locks in question or only
     * report them. Note, that this behavior could be overridden by specifically calling
     * {@link GridCache#dgc(long, boolean, boolean)} method.
     * <p>
     * If {@code false} DGC manager will not release the locks that are not owned by any other node.
     * This may be useful for debugging purposes. You may also enable DGC tracing by enabling DEBUG
     * on {@link #DGC_TRACE_LOGGER_NAME} category.
     * <p>
     * If not provided, default value is {@link GridCacheConfiguration#DFLT_DGC_REMOVE_LOCKS}.
     *
     * @return {@code True} if DGC should remove locks.
     * @see #DGC_TRACE_LOGGER_NAME
     */
    public boolean isDgcRemoveLocks();

    /**
     * Flag indicating whether GridGain should wait for commit replies from all nodes. By default
     * GridGain will not wait for responses from participating nodes, which means that remote
     * nodes may get their state updated a bit after {@link GridCacheTx#commit()} method completes.
     * Setting this flag to {@code true} guarantees that update will have reached all nodes prior
     * to completing {@link GridCacheTx#commit()} method.
     *
     * @return {@code True} in case of synchronous commit.
     */
    public boolean isSynchronousCommit();

    /**
     * Flag indicating whether GridGain should wait for rollback replies from all nodes. By default
     * GridGain will not wait for responses from participating nodes, which means that remote
     * nodes may get their state updated a bit after {@link GridCacheTx#commit()} method completes.
     * Setting this flag to {@code true} guarantees that update will have reached all nodes prior
     * to completing {@link GridCacheTx#commit()} method.
     *
     * @return {@code True} in case of synchronous rollback.
     */
    public boolean isSynchronousRollback();

    /**
     * Flag indicating whether GridGain should use swap storage by default. By default
     * swap is disabled which is defined via {@link #DFLT_SWAP_ENABLED} constant.
     * <p>
     * Note that this flag may be overridden for cache projection created with flag
     * {@link GridCacheFlag#SKIP_SWAP}.
     *
     * @return {@code True} if swap storage is enabled.
     */
    public boolean isSwapEnabled();

    /**
     * Flag indicating whether GridGain should activate read-through/write-through behaviour
     * by default.
     * <p>
     * Note that this flag may be overridden for cache projection created with flag
     * {@link GridCacheFlag#SKIP_STORE}.
     *
     * @return {@code true} if configured persistent store is used by default.
     */
    public boolean isStoreEnabled();

    /**
     * Flag indicating whether GridGain should use write-from-behind behaviour for the cache store.
     * By default write-from-behind is disabled which is defined via {@link #DFLT_WRITE_FROM_BEHIND_ENABLED}
     * constant.
     *
     * @return {@code True} if write-from-behind is enabled.
     */
    public boolean isWriteFromBehindEnabled();

    /**
     * Maximum size of the write-from-behind cache. If cache size exceeds this value,
     * all cached items are flushed to the cache store and write cache is cleared.
     * <p/>
     * If not provided, default value is {@link #DFLT_WRITE_FROM_BEHIND_FLUSH_SIZE}.
     * If this value is {@code 0}, then flush is performed according to the flush frequency interval.
     * <p/>
     * Note that you cannot set both, {@code flush} size and {@code flush frequency}, to {@code 0}.
     *
     * @return Maximum object count in write-from-behind cache.
     */
    public int getWriteFromBehindFlushSize();

    /**
     * Frequency with which write-from-behind cache is flushed to the cache store in milliseconds.
     * This value defines the maximum time interval between object insertion/deletion from the cache
     * ant the moment when corresponding operation is applied to the cache store.
     * </p>
     * If not provided, default value is {@link #DFLT_WRITE_FROM_BEHIND_FLUSH_FREQUENCY}.
     * If this value is {@code 0}, then flush is performed according to the flush size.
     * <p/>
     * Note that you cannot set both, {@code flush} size and {@code flush frequency}, to {@code 0}.
     *
     * @return Write-from-behind flush frequency in milliseconds.
     */
    public long getWriteFromBehindFlushFrequency();

    /**
     * Number of threads that will perform cache flushing if either cache size exceeded value defined by
     * {@link #getWriteFromBehindFlushSize()}, or flush interval defined by
     * {@link #getWriteFromBehindFlushFrequency()} is elapsed.
     * <p/>
     * If not provided, default value is {@link #DFLT_WRITE_FROM_BEHIND_FLUSH_THREAD_CNT}.
     *
     * @return Count of flush threads.
     */
    public int getWriteFromBehindFlushThreadCount();

    /**
     * Maximum batch size for write-from-behind cache store operations. Store operations (get or remove)
     * are combined in a batch of this size to be passed to
     * {@link GridCacheStore#putAll(String, GridCacheTx, Map)} or
     * {@link GridCacheStore#removeAll(String, GridCacheTx, Collection)} methods.
     * <p/>
     * If not provided, default value is {@link #DFLT_WRITE_FROM_BEHIND_BATCH_SIZE}.
     *
     * @return Maximum batch size for store operations.
     */
    public int getWriteFromBehindBatchSize();

    /**
     * Cloner to be used for cloning values that are returned to user only if {@link GridCacheFlag#CLONE}
     * is set on {@link GridCacheProjection}. Cloning values is useful when it is needed to get value from
     * cache, change it and put it back (if the value was not cloned, then user would be updating the
     * cached reference which would violate cache integrity).
     * <p>
     * <b>NOTE:</b> by default, cache uses {@link GridCacheBasicCloner} implementation which will clone only objects
     * implementing {@link Cloneable} interface. You can also configure cache to use
     * {@link GridCacheDeepCloner} which will perform deep-cloning of all objects returned from cache,
     * regardless of the {@link Cloneable} interface. If none of the above cloners fit your
     * logic, you can also provide your own implementation of {@link GridCacheCloner} interface.
     *
     * @return Cloner to be used if {@link GridCacheFlag#CLONE} flag is set on cache projection.
     */
    public GridCacheCloner getCloner();

    /**
     * Affinity key mapper used to provide custom affinity key for any given key.
     * Affinity mapper is particularly useful when several objects need to be collocated
     * on the same node (they will also be backed up on the same nodes as well).
     * <p>
     * If not provided, then default implementation will be used. The default behavior
     * is described in {@link GridCacheAffinityMapper} documentation.
     *
     * @return Mapper to use for affinity key mapping.
     */
    public <K> GridCacheAffinityMapper<K> getAffinityMapper();

    /**
     * Gets default number of sequence values reserved for {@link GridCacheAtomicSequence} instances. After
     * a certain number has been reserved, consequent increments of sequence will happen locally,
     * without communication with other nodes, until the next reservation has to be made.
     * <p>
     * Default value is {@link #DFLT_ATOMIC_SEQUENCE_RESERVE_SIZE}.
     *
     * @return Atomic sequence reservation size.
     */
    public int getAtomicSequenceReserveSize();

    /**
     * Flag to enable/disable near cache eviction policy. Default is {@code true}, which means that
     * eviction policy for near cache is enabled. If set to {@code false}, then evictions for
     * near cache will not happen even if {@link #getNearEvictionPolicy()} was set.
     * <p>
     * Note that this property only makes sense for {@link GridCacheMode#PARTITIONED PARTITIONED} caches.
     *
     * @return {@code True} if near eviction policy is enabled, {@code false} otherwise.
     */
    public boolean isNearEvictionEnabled();

    /**
     * Flag to enable/disable cache eviction policy. Default is {@code true}, which means that
     * eviction policy for cache is enabled. If set to {@code false}, then evictions
     * will not happen even if {@link #getEvictionPolicy()} was set.
     * <p>
     * Note that handling evictions does carry certain overhead, so it is recommended to set
     * this property to {@code false} if you are not planning to evict entries from cache.
     *
     * @return {@code True} if eviction policy is enabled, {@code false} otherwise.
     */
    public boolean isEvictionEnabled();
}

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
import org.gridgain.grid.cache.jta.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.typedef.internal.*;

import javax.transaction.*;
import java.util.*;

/**
 * Cache configuration adapter. Use this convenience adapter when creating
 * cache configuration to set on {@link GridConfigurationAdapter#setCacheConfiguration(GridCacheConfiguration...)}
 * method. This adapter is a simple bean and can be configured from Spring XML files
 * (or other DI frameworks).
 * <p>
 * Note that absolutely all configuration properties are optional, so users
 * should only change what they need.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheConfigurationAdapter implements GridCacheConfiguration {
    /** Cache name. */
    private String name;

    /** Default batch size for all cache's sequences. */
    private int seqReserveSize = DFLT_ATOMIC_SEQUENCE_RESERVE_SIZE;

    /** Preload thread pool size. */
    private int preloadPoolSize = DFLT_PRELOAD_THREAD_POOL_SIZE;

    /** Default time to live for cache entries. */
    private long ttl = DFLT_TIME_TO_LIVE;

    /** Cache expiration policy. */
    private GridCacheEvictionPolicy evictPolicy;

    /** Near cache eviction policy. */
    private GridCacheEvictionPolicy nearEvictPolicy;

    /** Flag indicating whether eviction is synchronized. */
    private boolean evictSync = DFLT_EVICT_SYNCHRONIZED;

    /** Flag indicating whether eviction is synchronized with near nodes. */
    private boolean evictNearSync = DFLT_EVICT_NEAR_SYNCHRONIZED;

    /** Eviction key buffer size. */
    private int evictKeyBufferSize = DFLT_EVICT_KEY_BUFFER_SIZE;

    /** Synchronous eviction timeout. */
    private int evictSyncConcurrencyLvl = DFLT_EVICT_SYNCHRONOUS_CONCURRENCY_LEVEL;

    /** Synchronous eviction timeout. */
    private long evictSyncTimeout = DFLT_EVICT_SYNCHRONOUS_TIMEOUT;

    /** Eviction filter. */
    private GridCacheEvictionFilter<?, ?> evictFilter;

    /** Maximum eviction overflow ratio. */
    private float evictMaxOverflowRatio = DFLT_MAX_EVICTION_OVERFLOW_RATIO;

    /** Transaction isolation. */
    private GridCacheTxIsolation dfltIsolation = DFLT_TX_ISOLATION;

    /** Cache concurrency. */
    private GridCacheTxConcurrency dfltConcurrency = DFLT_TX_CONCURRENCY;

    /** Default transaction serializable flag. */
    private boolean txSerEnabled = DFLT_TX_SERIALIZABLE_ENABLED;

    /** Default transaction timeout. */
    private long dfltTxTimeout = DFLT_TRANSACTION_TIMEOUT;

    /** Default lock timeout. */
    private long dfltLockTimeout = DFLT_LOCK_TIMEOUT;

    /** Default query timeout. */
    private long dfltQryTimeout = DFLT_QUERY_TIMEOUT;

    /** Default cache start size. */
    private int startSize = DFLT_START_SIZE;

    /** Default near cache start size. */
    private int nearStartSize = DFLT_NEAR_START_SIZE;

    /** Near cache flag. */
    private boolean nearEnabled = DFLT_NEAR_ENABLED;

    /** Eviction flag. */
    private boolean evictEnabled = DFLT_EVICTION_ENABLED;

    /** Near eviction flag. */
    private boolean nearEvictEnabled = DFLT_NEAR_EVICTION_ENABLED;

    /** */
    private GridCacheStore<?, ?> store;

    /** Node group resolver. */
    private GridCacheAffinity<?> aff;

    /** Cache mode. */
    private GridCacheMode cacheMode;

    /** Flag to enable transactional batch update. */
    private boolean txBatchUpdate = DFLT_TX_BATCH_UPDATE;

    /** Flag indicating whether this is invalidation-based cache. */
    private boolean invalidate = DFLT_INVALIDATE;

    /** Flag indicating if cached values should be additionally stored in serialized form. */
    private boolean storeValueBytes = DFLT_STORE_VALUE_BYTES;

    /** Refresh-ahead ratio. */
    private double refreshAheadRatio;

    /** */
    private GridCacheTmLookup tmLookup;

    /** Distributed cache preload mode. */
    private GridCachePreloadMode preloadMode = DFLT_PRELOAD_MODE;

    /** Preload batch size. */
    private int preloadBatchSize = DFLT_PRELOAD_BATCH_SIZE;

    /** */
    private Collection<GridCacheQueryType> autoIndexTypes;

    /** Path to index database, default will be used if null. */
    private String idxPath;

    /** Use full class names for index tables or short. */
    private boolean idxFullClassName;

    /** Mark that all keys will be the same type to make possible to store them as native database type. */
    private boolean idxFixedTyping = DFLT_IDX_FIXED_TYPING;

    /** Leave database after exit or not. */
    private boolean idxCleanup = DFLT_IDX_CLEANUP;

    /** Use only memory for index database if true.*/
    private boolean idxMemOnly = DFLT_IDX_MEM_ONLY;

    /** Maximum memory used for delete and insert in bytes. 0 means no limit. */
    private int idxMaxOperationMem = DFLT_IDX_MAX_OPERATIONAL_MEM;

    /** Query index db user. */
    private String idxUser;

    /** Query index db password. */
    private String idxPswd;

    /** Distributed garbage collection frequency. */
    private long dgcFreq = DFLT_DGC_FREQUENCY;

    /** */
    private long dgcSuspectLockTimeout = DFLT_DGC_SUSPECT_LOCK_TIMEOUT;

    /** */
    private boolean dgcRmvLocks = DFLT_DGC_REMOVE_LOCKS;

    /** Synchronous commit. */
    private boolean syncCommit = DFLT_SYNC_COMMIT;

    /** Synchronous rollback. */
    private boolean syncRollback = DFLT_SYNC_ROLLBACK;

    /** */
    private boolean swapEnabled = DFLT_SWAP_ENABLED;

    /** */
    private boolean storeEnabled = DFLT_STORE_ENABLED;

    /** Write from behind feature. */
    private boolean writeFromBehindEnabled = DFLT_WRITE_FROM_BEHIND_ENABLED;

    /** Maximum size of write from behind cache. */
    private int writeFromBehindFlushSize = DFLT_WRITE_FROM_BEHIND_FLUSH_SIZE;

    /** Write from behind flush frequency in milliseconds. */
    private long writeFromBehindFlushFrequency = DFLT_WRITE_FROM_BEHIND_FLUSH_FREQUENCY;

    /** Flush thread count for write from behind cache store. */
    private int writeFromBehindFlushThreadCnt = DFLT_WRITE_FROM_BEHIND_FLUSH_THREAD_CNT;

    /** Maximum batch size for write from behind cache store. */
    private int writeFromBehindBatchSize = DFLT_WRITE_FROM_BEHIND_BATCH_SIZE;

    /** */
    private String idxH2Opt;

    /** */
    private long idxAnalyzeFreq = DFLT_IDX_ANALYZE_FREQ;

    /** */
    private long idxAnalyzeSampleSize = DFLT_IDX_ANALYZE_SAMPLE_SIZE;

    /** */
    private GridCacheCloner cloner;

    /** */
    private GridCacheAffinityMapper affMapper;

    /**
     * Empty constructor (all values are initialized to their defaults).
     */
    public GridCacheConfigurationAdapter() {
        /* No-op. */
    }

    /**
     * Copy constructor.
     *
     * @param cc Configuration to copy.
     */
    public GridCacheConfigurationAdapter(GridCacheConfiguration cc) {
        /*
         * NOTE: MAKE SURE TO PRESERVE ALPHABETIC ORDER!
         * ==============================================
         */
        aff = cc.getAffinity();
        affMapper = cc.getAffinityMapper();
        autoIndexTypes = cc.getAutoIndexQueryTypes();
        cacheMode = cc.getCacheMode();
        cloner = cc.getCloner();
        dfltConcurrency = cc.getDefaultTxConcurrency();
        dfltIsolation = cc.getDefaultTxIsolation();
        dfltLockTimeout = cc.getDefaultLockTimeout();
        dfltQryTimeout = cc.getDefaultQueryTimeout();
        dfltTxTimeout = cc.getDefaultTxTimeout();
        dgcFreq = cc.getDgcFrequency();
        dgcRmvLocks = cc.isDgcRemoveLocks();
        dgcSuspectLockTimeout = cc.getDgcSuspectLockTimeout();
        evictEnabled = cc.isEvictionEnabled();
        evictFilter = cc.getEvictionFilter();
        evictKeyBufferSize = cc.getEvictSynchronisedKeyBufferSize();
        evictNearSync = cc.isEvictNearSynchronized();
        evictPolicy = cc.getEvictionPolicy();
        evictSync = cc.isEvictSynchronized();
        evictSyncConcurrencyLvl = cc.getEvictSynchronizedConcurrencyLevel();
        evictSyncTimeout = cc.getEvictSynchronizedTimeout();
        idxH2Opt = cc.getIndexH2Options();
        idxAnalyzeFreq = cc.getIndexAnalyzeFrequency();
        idxAnalyzeSampleSize = cc.getIndexAnalyzeSampleSize();
        idxCleanup = cc.isIndexCleanup();
        idxFixedTyping = cc.isIndexFixedTyping();
        idxFullClassName = cc.isIndexFullClassName();
        idxMaxOperationMem = cc.getIndexMaxOperationMemory();
        idxMemOnly = cc.isIndexMemoryOnly();
        idxPath = cc.getIndexPath();
        idxPswd = cc.getIndexPassword();
        idxUser = cc.getIndexUsername();
        invalidate = cc.isInvalidate();
        storeValueBytes = cc.isStoreValueBytes();
        txBatchUpdate = cc.isBatchUpdateOnCommit();
        txSerEnabled = cc.isTxSerializableEnabled();
        name = cc.getName();
        nearStartSize = cc.getNearStartSize();
        nearEnabled = cc.isNearEnabled();
        nearEvictEnabled = cc.isNearEvictionEnabled();
        nearEvictPolicy = cc.getNearEvictionPolicy();
        evictMaxOverflowRatio = cc.getEvictMaxOverflowRatio();
        preloadMode = cc.getPreloadMode();
        preloadBatchSize = cc.getPreloadBatchSize();
        preloadPoolSize = cc.getPreloadThreadPoolSize();
        refreshAheadRatio = cc.getRefreshAheadRatio();
        seqReserveSize = cc.getAtomicSequenceReserveSize();
        startSize = cc.getStartSize();
        store = cc.getStore();
        storeEnabled = cc.isStoreEnabled();
        swapEnabled = cc.isSwapEnabled();
        syncCommit = cc.isSynchronousCommit();
        syncRollback = cc.isSynchronousRollback();
        tmLookup = cc.getTransactionManagerLookup();
        ttl = cc.getDefaultTimeToLive();
        writeFromBehindBatchSize = cc.getWriteFromBehindBatchSize();
        writeFromBehindEnabled = cc.isWriteFromBehindEnabled();
        writeFromBehindFlushFrequency = cc.getWriteFromBehindFlushFrequency();
        writeFromBehindFlushSize = cc.getWriteFromBehindFlushSize();
        writeFromBehindFlushThreadCnt = cc.getWriteFromBehindFlushThreadCount();
    }

    /** {@inheritDoc} */
    @Override public String getName() {
        return name;
    }

    /**
     * Sets cache name.
     *
     * @param name Cache name. May be <tt>null</tt>, but may not be empty string.
     */
    public void setName(String name) {
        A.ensure(name == null || !name.isEmpty(), "Name cannot be null or empty.");

        this.name = name;
    }

    @Override public long getDefaultTimeToLive() {
        return ttl;
    }

    /**
     * Sets time to live for all objects in cache. This value can be override for individual objects.
     *
     * @param ttl Time to live for all objects in cache.
     */
    public void setDefaultTimeToLive(long ttl) {
        this.ttl = ttl;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCacheEvictionPolicy<K, V> getEvictionPolicy() {
        return evictPolicy;
    }

    /**
     * Sets cache eviction policy.
     *
     * @param evictPolicy Cache expiration policy.
     */
    public void setEvictionPolicy(GridCacheEvictionPolicy evictPolicy) {
        this.evictPolicy = evictPolicy;
    }

    /** {@inheritDoc} */
    @Override public boolean isEvictionEnabled() {
        return evictEnabled;
    }

    /**
     * Sets flag to enable/disable eviction policy. See {@link #isEvictionEnabled()}
     * for more information.
     *
     * @param evictEnabled Flag to enable/disable eviction policy.
     */
    public void setEvictionEnabled(boolean evictEnabled) {
        this.evictEnabled = evictEnabled;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCacheEvictionPolicy<K, V> getNearEvictionPolicy() {
        return nearEvictPolicy;
    }

    /**
     * Sets eviction policy for near cache. This property is only used for
     * {@link GridCacheMode#PARTITIONED} caching mode.
     *
     * @param nearEvictPolicy Eviction policy for near cache.
     */
    public void setNearEvictionPolicy(GridCacheEvictionPolicy nearEvictPolicy) {
        this.nearEvictPolicy = nearEvictPolicy;
    }

    /** {@inheritDoc} */
    @Override public boolean isNearEvictionEnabled() {
        return nearEvictEnabled;
    }

    /**
     * Sets flag to enable/disable near eviction policy. See {@link #isNearEvictionEnabled()}
     * for more information.
     *
     * @param nearEvictEnabled Flag to enable/disable near eviction policy.
     */
    public void setNearEvictionEnabled(boolean nearEvictEnabled) {
        this.nearEvictEnabled = nearEvictEnabled;
    }

    /** {@inheritDoc} */
    @Override public boolean isEvictSynchronized() {
        return evictSync;
    }

    /**
     * Sets flag indicating whether eviction is synchronized with backup nodes
     * (or the rest of the nodes for replicated cache).
     *
     * @param evictSync {@code true} if synchronized, {@code false} if not.
     */
    public void setEvictSynchronized(boolean evictSync) {
        this.evictSync = evictSync;
    }

    /**
     * Sets flag indicating whether eviction is synchronized with near nodes.
     *
     * @param evictNearSync {@code true} if synchronized, {@code false} if not.
     */
    public void setEvictNearSynchronized(boolean evictNearSync) {
        this.evictNearSync = evictNearSync;
    }

    /** {@inheritDoc} */
    @Override public boolean isEvictNearSynchronized() {
        return evictNearSync;
    }

    /** {@inheritDoc} */
    @Override public int getEvictSynchronisedKeyBufferSize() {
        return evictKeyBufferSize;
    }

    /**
     * Sets eviction key buffer size.
     *
     * @param evictKeyBufferSize Eviction key buffer size.
     */
    public void setEvictSynchronizedKeyBufferSize(int evictKeyBufferSize) {
        this.evictKeyBufferSize = evictKeyBufferSize;
    }

    /** {@inheritDoc} */
    @Override public int getEvictSynchronizedConcurrencyLevel() {
        return evictSyncConcurrencyLvl;
    }

    /**
     * Sets concurrency level for synchronous evictions
     *
     * @param evictSyncConcurrencyLvl Synchronous eviction concurrency level.
     */
    public void setEvictSynchronizedConcurrencyLevel(int evictSyncConcurrencyLvl) {
        this.evictSyncConcurrencyLvl = evictSyncConcurrencyLvl;
    }

    /** {@inheritDoc} */
    @Override public long getEvictSynchronizedTimeout() {
        return evictSyncTimeout;
    }

    /**
     * Sets synchronous eviction timeout.
     *
     * @param evictSyncTimeout Synchronous eviction timeout.
     */
    public void setEvictSynchronizedTimeout(long evictSyncTimeout) {
        this.evictSyncTimeout = evictSyncTimeout;
    }

    /** {@inheritDoc} */
    @Override public float getEvictMaxOverflowRatio() {
        return evictMaxOverflowRatio;
    }

    /**
     * Sets maximum eviction overflow ratio.
     *
     * @param evictMaxOverflowRatio Maximum eviction overflow ratio.
     */
    public void setEvictMaxOverflowRatio(float evictMaxOverflowRatio) {
        this.evictMaxOverflowRatio = evictMaxOverflowRatio;
    }

    /** {@inheritDoc} */
    @Override public <K, V> GridCacheEvictionFilter<K, V> getEvictionFilter() {
        return (GridCacheEvictionFilter<K, V>) evictFilter;
    }

    /**
     * Sets eviction filter.
     *
     * @param evictFilter Eviction filter.
     */
    public <K, V> void setEvictionFilter(GridCacheEvictionFilter<K, V> evictFilter) {
        this.evictFilter = evictFilter;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTxConcurrency getDefaultTxConcurrency() {
        return dfltConcurrency;
    }

    /**
     * Sets default transaction concurrency.
     *
     * @param dfltConcurrency Default cache transaction concurrency.
     */
    public void setDefaultTxConcurrency(GridCacheTxConcurrency dfltConcurrency) {
        this.dfltConcurrency = dfltConcurrency;
    }

    /** {@inheritDoc} */
    @Override public boolean isTxSerializableEnabled() {
        return txSerEnabled;
    }

    /**
     * Enables/disables serializable cache transactions. See {@link #isTxSerializableEnabled()}
     * for more information.
     *
     * @param txSerEnabled Flag to enable/disable serializable cache transactions.
     */
    public void setTxSerializableEnabled(boolean txSerEnabled) {
        this.txSerEnabled = txSerEnabled;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTxIsolation getDefaultTxIsolation() {
        return dfltIsolation;
    }

    /**
     * Sets default transaction isolation.
     *
     * @param dfltIsolation Default cache transaction isolation.
     */
    public void setDefaultTxIsolation(GridCacheTxIsolation dfltIsolation) {
        this.dfltIsolation = dfltIsolation;
    }

    /** {@inheritDoc} */
    @Override public int getStartSize() {
        return startSize;
    }

    /**
     * Initial size for internal hash map.
     *
     * @param startSize Cache start size.
     */
    public void setStartSize(int startSize) {
        this.startSize = startSize;
    }

    /** {@inheritDoc} */
    @Override public int getNearStartSize() {
        return nearStartSize;
    }

    /**
     * Start size for near cache. This property is only used for
     * {@link GridCacheMode#PARTITIONED} caching mode.
     *
     * @param nearStartSize Start size for near cache.
     */
    public void setNearStartSize(int nearStartSize) {
        this.nearStartSize = nearStartSize;
    }

    /** {@inheritDoc} */
    @Override public boolean isNearEnabled() {
        return nearEnabled;
    }

    /**
     * Sets flag indicating whether near cache is enabled in case of
     * {@link GridCacheMode#PARTITIONED PARTITIONED} mode. It is {@code true}
     * by default.
     *
     * @param nearEnabled Flag indicating whether near cache is enabled.
     */
    public void setNearEnabled(boolean nearEnabled) {
        this.nearEnabled = nearEnabled;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCacheStore<K, V> getStore() {
        return (GridCacheStore<K, V>)store;
    }

    /**
     * Sets persistent storage for cache data.
     *
     * @param store Persistent cache store.
     */
    public <K, V> void setStore(GridCacheStore<K, V> store) {
        this.store = store;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K> GridCacheAffinity<K> getAffinity() {
        return (GridCacheAffinity<K>)aff;
    }

    /**
     * Sets affinity for cache keys.
     *
     * @param aff Cache key affinity.
     */
    public <K> void setAffinity(GridCacheAffinity<K> aff) {
        this.aff = aff;
    }

    /** {@inheritDoc} */
    @Override public GridCacheMode getCacheMode() {
        return cacheMode;
    }

    /**
     * Sets caching mode.
     *
     * @param cacheMode Caching mode.
     */
    public void setCacheMode(GridCacheMode cacheMode) {
        this.cacheMode = cacheMode;
    }

    /** {@inheritDoc} */
    @Override public boolean isBatchUpdateOnCommit() {
        return txBatchUpdate;
    }

    /**
     * Sets flag indicating if persistent store should be updated after every cache
     * operation or once at commit time. Default is {@code true}.
     *
     * @param txBatchUpdate {@code True} if updates should be batched at the end of transaction,
     *      {@code false} if updates should be propagated to persistent store
     *      individually as they occur (without waiting to the end of transaction).
     */
    public void setBatchUpdateOnCommit(boolean txBatchUpdate) {
        this.txBatchUpdate = txBatchUpdate;
    }

    /** {@inheritDoc} */
    @Override public long getDefaultTxTimeout() {
        return dfltTxTimeout;
    }

    /**
     * Sets default transaction timeout in milliseconds. By default this value
     * is defined by {@link #DFLT_TRANSACTION_TIMEOUT}.
     *
     * @param dfltTxTimeout Default transaction timeout.
     */
    public void setDefaultTxTimeout(long dfltTxTimeout) {
        this.dfltTxTimeout = dfltTxTimeout;
    }

    /** {@inheritDoc} */
    @Override public long getDefaultLockTimeout() {
        return dfltLockTimeout;
    }

    /**
     * Sets default lock timeout in milliseconds. By default this value
     * is defined by {@link #DFLT_LOCK_TIMEOUT}.
     *
     * @param dfltLockTimeout Default lock timeout.
     */
    public void setDefaultLockTimeout(long dfltLockTimeout) {
        this.dfltLockTimeout = dfltLockTimeout;
    }

    /** {@inheritDoc} */
    @Override public long getDefaultQueryTimeout() {
        return dfltQryTimeout;
    }

    /**
     * Sets default query timeout, {@code 0} for never. For more information see
     * {@link #getDefaultQueryTimeout()}.
     *
     * @param dfltQryTimeout Default query timeout.
     */
    public void setDefaultQueryTimeout(long dfltQryTimeout) {
        this.dfltQryTimeout = dfltQryTimeout;
    }

    /** {@inheritDoc} */
    @Override public boolean isInvalidate() {
        return invalidate;
    }

    /**
     * Sets invalidation flag for this transaction. Default is {@code false}.
     *
     * @param invalidate Flag to set this cache into invalidation-based mode.
     *      Default value is {@code false}.
     */
    public void setInvalidate(boolean invalidate) {
        this.invalidate = invalidate;
    }

    /**
     * Flag indicating if cached values should be additionally stored in serialized
     * form. It's set to true by default.
     *
     * @param storeValueBytes {@code true} if cached values should be additionally
     *      stored in serialized form, {@code false} otherwise.
     */
    public void setStoreValueBytes(boolean storeValueBytes) {
        this.storeValueBytes = storeValueBytes;
    }

    /** {@inheritDoc} */
    @Override public boolean isStoreValueBytes() {
        return storeValueBytes;
    }

    /** {@inheritDoc} */
    @Override public double getRefreshAheadRatio() {
        return refreshAheadRatio;
    }

    /**
     * Sets refresh-ahead ratio for cache entries. Values other than zero
     * specify how soon entries will be auto-reloaded from persistent store prior to
     * expiration.
     *
     * @param refreshAheadRatio Refresh-ahead ratio.
     */
    public void setRefreshAheadRatio(double refreshAheadRatio) {
        this.refreshAheadRatio = refreshAheadRatio;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTmLookup getTransactionManagerLookup() {
        return tmLookup;
    }

    /**
     * Sets look up mechanism for available {@link TransactionManager} implementation, if any.
     *
     * @param tmLookup Lookup implementation that is used to receive JTA transaction manager.
     */
    public void setTransactionManagerLookup(GridCacheTmLookup tmLookup) {
        this.tmLookup = tmLookup;
    }

    /**
     * Sets cache preload mode.
     *
     * @param preloadMode Preload mode.
     */
    public void setPreloadMode(GridCachePreloadMode preloadMode) {
        this.preloadMode = preloadMode;
    }

    /** {@inheritDoc} */
    @Override public GridCachePreloadMode getPreloadMode() {
        return preloadMode;
    }

    /** {@inheritDoc} */
    @Override public int getPreloadBatchSize() {
        return preloadBatchSize;
    }

    /**
     * Sets preload batch size.
     *
     * @param preloadBatchSize Preload batch size.
     */
    public void setPreloadBatchSize(int preloadBatchSize) {
        this.preloadBatchSize = preloadBatchSize;
    }

    /** {@inheritDoc} */
    @Override public String getIndexPath() {
        return idxPath;
    }

    /**
     * Sets file path (absolute or relative to {@code GRIDGAIN_HOME} to store
     * cache indexes.
     *
     * @param idxPath Path to index database, default will be used if null.
     */
    public void setIndexPath(String idxPath) {
        this.idxPath = idxPath;
    }

    /** {@inheritDoc} */
    @Override public boolean isIndexFullClassName() {
        return idxFullClassName;
    }

    /**
     * Flag indicating weather full or simple class names should be used for querying.
     * Simple class name may result in more concise queries, but may not be unique.
     * Default is {@code false}. This property must be set to {@code true} whenever
     * simple class names are not unique.
     *
     * @param idxFullClassName Flag indicating weather full or simple class names should be used for querying.
     */
    public void setIndexFullClassName(boolean idxFullClassName) {
        this.idxFullClassName = idxFullClassName;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheQueryType> getAutoIndexQueryTypes() {
        return autoIndexTypes;
    }

    /**
     * Sets query types to use to auto index values of primitive types. If not empty,
     * then all encountered primitive or boxed types will be auto-indexed at all times
     * for specified query types.
     *
     * @param autoIndexTypes Query types to use to auto index values of primitive types.
     */
    public void setAutoIndexQueryTypes(Collection<GridCacheQueryType> autoIndexTypes) {
        this.autoIndexTypes = autoIndexTypes;
    }

    /** {@inheritDoc} */
    @Override public boolean isIndexFixedTyping() {
        return idxFixedTyping;
    }

    /**
     * Sets fixed typing flag. See {@link #isIndexFixedTyping()} for
     * more information.
     *
     * @param idxFixedTyping {@code True} for fixed typing.
     * @see #isIndexFixedTyping()
     */
    public void setIndexFixedTyping(boolean idxFixedTyping) {
        this.idxFixedTyping = idxFixedTyping;
    }

    /** {@inheritDoc} */
    @Override public boolean isIndexCleanup() {
        return idxCleanup;
    }

    /**
     * Flag indicating whether indexes should be deleted on system shutdown
     * or startup. Default is {@code true}.
     *
     * @param idxCleanup Flag indicating whether indexes should be deleted on stop or start.
     */
    public void setIndexCleanup(boolean idxCleanup) {
        this.idxCleanup = idxCleanup;
    }

    /** {@inheritDoc} */
    @Override public boolean isIndexMemoryOnly() {
        return idxMemOnly;
    }

    /**
     * Flag indicating whether query indexes should be kept only in memory
     * or offloaded on disk as well. Default is {@code false}.
     *
     * @param idxMemOnly Use only memory for indexes if {@code true}.
     */
    public void setIndexMemoryOnly(boolean idxMemOnly) {
        this.idxMemOnly = idxMemOnly;
    }

    /** {@inheritDoc} */
    @Override public int getIndexMaxOperationMemory() {
        return idxMaxOperationMem;
    }

    /**
     * Maximum memory used for delete and insert in bytes. {@code 0} means no limit.
     *
     * @param idxMaxOperationMem Maximum memory used for delete and insert in bytes.
     */
    public void setIndexMaxOperationMemory(int idxMaxOperationMem) {
        this.idxMaxOperationMem = idxMaxOperationMem;
    }

    /** {@inheritDoc} */
    @Override public String getIndexH2Options() {
        return idxH2Opt;
    }

    /**
     * Any additional options for the underlying H2 database used for querying.
     *
     * @param idxH2Opt Addition options for underlying H2 database.
     */
    public void setIndexH2Options(String idxH2Opt) {
        this.idxH2Opt = idxH2Opt;
    }

    /** {@inheritDoc} */
    @Override public long getIndexAnalyzeFrequency() {
        return idxAnalyzeFreq;
    }

    /**
     * Sets frequency of running H2 "ANALYZE" command ({@code 0} to disable).
     *
     * @param idxAnalyzeFreq Frequency in milliseconds.
     */
    public void setIndexAnalyzeFrequency(long idxAnalyzeFreq) {
        this.idxAnalyzeFreq = idxAnalyzeFreq;
    }

    /** {@inheritDoc} */
    @Override public long getIndexAnalyzeSampleSize() {
        return idxAnalyzeSampleSize;
    }

    /**
     * Sets number of samples used to run H2 "ANALYZE" command.
     *
     * @param idxAnalyzeSampleSize Number of samples (db table rows rows).
     */
    public void setIndexAnalyzeSampleSize(long idxAnalyzeSampleSize) {
        this.idxAnalyzeSampleSize = idxAnalyzeSampleSize;
    }

    /** {@inheritDoc} */
    @Override public String getIndexUsername() {
        return idxUser;
    }

    /**
     * Optional username to login to index database.
     *
     * @param idxUser Index database user name.
     */
    public void setIndexUsername(String idxUser) {
        this.idxUser = idxUser;
    }

    /** {@inheritDoc} */
    @Override public String getIndexPassword() {
        return idxPswd;
    }

    /**
     * Optional password to login to index database.
     *
     * @param idxPswd Index database password.
     */
    public void setIndexPassword(String idxPswd) {
        this.idxPswd = idxPswd;
    }

    /** {@inheritDoc} */
    @Override public long getDgcFrequency() {
        return dgcFreq;
    }

    /**
     * Sets frequency in milliseconds for internal distributed garbage collector.
     * Pass {@code 0} to disable distributed garbage collection.
     * <p>
     * If not provided, default value is {@link GridCacheConfiguration#DFLT_DGC_FREQUENCY}.
     *
     * @param dgcFreq Frequency of distributed GC in milliseconds ({@code 0} to disable GC).
     */
    public void setDgcFrequency(long dgcFreq) {
        this.dgcFreq = dgcFreq;
    }

    /** {@inheritDoc} */
    @Override public long getDgcSuspectLockTimeout() {
        return dgcSuspectLockTimeout;
    }

    /**
     * Sets suspect lock timeout in milliseconds for internal distributed garbage collector.
     * If lock's lifetime is greater than the timeout, then lock is considered to be suspicious.
     * <p>
     * If not provided, default value is {@link GridCacheConfiguration#DFLT_DGC_SUSPECT_LOCK_TIMEOUT}.
     *
     * @param dgcSuspectLockTimeout Timeout in milliseconds.
     */
    public void setDgcSuspectLockTimeout(long dgcSuspectLockTimeout) {
        this.dgcSuspectLockTimeout = dgcSuspectLockTimeout;
    }

    /** {@inheritDoc} */
    @Override public boolean isDgcRemoveLocks() {
        return dgcRmvLocks;
    }

    /**
     * Sets DGC remove locks flag.
     *
     * @param dgcRmvLocks {@code True} to remove locks.
     * @see #isDgcRemoveLocks()
     */
    public void setDgcRemoveLocks(boolean dgcRmvLocks) {
        this.dgcRmvLocks = dgcRmvLocks;
    }

    /** {@inheritDoc} */
    @Override public boolean isSynchronousCommit() {
        return syncCommit;
    }

    /**
     * Flag indicating whether nodes on which user transaction completed should wait
     * for the same transaction on remote nodes to complete.
     *
     * @param syncCommit {@code True} in case of synchronous commit.
     */
    public void setSynchronousCommit(boolean syncCommit) {
        this.syncCommit = syncCommit;
    }

    /** {@inheritDoc} */
    @Override public boolean isSynchronousRollback() {
        return syncRollback;
    }

    /**
     * Flag indicating whether nodes on which user transaction was rolled back should wait
     * for the same transaction on remote nodes to complete.
     *
     * @param syncRollback {@code True} in case of synchronous rollback.
     */
    public void setSynchronousRollback(boolean syncRollback) {
        this.syncRollback = syncRollback;
    }

    /** {@inheritDoc} */
    @Override public boolean isSwapEnabled() {
        return swapEnabled;
    }

    /**
     * Flag indicating whether swap storage ise enabled or not.
     *
     * @param swapEnabled {@code true} if swap storage is enabled by default.
     */
    public void setSwapEnabled(boolean swapEnabled) {
        this.swapEnabled = swapEnabled;
    }

    /** {@inheritDoc} */
    @Override public boolean isStoreEnabled() {
        return storeEnabled;
    }

    /**
     *
     * @param storeEnabled {@code true} if store is enabled by default.
     */
    public void setStoreEnabled(boolean storeEnabled) {
        this.storeEnabled = storeEnabled;
    }

    /** {@inheritDoc} */
    @Override public boolean isWriteFromBehindEnabled() {
        return writeFromBehindEnabled;
    }

    /**
     * Sets flag indicating whether write-from-behind is enabled.
     *
     * @param writeFromBehindEnabled {@code true} if write-from-behind is enabled.
     */
    public void setWriteFromBehindEnabled(boolean writeFromBehindEnabled) {
        this.writeFromBehindEnabled = writeFromBehindEnabled;
    }

    /** {@inheritDoc} */
    @Override public int getWriteFromBehindFlushSize() {
        return writeFromBehindFlushSize;
    }

    /**
     * Sets write-from-behind flush size.
     *
     * @param writeFromBehindFlushSize Write-from-behind cache flush size.
     * @see #getWriteFromBehindFlushSize()
     */
    public void setWriteFromBehindFlushSize(int writeFromBehindFlushSize) {
        this.writeFromBehindFlushSize = writeFromBehindFlushSize;
    }

    /** {@inheritDoc} */
    @Override public long getWriteFromBehindFlushFrequency() {
        return writeFromBehindFlushFrequency;
    }

    /**
     * Sets write-from-behind flush frequency.
     *
     * @param writeFromBehindFlushFrequency Write-from-behind flush frequency in milliseconds.
     * @see #getWriteFromBehindFlushFrequency()
     */
    public void setWriteFromBehindFlushFrequency(long writeFromBehindFlushFrequency) {
        this.writeFromBehindFlushFrequency = writeFromBehindFlushFrequency;
    }

    /** {@inheritDoc} */
    @Override public int getWriteFromBehindFlushThreadCount() {
        return writeFromBehindFlushThreadCnt;
    }

    /**
     * Sets flush thread count for write-from-behind cache.
     *
     * @param writeFromBehindFlushThreadCnt Count of flush threads.
     * @see #getWriteFromBehindFlushThreadCount()
     */
    public void setWriteFromBehindFlushThreadCount(int writeFromBehindFlushThreadCnt) {
        this.writeFromBehindFlushThreadCnt = writeFromBehindFlushThreadCnt;
    }

    /** {@inheritDoc} */
    @Override public int getWriteFromBehindBatchSize() {
        return writeFromBehindBatchSize;
    }

    /**
     * Sets maximum batch size for write-from-behind cache.
     *
     * @param writeFromBehindBatchSize Maximum batch size.
     * @see #getWriteFromBehindBatchSize()
     */
    public void setWriteFromBehindBatchSize(int writeFromBehindBatchSize) {
        this.writeFromBehindBatchSize = writeFromBehindBatchSize;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public GridCacheCloner getCloner() {
        return cloner;
    }

    /**
     * Sets cloner to be used if {@link GridCacheFlag#CLONE} flag is set on projection.
     *
     * @param cloner Cloner to use.
     * @see #getCloner()
     */
    public void setCloner(GridCacheCloner cloner) {
        this.cloner = cloner;
    }

    /** {@inheritDoc} */
    @Override public int getAtomicSequenceReserveSize() {
        return seqReserveSize;
    }

    /**
     * Sets default number of sequence values reserved for {@link GridCacheAtomicSequence} instances. After
     * a certain number has been reserved, consequent increments of sequence will happen locally,
     * without communication with other nodes, until the next reservation has to be made.
     *
     * @param seqReserveSize Atomic sequence reservation size.
     * @see #getAtomicSequenceReserveSize()
     */
    public void setAtomicSequenceReserveSize(int seqReserveSize) {
        this.seqReserveSize = seqReserveSize;
    }

    /** {@inheritDoc} */
    @Override public int getPreloadThreadPoolSize() {
        return preloadPoolSize;
    }

    /**
     * Sets size of preloading thread pool. Note that size serves as a hint and implementation
     * may create more threads for preloading than specified here (but never less threads).
     *
     * @param preloadPoolSize Size of preloading thread pool.
     */
    public void setPreloadThreadPoolSize(int preloadPoolSize) {
        this.preloadPoolSize = preloadPoolSize;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K> GridCacheAffinityMapper<K> getAffinityMapper() {
        return (GridCacheAffinityMapper<K>)affMapper;
    }

    /**
     * Sets custom affinity mapper. If not provided, then default implementation
     * will be used. The default behavior is described in
     * {@link GridCacheAffinityMapper} documentation.
     *
     * @param <K> Key type.
     * @param affMapper Affinity mapper.
     */
    public <K> void setAffinityMapper(GridCacheAffinityMapper<K> affMapper) {
        this.affMapper = affMapper;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheConfigurationAdapter.class, this);
    }
}

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
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.affinity.partitioned.*;
import org.gridgain.grid.cache.affinity.replicated.*;
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.cache.eviction.always.*;
import org.gridgain.grid.cache.eviction.lru.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.kernal.processors.cache.datastructures.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.kernal.processors.cache.distributed.replicated.*;
import org.gridgain.grid.kernal.processors.cache.local.*;
import org.gridgain.grid.kernal.processors.cache.query.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import javax.management.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridDeploymentMode.*;
import static org.gridgain.grid.cache.GridCacheConfiguration.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCachePreloadMode.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Cache processor.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheProcessor extends GridProcessorAdapter {
    /** Null cache name. */
    private static final String NULL_NAME = UUID.randomUUID().toString();

    /** */
    private static final String DIST_QUERY_MGR_CLS =
        "org.gridgain.grid.kernal.processors.cache.query.GridCacheDistributedQueryManager";

    /** */
    private static final String LOC_QUERY_MGR_CLS =
        "org.gridgain.grid.kernal.processors.cache.query.GridCacheLocalQueryManager";

    /** */
    private static final String ENT_DATA_STRUCTURES_MGR_CLS =
        "org.gridgain.grid.kernal.processors.cache.datastructures.GridCacheEnterpriseDataStructuresManager";

    /** */
    private static final String CMN_DATA_STRUCTURES_MGR_CLS =
        "org.gridgain.grid.kernal.processors.cache.datastructures.GridCacheCommunityDataStructuresManager";

    /** */
    private final Map<String, GridCacheAdapter<?, ?>> caches;

    /** Map of proxies. */
    private final Map<String, GridCache<?, ?>> proxies;

    /** MBean server. */
    private final MBeanServer mBeanSrv;

    /** Cache MBeans. */
    private final Collection<ObjectName> cacheMBeans = new LinkedList<ObjectName>();

    /**
     * @param ctx Kernal context.
     */
    public GridCacheProcessor(GridKernalContext ctx) {
        super(ctx);

        caches = new ConcurrentHashMap<String, GridCacheAdapter<?, ?>>();

        proxies = new ConcurrentHashMap<String, GridCache<?, ?>>();

        mBeanSrv = ctx.config().getMBeanServer();
    }

    /**
     * @param name Cache name.
     * @return Masked name accounting for {@code nulls}.
     */
    private String maskName(String name) {
        return name == null ? NULL_NAME : name;
    }

    /**
     * @param cfg Initializes cache configuration with proper defaults.
     */
    @SuppressWarnings( {"unchecked"})
    private void initialize(GridCacheConfigurationAdapter cfg) {
        if (cfg.getCacheMode() == null)
            cfg.setCacheMode(DFLT_CACHE_MODE);

        if (cfg.getDefaultTxConcurrency() == null)
            cfg.setDefaultTxConcurrency(DFLT_TX_CONCURRENCY);

        if (cfg.getDefaultTxIsolation() == null)
            cfg.setDefaultTxIsolation(DFLT_TX_ISOLATION);

        if (cfg.getAffinity() == null) {
            if (cfg.getCacheMode() == REPLICATED)
                cfg.setAffinity(new GridCacheReplicatedAffinity());
            else if (cfg.getCacheMode() == PARTITIONED)
                cfg.setAffinity(new GridCachePartitionedAffinity());
            else
                cfg.setAffinity(new LocalAffinity());
        }

        if (cfg.getAffinityMapper() == null)
            cfg.setAffinityMapper(new GridCacheDefaultAffinityMapper());

        if (cfg.getEvictionPolicy() == null)
            cfg.setEvictionPolicy(new GridCacheLruEvictionPolicy(DFLT_CACHE_SIZE));

        if (cfg.getPreloadMode() == null)
            cfg.setPreloadMode(ASYNC);

        if (cfg.getCacheMode() == PARTITIONED) {
            if (!cfg.isNearEnabled()) {
                if (cfg.getNearEvictionPolicy() != null)
                    U.warn(log, "Ignoring near eviction policy since near cache is disabled.");

                cfg.setNearEvictionPolicy(new GridCacheAlwaysEvictionPolicy());
            }
            else {
                GridCacheEvictionPolicy policy = cfg.getNearEvictionPolicy();

                if (policy == null)
                    cfg.setNearEvictionPolicy(new GridCacheLruEvictionPolicy((DFLT_NEAR_SIZE)));
            }
        }
    }

    /**
     * @param cfg Configuration to validate.
     * @throws GridException If failed.
     */
    private void validate(GridCacheConfiguration cfg) throws GridException {
        if (cfg.getCacheMode() == REPLICATED && cfg.getAffinity().getClass().equals(GridCachePartitionedAffinity.class))
            throw new GridException("Cannot start REPLICATED cache with GridCachePartitionedAffinity " +
                "(switch to GridCacheReplicatedAffinity or provide custom affinity) [cacheName=" + cfg.getName() + ']');

        if (cfg.getCacheMode() == PARTITIONED && cfg.getAffinity().getClass().equals(GridCacheReplicatedAffinity.class))
            throw new GridException("Cannot start PARTITIONED cache with GridCacheReplicatedAffinity (switch " +
                "to GridCachePartitionedAffinity or provide custom affinity) [cacheName=" + cfg.getName() + ']');

        if (cfg.getCacheMode() == LOCAL && !cfg.getAffinity().getClass().equals(LocalAffinity.class))
            U.warn(log, "GridCacheAffinity configuration parameter will be ignored for local cache [cacheName=" +
                cfg.getName() + ']');

        if (cfg.getPreloadMode() != NONE) {
            assertParameter(cfg.getPreloadThreadPoolSize() > 0, "preloadThreadPoolSize > 0");
            assertParameter(cfg.getPreloadBatchSize() > 0, "preloadBatchSize > 0");
        }

        if (!cfg.isTxSerializableEnabled() && cfg.getDefaultTxIsolation() == SERIALIZABLE)
            U.warn(log,
                "Serializable transactions are disabled while default transaction isolation is SERIALIZABLE " +
                    "(most likely misconfiguration - either update 'isTxSerializableEnabled' or " +
                    "'defaultTxIsolationLevel' properties)",
                "Serializable transactions are disabled while default transaction isolation is SERIALIZABLE.");

        if (cfg.isWriteFromBehindEnabled()) {
            if (cfg.getStore() == null)
                throw new GridException("Cannot enable write-from-behind cache (cache store is not provided): " +
                    "cacheName=" + cfg.getName());

            assertParameter(cfg.getWriteFromBehindBatchSize() > 0, "writeFromBehindBatchSize > 0");
            assertParameter(cfg.getWriteFromBehindFlushSize() >= 0, "writeFromBehindFlushSize >= 0");
            assertParameter(cfg.getWriteFromBehindFlushFrequency() >= 0, "writeFromBehindFlushFrequency >= 0");
            assertParameter(cfg.getWriteFromBehindFlushThreadCount() > 0, "writeFromBehindFlushThreadCount > 0");

            if (cfg.getWriteFromBehindFlushSize() == 0 && cfg.getWriteFromBehindFlushFrequency() == 0)
                throw new GridException("Cannot set both 'writeFromBehindFlushFrequency' and " +
                    "'writeFromBehindFlushSize' parameters to 0: cacheName=" + cfg.getName());
        }
    }

    /**
     * @param ctx Context.
     * @return DHT managers.
     */
    private Iterable<GridCacheManager> dhtManagers(GridCacheContext ctx) {
        GridCacheQueryManager qryMgr = ctx.queries();

        return qryMgr != null ?
            F.asList(ctx.mvcc(), ctx.events(), ctx.tm(), ctx.swap(), ctx.dgc(), ctx.evicts(), qryMgr) :
            F.asList(ctx.mvcc(), ctx.events(), ctx.tm(), ctx.swap(), ctx.dgc(), ctx.evicts());
    }

    /**
     * @param ctx Context.
     * @return Managers present in both, DHT and Near caches.
     */
    private Collection<GridCacheManager> dhtExcludes(GridCacheContext ctx) {
        GridCacheQueryManager qryMgr = ctx.queries();

        return ctx.config().getCacheMode() != PARTITIONED ? Collections.<GridCacheManager>emptyList() :
            qryMgr != null ? F.asList(ctx.dgc(), qryMgr) : F.<GridCacheManager>asList(ctx.dgc());
    }

    /**
     * @param cfg Configuration.
     * @throws GridException If failed to inject.
     */
    private void prepare(GridCacheConfiguration cfg) throws GridException {
        prepare(cfg, cfg.getEvictionPolicy(), false);
        prepare(cfg, cfg.getNearEvictionPolicy(), true);
        prepare(cfg, cfg.getAffinity(), false);
        prepare(cfg, cfg.getAffinityMapper(), false);
        prepare(cfg, cfg.getTransactionManagerLookup(), false);
        prepare(cfg, cfg.getCloner(), false);
        prepare(cfg, cfg.getStore(), false);
    }

    /**
     * @param cfg Cache configuration.
     * @param rsrc Resource.
     * @param near Near flag.
     * @throws GridException If failed.
     */
    private void prepare(GridCacheConfiguration cfg, Object rsrc, boolean near) throws GridException {
        if (rsrc != null) {
            ctx.resource().injectGeneric(rsrc);

            registerMbean(rsrc, cfg.getName(), near);
        }
    }

    /**
     * @param cfg Configuration.
     */
    private void cleanup(GridCacheConfiguration cfg) {
        cleanup(cfg, cfg.getEvictionPolicy(), false);
        cleanup(cfg, cfg.getNearEvictionPolicy(), true);
        cleanup(cfg, cfg.getAffinity(), false);
        cleanup(cfg, cfg.<Object>getAffinityMapper(), false);
        cleanup(cfg, cfg.getTransactionManagerLookup(), false);
        cleanup(cfg, cfg.getCloner(), false);
        cleanup(cfg, cfg.getStore(), false);
    }

    /**
     * @param cfg Cache configuration.
     * @param rsrc Resource.
     * @param near Near flag.
     */
    private void cleanup(GridCacheConfiguration cfg, Object rsrc, boolean near) {
        if (rsrc != null) {
            unregisterMbean(rsrc, cfg.getName(), near);

            try {
                ctx.resource().cleanupGeneric(rsrc);
            }
            catch (GridException e) {
                U.error(log, "Failed to cleanup resource: " + rsrc, e);
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public void start() throws GridException {
        if (ctx.config().isDaemon())
            return;

        GridDeploymentMode depMode = ctx.config().getDeploymentMode();

        if (!F.isEmpty(ctx.config().getCacheConfiguration()))
            if (depMode != CONTINUOUS && depMode != SHARED)
                U.warn(log, "Deployment mode for cache is not CONTINUOUS or SHARED " +
                    "(it is recommended that you change deployment mode and restart): " + depMode);

        for (GridCacheConfiguration c : ctx.config().getCacheConfiguration()) {
            GridCacheConfigurationAdapter cfg = new GridCacheConfigurationAdapter(c);

            // Initialize defaults.
            initialize(cfg);

            validate(cfg);

            prepare(cfg);

            GridCacheMvccManager mvccMgr = new GridCacheMvccManager();
            GridCacheTxManager tm = new GridCacheTxManager();
            GridCacheVersionManager verMgr = new GridCacheVersionManager();
            GridCacheEventManager evtMgr = new GridCacheEventManager();
            GridCacheSwapManager swapMgr = new GridCacheSwapManager(cfg.getCacheMode() != PARTITIONED);
            GridCacheDgcManager dgcMgr = new GridCacheDgcManager();
            GridCacheDeploymentManager depMgr = new GridCacheDeploymentManager();
            GridCacheEvictionManager evictMgr = new GridCacheEvictionManager();
            GridCacheQueryManager qryMgr = queryManager(cfg);
            GridCacheIoManager ioMgr = new GridCacheIoManager();
            GridCacheDataStructuresManager dataStructuresMgr = dataStructuresManager();

            GridCacheStore store = cacheStore(ctx.gridName(), cfg);

            GridCacheContext<?, ?> cacheCtx = new GridCacheContext(
                ctx,
                cfg,
                store,

                /*
                 * Managers in starting order!
                 * ===========================
                 */
                mvccMgr,
                verMgr,
                evtMgr,
                swapMgr,
                depMgr,
                evictMgr,
                ioMgr,
                qryMgr,
                dgcMgr,
                tm,
                dataStructuresMgr);

            GridCacheAdapter cache = null;

            switch (cfg.getCacheMode()) {
                case LOCAL: {
                    cache = new GridLocalCache(cacheCtx);

                    break;
                }
                case REPLICATED: {
                    cache = new GridReplicatedCache(cacheCtx);

                    break;
                }
                case PARTITIONED: {
                    cache = new GridNearCache(cacheCtx);

                    break;
                }

                default: {
                    assert false : "Invalid cache mode: " + cfg.getCacheMode();
                }
            }

            cacheCtx.cache(cache);

            if (caches.containsKey(maskName(cfg.getName()))) {
                String cacheName = cfg.getName();

                if (cacheName != null)
                    throw new GridException("Duplicate cache name found (check configuration and " +
                        "assign unique name to each cache): " + cacheName);
                else
                    throw new GridException("Default cache has already been configured (check configuration and " +
                        "assign unique name to each cache).");
            }

            caches.put(maskName(cfg.getName()), cache);

            // Start managers.
            for (GridCacheManager mgr : F.view(cacheCtx.managers(), F.notContains(dhtExcludes(cacheCtx))))
                mgr.start(cacheCtx);

            /*
             * Start DHT cache.
             * ================
             */
            if (cfg.getCacheMode() == PARTITIONED) {
                /*
                 * Specifically don't create the following managers
                 * here and reuse the one from Near cache:
                 * 1. GridCacheVersionManager
                 * 2. GridCacheIoManager
                 * 3. GridCacheDeploymentManager
                 * 4. GridCacheQueryManager (note, that we start it for DHT cache though).
                 * 5. GridCacheDgcManager
                 * ===============================================
                 */
                mvccMgr = new GridCacheMvccManager();
                tm = new GridCacheTxManager();
                swapMgr = new GridCacheSwapManager(true);
                evictMgr = new GridCacheEvictionManager();
                evtMgr = new GridCacheEventManager();

                cacheCtx = new GridCacheContext(
                    ctx,
                    cfg,
                    cfg.getStore(),

                    /*
                     * Managers in starting order!
                     * ===========================
                     */
                    mvccMgr,
                    verMgr,
                    evtMgr,
                    swapMgr,
                    depMgr,
                    evictMgr,
                    ioMgr,
                    qryMgr,
                    dgcMgr,
                    tm,
                    dataStructuresMgr);

                assert cache instanceof GridNearCache;

                GridNearCache near = (GridNearCache)cache;
                GridDhtCache dht = new GridDhtCache(cacheCtx);

                cacheCtx.cache(dht);

                near.dht(dht);
                dht.near(near);

                // Start managers.
                for (GridCacheManager mgr : dhtManagers(cacheCtx))
                    mgr.start(cacheCtx);

                dht.start();

                if (log.isDebugEnabled())
                    log.debug("Started DHT cache: " + dht.name());
            }

            if (store instanceof GridCacheWriteFromBehindStore)
                ((GridCacheWriteFromBehindStore)store).start();

            cache.start();

            if (log.isInfoEnabled())
                log.info("Started cache [name=" + cfg.getName() + ", mode=" + cfg.getCacheMode() + ']');
        }

        for (Map.Entry<String, GridCacheAdapter<?, ?>> e : caches.entrySet()) {
            GridCacheAdapter cache = e.getValue();

            proxies.put(e.getKey(), new GridCacheProxyImpl(cache.context(), null));
        }

        if (log.isDebugEnabled())
            log.debug("Started cache processor.");
    }

    /**
     * @param rmt Node.
     * @throws GridException If check failed.
     */
    private void checkCache(GridNode rmt) throws GridException {
        GridCacheAttributes[] localAttrs = U.cacheAttributes(ctx.discovery().localNode());

        for (GridCacheAttributes a1 : U.cacheAttributes(rmt)) {
            for (GridCacheAttributes a2 : localAttrs) {
                if (F.eq(a1.cacheName(), a2.cacheName())) {
                    if (a1.cacheMode() != a2.cacheMode())
                        throw new GridException("Cache mode mismatch (fix cache mode in configuration or specify " +
                            "empty cache configuration list if default cache should not be started) [cacheName=" +
                            a1.cacheName() + ", localCacheMode=" + a2.cacheMode() +
                            ", remoteCacheMode=" + a1.cacheMode() + ", rmtNodeId=" + rmt.id() + ']');

                    if (a1.cachePreloadMode() != a2.cachePreloadMode() && a1.cacheMode() != LOCAL)
                        throw new GridException("Cache preload mode mismatch (fix cache preload mode in " +
                            "configuration or specify empty cache configuration list if default cache should " +
                            "not be started) [cacheName=" + a1.cacheName() +
                            ", localCachePreloadMode=" + a2.cachePreloadMode() +
                            ", remoteCachePreloadMode=" + a1.cachePreloadMode() +
                            ", rmtNodeId=" + rmt.id() + ']');

                    if (!F.eq(a1.cacheAffinityClassName(), a2.cacheAffinityClassName()))
                        throw new GridException(U.compact("Cache affinity mismatch (fix cache affinity in " +
                            "configuration or specify empty cache configuration list if default cache should " +
                            "not be started) [cacheName=" + a1.cacheName() +
                            ", localCacheAffinity=" + a2.cacheAffinityClassName() +
                            ", remoteCacheAffinity=" + a1.cacheAffinityClassName() + ", rmtNodeId=" + rmt.id() + ']'));
                }
            }
        }
    }

    /**
     * @param cache Cache.
     * @throws GridException If failed.
     */
    private void onKernalStart(GridCacheAdapter<?, ?> cache) throws GridException {
        GridCacheContext<?, ?> ctx = cache.context();

        // Start DHT cache as well.
        if (ctx.config().getCacheMode() == PARTITIONED) {
            GridDhtCache dht = ctx.near().dht();

            GridCacheContext<?, ?> dhtCtx = dht.context();

            for (GridCacheManager mgr : dhtManagers(dhtCtx))
                mgr.onKernalStart();

            dht.onKernalStart();

            if (log.isDebugEnabled())
                log.debug("Executed onKernalStart() callback for DHT cache: " + dht.name());
        }

        for (GridCacheManager mgr : F.view(ctx.managers(), F.notContains(dhtExcludes(ctx))))
            mgr.onKernalStart();

        cache.onKernalStart();

        if (log.isDebugEnabled())
            log.debug("Executed onKernalStart() callback for cache [name=" + cache.name() + ", mode=" +
                cache.configuration().getCacheMode() + ']');
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        if (ctx.config().isDaemon())
            return;

        for (GridNode n : ctx.discovery().remoteNodes())
            checkCache(n);

        for (GridCacheAdapter<?, ?> cache : caches.values())
            onKernalStart(cache);

        for (GridCacheAdapter<?, ?> cache : caches.values()) {
            try {
                ObjectName mb = U.registerCacheMBean(mBeanSrv, ctx.gridName(), cache.name(), "Cache",
                    new GridCacheMBeanAdapter(cache.context()), GridCacheMBean.class);

                cacheMBeans.add(mb);

                if (log.isDebugEnabled())
                    log.debug("Registered cache MBean: " + mb);
            }
            catch (JMException ex) {
                U.error(log, "Failed to register cache MBean.", ex);
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public void onKernalStop(boolean cancel, boolean wait) {
        if (ctx.config().isDaemon())
            return;

        if (!F.isEmpty(cacheMBeans))
            for (ObjectName mb : cacheMBeans) {
                try {
                    mBeanSrv.unregisterMBean(mb);

                    if (log.isDebugEnabled())
                        log.debug("Unregistered cache MBean: " + mb);
                }
                catch (JMException e) {
                    U.error(log, "Failed to unregister cache MBean: " + mb, e);
                }
            }

        for (GridCacheAdapter<?, ?> cache : caches.values()) {
            GridCacheContext ctx = cache.context();

            if (ctx.config().getCacheMode() == PARTITIONED) {
                GridDhtCache dht = ctx.near().dht();

                if (dht != null) {
                    GridCacheContext<?, ?> dhtCtx = dht.context();

                    for (GridCacheManager mgr : dhtManagers(dhtCtx))
                        mgr.onKernalStop();

                    dht.onKernalStop();
                }
            }

            List<GridCacheManager> mgrs = ctx.managers();

            Collection<GridCacheManager> excludes = dhtExcludes(ctx);

            // Reverse order.
            for (ListIterator<GridCacheManager> it = mgrs.listIterator(mgrs.size()); it.hasPrevious();) {
                GridCacheManager mgr = it.previous();

                if (!excludes.contains(mgr))
                    mgr.onKernalStop();
            }

            cache.onKernalStop();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        if (ctx.config().isDaemon())
            return;

        for (GridCacheAdapter<?, ?> cache : caches.values()) {
            cache.stop();

            GridCacheContext ctx = cache.context();

            // Check whether write-from-behind is configured.
            GridCacheStore store = ctx.cacheStore();

            if (store instanceof GridCacheWriteFromBehindStore)
                ((GridCacheWriteFromBehindStore)store).stop();

            if (ctx.config().getCacheMode() == PARTITIONED) {
                GridDhtCache dht = ctx.near().dht();

                // Check whether dht cache has been started.
                if (dht != null) {
                    dht.stop();

                    GridCacheContext<?, ?> dhtCtx = dht.context();

                    for (GridCacheManager mgr : dhtManagers(dhtCtx))
                        mgr.stop(cancel, wait);
                }
            }

            List<GridCacheManager> mgrs = ctx.managers();

            Collection<GridCacheManager> excludes = dhtExcludes(ctx);

            // Reverse order.
            for (ListIterator<GridCacheManager> it = mgrs.listIterator(mgrs.size()); it.hasPrevious();) {
                GridCacheManager mgr = it.previous();

                if (!excludes.contains(mgr))
                    mgr.stop(cancel, wait);
            }

            cleanup(ctx.config());

            if (log.isInfoEnabled())
                log.info("Stopped cache: " + cache.name());
        }

        if (log.isDebugEnabled())
            log.debug("Stopped cache processor.");
    }

    /**
     * @param cfg Cache configuration.
     * @return Query manager.
     * @throws GridException In case of error.
     */
    @Nullable private GridCacheQueryManager queryManager(GridCacheConfiguration cfg) throws GridException {
        if (cfg.getCacheMode() == LOCAL) {
            try {
                Class cls = Class.forName(LOC_QUERY_MGR_CLS);

                if (log.isDebugEnabled())
                    log.debug("Local query manager found: " + cls);

                return (GridCacheQueryManager)cls.newInstance();
            }
            catch (Exception ex) {
                throw new GridException("Failed to find local query manager.", ex);
            }
        }
        else {
            try {
                Class cls = Class.forName(DIST_QUERY_MGR_CLS);

                if (log.isDebugEnabled())
                    log.debug("Distributed query manager found: " + cls);

                return (GridCacheQueryManager)cls.newInstance();
            }
            catch (Exception ignored) {
                // No-op.
            }
        }

        return null;
    }

    /**
     * @return Data structures manager.
     * @throws GridException In case of error.
     */
    private GridCacheDataStructuresManager dataStructuresManager() throws GridException {
        String clsName = U.isEnterprise() ? ENT_DATA_STRUCTURES_MGR_CLS : CMN_DATA_STRUCTURES_MGR_CLS;

        try {
            Class cls = Class.forName(clsName);

            if (log.isDebugEnabled())
                log.debug("Data structures manager found." + cls);

            return (GridCacheDataStructuresManager)cls.newInstance();
        }
        catch (Exception ex) {
            throw new GridException("Failed to find data structures manager.", ex);
        }
    }

    /**
     * @return Last data version.
     */
    public long lastDataVersion() {
        long max = 0;

        for (GridCacheAdapter<?, ?> cache : caches.values()) {
            GridCacheContext<?, ?> ctx = cache.context();

            if (ctx.versions().last().order() > max)
                max = ctx.versions().last().order();

            if (ctx.isNear()) {
                ctx = ctx.near().dht().context();

                if (ctx.versions().last().order() > max)
                    max = ctx.versions().last().order();
            }
        }

        assert caches.isEmpty() || max > 0;

        return max;
    }

    /**
     * @param <K> type of keys.
     * @param <V> type of values.
     * @return Default cache.
     */
    public <K, V> GridCache<K, V> cache() {
        return cache(null);
    }

    /**
     * @param <K> type of keys.
     * @param <V> type of values.
     * @return Default cache.
     */
    public <K, V> GridCacheAdapter<K, V> internalCache() {
        return internalCache(null);
    }

    /**
     * @param name Cache name.
     * @param <K> type of keys.
     * @param <V> type of values.
     * @return Cache instance for given name.
     */
    @SuppressWarnings("unchecked")
    public <K, V> GridCache<K, V> cache(@Nullable String name) {
        if (log.isDebugEnabled())
            log.debug("Getting cache for name: " + name);

        return (GridCache<K, V>)proxies.get(maskName(name));
    }

    /**
     * @return All configured cache instances.
     */
    public Collection<GridCache<?, ?>> caches() {
        return proxies.values();
    }

    /**
     * @param name Cache name.
     * @param <K> type of keys.
     * @param <V> type of values.
     * @return Cache instance for given name.
     */
    @SuppressWarnings("unchecked")
    public <K, V> GridCacheAdapter<K, V> internalCache(@Nullable String name) {
        if (log.isDebugEnabled()) {
            log.debug("Getting internal cache adapter: " + name);
        }

        return (GridCacheAdapter<K, V>)caches.get(maskName(name));
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        X.println(">>> ");

        for (GridCacheAdapter c : caches.values()) {
            X.println(">>> Cache memory stats [grid=" + ctx.gridName() + ", cache=" + c.name() + ']');

            c.context().printMemoryStats();
        }
    }

    /**
     * Callback invoked by deployment manager for whenever a class loader
     * gets undeployed.
     *
     * @param leftNodeId Left node ID.
     * @param ldr Class loader.
     */
    public void onUndeployed(@Nullable UUID leftNodeId, ClassLoader ldr) {
        if (!ctx.isStopping())
            for (GridCacheAdapter<?, ?> cache : caches.values())
                cache.onUndeploy(leftNodeId, ldr);
    }

    /**
     * Registers MBean for cache components.
     *
     * @param o Cache component.
     * @param cacheName Cache name.
     * @param near Near flag.
     * @throws GridException If registration failed.
     */
    @SuppressWarnings( {"unchecked"})
    private void registerMbean(Object o, @Nullable String cacheName, boolean near)
        throws GridException {
        assert o != null;

        MBeanServer srvr = ctx.config().getMBeanServer();

        assert srvr != null;

        cacheName = U.maskCacheName(cacheName);

        cacheName = near ? cacheName + "-near" : cacheName;

        for (Class<?> itf : o.getClass().getInterfaces()) {
            if (itf.getName().endsWith("MBean")) {
                try {
                    U.registerCacheMBean(srvr, ctx.gridName(), cacheName, o.getClass().getName(), o,
                        (Class<Object>)itf);
                }
                catch (JMException e) {
                    throw new GridException("Failed to register MBean for component: " + o, e);
                }

                break;
            }
        }
    }

    /**
     * Unregisters MBean for cache components.
     *
     * @param o Cache component.
     * @param cacheName Cache name.
     * @param near Near flag.
     */
    private void unregisterMbean(Object o, @Nullable String cacheName, boolean near) {
        assert o != null;

        MBeanServer srvr = ctx.config().getMBeanServer();

        assert srvr != null;

        cacheName = U.maskCacheName(cacheName);

        cacheName = near ? cacheName + "-near" : cacheName;

        for (Class<?> itf : o.getClass().getInterfaces()) {
            if (itf.getName().endsWith("MBean")) {
                try {
                    srvr.unregisterMBean(U.makeCacheMBeanName(ctx.gridName(), cacheName, o.getClass().getName()));
                }
                catch (JMException e) {
                    log.error("Failed to unregister MBean for component: " + o, e);
                }

                break;
            }
        }
    }

    /**
     * Creates a wrapped cache store if write from behind cache is configured.
     *
     * @param gridName Name of grid.
     * @param cfg Cache configuration.
     * @return Instance if {@link GridCacheWriteFromBehindStore} if write from behind store is configured,
     *         or user-defined cache store.
     */
    @SuppressWarnings({"unchecked"})
    private GridCacheStore cacheStore(String gridName, GridCacheConfiguration cfg) {
        if (cfg.getStore() == null || !cfg.isWriteFromBehindEnabled())
            return cfg.getStore();

        GridCacheWriteFromBehindStore store = new GridCacheWriteFromBehindStore(gridName, log, cfg.getStore(),
            cfg.getName());

        store.setFlushSize(cfg.getWriteFromBehindFlushSize());
        store.setFlushThreadCount(cfg.getWriteFromBehindFlushThreadCount());
        store.setFlushFrequency(cfg.getWriteFromBehindFlushFrequency());
        store.setBatchSize(cfg.getWriteFromBehindBatchSize());

        return store;
    }

    /**
     *
     */
    private static class LocalAffinity implements GridCacheAffinity<Object> {
        /** {@inheritDoc} */
        @Override public Collection<GridRichNode> nodes(int partition, Collection<GridRichNode> nodes) {
            for (GridRichNode n : nodes)
                if (n.isLocal())
                    return Arrays.asList(n);

            throw new GridRuntimeException("Local node is not included into affinity nodes for 'LOCAL' cache");
        }

        /** {@inheritDoc} */
        @Override public void reset() {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public int partitions() {
            return 1;
        }

        /** {@inheritDoc} */
        @Override public int partition(Object key) {
            return 0;
        }
    }
}

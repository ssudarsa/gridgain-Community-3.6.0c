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
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.processors.cache.query.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Deployment manager for cache.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheDeploymentManager<K, V> extends GridCacheManager<K, V> {
    /** Node filter. */
    private GridPredicate<GridNode> nodeFilter;

    /** GridGain class loader. */
    private ClassLoader ggLdr = getClass().getClassLoader();

    /** System class loader. */
    private ClassLoader sysLdr = U.detectClassLoader(Integer.class);

    /** Cache class loader */
    private volatile ClassLoader ldr;

    /** Undeployed class loaders. */
    private final Collection<GridUuid> deadClsLdrs = new GridBoundedLinkedHashSet<GridUuid>(1024);

    /** Undeploys. */
    private final ConcurrentLinkedQueue<CA> undeploys = new ConcurrentLinkedQueue<CA>();

    /** Per-thread deployment context. */
    private ThreadLocal<GridTupleV> depBean = new GridThreadLocalEx<GridTupleV>() {
        @Override protected GridTupleV initialValue() {
            return new GridTupleV(5);
        }
    };

    /** Local deployment. */
    private final AtomicReference<GridDeployment> locDep = new AtomicReference<GridDeployment>();

    /** {@inheritDoc} */
    @Override public void start0() throws GridException {
        ldr = new CacheClassLoader();

        nodeFilter = new P1<GridNode>() {
            @Override public boolean apply(GridNode node) {
                return U.hasCache(node, cctx.namex());
            }
        };
    }

    /**
     * @return Local-only class loader.
     */
    public ClassLoader localLoader() {
        GridDeployment dep = locDep.get();

        return dep == null ? ggLdr : dep.classLoader();
    }

    /**
     * Gets distributed class loader. Note that
     * {@link #p2pContext(UUID, GridUuid, String, GridDeploymentMode, Map)} must be
     * called from the same thread prior to using this class loader, or the
     * loading may happen for the wrong node or context.
     *
     * @return Cache class loader.
     */
    public ClassLoader globalLoader() {
        return ldr;
    }

    /**
     * Callback on method enter.
     */
    public void onEnter() {
        ClassLoader ldr = Thread.currentThread().getContextClassLoader();

        if (cctx.gridDeploy().isGlobalLoader(ldr)) {
            GridDeploymentInfo info = (GridDeploymentInfo)ldr;

            UUID senderId = F.first(info.participants().keySet());

            p2pContext(senderId, info.classLoaderId(), info.userVersion(), info.deployMode(), info.participants());
        }
    }

    /**
     * Undeploy all queued up closures.
     */
    public void unwind() {
        int cnt = 0;

        for (CA c = undeploys.poll(); c != null; c = undeploys.poll()) {
            c.apply();

            cnt++;
        }

        if (log.isDebugEnabled())
            log.debug("Unwinded undeploys count: " + cnt);
    }

    /**
     * Undeploys given class loader.
     *
     * @param leftNodeId Left node ID.
     * @param ldr Class loader to undeploy.
     */
    public void onUndeploy(@Nullable UUID leftNodeId, final ClassLoader ldr) {
        assert ldr != null;

        GridCacheAdapter<K, V> cache = cctx.cache();

        if (log.isDebugEnabled()) {
            log.debug("Received onUndeploy() request [ldr=" + ldr + ", cctx=" + cctx +
                ", cacheCls=" + cctx.cache().getClass().getSimpleName() + ", cacheSize=" + cache.size() + ']');
        }

        undeploys.add(new CA() {
            @Override public void apply() {
                GridCacheAdapter<K, V> cache = cctx.cache();

                Collection<K> keys = new LinkedList<K>(
                    cache.keySet(cctx.vararg(
                        new P1<GridCacheEntry<K, V>>() {
                            @Override public boolean apply(GridCacheEntry<K, V> e) {
                                return cctx.isNear() ?
                                    undeploy(e, cctx.near()) ||
                                        undeploy(e, cctx.near().dht()) : undeploy(e, cctx.cache());
                            }

                            private boolean undeploy(GridCacheEntry<K, V> e, GridCacheAdapter<K, V> cache) {
                                K k = e.getKey();

                                GridCacheEntryEx<K, V> entry = cache.peekEx(e.getKey());

                                if (entry == null)
                                    return false;

                                V v;

                                try {
                                    v = entry.peek(GridCachePeekMode.GLOBAL, CU.<K, V>empty());
                                }
                                catch (GridCacheEntryRemovedException ignore) {
                                    return false;
                                }

                                assert k != null : "Key cannot be null for cache entry: " + e;

                                ClassLoader keyLdr = U.detectObjectClassLoader(k);
                                ClassLoader valLdr = U.detectObjectClassLoader(v);

                                boolean res = F.eq(ldr, keyLdr) || F.eq(ldr, valLdr);

                                if (res) {
                                    try {
                                        cctx.swap().remove(k, entry.getOrMarshalKeyBytes());
                                    }
                                    catch (GridException ex) {
                                        U.error(log, "Failed to undeploy swapped entry: " + e, ex);

                                        return false;
                                    }
                                }

                                if (log.isDebugEnabled()) {
                                    log.debug("Finished examining entry [entryCls=" + e.getClass() +
                                        ", key=" + k + ", keyCls=" + k.getClass() +
                                        ", valCls=" + (v != null ? v.getClass() : "null") +
                                        ", keyLdr=" + keyLdr + ", valLdr=" + valLdr + ", res=" + res + ']');
                                }

                                return res;
                            }
                        }))
                );

                if (log.isDebugEnabled())
                    log.debug("Finished searching keys for undeploy [keysCnt=" + keys.size() + ']');

                cache.clearAll(keys, true);

                if (cctx.isNear())
                    cctx.near().dht().clearAll(keys, true);

                // TODO: do it properly accounting relations between class loaders.
                GridCacheQueryManager<K, V> qryMgr = cctx.queries();

                if (qryMgr != null)
                    qryMgr.onUndeploy(ldr);
                else if (log.isDebugEnabled())
                    log.debug("Query manager is null.");

                if (log.isInfoEnabled())
                    log.info("Undeployed all entries (if any) for obsolete class loader [undeployCnt=" + keys.size() +
                        ", clsLdr=" + ldr.getClass().getName() + ']');

                if (ldr instanceof GridDeploymentInfo)
                    deadClsLdrs.add(((GridDeploymentInfo)ldr).classLoaderId());

                // Avoid class caching issues inside classloader.
                GridCacheDeploymentManager.this.ldr = new CacheClassLoader();
            }
        });
    }

    /**
     * @param ldr Class loader to check (may be null).
     * @return {@code True} if class loader has been recently undeployed.
     */
    public boolean deadClassLoader(@Nullable ClassLoader ldr) {
        return ldr instanceof GridDeploymentInfo && deadClsLdrs.contains(((GridDeploymentInfo)ldr).classLoaderId());
    }

    /**
     * @param senderId Sender node ID.
     * @param ldrId Loader ID.
     * @param userVer User version.
     * @param mode Deployment mode.
     * @param participants Node participants.
     */
    public void p2pContext(UUID senderId, GridUuid ldrId, String userVer, GridDeploymentMode mode,
        Map<UUID, GridTuple2<GridUuid, Long>> participants) {
        depBean.get().set(senderId, ldrId, userVer, mode, participants);
    }

    /**
     * Register local classes.
     *
     * @param objs Objects to register.
     * @throws GridException If registration failed.
     */
    public void registerClasses(Object... objs) throws GridException {
        registerClasses(F.asList(objs));
    }

    /**
     * Register local classes.
     *
     * @param objs Objects to register.
     * @throws GridException If registration failed.
     */
    @SuppressWarnings({"unchecked"})
    public void registerClasses(Iterable<?> objs) throws GridException {
        if (objs != null)
            for (Object o : objs)
                registerClass(o);
    }

    /**
     * @param obj Object whose class to register.
     * @throws GridException If failed.
     */
    public void registerClass(Object obj) throws GridException {
        if (obj == null)
            return;

        if (obj instanceof GridPeerDeployAware) {
            GridPeerDeployAware p = (GridPeerDeployAware)obj;

            registerClass(p.deployClass(), p.classLoader());
        }
        else
            registerClass(obj instanceof Class ? (Class)obj : obj.getClass());
    }

    /**
     * @param cls Class to register.
     * @throws GridException If failed.
     */
    public void registerClass(Class<?> cls) throws GridException {
        if (cls == null)
            return;

        registerClass(cls, U.detectClassLoader(cls));
    }

    /**
     * @param cls Class to register.
     * @param ldr Class loader.
     * @throws GridException If registration failed.
     */
    public void registerClass(Class<?> cls, ClassLoader ldr) throws GridException {
        if (cls == null)
            return;

        if (ldr == null)
            ldr = U.detectClassLoader(cls);

        // Don't register remote class loaders.
        if (U.p2pLoader(ldr))
            return;

        if (locDep.get() == null || !ldr.equals(locDep.get().classLoader())) {
            while (true) {
                GridDeployment dep = locDep.get();

                // Don't register remote class loaders.
                if (dep != null && !dep.isLocal())
                    return;

                if (dep != null) {
                    ClassLoader curLdr = dep.classLoader();

                    if (curLdr.equals(ldr))
                        break;

                    // If current deployment is either system loader or GG loader,
                    // then we don't check it, as new loader is most likely wider.
                    if (!curLdr.equals(sysLdr) && !curLdr.equals(ggLdr))
                        if (dep.deployedClass(cls.getName()) != null)
                            // Local deployment can load this class already, so no reason
                            // to look for another class loader.
                            break;
                }

                GridDeployment newDep = cctx.gridDeploy().deploy(cls, ldr);

                if (newDep != null) {
                    if (dep != null) {
                        // Check new deployment.
                        if (newDep.deployedClass(dep.sampleClassName()) != null) {
                            if (locDep.compareAndSet(dep, newDep))
                                break; // While loop.
                        }
                        else
                            throw new GridException("Encountered incompatible class loaders for cache " +
                                "[class1=" + cls.getName() + ", class2=" + dep.sampleClassName() + ']');
                    }
                    else if (locDep.compareAndSet(null, newDep))
                        break; // While loop.
                }
            }
        }
    }

    /**
     * Prepares deployable object.
     *
     * @param deployable Deployable object.
     */
    public void prepare(GridCacheDeployable deployable) {
        GridDeployment dep = locDep.get();

        // Only set deployment info if it was not set automatically.
        if (dep != null && deployable.deployInfo() == null)
            deployable.prepare(new GridDeploymentInfoBean(dep));

        if (log.isDebugEnabled())
            log.debug("Prepared grid cache deployable [locDep=" + dep + ", deployable=" + deployable + ']');
    }

    /** {@inheritDoc} */
    @Override protected void printMemoryStats() {
        X.println(">>> ");
        X.println(">>> Cache deployment manager memory stats [grid=" + cctx.gridName() +
            ", cache=" + cctx.name() + ']');
        X.println(">>>   Undeploys: " + undeploys.size());
        X.println(">>>   Dead class loaders: " + deadClsLdrs.size());
    }

    /**
     * Cache class loader.
     */
    private class CacheClassLoader extends ClassLoader {
        /**
         * Sets context class loader as parent.
         */
        private CacheClassLoader() {
            super(U.detectClassLoader(GridCacheDeploymentManager.class));
        }

        /** {@inheritDoc} */
        @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
            // Always delegate to deployment manager.
            return findClass(name);
        }

        /** {@inheritDoc} */
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            GridTupleV t = depBean.get();

            assert t != null;

            UUID senderId = t.get(0);
            GridUuid ldrId = t.get(1);
            String userVer = t.get(2);
            GridDeploymentMode mode = t.get(3);
            Map<UUID, GridTuple2<GridUuid, Long>> participants = t.get(4);

            GridDeployment d = senderId == null ? cctx.gridDeploy().getLocalDeployment(name) :
                cctx.gridDeploy().getGlobalDeployment(
                    mode,
                    name,
                    name,
                    -1,
                    userVer,
                    senderId,
                    ldrId,
                    participants,
                    nodeFilter
                );

            if (d != null) {
                Class cls = d.deployedClass(name);

                if (cls != null)
                    return cls;
            }

            throw new ClassNotFoundException("Failed to load class [name=" + name+ ", ctx=" + t + ']');
        }
    }

    /**
     *
     * @param ldr Class loader to get ID for.
     * @return ID for given class loader or {@code null} if given loader is not
     *      grid deployment class loader.
     */
    @Nullable public GridUuid getClassLoaderId(@Nullable ClassLoader ldr) {
        if (ldr == null)
            return null;

        return cctx.gridDeploy().getClassLoaderId(ldr);
    }

    /**
     *
     * @param ldrId Class loader ID.
     * @return Class loader ID or {@code null} if loader not found.
     */
    @Nullable public ClassLoader getClassLoader(GridUuid ldrId) {
        assert ldrId != null;

        GridDeployment dep = cctx.gridDeploy().getDeployment(ldrId);

        return dep != null ? dep.classLoader() : null;
    }

    /**
     * @return {@code True} if context class loader is global.
     */
    public boolean isGlobalLoader() {
        return cctx.gridDeploy().isGlobalLoader(Thread.currentThread().getContextClassLoader());
    }
}

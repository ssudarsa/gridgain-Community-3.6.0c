// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCachePeekMode.*;
import static org.gridgain.grid.cache.query.GridCacheQueryType.*;

/**
 * Query and index manager.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@SuppressWarnings({"UnnecessaryFullyQualifiedName"})
public abstract class GridCacheQueryManager<K, V> extends GridCacheManager<K, V> {
    /** Number of entries to keep in annotation cache. */
    private static final int DFLT_CLASS_CACHE_SIZE = 1000;

    /** */
    private static final List<GridCachePeekMode> DB_SWAP_GLOBAL = F.asList(DB, SWAP, GLOBAL);

    /** */
    private static final List<GridCachePeekMode> SWAP_GLOBAL = F.asList(SWAP, GLOBAL);

    /** */
    private static final GridCachePeekMode[] TX = new GridCachePeekMode[]{GridCachePeekMode.TX};

    /** */
    private GridCacheQueryIndex<K, V> idx;

    /** Busy lock. */
    protected final GridBusyLock busyLock = new GridBusyLock();

    /** Queries metrics bounded cache. */
    private final Map<GridCacheQueryMetricsKey, GridCacheQueryMetricsAdapter> metrics =
        new GridBoundedLinkedHashMap<GridCacheQueryMetricsKey, GridCacheQueryMetricsAdapter>(DFLT_CLASS_CACHE_SIZE);

    /** {@inheritDoc} */
    @Override public void start0() throws GridException {
        idx = new GridCacheQueryIndex<K, V>(cctx);

        idx.start();
    }

    /**
     * Stops query manager.
     *
     * @param cancel Cancel queries.
     * @param wait Wait for current queries finish.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    @Override public final void stop0(boolean cancel, boolean wait) {
        try {
            if (cancel)
                onCancelAtStop();

            if (wait)
                onWaitAtStop();
        }
        finally {
            // Acquire write lock so that any new activity could not be started.
            busyLock.block();

            idx.stop();
        }

        if (log.isDebugEnabled())
            log.debug("Stopped cache query manager.");
    }

    /**
     * Marks this request as canceled.
     *
     * @param reqId Request id.
     */
    void onQueryFutureCanceled(long reqId) {
        // No-op.
    }

    /**
     * Cancel flag handler at stop.
     */
    void onCancelAtStop() {
        // No-op.
    }

    /**
     * Wait flag handler at stop.
     */
    void onWaitAtStop() {
        // No-op.
    }

    /**
     * Writes key-value pair to index.
     *
     * @param key Key.
     * @param keyBytes Byte array with key data.
     * @param val Value.
     * @param ver Cache entry version.
     * @throws GridException In case of error.
     */
    public void store(K key, @Nullable byte[] keyBytes, V val, GridCacheVersion ver) throws GridException {
        assert key != null;
        assert val != null;

        if (!busyLock.enterBusy()) {
            if (log.isDebugEnabled())
                log.debug("Received store request while stopping or after shutdown (will ignore).");

            return;
        }

        try {
            idx.store(key, keyBytes, val, ver);
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * @param key Key.
     * @param keyBytes Byte array with key value.
     * @return {@code true} if key was found and removed, otherwise {@code false}.
     * @throws GridException Thrown in case of any errors.
     */
    public boolean remove(K key, @Nullable byte[] keyBytes) throws GridException {
        assert key != null;

        if (!busyLock.enterBusy()) {
            if (log.isDebugEnabled())
                log.debug("Received remove request while stopping or after shutdown (will ignore).");

            return false;
        }

        try {
            return idx.remove(key, keyBytes);
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * Undeploys given class loader.
     *
     * @param ldr Class loader to undeploy.
     */
    public void onUndeploy(ClassLoader ldr) {
        if (!busyLock.enterBusy()) {
            if (log.isDebugEnabled())
                log.debug("Received onUndeploy() request while stopping or after shutdown (will ignore).");

            return;
        }

        try {
            idx.onUndeploy(ldr);
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * NOTE: For testing purposes.
     *
     * @return Collection of currently registered table names.
     */
    public Collection<String> indexTables() {
        return idx.indexTables();
    }

    /**
     * NOTE: For testing purposes.
     *
     * @return Query index.
     */
    public GridCacheQueryIndex<K, V> index() {
        return idx;
    }

    /**
     * Executes distributed query.
     *
     * @param qry Query.
     * @param single {@code true} if single result requested, {@code false} if multiple.
     * @param rmtRdcOnly {@code true} for reduce query when using remote reducer only,
     *      otherwise it is always {@code false}.
     * @param pageLsnr Page listener.
     * @return Iterator over query results. Note that results become available as they come.
     */
    public abstract <R> GridCacheQueryFuture<R> queryLocal(GridCacheQueryBaseAdapter<K, V> qry, boolean single,
        boolean rmtRdcOnly, @Nullable GridInClosure2<UUID, Collection<R>> pageLsnr);

    /**
     * Executes distributed query.
     *
     * @param qry Query.
     * @param nodes Nodes.
     * @param single {@code true} if single result requested, {@code false} if multiple.
     * @param rmtOnly {@code true} for reduce query when using remote reducer only,
     *      otherwise it is always {@code false}.
     * @param pageLsnr Page listener.
     * @return Iterator over query results. Note that results become available as they come.
     */
    public abstract <R> GridCacheQueryFuture<R> queryDistributed(GridCacheQueryBaseAdapter<K, V> qry,
        Collection<GridRichNode> nodes, boolean single, boolean rmtOnly,
        @Nullable GridInClosure2<UUID, Collection<R>> pageLsnr);

    /**
     * Performs query.
     *
     * @param qry Query.
     * @param loc Local query or not.
     * @return Collection of found keys.
     * @throws GridException In case of error.
     */
    private Iterator<GridCacheQueryIndexRow<K, V>> executeQuery(GridCacheQueryBaseAdapter qry, boolean loc) throws
        GridException {
        return qry.type() == SQL ? idx.querySql(qry, loc) : qry.type() == SCAN ? scanIterator(qry, loc) :
            idx.queryText(qry, loc);
    }

    /**
     *
     * @param qry query
     * @param loc {@code true} if local query.
     * @return Full-scan row iterator.
     * @throws GridException If failed to get iterator.
     */
    @SuppressWarnings({"unchecked"})
    private Iterator<GridCacheQueryIndexRow<K, V>> scanIterator(GridCacheQueryBaseAdapter qry, boolean loc)
        throws GridException {
        ClassLoader ldr = loc ? cctx.deploy().localLoader() : cctx.deploy().globalLoader();

        final Class qryCls;

        try {
            qryCls = qry.queryClass(ldr);
        }
        catch (ClassNotFoundException e) {
            throw new GridException("Failed to create scan query iterator on node: " + cctx.nodeId(), e);
        }

        P1<GridCacheEntry<K, V>> clsPred = new P1<GridCacheEntry<K, V>>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                V val = e.peek();

                return val != null && (qryCls == null || qryCls.isAssignableFrom(val.getClass()));
            }
        };

        GridPredicate<GridCacheEntry<K, V>>[] filter =
            cctx.vararg(qry.projectionFilter() != null ? F.and(qry.projectionFilter(), clsPred) : clsPred);

        Set<Map.Entry<K, V>> entries =
            qry.readThrough() ?
                cctx.cache().getAll(filter).entrySet() :
                cctx.cache().peekAll(filter).entrySet();

        return F.iterator(entries,
            new C1<Map.Entry<K, V>, GridCacheQueryIndexRow<K, V>>() {
                @Override public GridCacheQueryIndexRow<K, V> apply(Map.Entry<K, V> e) {
                    return new GridCacheQueryIndexRow<K, V>(e.getKey(), e.getValue(), null, null);
                }
            }, true);
    }

    /**
     * @param o Object to inject resources to.
     * @throws GridException If failure occurred while injecting resources.
     */
    private void injectResources(@Nullable Object o) throws GridException {
        if (o != null) {
            GridKernalContext ctx = cctx.kernalContext();

            ClassLoader ldr = o.getClass().getClassLoader();

            if (ctx.deploy().isGlobalLoader(ldr))
                ctx.resource().inject(ctx.deploy().getDeployment(ctx.deploy().getClassLoaderId(ldr)), o.getClass(), o);
            else
                ctx.resource().inject(ctx.deploy().getDeployment(o.getClass().getName()), o.getClass(), o);
        }
    }

    /**
     * Processes cache query request.
     *
     * @param qryInfo Query info.
     */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    protected <R> void runQuery(GridCacheQueryInfo<K, V> qryInfo) {
        boolean loc = qryInfo.local();

        int qryId = qryInfo.query().id();

        if (log.isDebugEnabled())
            log.debug("Running query [qryId=" + qryId + ", qryInfo=" + qryInfo + ']');

        Collection<?> data = null;

        try {
            // Preparing query closures.
            GridPredicate<Object> keyFilter = (GridPredicate<Object>)qryInfo.keyFilter();
            GridPredicate<Object> valFilter = (GridPredicate<Object>)qryInfo.valueFilter();
            GridPredicate<GridCacheEntry<K, V>>[] prjFilter = cctx.vararg(qryInfo.projectionPredicate());
            GridClosure<Object, Object> trans = (GridClosure<Object, Object>)qryInfo.transformer();
            GridReducer<Map.Entry<K, V>, Object> rdc = qryInfo.reducer();

            // Injecting resources into query closures.
            injectResources(keyFilter);
            injectResources(valFilter);
            injectResources(prjFilter);
            injectResources(trans);
            injectResources(rdc);

            GridCacheQueryBaseAdapter<K, V> qry = qryInfo.query();

            boolean single = qryInfo.single();

            int pageSize = qryInfo.pageSize();

            boolean readThrough = qryInfo.readThrough();

            boolean incBackups = qryInfo.includeBackups();

            Map<K, Object> map = new LinkedHashMap<K, Object>(pageSize);

            Iterator<GridCacheQueryIndexRow<K, V>> iter = executeQuery(qry, loc);

            GridCacheAdapter<K, V> cache = cctx.cache();

            if (log.isDebugEnabled())
                log.debug("Received index iterator [qryId=" + qryId + ", iterHasNext=" + iter.hasNext() +
                    ", cacheSize=" + cache.keySize() + ']');

            int cnt = 0;

            boolean stop = false;

            while (iter.hasNext()) {
                GridCacheQueryIndexRow<K, V> row = iter.next();

                K key = row.key();

                if (cctx.config().getCacheMode() != LOCAL && !incBackups && !cctx.primary(cctx.localNode(), key)) {
                    if (log.isDebugEnabled())
                        log.debug("Ignoring backup element [qryId=" + qryId + ", row=" + row +
                            ", cacheMode=" + cctx.config().getCacheMode() + ", incBackups=" + incBackups +
                            ", primary=" + cctx.primary(cctx.localNode(), key) + ']');

                    continue;
                }

                if (!F.isAll(key, keyFilter))
                    continue;

                V val = row.value();

                if (val == null) {
                    assert row.valueBytes() != null;

                    GridCacheEntryEx<K, V> entry = cache.entryEx(key);

                    boolean unmarshal;

                    try {
                        GridCachePeekMode[] oldExcl = cctx.excludePeekModes(TX);

                        try {
                            val = readThrough ?
                                entry.peek(DB_SWAP_GLOBAL, prjFilter) :
                                entry.peek(SWAP_GLOBAL, prjFilter);
                        }
                        finally {
                            cctx.excludePeekModes(oldExcl);
                        }

                        if (qry.cloneValues())
                            val = cctx.cloneValue(val);

                        GridCacheVersion ver = entry.version();

                        unmarshal = !row.version().equals(ver.id().toString() + ver.order());
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        // If entry has been removed concurrently we have to unmarshal from bytes.
                        unmarshal = true;
                    }

                    if (unmarshal)
                        val = (V)U.unmarshal(cctx.marshaller(), new GridByteArrayList(row.valueBytes()),
                            loc ? cctx.deploy().localLoader() : cctx.deploy().globalLoader());
                }

                if (log.isDebugEnabled())
                    log.debug("Record [qryId=" + qryId + ", key=" + row.key() + ", val=" + val + ", incBackups=" +
                        incBackups + "priNode=" + CU.primaryNode(cctx, row.key()).id8() +
                        ", node=" + cctx.grid().localNode().id8() + ']');

                if (val == null || !F.isAll(val, valFilter)) {
                    if (log.isDebugEnabled())
                        log.debug("Unsuitable record value [qryId=" + qryId + ", val=" + val + ']');

                    continue;
                }

                map.put(row.key(), trans == null ? val : trans.apply(val));

                if (single)
                    break;

                if (++cnt == pageSize || !iter.hasNext()) {
                    if (rdc == null) {
                        boolean finished = !iter.hasNext();

                        if (loc)
                            onPageReady(loc, qryInfo, map.entrySet(), finished, null);
                        else {
                            // Put GridCacheQueryResponseEntry as map value to avoid using any new container.
                            for (Map.Entry entry : map.entrySet())
                                entry.setValue(new GridCacheQueryResponseEntry(entry.getKey(), entry.getValue()));

                            if (!onPageReady(loc, qryInfo, map.values(), finished, null))
                                // Finish processing on any error.
                                return;
                        }

                        cnt = 0;

                        if (finished)
                            return;
                    }
                    else {
                        for (Map.Entry<K, Object> entry : map.entrySet())
                            if (!rdc.collect((Map.Entry<K, V>)entry)) {
                                stop = true;

                                break; // for
                            }
                    }

                    map = new LinkedHashMap<K, Object>(pageSize);

                    if (stop)
                        break; // while
                }
            }

            if (rdc == null) {
                if (!loc) {
                    for (Map.Entry entry : map.entrySet())
                        entry.setValue(new GridCacheQueryResponseEntry(entry.getKey(), entry.getValue()));

                    data = map.values();
                }
                else
                    data = map.entrySet();
            }
            else {
                if (!stop)
                    for (Map.Entry<K, Object> entry : map.entrySet())
                        if (!rdc.collect((Map.Entry<K, V>)entry))
                            break;

                data = Collections.singletonList(rdc.apply());
            }

            onPageReady(loc, qryInfo, data, true, null);
        }
        catch (Throwable e) {
            log.error("Failed to run query [qry=" + qryInfo + ", node=" + cctx.nodeId() + "]", e);

            onPageReady(loc, qryInfo, null, true, e);
        }

        if (log.isDebugEnabled())
            log.debug("End of running query [qryId=" + qryId + ", res=" + data + ']');
    }

    /**
     * Called when data for page is ready.
     *
     * @param loc Local query or not.
     * @param qryInfo Query info.
     * @param data Result data.
     * @param finished Last page or not.
     * @param e Exception in case of error.
     * @return {@code true} if page was processed right.
     */
    protected abstract boolean onPageReady(boolean loc, GridCacheQueryInfo<K, V> qryInfo, @Nullable Collection<?> data,
        boolean finished, @Nullable Throwable e);

    /**
     *
     * @param qry Query to validate.
     * @throws GridException In case of validation error.
     */
    public void validateQuery(GridCacheQueryBase<K, V> qry) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Validating query: " + qry);

        if (qry.type() == null)
            throw new GridException("Type must be set for query.");

        if (qry.type() == SQL || qry.type() == H2TEXT || qry.type() == LUCENE) {
            if (F.isEmpty(qry.className()))
                throw new GridException("Class must be set for " + qry.type().name() + " query.");

            if (F.isEmpty(qry.clause()))
                throw new GridException("Clause must be set for " + qry.type().name() + " query.");
        }
    }

    /**
     * Creates user's query.
     *
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(@Nullable GridPredicate<GridCacheEntry<K, V>> filter,
        Set<GridCacheFlag> flags) {
        return new GridCacheQueryAdapter<K, V>(cctx, null, null, null, filter, flags);
    }

    /**
     * Creates user's query.
     *
     * @param type Query type.
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type,
        @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheQueryAdapter<K, V>(cctx, type, null, null, filter, flags);
    }

    /**
     * Creates user's query.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, String clsName, String clause,
        @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheQueryAdapter<K, V>(cctx, type, clause, clsName, filter, flags);
    }

    /**
     * Creates user's query.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, Class<?> cls, String clause,
        @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheQueryAdapter<K, V>(cctx, type, clause,
            (cls != null ? cls.getName() : null), filter, flags);
    }

    /**
     * Creates user's transform query.
     *
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(
        @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheTransformQueryAdapter<K, V, T>(cctx, null, null, null,
            filter, flags);
    }

    /**
     * Creates user's transform query.
     *
     * @param type Query type.
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type,
        @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheTransformQueryAdapter<K, V, T>(cctx, type, null, null,
            filter, flags);
    }

    /**
     * Creates user's transform query.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type, String clsName,
        String clause, @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheTransformQueryAdapter<K, V, T>(cctx, type, clause,
            clsName, filter, flags);
    }

    /**
     * Creates user's transform query.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type, Class<?> cls,
        String clause, @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheTransformQueryAdapter<K, V, T>(cctx, type, clause,
            (cls != null ? cls.getName() : null), filter, flags);
    }

    /**
     * Creates user's reduce query.
     *
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(
        @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheReduceQueryAdapter<K, V, R1, R2>(cctx, null, null,
            null, filter, flags);
    }

    /**
     * Creates user's reduce query.
     *
     * @param type Query type.
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheReduceQueryAdapter<K, V, R1, R2>(cctx, type, null,
            null, filter, flags);
    }

    /**
     * Creates user's reduce query.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type, String clsName,
        String clause, @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheReduceQueryAdapter<K, V, R1, R2>(cctx, type, clause,
            clsName, filter, flags);
    }

    /**
     * Creates user's reduce query.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        Class<?> cls, String clause, @Nullable GridPredicate<GridCacheEntry<K, V>> filter,
        Set<GridCacheFlag> flags) {
        return new GridCacheReduceQueryAdapter<K, V, R1, R2>(cctx, type, clause,
            (cls != null ? cls.getName() : null), filter, flags);
    }

    /**
     * Gets cache queries metrics.
     *
     * @return Cache queries metrics.
     */
    public Collection<GridCacheQueryMetrics> metrics() {
        synchronized (metrics) {
            return new GridBoundedLinkedHashSet<GridCacheQueryMetrics>(metrics.values(), DFLT_CLASS_CACHE_SIZE);
        }
    }

    /**
     * @param m Updated metrics.
     * @param startTime Execution start time.
     * @param duration Execution duration.
     * @param fail {@code true} if execution failed.
     */
    public void onMetricsUpdate(GridCacheQueryMetricsAdapter m, long startTime, long duration, boolean fail) {
        assert m != null;

        synchronized (metrics) {
            GridCacheQueryMetricsAdapter prev = metrics.get(m.key());

            if (prev == null)
                metrics.put(m.key(), m.copy());
            else
                prev.onQueryExecute(startTime, duration, fail);
        }
    }

    /**
     * Prints memory statistics for debugging purposes.
     */
    @Override public void printMemoryStats() {
        X.println(">>>");
        X.println(">>> Query manager memory stats [grid=" + cctx.gridName() + ", cache=" + cctx.name() + ']');
        X.println(">>>   Metrics: " + metrics.size());

        idx.printMemoryStats();
    }
}

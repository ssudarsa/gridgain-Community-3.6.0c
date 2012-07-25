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
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheConfiguration.*;
import static org.gridgain.grid.cache.GridCacheFlag.*;
import static org.gridgain.grid.cache.query.GridCacheQueryType.*;

/**
 * Query adapter.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public abstract class GridCacheQueryBaseAdapter<K, V> extends GridMetadataAwareAdapter implements
    GridCacheQueryBase<K, V> {
    /** Logger reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Sequence of query id. */
    protected static final AtomicInteger idGen = new AtomicInteger();

    /** Query id.  */
    protected final int id;

    /** Empty array. */
    private static final GridPredicate[] EMPTY_NODES = new GridPredicate[0];

    /** */
    @GridToStringExclude
    private GridThreadLocal<PreparedStatement> stmt = new GridThreadLocal<PreparedStatement>();

    /** */
    protected final GridCacheContext<K, V> cctx;

    /** Query activity logger. */
    protected final GridLogger qryLog;

    /** Default logger. */
    protected final GridLogger log;

    /** */
    private final GridCacheQueryType type;

    /** */
    private volatile String clause;

    /** */
    private volatile String clsName;

    /** */
    private volatile Class<?> cls;

    /** */
    private volatile GridClosure<Object[], GridPredicate<? super K>> rmtKeyFilter;

    /** */
    private volatile GridClosure<Object[], GridPredicate<? super V>> rmtValFilter;

    /** */
    private volatile GridPredicate<GridCacheEntry<K, V>> prjFilter;

    /** */
    private volatile int pageSize = GridCacheQuery.DFLT_PAGE_SIZE;

    /** */
    private volatile long timeout;

    /** */
    private volatile Object[] args;

    /** */
    private volatile Object[] closureArgs;

    /** */
    private volatile boolean keepAll = true;

    /** False by default. */
    private volatile boolean incBackups;

    /** False by default. */
    private volatile boolean dedup;

    /** */
    private volatile boolean readThrough;

    /** */
    private volatile boolean clone;

    /** Query metrics.*/
    private volatile GridCacheQueryMetricsAdapter metrics;

    /** Sealed flag. Query cannot be modified once it's set to true. */
    private volatile boolean sealed;

    /** */
    protected final Object mux = new Object();

    /**
     * @param cctx Cache registry.
     * @param type Query type.
     * @param clause Query clause.
     * @param clsName Query class name.
     * @param prjFilter Projection filter.
     * @param prjFlags Projection flags.
     */
    protected GridCacheQueryBaseAdapter(GridCacheContext<K, V> cctx, @Nullable GridCacheQueryType type,
        @Nullable String clause, @Nullable String clsName, GridPredicate<GridCacheEntry<K, V>> prjFilter,
        Collection<GridCacheFlag> prjFlags) {
        this(cctx, -1, type, clause, clsName, prjFilter, prjFlags);
    }

    /**
     * @param cctx Cache registry.
     * @param qryId Query id. If it less than {@code 0} new query id will be created.
     * @param type Query type.
     * @param clause Query clause.
     * @param clsName Query class name.
     * @param prjFilter Projection filter.
     * @param prjFlags Projection flags.
     */
    protected GridCacheQueryBaseAdapter(GridCacheContext<K, V> cctx, int qryId, @Nullable GridCacheQueryType type,
        @Nullable String clause, @Nullable String clsName, GridPredicate<GridCacheEntry<K, V>> prjFilter,
        Collection<GridCacheFlag> prjFlags) {
        assert cctx != null;

        if (cctx.config().isIndexMemoryOnly() && (type == H2TEXT || type == LUCENE))
            throw new GridRuntimeException("Text queries are not supported for in-memory index " +
                "(change GridCacheConfiguration.isIndexMemoryOnly() property to false)");

        this.cctx = cctx;
        this.type = type;
        this.clause = clause;
        this.clsName = clsName;
        this.prjFilter = prjFilter;

        validateSql();

        log = U.logger(cctx.kernalContext(), logRef, GridCacheQueryBaseAdapter.class);

        qryLog = cctx.logger(DFLT_QUERY_LOGGER_NAME);

        clone = prjFlags.contains(CLONE);

        metrics = new GridCacheQueryMetricsAdapter(new GridCacheQueryMetricsKey(type, clsName, clause));

        id = qryId < 0 ? idGen.incrementAndGet() : qryId;

        timeout = cctx.config().getDefaultQueryTimeout();
    }

    /**
     * @param qry Query to copy from.
     */
    protected GridCacheQueryBaseAdapter(GridCacheQueryBaseAdapter<K, V> qry) {
        stmt = qry.stmt;
        cctx = qry.cctx;
        type = qry.type;
        clause = qry.clause;
        prjFilter = qry.prjFilter;
        clsName = qry.clsName;
        cls = qry.cls;
        rmtKeyFilter = qry.rmtKeyFilter;
        rmtValFilter = qry.rmtValFilter;
        args = qry.args;
        closureArgs = qry.closureArgs;
        pageSize = qry.pageSize;
        timeout = qry.timeout;
        keepAll = qry.keepAll;
        incBackups = qry.incBackups;
        dedup = qry.dedup;
        readThrough = qry.readThrough;
        clone = qry.clone;

        log = U.logger(cctx.kernalContext(), logRef, GridCacheQueryBaseAdapter.class);

        qryLog = cctx.kernalContext().config().getGridLogger().getLogger(DFLT_QUERY_LOGGER_NAME);

        metrics = qry.metrics;

        id = qry.id;
    }

    /**
     * Validates sql clause.
     */
    private void validateSql() {
        if (type == GridCacheQueryType.SQL) {
            if (clause == null)
                throw new IllegalArgumentException("SQL string cannot be null for query.");

            if (clause.startsWith("where"))
                throw new IllegalArgumentException("SQL string cannot start with 'where' ('where' keyword is assumed). " +
                    "Valid examples: \"col1 like '%val1%'\" or \"from MyClass1 c1, MyClass2 c2 where c1.col1 = c2.col1 " +
                    "and c1.col2 like '%val2%'");
        }
    }

    /**
     * @return Context.
     */
    protected GridCacheContext<K, V> context() {
        return cctx;
    }

    /**
     * Checks if metrics should be recreated and does it in this case.
     */
    private void checkMetrics() {
        synchronized (mux) {
            if (!F.eq(metrics.clause(), clause) || !F.eq(metrics.className(), clsName))
                metrics = new GridCacheQueryMetricsAdapter(new GridCacheQueryMetricsKey(type, clsName, clause));
        }
    }

    /** {@inheritDoc} */
    @Override public int id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueryType type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override public String clause() {
        return clause;
    }

    /** {@inheritDoc} */
    @Override public void clause(String clause) {
        synchronized (mux) {
            checkSealed();

            this.clause = clause;

            validateSql();

            checkMetrics();
        }
    }

    /** {@inheritDoc} */
    @Override public int pageSize() {
        return pageSize;
    }

    /** {@inheritDoc} */
    @Override public void pageSize(int pageSize) {
        synchronized (mux) {
            checkSealed();

            this.pageSize = pageSize < 1 ? GridCacheQuery.DFLT_PAGE_SIZE : pageSize;
        }
    }

    /** {@inheritDoc} */
    @Override public long timeout() {
        return timeout;
    }

    /** {@inheritDoc} */
    @Override public void timeout(long timeout) {
        synchronized (mux) {
            checkSealed();

            this.timeout = timeout <= 0 ? Long.MAX_VALUE : timeout;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean keepAll() {
        return keepAll;
    }

    /** {@inheritDoc} */
    @Override public void keepAll(boolean keepAll) {
        synchronized (mux) {
            checkSealed();

            this.keepAll = keepAll;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean includeBackups() {
        return incBackups;
    }

    /** {@inheritDoc} */
    @Override public void includeBackups(boolean incBackups) {
        synchronized (mux) {
            checkSealed();

            this.incBackups = incBackups;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean enableDedup() {
        return dedup;
    }

    /** {@inheritDoc} */
    @Override public void enableDedup(boolean dedup) {
        synchronized (mux) {
            checkSealed();

            this.dedup = dedup;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean readThrough() {
        return readThrough;
    }

    /**
     * @return Clone values flag.
     */
    public boolean cloneValues() {
        return clone;
    }

    /** {@inheritDoc} */
    @Override public void readThrough(boolean readThrough) {
        synchronized (mux) {
            checkSealed();

            this.readThrough = readThrough;
        }
    }

    /** {@inheritDoc} */
    @Override public String className() {
        return clsName;
    }

    /** {@inheritDoc} */
    @Override public void className(String clsName) {
        synchronized (mux) {
            checkSealed();

            this.clsName = clsName;

            checkMetrics();
        }
    }

    /**
     * Gets query class.
     *
     * @param ldr Classloader.
     * @return Query class.
     * @throws ClassNotFoundException Thrown if class not found.
     */
    public Class<?> queryClass(ClassLoader ldr) throws ClassNotFoundException {
        if (cls == null)
            cls = clsName != null ? Class.forName(clsName, true, ldr) : null;

        return cls;
    }

    /**
     * @return Remote key filter.
     */
    public GridClosure<Object[], GridPredicate<? super K>> remoteKeyFilter() {
        return rmtKeyFilter;
    }

    /**
     *
     * @param rmtKeyFilter Remote key filter
     */
    @Override public void remoteKeyFilter(GridClosure<Object[], GridPredicate<? super K>> rmtKeyFilter) {
        synchronized (mux) {
            checkSealed();

            this.rmtKeyFilter = rmtKeyFilter;
        }
    }

    /**
     * @return Remote value filter.
     */
    public GridClosure<Object[], GridPredicate<? super V>> remoteValueFilter() {
        return rmtValFilter;
    }

    /**
     * @param rmtValFilter Remote value filter.
     */
    @Override public void remoteValueFilter(GridClosure<Object[], GridPredicate<? super V>> rmtValFilter) {
        synchronized (mux) {
            checkSealed();

            this.rmtValFilter = rmtValFilter;
        }
    }

    /**
     * @return Projection filter.
     */
    public GridPredicate<GridCacheEntry<K, V>> projectionFilter() {
        return prjFilter;
    }

    /**
     * @param prjFilter Projection filter.
     */
    public void projectionFilter(GridPredicate<GridCacheEntry<K, V>> prjFilter) {
        synchronized (mux) {
            checkSealed();

            this.prjFilter = prjFilter;
        }
    }

    /**
     * @param args Arguments.
     */
    public void arguments(@Nullable Object[] args) {
        this.args = args;
    }

    /**
     * @return Arguments.
     */
    public Object[] arguments() {
        return args;
    }

    /**
     * Sets closure arguments.
     * <p>
     * Note that the name of the method has "set" in it not to conflict with
     * {@link GridCacheQuery#closureArguments(Object...)} method.
     *
     * @param closureArgs Arguments.
     */
    public void setClosureArguments(Object[] closureArgs) {
        this.closureArgs = closureArgs;
    }

    /**
     * Gets closure arguments.
     * <p>
     * Note that the name of the method has "set" in it not to conflict with
     * {@link GridCacheQuery#closureArguments(Object...)} method.
     *
     * @return Closure's arguments.
     */
    public Object[] getClosureArguments() {
        return closureArgs;
    }

    /**
     * Sets PreparedStatement for this query for this thread.
     *
     * @param stmt PreparedStatement to set.
     */
    public void preparedStatementForThread(PreparedStatement stmt) {
        this.stmt.set(stmt);
    }

    /** @return PreparedStatement for this query for this thread. */
    public PreparedStatement preparedStatementForThread() {
        return stmt.get();
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueryMetrics metrics() {
        return metrics;
    }

    /**
     * @throws GridException In case of error.
     */
    protected abstract void registerClasses() throws GridException;

    /**
     * @param nodes Nodes.
     * @param single {@code true} if single result requested, {@code false} if multiple.
     * @param rmtRdcOnly {@code true} for reduce query when using remote reducer only,
     *      otherwise it is always {@code false}.
     * @param pageLsnr Page listener.
     * @param <R> Result type.
     * @return Future.
     */
    protected <R> GridCacheQueryFuture<R> execute(Collection<GridRichNode> nodes, boolean single, boolean rmtRdcOnly,
        @Nullable GridInClosure2<UUID, Collection<R>> pageLsnr) {
        // Seal the query.
        seal();

        if (log.isDebugEnabled())
            log.debug("Executing query [query=" + this + ", nodes=" + nodes + ']');

        try {
            cctx.deploy().registerClasses(cls, rmtKeyFilter, rmtValFilter, prjFilter);

            registerClasses();

            cctx.deploy().registerClasses(args);
            cctx.deploy().registerClasses(closureArgs);
        }
        catch (GridException e) {
            return new GridCacheErrorQueryFuture<R>(cctx.kernalContext(), e);
        }

        if (F.isEmpty(nodes))
            nodes = CU.allNodes(cctx);

        GridCacheQueryManager<K, V> qryMgr = cctx.queries();

        assert qryMgr != null;

        return nodes.size() == 1 && nodes.iterator().next().equals(cctx.discovery().localNode()) ?
            qryMgr.queryLocal(this, single, rmtRdcOnly, pageLsnr) :
            qryMgr.queryDistributed(this, nodes, single, rmtRdcOnly, pageLsnr);
    }

    /**
     * Check if this query is sealed.
     */
    protected void checkSealed() {
        assert Thread.holdsLock(mux);

        if (sealed)
            throw new IllegalStateException("Query cannot be modified after first execution: " + this);
    }

    /**
     * Seal this query so that it can't be modified.
     */
    private void seal() {
        synchronized (mux) {
            sealed = true;
        }
    }

    /**
     * @param res Query result.
     * @param err Error or {@code null} if query executed successfully.
     * @param startTime Start time.
     * @param duration Duration.
     */
    public void onExecuted(Object res, Throwable err, long startTime, long duration) {
        boolean fail = err != null;

        // Update own metrics.
        metrics.onQueryExecute(startTime, duration, fail);

        // Update metrics in query manager.
        cctx.queries().onMetricsUpdate(metrics, startTime, duration, fail);

        if (qryLog.isDebugEnabled())
            qryLog.debug("Query execution finished [qry=" + this + ", startTime=" + startTime +
                ", duration=" + duration + ", fail=" + fail + ", res=" + res + ']');
    }

    /**
     * @param grid Grid.
     * @return Predicates for nodes.
     */
    @SuppressWarnings({"unchecked"})
    protected GridPredicate<GridRichNode>[] nodes(GridProjection[] grid) {
        if (F.isEmpty(grid))
            return EMPTY_NODES;

        GridPredicate<GridRichNode>[] res = new GridPredicate[grid.length];

        for (int i = 0; i < grid.length; i++)
            res[i] = grid[i].predicate();

        return res;
    }

    /**
     * @param nodes Nodes.
     * @return Short representation of query.
     */
    public String toShortString(Collection<? extends GridNode> nodes) {
        return "[id=" + id + ", clause=" + clause + ", type=" + type + ", clsName=" + clsName + ", nodes=" +
            U.toShortString(nodes) + ']';
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheQueryBaseAdapter.class, this);
    }

    /**
     * Future for single query result.
     *
     * @param <R> Result type.
     */
    protected class SingleFuture<R> extends GridFutureAdapter<R> {
        /** */
        private GridCacheQueryFuture<R> fut;

        /**
         * Required by {@link Externalizable}.
         */
        public SingleFuture() {
            super(cctx.kernalContext());
        }

        /**
         * @param nodes Nodes.
         */
        SingleFuture(Collection<GridRichNode> nodes) {
            super(cctx.kernalContext());

            fut = execute(nodes, true, false, new CI2<UUID, Collection<R>>() {
                @Override public void apply(UUID uuid, Collection<R> pageData) {
                    try {
                        if (!F.isEmpty(pageData))
                            onDone(pageData.iterator().next());
                    }
                    catch (Throwable e) {
                        onDone(e);
                    }
                }
            });

            fut.listenAsync(new CI1<GridFuture<Collection<R>>>() {
                @Override public void apply(GridFuture<Collection<R>> t) {
                    try {
                        if (!fut.hasNextX())
                            onDone(null, null);
                    }
                    catch (Throwable e) {
                        onDone(e);
                    }
                }
            });
        }

        /** {@inheritDoc} */
        @Override public boolean cancel() throws GridException {
            if (onCancelled()) {
                fut.cancel();

                return true;
            }

            return false;
        }
    }
}

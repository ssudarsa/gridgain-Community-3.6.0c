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
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Query future adapter.
 *
 * @param <R> Result type.
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public abstract class GridCacheQueryFutureAdapter<K, V, R> extends GridFutureAdapter<Collection<R>>
    implements GridCacheQueryFuture<R>, GridTimeoutObject {
    /** Logger reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** */
    private static final Object NULL = new Object();

    /** Cache context. */
    protected GridCacheContext<K, V> cctx;

    /** Logger. */
    protected GridLogger log;

    /** */
    protected final GridCacheQueryBaseAdapter<K, V> qry;

    /** Set of received keys used to deduplicate query result set. */
    private final Collection<K> keys = new HashSet<K>();

    /** */
    private final Queue<Collection<Object>> queue = new LinkedList<Collection<Object>>();

    /** */
    protected final Collection<Object> allColl = new LinkedList<Object>();

    /** */
    private volatile int cnt;

    /** */
    private Iterator<R> iter;

    /** */
    protected final Object mux = new Object();

    /** */
    protected volatile GridReducer<Object, Object> locRdc;

    /** */
    private GridUuid timeoutId = GridUuid.randomUuid();

    /** */
    private long startTime;

    /** */
    private long endTime;

    /** */
    protected boolean loc;

    /** */
    protected boolean single;

    /** */
    protected GridFuture<?> locFut;

    /** */
    private GridInClosure2<UUID, Collection<R>> pageLsnr;

    /**
     *
     */
    protected GridCacheQueryFutureAdapter() {
        qry = null;
    }

    /**
     * @param cctx Context.
     * @param qry Query.
     * @param loc Local query or not.
     * @param single Single result or not.
     * @param rmtRdcOnly {@code true} for reduce query when using remote reducer only,
     *      otherwise it is always {@code false}.
     * @param pageLsnr Page listener which is executed each time on page arrival.
     */
    @SuppressWarnings("unchecked")
    protected GridCacheQueryFutureAdapter(GridCacheContext<K, V> cctx, GridCacheQueryBaseAdapter<K, V> qry,
        boolean loc, boolean single, boolean rmtRdcOnly, @Nullable GridInClosure2<UUID, Collection<R>> pageLsnr) {
        super(cctx.kernalContext());

        this.cctx = cctx;
        this.qry = qry;
        this.loc = loc;
        this.single = single;
        this.pageLsnr = pageLsnr;

        log = U.logger(ctx, logRef, GridCacheQueryFutureAdapter.class);

        startTime = System.currentTimeMillis();

        long timeout = qry.timeout();

        if (timeout > 0) {
            endTime = startTime + timeout;

            // Protect against overflow.
            if (endTime < 0)
                endTime = Long.MAX_VALUE;

            cctx.time().addTimeoutObject(this);
        }

        if (qry instanceof GridCacheReduceQueryAdapter) {
            GridCacheReduceQueryAdapter rdcQry = (GridCacheReduceQueryAdapter)qry;

            locRdc = rmtRdcOnly ?
                null :
                rdcQry.localReducer() == null ?
                    null : (GridReducer<Object, Object>)rdcQry.localReducer().apply(qry.getClosureArguments());
        }

    }

    /**
     * @return Query.
     */
    public GridCacheQueryBaseAdapter<K, V> query() {
        return qry;
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(Collection<R> res, Throwable err) {
        cctx.time().removeTimeoutObject(this);

        qry.onExecuted(res, err, startTime(), duration());

        return super.onDone(res, err);
    }

    /** {@inheritDoc} */
    @Override public int size() {
        return cnt;
    }

    /** {@inheritDoc} */
    @Override public boolean available() {
        synchronized (mux) {
            return iter != null && iter.hasNext();
        }
    }

    /** {@inheritDoc} */
    @Override public GridIterator<R> iterator() {
        return this;
    }

    /** {@inheritDoc} */
    @Override public boolean hasNext() {
        try {
            return internalIterator().hasNext();
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public R next() {
        try {
            return unmaskNull(internalIterator().next());
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasNextX() throws GridException {
        return internalIterator().hasNext();
    }

    /** {@inheritDoc} */
    @Override public R nextX() throws GridException {
        return internalIterator().next();
    }

    /** {@inheritDoc} */
    @Override public void remove() {
        throw new UnsupportedOperationException("Remove is not supported.");
    }

    /**
     * @throws GridException If future is done with an error.
     */
    private void checkError() throws GridException {
        if (error() != null) {
            clear();

            throw new GridException("Query execution failed: " + qry, error());
        }
    }

    /**
     * @return Iterator.
     * @throws GridException In case of error.
     */
    private Iterator<R> internalIterator() throws GridException {
        checkError();

        synchronized (mux) {
            while (iter == null || !iter.hasNext()) {
                Collection<Object> c = queue.poll();

                if (c != null)
                    iter = new TypedIterator<R>(c.iterator());

                if (isDone() && queue.peek() == null)
                    break;

                if (c == null && !isDone()) {
                    long timeout = qry.timeout();

                    long waitTime = timeout == 0 ?
                        Long.MAX_VALUE : timeout - (System.currentTimeMillis() - startTime);

                    if (waitTime <= 0)
                        continue;

                    try {
                        mux.wait(waitTime);
                    }
                    catch (InterruptedException e) {
                        throw new GridException("Query was interrupted: " + qry, e);
                    }
                }

                checkError();
            }

            return iter;
        }
    }

    /**
     * @param eventNodeId Removed or failed node Id.
     */
    protected void onNodeLeft(UUID eventNodeId) {
        // No-op.
    }

    /**
     * @param col Collection.
     */
    @SuppressWarnings({"unchecked"})
    protected void enqueue(Collection<?> col) {
        assert Thread.holdsLock(mux);

        queue.add((Collection<Object>)col);

        cnt += col.size();
    }

    /**
     * @param col Query data collection.
     * @return If dedup flag is {@code true} deduplicated collection (considering keys),
     *      otherwise passed in collection without any modifications.
     */
    @SuppressWarnings({"unchecked"})
    private Collection<?> dedupIfRequired(Collection<?> col) {
        if (!qry.enableDedup())
            return col;

        Collection<Object> dedupCol = new LinkedList<Object>();

        synchronized (mux) {
            for (Object o : col)
                if (!(o instanceof Map.Entry) || keys.add(((Map.Entry<K, V>)o).getKey()))
                    dedupCol.add(o);
        }

        return dedupCol;
    }

    /**
     * @param nodeId Sender node.
     * @param data Page data.
     * @param err Error (if was).
     * @param finished Finished or not.
     */
    @SuppressWarnings({"unchecked", "NonPrivateFieldAccessedInSynchronizedContext"})
    public void onPage(@Nullable UUID nodeId, @Nullable Collection<?> data, @Nullable Throwable err, boolean finished) {
        if (log.isDebugEnabled())
            log.debug("Received query result page [nodeId=" + nodeId + ", qryId=" + qry.id + ", data=" + data +
                ", err=" + err + ", finished=" + finished + "]");

        if (err != null)
            synchronized (mux) {
                enqueue(Collections.emptyList());

                onDone(nodeId != null ? new GridException("Query failed at node: [query=" + qry
                    + ", node=" + nodeId + "]", err) : err);

                mux.notifyAll();
            }
        else {
            if (data == null)
                data = Collections.emptyList();

            data = dedupIfRequired((Collection<Object>)data);

            if (pageLsnr != null)
                pageLsnr.apply(nodeId, (Collection<R>)data);

            if (locRdc == null) {
                synchronized (mux) {
                    enqueue(data);

                    if (qry.keepAll())
                        allColl.addAll(maskNulls((Collection<Object>)data));

                    if (finished && onLastPage(nodeId)) {
                        clear();

                        onDone((Collection<R>)(qry.keepAll() ? unmaskNulls(allColl) : data));
                    }

                    mux.notifyAll();
                }
            }
            else {
                synchronized (mux) {
                    for (Object obj : data)
                        locRdc.collect(obj);

                    if (finished && onLastPage(nodeId)) {
                        clear();

                        List<R> resCol = Collections.singletonList((R)locRdc.apply());

                        enqueue(resCol);

                        onDone(resCol);

                        mux.notifyAll();
                    }
                }
            }
        }
    }

    /**
     * @param col Collection.
     * @return Collection with masked {@code null} values.
     */
    private Collection<Object> maskNulls(Collection<Object> col) {
        assert col != null;

        return F.viewReadOnly(col, new C1<Object, Object>() {
            @Override public Object apply(Object e) {
                return e != null ? e : NULL;
            }
        });
    }

    /**
     * @param col Collection.
     * @return Collection with unmasked {@code null} values.
     */
    private Collection<Object> unmaskNulls(Collection<Object> col) {
        assert col != null;

        return F.viewReadOnly(col, new C1<Object, Object>() {
            @Override public Object apply(Object e) {
                return e != NULL ? e : null;
            }
        });
    }

    /**
     * @param obj Object.
     * @return Unmasked object.
     */
    private R unmaskNull(R obj) {
        return obj != NULL ? obj : null;
    }

    /**
     * @param nodeId Sender node id.
     * @return Is query finished or not.
     */
    protected abstract boolean onLastPage(UUID nodeId);

    /**
     * Clears future.
     */
    void clear() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws GridException {
        if (onCancelled()) {
            cancelQuery();

            return true;
        }
        else
            return false;
    }

    /**
     * @throws GridException In case of error.
     */
    protected abstract void cancelQuery() throws GridException;

    /** {@inheritDoc} */
    @Override public GridUuid timeoutId() {
        return timeoutId;
    }

    /** {@inheritDoc} */
    @Override public long endTime() {
        return endTime;
    }

    /** {@inheritDoc} */
    @Override public void onTimeout() {
        try {
            cancelQuery();

            onDone(new GridFutureTimeoutException("Query timed out."));
        }
        catch (GridException e) {
            onDone(e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheQueryFutureAdapter.class, this);
    }

    /**
     *
     */
    public void printMemoryStats() {
        X.println(">>> Query future memory statistics.");
        X.println(">>>  queueSize: " + queue.size());
        X.println(">>>  allCollSize: " + allColl.size());
        X.println(">>>  keysSize: " + keys.size());
        X.println(">>>  cnt: " + cnt);
    }

    /**
     *
     */
    protected static class LocalQueryRunnable<K, V, R> extends GridRunnable {
        /** */
        private GridCacheQueryFutureAdapter<K, V, R> fut;

        /** */
        private GridCacheQueryManager<K, V> mgr;

        /** */
        private boolean single;

        /**
         * @param mgr Query manager.
         * @param fut Query future.
         * @param single Single result or not.
         */
        protected LocalQueryRunnable(GridCacheQueryManager<K, V> mgr, GridCacheQueryFutureAdapter<K, V, R> fut,
            boolean single) {
            this.mgr = mgr;
            this.fut = fut;
            this.single = single;
        }

        /** {@inheritDoc} */
        @Override public void run() {
            try {
                mgr.validateQuery(fut.query());

                mgr.runQuery(localQueryInfo(fut, single));
            }
            catch (Throwable e) {
                fut.onDone(e);
            }
        }

        /**
         * @param fut Query future.
         * @param single Single result or not.
         * @return Query info.
         */
        @SuppressWarnings({"unchecked"})
        private GridCacheQueryInfo<K, V> localQueryInfo(GridCacheQueryFutureAdapter<K, V, R> fut, boolean single) {
            this.fut = fut;

            GridCacheQueryBaseAdapter<K, V> qry = fut.query();

            GridPredicate<K> keyFilter =
                qry.remoteKeyFilter() == null ? null :
                    (GridPredicate<K>)qry.remoteKeyFilter().apply(qry.getClosureArguments());

            GridPredicate<V> valFilter =
                qry.remoteValueFilter() == null ? null :
                    (GridPredicate<V>)qry.remoteValueFilter().apply(qry.getClosureArguments());

            GridPredicate<GridCacheEntry<K, V>> prjPred =
                qry.projectionFilter() == null ? F.<GridCacheEntry<K, V>>alwaysTrue() : qry.projectionFilter();

            GridClosure<V, Object> trans = null;

            if (qry instanceof GridCacheTransformQueryAdapter) {
                GridCacheTransformQueryAdapter tmp = (GridCacheTransformQueryAdapter)qry;

                trans = tmp.remoteTransformer() == null ? null :
                    (GridClosure<V, Object>)tmp.remoteTransformer().apply(qry.getClosureArguments());
            }

            GridReducer<Map.Entry<K, V>, Object> rdc = null;

            if (qry instanceof GridCacheReduceQueryAdapter) {
                GridCacheReduceQueryAdapter tmp = (GridCacheReduceQueryAdapter)qry;

                rdc = tmp.remoteReducer() == null ? null :
                    (GridReducer<Map.Entry<K, V>, Object>)tmp.remoteReducer().apply(qry.getClosureArguments());
            }

            return new GridCacheQueryInfo<K, V>(
                true,
                single,
                keyFilter,
                valFilter,
                prjPred,
                trans,
                rdc,
                qry,
                qry.pageSize(),
                qry.readThrough(),
                qry.cloneValues(),
                qry.includeBackups(),
                fut,
                null,
                -1
            );
        }
    }

    /**
     * Iterator that converts elements of wrapped iterator to required type.
     *
     * @param <R> New iterator element type.
     */
    protected static class TypedIterator<R> extends GridIteratorAdapter<R> {
        /** */
        private Iterator<?> iter;

        /**
         * @param iter Iterator
         */
        private TypedIterator(Iterator<?> iter) {
            this.iter = iter;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return iter.hasNext();
        }

        /** {@inheritDoc} */
        @SuppressWarnings("unchecked")
        @Override public R next() {
            return (R)iter.next();
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            throw new UnsupportedOperationException("Remove is not supported.");
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(TypedIterator.class, this);
        }
    }
}

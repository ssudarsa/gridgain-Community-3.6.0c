// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.kernal.executor.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.nodestart.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridClosureCallMode.*;
import static org.gridgain.grid.kernal.GridNodeAttributes.*;
import static org.gridgain.grid.kernal.processors.task.GridTaskThreadContextKey.*;
import static org.gridgain.grid.util.nodestart.GridNodeStartUtils.*;

/**
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
abstract class GridProjectionAdapter extends GridMetadataAwareAdapter implements GridProjection {
    /** Log reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Empty rich node predicate array. */
    private static final GridPredicate<GridRichNode>[] EMPTY_PN = new PN[] {};

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    protected transient GridKernalContext ctx;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private transient GridLogger log;

    /** */
    private GridProjection parent;

    /** */
    private Random rand = new Random();

    /**
     *
     * @param parent Parent of this projection.
     * @param ctx Grid kernal context.
     */
    protected GridProjectionAdapter(@Nullable GridProjection parent, GridKernalContext ctx) {
        this(parent);

        assert ctx != null;

        setKernalContext(ctx);
    }

    /**
     *
     * @param parent Parent of this projection.
     */
    protected GridProjectionAdapter(@Nullable GridProjection parent) {
        this.parent = parent;
    }

    /**
     * Gets logger.
     *
     * @return Logger.
     */
    protected GridLogger log() {
        return log;
    }

    /**
     * <tt>ctx.gateway().readLock()</tt>
     */
    protected void guard() {
        assert ctx != null;

        ctx.gateway().readLock();
    }

    /**
     * <tt>ctx.gateway().readUnlock()</tt>
     */
    protected void unguard() {
        assert ctx != null;

        ctx.gateway().readUnlock();
    }

    /**
     * <tt>ctx.gateway().lightCheck()</tt>
     */
    protected void lightCheck() {
        assert ctx != null;

        ctx.gateway().lightCheck();
    }

    /**
     * Sets kernal context.
     *
     * @param ctx Kernal context to set.
     */
    protected void setKernalContext(GridKernalContext ctx) {
        assert ctx != null;
        assert this.ctx == null;

        this.ctx = ctx;

        if (parent == null)
            parent = ctx.grid();

        log = U.logger(ctx, logRef, GridProjectionAdapter.class);
    }

    /** {@inheritDoc} */
    @Override public Grid grid() {
        assert ctx != null;

        guard();

        try {
            return ctx.grid();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void affinityRun(
        String cacheName,
        @Nullable Object affKey,
        @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        affinityRunAsync(cacheName, affKey, job, p).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> affinityRunAsync(
        String cacheName,
        @Nullable Object affKey,
        @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        A.notNull(cacheName, "cacheName");

        guard();

        try {
            return (affKey == null || job == null) ?
                new GridFinishedFuture(ctx) :
                ctx.closure().runAsync(BALANCE, wrapRun(cacheName, affKey, job), F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /**
     *
     * @param cacheName Cache name.
     * @param affKey Affinity key.
     * @param run Run to wrap.
     * @return Wrapped call.
     */
    private CA wrapRun(final String cacheName, final Object affKey, final Runnable run) {
        return new CA() {
            @GridCacheName
            private final String cn = cacheName;

            @GridCacheAffinityMapped
            private final Object ak = affKey;

            @Override public void apply() {
                run.run();
            }
        };
    }

    /**
     *
     * @param cacheName Cache name.
     * @param affKey Affinity key.
     * @param call Call to wrap.
     * @param <R> Type of the {@code call} return value.
     * @return Wrapped call.
     */
    private <R> CO<R> wrapCall(final String cacheName, final Object affKey, final Callable<R> call) {
        return new CO<R>() {
            @GridCacheName
            private final String cn = cacheName;

            @GridCacheAffinityMapped
            private final Object ak = affKey;

            @Override public R apply() {
                try {
                    return call.call();
                }
                catch (Exception e) {
                    throw F.wrap(e);
                }
            }
        };
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> affinityCallAsync(
        final String cacheName,
        @Nullable Collection<?> affKeys,
        @Nullable final Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        A.notNull(cacheName, "cacheName");

        guard();

        try {
            return (affKeys == null || affKeys.isEmpty() || job == null) ?
                new GridFinishedFuture<Collection<R>>(ctx) :
                ctx.closure().callAsync(
                    BALANCE,
                    F.transform(
                        affKeys,
                        new C1<Object, CO<R>>() {
                            @Override public CO<R> apply(Object affKey) {
                                return wrapCall(cacheName, affKey, job);
                            }
                        }
                    ),
                    F.retain(nodes(), true, p)
                );
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> affinityCallAsync(
        String cacheName,
        @Nullable Object affKey,
        @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        A.notNull(cacheName, "cacheName");

        guard();

        try {
            return (affKey == null || job == null) ?
                new GridFinishedFuture<R>(ctx) :
                ctx.closure().callAsync(BALANCE, wrapCall(cacheName, affKey, job), F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> affinityCall(
        String cacheName,
        @Nullable Collection<?> affKeys,
        @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        return affinityCallAsync(cacheName, affKeys, job, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R> R affinityCall(
        String cacheName,
        Object affKey,
        @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        return affinityCallAsync(cacheName, affKey, job, p).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> affinityRunAsync(
        final String cacheName,
        @Nullable Collection<?> affKeys,
        @Nullable final Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {

        guard();

        try {
            return (affKeys == null || affKeys.isEmpty() || job == null) ?
                new GridFinishedFuture(ctx) :
                ctx.closure().runAsync(
                    BALANCE,
                    F.transform(
                        affKeys,
                        new C1<Object, CA>() {
                            @Override public CA apply(Object affKey) {
                                return wrapRun(cacheName, affKey, job);
                            }
                        }
                    ),
                    F.retain(nodes(), true, p)
                );
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void affinityRun(String cacheName, @Nullable Collection<?> affKeys, @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        affinityRunAsync(cacheName, affKeys, job, p).get();
    }

    /** {@inheritDoc} */
    @Override public GridProjectionMetrics projectionMetrics() throws GridException {
        guard();

        try {
            if (nodes().isEmpty())
                throw U.makeException();

            return new GridProjectionMetricsImpl(this);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T> GridOutClosure<GridFuture<T>> gridify(final GridClosureCallMode mode, final Callable<T> c,
        @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new CO<GridFuture<T>>() {
                {
                    peerDeployLike(U.peerDeployAware(c));
                }

                @Override public GridFuture<T> apply() {
                    return callAsync(mode, c, p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridOutClosure<GridFuture<?>> gridify(final GridClosureCallMode mode, final Runnable r,
        @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(r, "r");

        guard();

        try {
            return U.withMeta(new CO<GridFuture<?>>() {
                {
                    peerDeployLike(U.peerDeployAware(r));
                }

                @Override public GridFuture<?> apply() {
                    return runAsync(mode, r, p);
                }
            }, r);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E, T> GridClosure<E, GridFuture<T>> gridify(final GridClosureCallMode mode,
        final GridClosure<E, T> c, @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C1<E, GridFuture<T>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<T> apply(E e) {
                    return callAsync(mode, c.curry(e), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E1, E2, T> GridClosure2<E1, E2, GridFuture<T>> gridify(final GridClosureCallMode mode,
        final GridClosure2<E1, E2, T> c, @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C2<E1, E2, GridFuture<T>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<T> apply(E1 e1, E2 e2) {
                    return callAsync(mode, c.curry(e1, e2), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E1, E2, E3, T> GridClosure3<E1, E2, E3, GridFuture<T>> gridify(
        final GridClosureCallMode mode, final GridClosure3<E1, E2, E3, T> c,
        @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C3<E1, E2, E3, GridFuture<T>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<T> apply(E1 e1, E2 e2, E3 e3) {
                    return callAsync(mode, c.curry(e1, e2, e3), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E> GridClosure<E, GridFuture<?>> gridify(final GridClosureCallMode mode,
        final GridInClosure<E> c,
        @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C1<E, GridFuture<?>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<?> apply(E e) {
                    return runAsync(mode, c.curry(e), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E1, E2> GridClosure2<E1, E2, GridFuture<?>> gridify(final GridClosureCallMode mode,
        final GridInClosure2<E1, E2> c, @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C2<E1, E2, GridFuture<?>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<?> apply(E1 e1, E2 e2) {
                    return runAsync(mode, c.curry(e1, e2), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E1, E2, E3> GridClosure3<E1, E2, E3, GridFuture<?>> gridify(final GridClosureCallMode mode,
        final GridInClosure3<E1, E2, E3> c, @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C3<E1, E2, E3, GridFuture<?>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<?> apply(E1 e1, E2 e2, E3 e3) {
                    return runAsync(mode, c.curry(e1, e2, e3), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridOutClosure<GridFuture<Boolean>> gridify(final GridClosureCallMode mode,
        final GridAbsPredicate c, @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new CO<GridFuture<Boolean>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<Boolean> apply() {
                    return callAsync(mode, F.as(c), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E> GridClosure<E, GridFuture<Boolean>> gridify(final GridClosureCallMode mode,
        final GridPredicate<E> c, @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C1<E, GridFuture<Boolean>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<Boolean> apply(E e) {
                    return callAsync(mode, F.as(c.curry(e)), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E1, E2> GridClosure2<E1, E2, GridFuture<Boolean>> gridify(
        final GridClosureCallMode mode, final GridPredicate2<E1, E2> c,
        @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C2<E1, E2, GridFuture<Boolean>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<Boolean> apply(E1 e1, E2 e2) {
                    return callAsync(mode, F.as(c.curry(e1, e2)), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <E1, E2, E3> GridClosure3<E1, E2, E3, GridFuture<Boolean>> gridify(
        final GridClosureCallMode mode, final GridPredicate3<E1, E2, E3> c,
        @Nullable final GridPredicate<? super GridRichNode>... p) {
        A.notNull(c, "c");

        guard();

        try {
            return U.withMeta(new C3<E1, E2, E3, GridFuture<Boolean>>() {
                {
                    peerDeployLike(c);
                }

                @Override public GridFuture<Boolean> apply(E1 e1, E2 e2, E3 e3) {
                    return callAsync(mode, F.as(c.curry(e1, e2, e3)), p);
                }
            }, c);
        }
        finally {
            unguard();
        }
    }

    /**
     * Checks if all given projections are static.
     *
     * @param prjs Projections to check.
     * @return {@code True} if all given projections are static.
     */
    private boolean isAllStatic(GridProjection... prjs) {
        assert prjs != null;
        assert prjs.length > 0;

        for (GridProjection p : prjs)
            if (p.dynamic())
                return false;

        return true;
    }

    /** {@inheritDoc} */
    @Override public GridProjection merge(@Nullable GridProjection... prjs) {
        if (F.isEmpty(prjs))
            return this;

        assert prjs != null;
        assert prjs.length > 0;

        // Maintain non-dynamic status of the merged
        // projection if all constituent projections are
        // static.
        if (isAllStatic(prjs)) {
            Collection<GridRichNode> c = new GridLeanSet<GridRichNode>(nodes());

            for (GridProjection p : prjs)
                c.addAll(p.nodes());

            // New static projection.
            return newProjection(c);
        }
        else
            // New dynamic projection.
            return new GridProjectionImpl(this, ctx, F.<GridRichNode>or(F.or(F.transform(prjs, F.predicate())),
                predicate()));
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForPredicate(@Nullable GridPredicate<? super GridRichNode>... p) {
        guard();

        try {
            // New projection will be dynamic.
            return new GridProjectionImpl(this, ctx, F.and(p, predicate()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForAttribute(final String n, @Nullable final String v) {
        A.notNull(n, "n");

        return projectionForPredicate(new PN() {
            @Override public boolean apply(GridRichNode node) {
                return v == null ? node.attributes().containsKey(n) : v.equals(node.attribute(n));
            }
        });
    }

    @Override public GridProjection projectionForNodes(@Nullable Collection<? extends GridNode> nodes) {
        guard();

        try {
            if (F.isEmpty(nodes))
                return this;

            // Check for daemons in the projection.
            if (F.exist(nodes, new GridPredicate<GridNode>() {
                @Override public boolean apply(GridNode e) {
                    return ctx.rich().rich(e).isDaemon();
                }
            }))
                U.warn(log, "Creating projection with daemon node. Likely a misuse.");

            // Maintain dynamic/static static of the projection.
            return !dynamic() ? newProjection(F.retain(nodes(), true, nodes)) :
                new GridProjectionImpl(this, ctx, F.and(predicate(),
                    new GridNodePredicate<GridRichNode>(F.nodeIds(nodes))));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForCaches(@Nullable final String cacheName,
        @Nullable final String[] cacheNames) {
        return projectionForPredicate(new PN() {
            @Override public boolean apply(GridRichNode n) {
                if (!U.hasCache(n, cacheName))
                    return false;

                if (!F.isEmpty(cacheNames))
                    for (String cn : cacheNames)
                        if (!U.hasCache(n, cn))
                            return false;

                return true;
            }
        });
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public GridPair<GridProjection> split(@Nullable GridPredicate<? super GridRichNode>... p) {
        guard();

        try {
            // Maintain dynamic/static static of the projection.
            if (dynamic())
                return F.pair(projectionForPredicate(p), projectionForPredicate(F.not(p)));
            else {
                GridPair<Collection<GridRichNode>> pair = F.split(nodes(), p);

                return F.pair(newProjection(pair.get1()), newProjection(pair.get2()));
            }
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection cross(@Nullable Collection<? extends GridNode> nodes) {
        return projectionForNodes(nodes);
    }

    /** {@inheritDoc} */
    @Override public GridProjection cross0(@Nullable GridRichNode... nodes) {
        return cross(nodes == null ? null : Arrays.asList(nodes));
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodes(@Nullable GridRichNode... nodes) {
        return projectionForNodes(nodes == null ? null : Arrays.asList(nodes));
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodeIds(@Nullable UUID... ids) {
        return projectionForNodeIds(ids == null ? null : Arrays.asList(ids));
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodeIds(@Nullable Collection<UUID> ids) {
        guard();

        try {
            if (F.isEmpty(ids))
                return this;

            // Check for daemons in the projection.
            if (F.exist(ids, new GridPredicate<UUID>() {
                @Override public boolean apply(UUID id) {
                    return node(id, EMPTY_PN).isDaemon();
                }
            }))
                U.warn(log, "Creating projection with daemon node. Likely a misuse.");

            // Maintain dynamic/static static of the projection.
            return !dynamic() ? newProjection(F.retain(nodes(), true, F.<GridNode>nodeForNodeIds(ids))) :
                new GridProjectionImpl(this, ctx, F.and(predicate(), new GridNodePredicate<GridRichNode>(ids)));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection cross(@Nullable GridProjection... prjs) {
        guard();

        try {
            if (F.isEmpty(prjs))
                return this;

            assert prjs != null;
            assert prjs.length > 0;

            // Maintain non-dynamic status of the merged
            // projection if all constituent projections are
            // static.
            if (isAllStatic(prjs)) {
                Collection<GridRichNode> c = new LinkedList<GridRichNode>(nodes());

                for (GridProjection p : prjs)
                    c.retainAll(p.nodes());

                // New static projection.
                return newProjection(c);
            }
            else
                // New dynamic projection.
                return new GridProjectionImpl(this, ctx, F.<GridRichNode>and(F.and(F.transform(prjs, F.predicate())),
                    predicate()));
        }
        finally {
            unguard();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override public GridProjection parent() {
        guard();

        try {
            return parent;
        }
        finally {
            unguard();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override public int size(@Nullable GridPredicate<? super GridRichNode>... p) {
        guard();

        try {
            return nodes(p).size();
        }
        finally {
            unguard();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override public int cpus() {
        guard();

        try {
            int cpus = 0;

            for (GridProjection prj : neighborhood()) {
                GridRichNode first = F.first(prj.nodes());

                assert first != null;

                cpus += first.metrics().getTotalCpus();
            }

            return cpus;
        }
        finally {
            unguard();
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    @Override public Collection<GridProjection> neighborhood() {
        guard();

        try {
            Map<String, Collection<GridRichNode>> map = new HashMap<String, Collection<GridRichNode>>();

            for (GridRichNode n : nodes()) {
                String macs = n.attribute(ATTR_MACS);

                assert macs != null : "Missing MACs attribute: " + n;

                Collection<GridRichNode> neighbors = map.get(macs);

                if (neighbors == null)
                    map.put(macs, neighbors = new ArrayList<GridRichNode>(2));

                neighbors.add(n);
            }

            if (map.isEmpty())
                return Collections.emptyList();
            else {
                Collection<GridProjection> neighborhood = new ArrayList<GridProjection>(map.size());

                for (Collection<GridRichNode> neighbors : map.values())
                    neighborhood.add(newProjection(neighbors));

                return neighborhood;
            }
        }
        finally {
            unguard();
        }
    }

    /**
     * Utility method that creates new grid projection with necessary short-circuit logic.
     *
     * @param nodes Nodes to create projection with.
     * @return Newly created projection.
     */
    @SuppressWarnings({"ConstantConditions"})
    protected GridProjection newProjection(Collection<GridRichNode> nodes) {
        assert nodes != null;

        switch (nodes.size()) {
            case 0: return new GridProjectionImpl(this, ctx, Collections.<GridRichNode>emptyList());
            case 1: return F.first(nodes);
            default: return new GridProjectionImpl(this, ctx, nodes);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override @Nullable public GridRichNode youngest() {
        guard();

        try {
            long max = Long.MIN_VALUE;

            GridRichNode youngest = null;

            for (GridRichNode n : nodes())
                if (n.order() > max) {
                    max = n.order();
                    youngest = n;
                }

            return youngest;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override @Nullable public GridRichNode oldest() {
        guard();

        try {
            long min = Long.MAX_VALUE;

            GridRichNode oldest = null;

            for (GridRichNode n : nodes())
                if (n.order() < min) {
                    min = n.order();
                    oldest = n;
                }

            return oldest;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridRichNode randomx() throws GridEmptyProjectionException {
        GridRichNode n = random();

        if (n != null)
            return n;
        else
            throw U.makeException();
    }

    /** {@inheritDoc} */
    @Override public GridRichNode oldestx() throws GridEmptyProjectionException {
        GridRichNode n = oldest();

        if (n != null)
            return n;
        else
            throw U.makeException();
    }

    /** {@inheritDoc} */
    @Override public GridRichNode youngestx() throws GridEmptyProjectionException {
        GridRichNode n = youngest();

        if (n != null)
            return n;
        else
            throw U.makeException();
    }

    /** {@inheritDoc} */
    @Override public GridRichNode random() {
        guard();

        try {
            GridRichNode rn = null;

            Collection<GridRichNode> c = nodes();

            if (!c.isEmpty()) {
                int rnd = rand.nextInt(c.size());

                Iterator<GridRichNode> iter = c.iterator();

                int i = 0;

                do {
                    rn = iter.next();
                }
                while (i++ < rnd);
            }

            return rn;
        }
        finally {
            unguard();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override public int hosts() {
        return neighborhood().size();
    }

    /** {@inheritDoc} */
    @Override public boolean hasRemoteNodes() {
        guard();

        try {
            Collection<GridRichNode> c = nodes();

            if (c.isEmpty())
                return false; // In case of running on daemon node.
            else if (c.size() == 1) {
                GridRichNode n = F.first(c);

                assert n != null;

                return !ctx.localNodeId().equals(n.id());
            }
            else
                return true;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasLocalNode() {
        guard();

        try {
            return F.forAny(nodes(), F.localNode(ctx.localNodeId()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> R executeSync(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return this.<T, R>execute(task, arg, timeout, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T, R> R executeSync(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        long timeout, @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return this.<T, R>execute(taskCls, arg, timeout, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T, R> R executeSync(String taskName, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return this.<T, R>execute(taskName, arg, timeout, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(taskName, "taskName");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(taskName, arg, 0, null);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(taskName, "taskName");
        A.ensure(timeout >= 0, "timeout >= 0");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(taskName, arg, timeout, null);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(taskName, "taskName");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(taskName, arg, 0, lsnr);

        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(taskName, "taskName");
        A.ensure(timeout >= 0, "timeout >= 0");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(taskName, arg, timeout, lsnr);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(taskCls, "taskCls");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(taskCls, arg, 0, null);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        long timeout, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(taskCls, "taskCls");
        A.ensure(timeout >= 0, "timeout >= 0");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(taskCls, arg, timeout, null);
        }
        finally {
            unguard();
        }
    }

    /**
     * Utility method.
     *
     * @param p Predicate for the array.
     * @return One-element array.
     */
    @SuppressWarnings("unchecked")
    protected <T> GridPredicate<T>[] asArray(GridPredicate<T> p) {
        return (GridPredicate<T>[])new GridPredicate[] { p };
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(@Nullable Collection<UUID> nodeIds) {
        if (F.isEmpty(nodeIds))
            return Collections.emptyList();

        guard();

        try {
            return F.view(ctx.discovery().richNodes(nodeIds), predicate());
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> daemonNodes(GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return F.view(F.viewReadOnly(ctx.discovery().daemonNodes(), ctx.rich().richNode()),
                F.and(p, predicate()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodeId8(final String id8) {
        assert id8 != null;

        guard();

        try {
            return F.view(F.concat(false, nodes(), daemonNodes(EMPTY_PN)), new PN() {
                @Override public boolean apply(GridRichNode e) {
                    return e.id8().equals(id8);
                }
            });
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(taskCls, "taskCls");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(taskCls, arg, 0, lsnr);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        long timeout, @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(taskCls, "taskCls");
        A.ensure(timeout >= 0, "timeout >= 0");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(taskCls, arg, timeout, lsnr);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(task, "task");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(task, arg, 0, null);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(task, "task");
        A.ensure(timeout >= 0, "timeout >= 0");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(task, arg, timeout, null);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(task, "task");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(task, arg, 0, lsnr);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(task, "task");
        A.ensure(timeout >= 0, "timeout >= 0");

        guard();

        try {
            ctx.task().setThreadContext(TC_SUBGRID, F.retain(nodes(), true, p));

            return ctx.task().execute(task, arg, timeout, lsnr);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T extends Callable<R1>> R2 mapreduce(@Nullable GridMapper<T, GridRichNode> mapper,
        @Nullable Collection<T> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return mapreduceAsync(mapper, jobs, rdc, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T extends Callable<R1>> GridFuture<R2> mapreduceAsync(@Nullable GridMapper<T,
        GridRichNode> mapper, @Nullable Collection<T> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return ctx.closure().forkjoinAsync(mapper, jobs, rdc, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public long topologyHash(@Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return ctx.discovery().topologyHash(F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> remoteNodes(@Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return nodes(F.and(p, F.remoteNodes(ctx.localNodeId())));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection remoteProjection(@Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return new GridProjectionImpl(this, ctx, F.and(p, predicate(), F.not(F.nodeForNodeId(ctx.localNodeId()))));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void run(@Nullable GridMapper<Runnable, GridRichNode> mapper,
        @Nullable Collection<? extends Runnable> jobs, @Nullable GridPredicate<? super GridRichNode>[] p)
        throws GridException {
        runAsync(mapper, jobs, p).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runAsync(@Nullable GridMapper<Runnable, GridRichNode> mapper,
        @Nullable Collection<? extends Runnable> jobs, @Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return ctx.closure().runAsync(mapper, jobs, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void run(GridClosureCallMode mode, @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        runAsync(mode, job, p).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return ctx.closure().runAsync(mode, job, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void run(GridClosureCallMode mode, @Nullable Collection<? extends Runnable> jobs,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        runAsync(mode, jobs, p).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Collection<? extends Runnable> jobs,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return ctx.closure().runAsync(mode, jobs, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection withName(@Nullable String taskName) {
        if (taskName != null) {
            guard();

            try {
                ctx.task().setThreadContext(TC_TASK_NAME, taskName);
            }
            finally {
                unguard();
            }
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public GridProjection withResultClosure(@Nullable GridClosure2X<GridJobResult, List<GridJobResult>,
        GridJobResultPolicy> res) {
        if (res != null) {
            guard();

            try {
                ctx.task().setThreadContext(TC_RESULT, res);
            }
            finally {
                unguard();
            }
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public GridProjection withFailoverSpi(@Nullable String spiName) {
        if (spiName != null) {
            guard();

            try {
                ctx.task().setThreadContext(TC_FAILOVER_SPI, spiName);
            }
            finally {
                unguard();
            }
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public GridProjection withCheckpointSpi(@Nullable String spiName) {
        if (spiName != null) {
            guard();

            try {
                ctx.task().setThreadContext(TC_CHECKPOINT_SPI, spiName);
            }
            finally {
                unguard();
            }
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public GridProjection withLoadBalancingSpi(@Nullable String spiName) {
        if (spiName != null) {
            guard();

            try {
                ctx.task().setThreadContext(TC_LOAD_BALANCING_SPI, spiName);
            }
            finally {
                unguard();
            }
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public GridProjection withTopologySpi(@Nullable String spiName) {
        if (spiName != null) {
            guard();

            try {
                ctx.task().setThreadContext(TC_TOPOLOGY_SPI, spiName);
            }
            finally {
                unguard();
            }
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public boolean runOptimistic(GridAbsClosure c, int attempts, @Nullable GridAbsClosure rollback,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(c, "c");
        A.ensure(attempts >= 1, "attempts >= 1");

        guard();

        try {
            for (int i = 0; i < attempts; i++) {
                long h1 = topologyHash(p);

                c.apply();

                long h2 = topologyHash(p);

                if (h1 == h2)
                    return true;

                if (rollback != null)
                    rollback.apply();
            }

            return false;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> R callOptimistic(GridOutClosure<R> c, int attempts, R dfltVal,
        @Nullable GridAbsClosure rollback, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(c, "c");
        A.ensure(attempts >= 1, "attempts >= 1");

        guard();

        try {
            for (int i = 0; i < attempts; i++) {
                long h1 = topologyHash(p);

                R r = c.apply();

                long h2 = topologyHash(p);

                if (h1 == h2)
                    return r;

                if (rollback != null)
                    rollback.apply();
            }

            return dfltVal;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> runOptimisticAsync(final GridAbsClosure c, final int attempts,
        @Nullable final GridAbsClosure rollback, @Nullable final GridPredicate<? super GridRichNode>[] p) {
        A.notNull(c, "c");
        A.ensure(attempts >= 1, "attempts >= 1");

        guard();

        try {
            return ctx.closure().callLocalSafe(new GridOutClosure<Boolean>() {
                @Override public Boolean apply() {
                    return runOptimistic(c, attempts, rollback, p);
                }
            });
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> callOptimisticAsync(final GridOutClosure<R> c, final int attempts,
        final R dfltVal, @Nullable final GridAbsClosure rollback,
        @Nullable final GridPredicate<? super GridRichNode>[] p) {
        A.notNull(c, "c");
        A.ensure(attempts >= 1, "attempts >= 1");

        guard();

        try {
            return ctx.closure().callLocalSafe(new GridOutClosure<R>() {
                @Override public R apply() {
                    return callOptimistic(c, attempts, dfltVal, rollback, p);
                }
            });
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> R call(GridClosureCallMode mode, @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>[] p)
        throws GridException {
        return callAsync(mode, job, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> callAsync(GridClosureCallMode mode, @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().callAsync(mode, job, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> call(GridClosureCallMode mode, @Nullable Collection<? extends Callable<R>> jobs,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return callAsync(mode, jobs, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().callAsync(mode, jobs, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> R2 reduce(GridClosureCallMode mode, @Nullable Collection<? extends Callable<R1>> jobs,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return reduceAsync(mode, jobs, rdc, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends Callable<R1>> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().forkjoinAsync(mode, jobs, rdc, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void send(@Nullable Object msg, @Nullable GridPredicate<? super GridRichNode>[] p)
        throws GridException {
        guard();

        try {
            if (msg != null) {
                Collection<GridRichNode> snapshot = nodes(p);

                if (snapshot.isEmpty())
                    throw U.makeException();

                ctx.io().sendUserMessage(snapshot, msg);
            }
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void send(@Nullable Collection<?> msgs,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        guard();

        try {
            if (!F.isEmpty(msgs)) {
                assert msgs != null;

                Collection<GridRichNode> snapshot = nodes(p);

                if (snapshot.isEmpty())
                    throw U.makeException();

                for (Object msg : msgs)
                    ctx.io().sendUserMessage(snapshot, msg);
            }
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridRichNode node(UUID nodeId, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(nodeId, "nodeId");

        guard();

        try {
            return F.find(F.retain(F.concat(false, nodes(), daemonNodes(EMPTY_PN)), true, p), null,
                F.<GridRichNode>nodeForNodeId(nodeId));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isEmptyFor(@Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            if (F.isEmpty(p) || F.isAlwaysTrue(p))
                return nodes().isEmpty();

            return F.isAlwaysFalse(p) || F.size(nodes(), p) == 0;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        guard();

        try {
            return nodes().isEmpty();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean contains(GridNode node, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(node, "node");

        guard();

        try {
            return F.exist(F.retain(nodes(), true, p), F.equalTo(node));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean contains(UUID nodeId, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(nodeId, "nodeId");

        guard();

        try {
            return F.exist(F.retain(nodes(), true, p), F.nodeForNodeId(nodeId));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public List<GridEvent> remoteEvents(GridPredicate<? super GridEvent> pe, long timeout,
        @Nullable GridPredicate<? super GridRichNode>[] pn) throws GridException {
        return remoteEventsAsync(pe, timeout, pn).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<List<GridEvent>> remoteEventsAsync(GridPredicate<? super GridEvent> pe, long timeout,
        @Nullable GridPredicate<? super GridRichNode>[] pn) {
        A.notNull(pe, "pe");

        guard();

        try {
            return ctx.event().remoteEventsAsync(pe, F.retain(nodes(), true, pn), timeout);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public ExecutorService executor(@Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return new GridExecutorService(ctx.grid(), log(), p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridRichNode> iterator() {
        guard();

        try {
            return nodes().iterator();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> call(@Nullable GridMapper<Callable<R>, GridRichNode> mapper,
        @Nullable Collection<? extends Callable<R>> jobs,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return callAsync(mapper, jobs, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> callAsync(@Nullable GridMapper<Callable<R>, GridRichNode> mapper,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return ctx.closure().callAsync(mapper, jobs, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> Collection<R> call(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R>> jobs,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return callAsync(mode, jobs, args, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().callAsync(mode, F.curry(jobs, args), F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> Collection<R> call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>[] p)
        throws GridException {
        return callAsync(mode, job, args, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R> job, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().callAsync(mode, F.curry(job, args), F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> Collection<R> call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridPredicate<? super GridRichNode>[] p)
        throws GridException {
        return callAsync(mode, job, pdc, cnt, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");
        A.ensure(cnt > 0, "cnt > 0");

        guard();

        try {
            return ctx.closure().callAsync(mode, F.curry(cnt, job, pdc), F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T> void run(GridClosureCallMode mode,
        @Nullable Collection<? extends GridInClosure<? super T>> jobs,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>[] p)
        throws GridException {
        runAsync(mode, jobs, args, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> runAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridInClosure<? super T>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().runAsync(mode, F.curry0(jobs, args), F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        runAsync(mode, job, args, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().runAsync(mode, F.curry(job, args), F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable GridOutClosure<T> pdc,
        int cnt, @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        runAsync(mode, job, pdc, cnt, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");
        A.ensure(cnt > 0, "cnt > 0");

        guard();

        try {
            return ctx.closure().runAsync(mode, F.curry(cnt, job, pdc), F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 reduce(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return reduceAsync(mode, jobs, args, rdc, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().forkjoinAsync(mode, F.curry(jobs, args), rdc, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 reduce(GridClosureCallMode mode, @Nullable GridClosure<? super T, R1> job,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return reduceAsync(mode, job, args, rdc, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R1> job, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return ctx.closure().forkjoinAsync(mode, F.curry(job, args), rdc, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 reduce(GridClosureCallMode mode, @Nullable GridClosure<? super T, R1> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return reduceAsync(mode, job, pdc, cnt, rdc, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R1> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");
        A.ensure(cnt > 0, "cnt > 0");

        guard();

        try {
            return ctx.closure().forkjoinAsync(mode, F.curry(cnt, job, pdc), rdc, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return mapreduceAsync(mapper, jobs, args, rdc, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> mapreduceAsync(
        @Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return ctx.closure().forkjoinAsync(mapper, F.curry(jobs, args), rdc, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return mapreduceAsync(mapper, job, args, rdc, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> mapreduceAsync(
        @Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return ctx.closure().forkjoinAsync(mapper, F.curry(job, args), rdc, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return mapreduceAsync(mapper, job, pdc, cnt, rdc, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> mapreduceAsync(
        @Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.ensure(cnt > 0, "cnt > 0");

        guard();

        try {
            return ctx.closure().forkjoinAsync(mapper, F.curry(cnt, job, pdc), rdc, F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        runAsync(mode, job, arg, p).get();
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable T arg, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode");

        guard();

        try {
            return job == null ? new GridFinishedFuture<T>(ctx) : ctx.closure().runAsync(mode, job.curry(arg),
                F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R, T> R call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>[] p) throws GridException {
        return callAsync(mode, job, arg, p).get();
    }

    /** {@inheritDoc} */
    @Override public <R, T> GridFuture<R> callAsync(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable T arg, @Nullable GridPredicate<? super GridRichNode>[] p) {
        A.notNull(mode, "mode", job, "job");

        guard();

        try {
            return job == null ? new GridFinishedFuture<R>(ctx) : ctx.closure().callAsync(mode, job.curry(arg),
                F.retain(nodes(), true, p));
        }
        finally {
            unguard();
        }
    }

    /**
     * Runnable that registers given listeners from given nodes. This class
     * is used for registering listeners on the remote nodes.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private static class RemoteListenAsyncJob<T> extends GridRunnable {
        /** */
        @GridInstanceResource
        private Grid grid;

        /** */
        private Collection<UUID> nodeIds;

        /** */
        private GridPredicate2<UUID, ? super T>[] p;

        /**
         * @param nodeIds IDs of nodes to listen messages from.
         * @param p Set of message listeners to register.
         */
        RemoteListenAsyncJob(Collection<UUID> nodeIds, GridPredicate2<UUID, ? super T>... p) {
            assert nodeIds != null;
            assert p != null;
            assert p.length > 0;

            this.nodeIds = nodeIds;
            this.p = p;

            peerDeployLike(U.peerDeployAware0((Object[])p));
        }

        /** {@inheritDoc} */
        @Override public void run() {
            for (GridRichNode n : grid.nodes(nodeIds)) {
                n.listen(p);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> remoteListenAsync(@Nullable Collection<? extends GridNode> nodes,
        @Nullable GridPredicate2<UUID, ? super T>... p) {
        if (!F.isEmpty(nodes) && !F.isEmpty(p)) {
            guard();

            try {
                return ctx.closure().runAsync(BROADCAST, new RemoteListenAsyncJob<T>(F.nodeIds(nodes), p), nodes());
            }
            finally {
                unguard();
            }
        }
        else {
            return new GridFinishedFuture<T>(ctx);
        }
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> remoteListenAsync(@Nullable GridNode node,
        @Nullable GridPredicate2<UUID, ? super T>... p) {
        return remoteListenAsync(node == null ? null : Collections.singleton(node), p);
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> remoteListenAsync(@Nullable GridPredicate<? super GridRichNode> pn,
        @Nullable GridPredicate2<UUID, ? super T>... p) {
        return remoteListenAsync(nodes(pn), p);
    }

    /** {@inheritDoc} */
    @Override public <T> void listen(@Nullable GridPredicate2<UUID, ? super T>[] p) {
        if (!F.isEmpty(p)) {
            guard();

            try {
                ctx.io().listenAsync(nodes(), p);
            }
            finally {
                unguard();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public <K> Map<GridRichNode, Collection<K>> mapKeysToNodes(String cacheName,
        @Nullable Collection<? extends K> keys) throws GridException {
        if (!F.isEmpty(keys)) {
            guard();

            try {
                return ctx.affinity().mapKeysToNodes(cacheName, nodes(), keys, false);
            }
            finally {
                unguard();
            }
        }

        return Collections.emptyMap();
    }

    /** {@inheritDoc} */
    @Override public <K> Map<GridRichNode, Collection<K>> mapKeysToNodes(
        @Nullable Collection<? extends K> keys) throws GridException {
        if (!F.isEmpty(keys)) {
            guard();

            try {
                return ctx.affinity().mapKeysToNodes(null, nodes(), keys, false);
            }
            finally {
                unguard();
            }
        }

        return Collections.emptyMap();
    }

    /** {@inheritDoc} */
    @Nullable @Override public <K> GridRichNode mapKeyToNode(String cacheName, K key) throws GridException {
        A.notNull(key, "key");

        guard();

        try {
            return ctx.affinity().mapKeyToNode(cacheName, nodes(), key, false);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public <K> GridRichNode mapKeyToNode(K key) throws GridException {
        A.notNull(key, "key");

        guard();

        try {
            return ctx.affinity().mapKeyToNode(nodes(), key, false);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridTuple3<String, Boolean, String>> startNodes(
        File file,
        @Nullable String dfltUname,
        @Nullable String dfltPasswd,
        @Nullable File key,
        int nodes,
        @Nullable String ggHome,
        @Nullable String cfg,
        @Nullable String script,
        @Nullable String log,
        boolean restart) throws GridException {
        assert file != null;
        assert file.exists();
        assert file.isFile();
        assert nodes > 0;

        try {
            Collection<String> specs = new HashSet<String>();

            BufferedReader r = new BufferedReader(new FileReader(file));

            String line;

            while ((line = r.readLine()) != null)
                specs.add(line);

            return startNodes(specs, dfltUname, dfltPasswd, key, nodes, ggHome, cfg, script, log, restart);
        }
        catch (IOException e) {
            throw new GridException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridTuple3<String, Boolean, String>> startNodes(
        Collection<String> hostSpecs,
        @Nullable String dfltUname,
        @Nullable String dfltPasswd,
        @Nullable File key,
        int nodes,
        @Nullable String ggHome,
        @Nullable String cfg,
        @Nullable String script,
        @Nullable String log,
        boolean restart) throws GridException {
        assert hostSpecs != null;
        assert nodes > 0;

        if (key != null) {
            assert key.exists();
            assert key.isFile();
        }

        Collection<GridHost> hosts = new HashSet<GridHost>();

        for (String spec : hostSpecs)
            hosts.addAll(mkHosts(spec, dfltUname, dfltPasswd, nodes, key != null));

        Collection<GridHostRunnable> hostRuns = new ArrayList<GridHostRunnable>();

        List<GridTuple3<String, Boolean, String>> res =
            new ArrayList<GridTuple3<String, Boolean, String>>();

        for (GridHost h : hosts) {
            InetAddress addr;

            try {
                addr = InetAddress.getByName(h.host());

                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress())
                    throw new GridException("Host resolves to loopback address: " + h.host());
            }
            catch (UnknownHostException e) {
                throw new GridException("Invalid host name: " + h.host(), e);
            }

            GridProjection neighbors = null;

            for (GridProjection p : neighborhood()) {
                if (F.first(F.first(p).internalAddresses()).equals(addr.getHostAddress())) {
                    neighbors = p;

                    break;
                }
            }

            int startIdx = 1;

            if (neighbors != null) {
                if (restart)
                    neighbors.stopNodes();
                else
                    startIdx = neighbors.size() + 1;
            }

            List<GridNodeRunnable> nodeRuns = new ArrayList<GridNodeRunnable>();

            for (int i = startIdx; i <= h.nodes(); i++)
                nodeRuns.add(new GridNodeRunnable(i, h.host(), h.port(), h.uname(),
                    h.password(), key, ggHome, script, cfg, log, res));

            if (!nodeRuns.isEmpty())
                hostRuns.add(new GridHostRunnable(ctx.config().getSystemExecutorService(), nodeRuns, 5));
        }

        Collection<Future<?>> futs = new ArrayList<Future<?>>(hostRuns.size());

        try {
            for (GridHostRunnable run : hostRuns)
                futs.add(ctx.config().getSystemExecutorService().submit(run));

            for (Future<?> fut : futs)
                fut.get();
        }
        catch (Exception e) {
            throw new GridRuntimeException(e);
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public void stopNodes(@Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        guard();

        try {
            projectionForPredicate(p).withName("grid-kill").execute(new GridKillTask(false), null).get();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void stopNodes(UUID id, @Nullable UUID... ids) throws GridException {
        A.notNull(id, "id");

        stopNodes(F.<GridNode>or(F.nodeForNodeId(id), F.nodeForNodeIds(ids)));
    }

    /** {@inheritDoc} */
    @Override public void stopNodes(Collection<UUID> ids) throws GridException {
        A.notNull(ids, "ids");

        stopNodes(F.nodeForNodeIds(ids));
    }

    /** {@inheritDoc} */
    @Override public void restartNodes(@Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        guard();

        try {
            projectionForPredicate(p).withName("grid-restart").execute(new GridKillTask(true), null).get();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void restartNodes(UUID id, @Nullable UUID... ids) throws GridException {
        A.notNull(id, "id");

        restartNodes(F.<GridNode>or(F.nodeForNodeId(id), F.nodeForNodeIds(ids)));
    }

    /** {@inheritDoc} */
    @Override public void restartNodes(Collection<UUID> ids) throws GridException {
        A.notNull(ids, "ids");

        restartNodes(F.nodeForNodeIds(ids));
    }
}

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
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Adapter for transforming cache queries.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheTransformQueryAdapter<K, V, T> extends GridCacheQueryBaseAdapter<K, V>
    implements GridCacheTransformQuery<K, V, T> {
    /** Transformation closure. */
    private volatile GridClosure<Object[], GridClosure<V, T>> trans;

    /**
     * @param ctx Cache registry.
     * @param type Query type.
     * @param clause Query clause.
     * @param clsName Query class name.
     * @param prjFilter Projection filter.
     * @param prjFlags Projection flags.
     */
    public GridCacheTransformQueryAdapter(GridCacheContext<K, V> ctx, GridCacheQueryType type, String clause,
        String clsName, GridPredicate<GridCacheEntry<K, V>> prjFilter, Collection<GridCacheFlag> prjFlags) {
        super(ctx, type, clause, clsName, prjFilter, prjFlags);
    }

    /**
     * @param query Query to copy from (ignoring arguments).
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    private GridCacheTransformQueryAdapter(GridCacheTransformQueryAdapter<K, V, T> query) {
        super(query);

        trans = query.trans;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTransformQuery<K, V, T> queryArguments(@Nullable Object[] args) {
        GridCacheTransformQueryAdapter<K, V, T> copy = new GridCacheTransformQueryAdapter<K, V, T>(this);

        copy.arguments(args);

        return copy;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTransformQuery<K, V, T> closureArguments(@Nullable Object[] args) {
        GridCacheTransformQueryAdapter<K, V, T> copy = new GridCacheTransformQueryAdapter<K, V, T>(this);

        copy.setClosureArguments(args);

        return copy;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map.Entry<K, T>> executeSingle(GridProjection[] grid) {
        if (trans == null) {
            GridFutureAdapter<Map.Entry<K, T>> err = new GridFutureAdapter<Map.Entry<K, T>>(cctx.kernalContext());

            err.onDone(new GridException("Transformer must be set."));

            return err;
        }

        Collection<GridRichNode> nodes = F.retain(CU.allNodes(cctx), true, nodes(grid));

        if (qryLog.isDebugEnabled())
            qryLog.debug("Executing transform query for single result " + toShortString(nodes));

        return new SingleFuture<Map.Entry<K, T>>(nodes);
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueryFuture<Map.Entry<K, T>> execute(GridProjection[] grid) {
        if (trans == null)
            return new GridCacheErrorQueryFuture<Map.Entry<K, T>>
                (cctx.kernalContext(), new GridException("Transformer must be set for transform query."));

        Collection<GridRichNode> nodes = F.retain(CU.allNodes(cctx), true, nodes(grid));

        if (qryLog.isDebugEnabled())
            qryLog.debug(U.compact("Executing transform query " + toShortString(nodes)));

        return execute(nodes, false, false, null);
    }

    /** {@inheritDoc} */
    @Override protected void registerClasses() throws GridException {
        context().deploy().registerClass(trans);
    }

    /** {@inheritDoc} */
    @Override public void remoteTransformer(GridClosure<Object[], GridClosure<V, T>> trans) {
        synchronized (mux) {
            checkSealed();

            this.trans = trans;
        }
    }

    /**
     * @return Transformer.
     */
    public GridClosure<Object[], GridClosure<V, T>> remoteTransformer() {
        return trans;
    }

    /** {@inheritDoc} */
    @Override public void close() throws IOException {
        // No-op.
    }
}

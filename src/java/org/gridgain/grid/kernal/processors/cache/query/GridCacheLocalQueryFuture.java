// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Local query future.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheLocalQueryFuture<K, V, R> extends GridCacheQueryFutureAdapter<K, V, R> {
    /**
     * Required by {@link Externalizable}.
     */
    public GridCacheLocalQueryFuture() {
        // No-op.
    }

    /**
     * @param ctx Context.
     * @param qry Query.
     * @param loc Local query or not.
     * @param single Single result or not.
     * @param rmtRdcOnly {@code true} for reduce query when using remote reducer only,
     *      otherwise it is always {@code false}.
     * @param pageLsnr Page listener.
     */
    protected GridCacheLocalQueryFuture(GridCacheContext<K, V> ctx, GridCacheQueryBaseAdapter<K, V> qry,
        boolean loc, boolean single, boolean rmtRdcOnly, @Nullable GridInClosure2<UUID, Collection<R>> pageLsnr) {
        super(ctx, qry, loc, single, rmtRdcOnly, pageLsnr);

        locFut = ctx.closures().runLocalSafe(new LocalQueryRunnable<K, V, R>(ctx.queries(), this, single), true);
    }

    /** {@inheritDoc} */
    @Override protected void cancelQuery() throws GridException {
        locFut.cancel();
    }

    /** {@inheritDoc} */
    @Override protected boolean onLastPage(UUID nodeId) {
        return true;
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

/**
 * Cache gateway.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridToStringExclude
public class GridCacheGateway<K, V> {
    /** Context. */
    private final GridCacheContext<K, V> ctx;

    /**
     * @param ctx Cache context.
     */
    public GridCacheGateway(GridCacheContext<K, V> ctx) {
        assert ctx != null;

        this.ctx = ctx;
    }

    /**
     * Enter a cache call.
     */
    public void enter() {
        ctx.deploy().onEnter();

        ctx.kernalContext().gateway().readLock();
    }

    /**
     * Leave a cache call entered by {@link #enter()} method.
     */
    public void leave() {
        // Unwind eviction notifications.
        CU.unwindEvicts(ctx);

        ctx.kernalContext().gateway().readUnlock();
    }

    /**
     * @param prj Projection to guard.
     * @return Previous projection set on this thread.
     */
    @Nullable public GridCacheProjectionImpl<K, V> enter(GridCacheProjectionImpl<K, V> prj) {
        try {
            ctx.preloader().startFuture().get();
        }
        catch (GridException e) {
            throw new GridRuntimeException("Failed to wait for cache preloader start [cacheName=" +
                ctx.name() + "]", e);
        }

        ctx.deploy().onEnter();

        ctx.kernalContext().gateway().readLock();

        // Set thread local projection per call.
        GridCacheProjectionImpl<K, V> prev = ctx.projectionPerCall();

        if (prev != null || prj != null)
            ctx.projectionPerCall(prj);

        return prev;
    }

    /**
     * @param prev Previous.
     */
    public void leave(GridCacheProjectionImpl<K, V> prev) {
        // Unwind eviction notifications.
        CU.unwindEvicts(ctx);

        // Return back previous thread local projection per call.
        ctx.projectionPerCall(prev);

        ctx.kernalContext().gateway().readUnlock();
    }
}

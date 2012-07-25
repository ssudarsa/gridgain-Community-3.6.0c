// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Adapter for preloading which always assumes that preloading finished.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCachePreloaderAdapter<K, V> implements GridCachePreloader<K, V> {
    /** Cache context. */
    protected final GridCacheContext<K, V> cctx;

    /** Logger.*/
    protected final GridLogger log;

    /** Affinity. */
    protected final GridCacheAffinity<K> aff;

    /** Start future (always completed by default). */
    private final GridFuture finFut;

    /**
     * @param cctx Cache context.
     */
    public GridCachePreloaderAdapter(GridCacheContext<K, V> cctx) {
        assert cctx != null;

        this.cctx = cctx;

        log = cctx.logger(getClass());
        aff = cctx.config().getAffinity();

        finFut = new GridFinishedFuture(cctx.kernalContext());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> startFuture() {
        return finFut;
    }

    /**
     * Gets consistent preload entry.
     *
     * @param entry Cached entry.
     * @return Preload entry or {@code null} if entry has been removed
     *      or has value equal to null.
     */
    @Nullable protected GridCacheEntryInfo<K, V> createEntryInfo(GridCacheEntryEx<K, V> entry) {
        assert entry != null;

        GridCacheEntryInfo<K, V> info = entry.info();

        return info != null && info.value() != null ? info : null;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Object> request(Collection<? extends K> keys, long topVer) {
        return new GridFinishedFuture<Object>(cctx.kernalContext());
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.datastructures;

import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.processors.cache.*;

/**
 * Community manager of data structures.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridCacheCommunityDataStructuresManager<K, V> extends GridCacheDataStructuresManager<K, V> {
    /** Error message. */
    private static final String MSG = "Cache data structures";

    /** {@inheritDoc} */
    @Override public GridCacheAtomicSequence sequence(String name, long initVal, boolean persistent, boolean create) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public boolean removeSequence(String name) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public GridCacheAtomicLong atomicLong(String name, long initVal, boolean persistent, boolean create) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAtomicLong(String name) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheAtomicReference<T> atomicReference(String name, T initVal, boolean persistent,
        boolean create) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAtomicReference(String name) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public <T, S> GridCacheAtomicStamped<T, S> atomicStamped(String name, T initVal, S initStamp,
        boolean create) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAtomicStamped(String name) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheQueue<T> queue(String name, GridCacheQueueType type, int capacity,
        boolean collocated, boolean create) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public boolean removeQueue(String name, int batchSize) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public GridCacheCountDownLatch countDownLatch(String name, int cnt, boolean autoDel, boolean create) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public boolean removeCountDownLatch(String name) {
        throw new GridEnterpriseFeatureException(MSG);
    }

    /** {@inheritDoc} */
    @Override public void onTxCommitted(GridCacheTxEx tx) {
        // This method is always called on transaction commit from transaction
        // manager and it should be no-op for community edition.
    }

    /** {@inheritDoc} */
    @Override public void onPartitionsChange() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public GridCacheAnnotationHelper<GridCacheQueuePriority> priorityAnnotations() {
        throw new GridEnterpriseFeatureException(MSG);
    }
}

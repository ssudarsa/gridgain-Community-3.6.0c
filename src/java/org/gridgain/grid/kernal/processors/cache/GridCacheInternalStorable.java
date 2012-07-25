// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

/**
 * Provides ability to store internal cache structures.
 * User must provide implementation of storage.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridCacheInternalStorable<T> extends GridCacheInternal{
    /**
     * Persisted flag.
     *
     * @return {@code true} if object should be stored, {@code false} otherwise.
     */
     public boolean persistent();

    /**
     * Returns value from internal object which will be stored.
     * User must provide implementation of storage.
     *
     * @return Value, which will be stored.
     */
    public T cached2Store();
}

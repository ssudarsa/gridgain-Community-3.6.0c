// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.jetbrains.annotations.*;

/**
 * Key is used for caching internal and storable cache structures.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridCacheInternalStorableKey<E1, R> extends GridCacheInternal {
    /**
     *
     * @return Name of additional cache structure.
     */
    public String name();

    /**
     *
     * @param val Value from store.
     * @return Result of transformation.
     */
    @Nullable public R stored2cache(E1 val);
}

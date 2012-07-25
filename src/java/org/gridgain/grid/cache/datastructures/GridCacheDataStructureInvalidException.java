// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.datastructures;

import org.gridgain.grid.*;
import org.jetbrains.annotations.*;

/**
 * This exception gets thrown if attempt to access an invalid data structure has been made.
 * Data structure may become invalid if communication with remote nodes has been lost or
 * any other error condition happened that prevented from insuring consistent state.
 * <p>
 * The best way to handle this error is to discard the invalid data structure instance and try
 * getting the underlying data structure from cache again.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheDataStructureInvalidException extends GridException {
    /**
     * Creates new exception with given error message.
     *
     * @param msg Error message.
     */
    public GridCacheDataStructureInvalidException(String msg) {
        super(msg);
    }

    /**
     * Creates new exception with given throwable as a nested cause and
     * source of error message.
     *
     * @param cause Non-null throwable cause.
     */
    public GridCacheDataStructureInvalidException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    /**
     * Creates a new exception with given error message and optional nested cause exception.
     *
     * @param msg Error message.
     * @param cause Optional nested exception (can be {@code null}).
     */
    public GridCacheDataStructureInvalidException(String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.jetbrains.annotations.*;

/**
 * This exception is used to indicate error with grid topology (e.g., crashed node, etc.).
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridTopologyException extends GridException {
    /**
     * Creates new topology exception with given error message.
     *
     * @param msg Error message.
     */
    public GridTopologyException(String msg) {
        super(msg);
    }

    /**
     * Creates new topology exception with given error message and optional
     * nested exception.
     *
     * @param msg Error message.
     * @param cause Optional nested exception (can be {@code null}).
     */
    public GridTopologyException(String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}

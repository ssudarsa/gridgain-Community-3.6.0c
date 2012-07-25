// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;
import org.jetbrains.annotations.*;

/**
 * Internal task session interface.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridTaskSessionInternal extends GridTaskSession {
    /**
     * @return Checkpoint SPI name.
     */
    public String getCheckpointSpi();

    /**
     * @return Topology SPI name.
     */
    public String getTopologySpi();

    /**
     * @return Job ID (possibly <tt>null</tt>).
     */
    @Nullable public GridUuid getJobId();

    /**
     * @return {@code True} if task node.
     */
    public boolean isTaskNode();

    /**
     * Closes session.
     */
    public void onClosed();

    /**
     * @return Checks if session is closed.
     */
    public boolean isClosed();

    /**
     * @return Task session.
     */
    public GridTaskSessionInternal session();
}

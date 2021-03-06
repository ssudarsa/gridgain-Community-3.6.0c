// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;

import java.util.*;

/**
 * This interface should be implemented by all distributed futures.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridCacheFuture<R> extends GridFuture<R> {
    /**
     * @return Unique identifier for this future.
     */
    public GridUuid futureId();

    /**
     * @return Future version.
     */
    public GridCacheVersion version();

    /**
     * @return Involved nodes.
     */
    public Collection<? extends GridNode> nodes();

    /**
     * Callback for when node left.
     *
     * @param nodeId Left node ID.
     * @return {@code True} if future cared about this node.
     */
    public boolean onNodeLeft(UUID nodeId);

    /**
     * @return {@code True} if future should be tracked.
     */
    public boolean trackable();

    /**
     * Marks this future as non-trackable.
     */
    public void markNotTrackable();
}

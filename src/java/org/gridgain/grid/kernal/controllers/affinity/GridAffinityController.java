// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers.affinity;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.controllers.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Data affinity controller.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridAffinityController extends GridController {
    /**
     * @param cacheName Cache name.
     * @param keys Keys.
     * @param nodes Allowed nodes.
     * @param sys If {@code true}, request will be performed on system pool.
     * @return Affinity map.
     * @throws GridException If failed.
     */
    public <K> Map<GridRichNode, Collection<K>> mapKeysToNodes(@Nullable String cacheName,
        Collection<GridRichNode> nodes, @Nullable Collection<? extends K> keys, boolean sys) throws GridException;

    /**
     * @param keys Keys.
     * @param nodes Allowed nodes.
     * @param sys If {@code true}, request will be performed on system pool.
     * @return Affinity map.
     * @throws GridException If failed.
     */
    public <K> Map<GridRichNode, Collection<K>> mapKeysToNodes(Collection<GridRichNode> nodes,
        @Nullable Collection<? extends K> keys, boolean sys) throws GridException;

    /**
     * @param cacheName Cache name.
     * @param key Key.
     * @param nodes Allowed nodes.
     * @param sys If {@code true}, request will be performed on system pool.
     * @return Affinity map.
     * @throws GridException If failed.
     */
    @Nullable public <K> GridRichNode mapKeyToNode(@Nullable String cacheName, Collection<GridRichNode> nodes, K key,
        boolean sys) throws GridException;

    /**
     * @param key Key.
     * @param nodes Allowed nodes.
     * @param sys If {@code true}, request will be performed on system pool.
     * @return Affinity map.
     * @throws GridException If failed.
     */
    @Nullable public <K> GridRichNode mapKeyToNode(Collection<GridRichNode> nodes, K key, boolean sys)
        throws GridException;
}

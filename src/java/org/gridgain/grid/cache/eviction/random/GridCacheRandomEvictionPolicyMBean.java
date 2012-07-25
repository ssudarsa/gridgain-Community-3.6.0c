// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.random;

import org.gridgain.grid.util.mbean.*;

/**
 * MBean for {@code random} eviction policy.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridMBeanDescription("MBean for random cache eviction policy.")
public interface GridCacheRandomEvictionPolicyMBean {
    /**
     * Gets maximum allowed cache size.
     *
     * @return Maximum allowed cache size.
     */
    @GridMBeanDescription("Maximum allowed cache size.")
    public int getMaxSize();

    /**
     * Sets maximum allowed cache size.
     *
     * @param max Maximum allowed cache size.
     */
    @GridMBeanDescription("Sets maximum allowed cache size.")
    public void setMaxSize(int max);

    /**
     * Gets flag indicating whether empty entries (entries with {@code null} values)
     * are allowed.
     *
     * @return {@code True} if empty entries are allowed, {@code false} otherwise.
     */
    @GridMBeanDescription("Flag indicating whether empty entries are allowed.")
    public boolean isAllowEmptyEntries();

    /**
     * Sets flag that allows empty entries (entries with {@code null} values)
     * to be stored in cache.
     *
     * @param allowEmptyEntries If {@code false}, empty entries will be evicted immediately.
     */
    @GridMBeanDescription("Sets flag allowing presence of empty entries in cache.")
    public void setAllowEmptyEntries(boolean allowEmptyEntries);
}

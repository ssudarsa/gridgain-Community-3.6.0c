// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.lirs;

import org.gridgain.grid.util.mbean.*;

/**
 * MBean for {@code LIRS} eviction policy.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridMBeanDescription("MBean for LIRS cache eviction policy.")
public interface GridCacheLirsEvictionPolicyMBean {
    /**
     * Gets name of metadata attribute used to store eviction policy data.
     *
     * @return Name of metadata attribute used to store eviction policy data.
     */
    @GridMBeanDescription("Name of metadata attribute used to store eviction policy data.")
    public String getMetaAttributeName();

    /**
     * Gets maximum allowed cache size.
     *
     * @return Maximum allowed cache size.
     */
    @GridMBeanDescription("Maximum allowed main stack size.")
    public int getMaxSize();

    /**
     * Sets maximum allowed cache size.
     *
     * @param max Maximum allowed cache size.
     */
    @GridMBeanDescription("Sets maximum allowed main stack size.")
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

    /**
     * Gets ratio for {@code HIRS} queue size relative to main stack size.
     *
     * @return Ratio for {@code HIRS} queue size relative to main stack size.
     */
    @GridMBeanDescription("Ratio for HIRS queue size relative to main stack size.")
    public double getQueueSizeRatio();

    /**
     * Gets maximum allowed size of {@code HIRS} queue before entries will start getting evicted.
     * This value is computed based on {@link #getQueueSizeRatio()} value.
     *
     * @return Maximum allowed size of {@code HIRS} queue before entries will start getting evicted.
     */
    @GridMBeanDescription("Maximum allowed HIRS queue size.")
    public int getMaxQueueSize();

    /**
     * Gets maximum allowed size of main stack This value is computed based on
     * {@link #getQueueSizeRatio()} value.
     *
     * @return Maximum allowed size of main stack.
     */
    @GridMBeanDescription("Maximum allowed HIRS queue size.")
    public int getMaxStackSize();

    /**
     * Gets current main stack size.
     *
     * @return Current main stack size.
     */
    @GridMBeanDescription("Current main stack size.")
    public int getCurrentStackSize();

    /**
     * Gets current {@code HIRS} queue size.
     *
     * @return Current {@code HIRS} queue size.
     */
    @GridMBeanDescription("Current HIRS queue size.")
    public int getCurrentQueueSize();
}

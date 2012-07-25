// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.metrics.jdk;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.metrics.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management MBean for {@link GridJdkLocalMetricsSpi} SPI.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridMBeanDescription("MBean that provides access to JDK local metrics SPI configuration.")
public interface GridJdkLocalMetricsSpiMBean extends GridLocalMetrics, GridSpiManagementMBean {
    /**
     * Configuration parameter indicating if Hyperic Sigar should be used regardless
     * of JDK version. Hyperic Sigar is used to provide CPU load. Starting with JDK 1.6,
     * method {@code OperatingSystemMXBean.getSystemLoadAverage()} method was added.
     * However, even in 1.6 and higher this method does not always provide CPU load
     * on some operating systems - in such cases Hyperic Sigar will be used automatically.
     *
     * @return If {@code true} then Hyperic Sigar should be used regardless of JDK version,
     *      if {@code false}, then implementation will attempt to use
     *      {@code OperatingSystemMXBean.getSystemLoadAverage()} for JDK 1.6 and higher.
     */
    @GridMBeanDescription("Parameter indicating if Hyperic Sigar should be used regardless of JDK version.")
    public boolean isPreferSigar();

    /**
     * Checks whether file system metrics are enabled. These metrics may be expensive to get in
     * certain environments and are disabled by default.
     *
     * @return Flag indicating whether file system metrics are enabled.
     * @see GridJdkLocalMetricsSpi#setFileSystemMetricsEnabled(boolean)
     */
    @GridMBeanDescription("Flag indicating whether file system metrics are enabled.")
    public boolean isFileSystemMetricsEnabled();

    /**
     * Gets root from which file space metrics should be counted. This property only makes sense
     * if {@link #isFileSystemMetricsEnabled()} set to {@code true}.
     *
     * @return Root from which file space metrics should be counted.
     */
    @GridMBeanDescription("Root from which file space metrics should be counted.")
    public String getFileSystemRoot();
}

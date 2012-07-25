package org.gridgain.grid.spi.checkpoint.cache;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management bean that provides general administrative and configuration information
 * about cache checkpoint SPI.
 * 
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridMBeanDescription("MBean provides information about cache checkpoint SPI.")
public interface GridCacheCheckpointSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets cache name to be used by this SPI..
     *
     * @return Cache name to be used by this SPI.
     */
    @GridMBeanDescription("Cache name to be used by this SPI.")
    public String getCacheName();
}

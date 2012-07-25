// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.jboss;

import org.jboss.system.*;

/**
 * This MBean interface defines service interface for JBoss loader.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridJbossLoaderMBean extends ServiceMBean {
    /**
     * Gets configuration file path set in XML configuration for this service.
     *
     * @return Configuration file path.
     */
    public String getConfigurationFile();

    /**
     * Sets configuration file path.
     *
     * @param cfgFile Configuration file path.
     */
    public void setConfigurationFile(String cfgFile);
}

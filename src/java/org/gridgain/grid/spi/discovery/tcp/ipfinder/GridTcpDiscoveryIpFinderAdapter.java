// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.ipfinder;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.typedef.internal.*;

/**
 * IP finder interface implementation adapter.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public abstract class GridTcpDiscoveryIpFinderAdapter implements GridTcpDiscoveryIpFinder {
    /** Shared flag. */
    private boolean shared;

    /** {@inheritDoc} */
    @Override public boolean isShared() {
        return shared;
    }

    /**
     * Sets shared flag. If {@code true} then it is expected that IP addresses registered
     * with IP finder will be seen by IP finders on all other nodes.
     *
     * @param shared {@code true} if this IP finder is shared.
     */
    @GridSpiConfiguration(optional = true)
    public void setShared(boolean shared) {
        this.shared = shared;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryIpFinderAdapter.class, this);
    }
}

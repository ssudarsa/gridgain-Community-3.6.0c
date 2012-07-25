// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.events;

import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * TODO.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridLicenseEvent extends GridEventAdapter {
    /** License ID. */
    private UUID licId;

    /**
     * No-arg constructor.
     */
    public GridLicenseEvent() {
        // No-op.
    }

    /**
     * Creates license event with given parameters.
     *
     * @param nodeId Node ID.
     * @param msg Optional message.
     * @param type Event type.
     */
    public GridLicenseEvent(UUID nodeId, String msg, int type) {
        super(nodeId, msg, type);
    }

    /** {@inheritDoc} */
    @Override public String shortDisplay() {
        return name() + ": licId8=" + U.id8(licId) + ", msg=" + message();
    }

    /**
     * Gets license ID.
     *
     * @return License ID.
     */
    public UUID licenseId() {
        return licId;
    }

    /**
     * Sets license ID.
     *
     * @param licId License ID to set.
     */
    public void licenseId(UUID licId) {
        this.licId = licId;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridLicenseEvent.class, this,
            "nodeId8", U.id8(nodeId()),
            "msg", message(),
            "type", name(),
            "tstamp", timestamp());
    }
}

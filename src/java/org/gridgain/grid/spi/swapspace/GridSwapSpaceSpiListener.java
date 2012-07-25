// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.swapspace;

import org.jetbrains.annotations.*;

/**
 * Swap space SPI eviction listener.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridSwapSpaceSpiListener {
    /**
     * Notification for swap space events.
     *
     * @param evtType Event type. See {@link org.gridgain.grid.events.GridSwapSpaceEvent}
     * @param spaceName Space name for this event or {@code null} for default space.
     * @param keyBytes Key bytes of affected entry. Not {@code null} only for evict notifications.
     */
    public void onSwapEvent(int evtType, @Nullable String spaceName, @Nullable byte[] keyBytes);
}

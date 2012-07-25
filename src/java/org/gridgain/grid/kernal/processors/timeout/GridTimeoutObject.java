// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.timeout;

import org.gridgain.grid.lang.utils.*;

/**
 * All objects that can timeout should implement this interface.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridTimeoutObject {
    /**
     * @return ID of the object.
     */
    public GridUuid timeoutId();

    /**
     * @return End time.
     */
    public long endTime();

    /**
     * Timeout callback.
     */
    public void onTimeout();
}

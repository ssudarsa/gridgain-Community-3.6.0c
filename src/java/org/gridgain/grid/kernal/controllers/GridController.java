// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers;

import org.gridgain.grid.kernal.*;
import org.gridgain.grid.util.tostring.*;

/**
 * Controller for enterprise kernal-level functionality.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridToStringExclude
public interface GridController extends GridComponent {
    /**
     * Indicates whether or not implementation for this controller is provided. Generally
     * community edition does not provides implementations for controller and kernal
     * substitute them with no-op dynamic proxies.
     * <p>
     * This method should be used in those rare use cases when controller method return
     * value. In such cases, the kernal's dynamic proxy will throw exception and to avoid
     * it the caller can call this method to see if implementation is actually provided.
     *
     * @return {@code True} if controller is implemented (Enterprise Edition), {@link false}
     *      otherwise (Community Edition).
     */
    public boolean implemented();
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.lifecycle;

import org.gridgain.grid.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;

import static org.gridgain.grid.GridLifecycleEventType.*;

/**
 * Simple {@link GridLifecycleBean} implementation that outputs event type when it is occurred.
 * Please refer to the log output to find print outs of GridGain lifecycle events.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridLifecycleExampleBean implements GridLifecycleBean {
    /**
     * Grid instance will be automatically injected. For additional resources
     * that can be injected into lifecycle beans see {@link GridLifecycleBean}
     * documentation.
     */
    @GridInstanceResource
    private Grid grid;

    /** */
    private boolean isStarted;

    /**
     * {@inheritDoc}
     */
    @Override public void onLifecycleEvent(GridLifecycleEventType evt) {
        X.println("");
        X.println(">>>");
        X.println(">>> Grid lifecycle event occurred: " + evt);
        X.println(">>> Grid name: " + grid.name());
        X.println(">>>");
        X.println("");

        if (evt == AFTER_GRID_START) {
            isStarted = true;
        }
        else if (evt == AFTER_GRID_STOP) {
            isStarted = false;
        }
    }

    /**
     * Checks if grid has been started.
     *
     * @return {@code True} if grid has been started.
     */
    public boolean isStarted() {
        return isStarted;
    }
}

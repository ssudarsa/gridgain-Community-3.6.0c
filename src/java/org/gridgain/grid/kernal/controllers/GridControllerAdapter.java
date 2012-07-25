// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.util.tostring.*;

/**
 * Convenient adapter for grid controllers.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public abstract class GridControllerAdapter implements GridController {
    /** Kernal context. */
    @GridToStringExclude
    protected GridKernalContext ctx;

    /** Grid logger. */
    @GridToStringExclude
    protected GridLogger log;

    /**
     * @param ctx Kernal context.
     */
    protected GridControllerAdapter(GridKernalContext ctx) {
        this.ctx = ctx;

        log = ctx.log(getClass());
    }

    /** {@inheritDoc} */
    @Override public boolean implemented() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel, boolean wait) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        // No-op.
    }
}

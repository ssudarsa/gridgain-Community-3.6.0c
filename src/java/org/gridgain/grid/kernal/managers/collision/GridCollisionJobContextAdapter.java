// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.collision;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.job.*;
import org.gridgain.grid.spi.collision.*;

/**
 * Adapter for {@link GridCollisionJobContext}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public abstract class GridCollisionJobContextAdapter implements GridCollisionJobContext {
    /** */
    private final GridJobWorker jobWorker;

    /**
     * @param jobWorker Job worker instance.
     */
    protected GridCollisionJobContextAdapter(GridJobWorker jobWorker) {
        assert jobWorker != null;

        this.jobWorker = jobWorker;
    }

    /** {@inheritDoc} */
    @Override public GridJobSessionImpl getTaskSession() {
        return jobWorker.getSession();
    }

    /** {@inheritDoc} */
    @Override public GridJobContext getJobContext() {
        return jobWorker.getJobContext();
    }

    /**
     * @return Job worker.
     */
    public GridJobWorker getJobWorker() {
        return jobWorker;
    }

    /** {@inheritDoc} */
    @Override public GridJob getJob() {
        return jobWorker.getJob();
    }
}

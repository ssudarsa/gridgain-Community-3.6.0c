// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.stopwatch;

import org.gridgain.grid.typedef.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Stopwatch.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridToStringExclude
public interface GridStopwatch {
    /**
     * @return Watch name.
     */
    public GridStopWatchName name();

    /**
     * Reset watch explicitly (watch is automatically reset on creation).
     */
    public void watch();

    /**
     * Stop watch.
     */
    public void stop();

    /**
     * Checkpoint a single step.
     *
     * @param stepName Step name.
     */
    public void step(String stepName);

    /**
     * Checkpoint a single step and then stop this watch. This method is analogous
     * to calling:
     * <pre>
     *     step(stepName);
     *     stop();
     * </pre>
     *
     * @param stepName Step name.
     */
    public void lastStep(String stepName);

    /**
     * @return Map of steps keyed by name containing execution count and total time for the step.
     */
    public Map<GridStopWatchName, T2<AtomicInteger, AtomicLong>> steps();
}

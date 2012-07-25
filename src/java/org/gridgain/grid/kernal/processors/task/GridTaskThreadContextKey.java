// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.task;

import org.gridgain.grid.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Defines keys for thread-local context in task processor.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public enum GridTaskThreadContextKey {
    /** Failover SPI name. */
    TC_FAILOVER_SPI,

    /** Topology SPI name. */
    TC_TOPOLOGY_SPI,

    /** Load balancing SPI name. */
    TC_LOAD_BALANCING_SPI,

    /** Checkpoint SPI name. */
    TC_CHECKPOINT_SPI,

    /** Task name. */
    TC_TASK_NAME,

    /** Ad-hoc task {@link GridTask#result(GridJobResult, List)} method implementation. */
    TC_RESULT,

    /** Projection for the task. */
    TC_SUBGRID;

    /** Enum values. */
    private static final GridTaskThreadContextKey[] VALS = values();

    /**
     * @param ord Byte to convert to enum.
     * @return Enum.
     */
    @Nullable
    public static GridTaskThreadContextKey fromOrdinal(int ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }
}

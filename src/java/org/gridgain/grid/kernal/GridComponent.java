// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;

/**
 * Interface for all main internal GridGain components (managers, processors, and controllers).
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridComponent {
    /**
     * Starts grid component.
     *
     * @throws GridException Throws in case of any errors.
     */
    public void start() throws GridException;

    /**
     * Stops grid component.
     *
     * @param cancel If {@code true}, then all ongoing tasks or jobs for relevant
     *      components need to be cancelled.
     * @param wait Flag indicating whether to wait for task completion.
     * @throws GridException Thrown in case of any errors.
     */
    public void stop(boolean cancel, boolean wait) throws GridException;

    /**
     * Callback that notifies that kernal has successfully started,
     * including all managers and processors.
     *
     * @throws GridException Thrown in case of any errors.
     */
    public void onKernalStart() throws GridException;

    /**
     * Callback to notify that kernal is about to stop.
     *
     * @param cancel Flag indicating whether jobs should be canceled.
     * @param wait Flag indicating whether to wait for task completion.
     */
    public void onKernalStop(boolean cancel, boolean wait);

    /**
     * Prints memory statistics (sizes of internal structures, etc.).
     *
     * NOTE: this method is for testing and profiling purposes only.
     */
    public void printMemoryStats();
}

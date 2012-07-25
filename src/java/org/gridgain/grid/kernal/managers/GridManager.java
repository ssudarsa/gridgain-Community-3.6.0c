// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;

/**
 * This interface defines life-cycle for kernal manager. Managers provide layer of indirection
 * between kernal and SPI modules. Kernel never calls SPI modules directly but
 * rather calls manager that further delegate the apply to specific SPI module.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridToStringExclude
public interface GridManager extends GridComponent {
    /**
     * Adds attributes from underlying SPI to map of all attributes.
     *
     * @param attrs Map of all attributes gotten from SPI's so far.
     * @throws GridException Wrapper for exception thrown by underlying SPI.
     */
    public void addSpiAttributes(Map<String, Object> attrs) throws GridException;
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.gridify;

import org.gridgain.grid.gridify.*;
import java.util.*;

/**
 * Simple bean interface for Spring AOP-based annotations example.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridifySpringMathematics {
    /**
     * Finds maximum value in collection.
     *
     * @param input Input collection.
     * @return Maximum value.
     */
    @GridifySetToValue(threshold = 2, splitSize = 2)
    public Long findMaximum(Collection<Long> input);

    /**
     * Finds prime numbers in collection.
     *
     * @param input Input collection.
     * @return Prime numbers.
     */
    @GridifySetToSet(threshold = 2, splitSize = 2)
    public Collection<Long> findPrimes(Collection<Long> input);
}

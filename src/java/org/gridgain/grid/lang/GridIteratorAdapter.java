// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.*;

import java.util.*;

/**
 * Convenient adapter for "rich" iterator interface.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public abstract class GridIteratorAdapter<T> implements GridIterator<T> {
    /** {@inheritDoc} */
    @Override public Iterator<T> iterator() {
        return this;
    }

    /** {@inheritDoc} */
    @Override public T nextX() throws GridException {
        return next();
    }

    /** {@inheritDoc} */
    @Override public boolean hasNextX() throws GridException {
        return hasNext();
    }
}

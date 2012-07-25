// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.lang

import org.gridgain.grid.lang.*

/**
 * Adapter for Java's {@code GridMapper}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
abstract class GroverMapper<T1, T2> extends GridMapper<T1, T2> {
    /** Map function. */
    private Function1<T1, T2> f

    /**
     * Calls {@code map(Collection<t2>)} method to create
     * map function based on given values.
     *
     * @param vals Values.
     */
    @Override void collect(Collection<T2> vals) {
        assert(vals != null)

        f = map(vals)
    }

    /**
     * Calls map function.
     *
     * @param e Map function argument.
     * @return Map function result.
     */
    @Override T2 apply(T1 e) {
        assert f != null

        f.call(e)
    }

    /**
     * Map function (can be specified as Groovy closure).
     *
     * @param c Collection of values.
     * @return Map function.
     */
    abstract Function1<T1, T2> map(Collection<T2> c);
}

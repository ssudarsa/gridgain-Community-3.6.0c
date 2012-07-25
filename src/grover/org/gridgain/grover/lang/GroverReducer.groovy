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
 * Adapter for Java's {@code GridReducer}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
abstract class GroverReducer<E, R> extends GridReducer<E, R> {
    /** Buffer. */
    private final Collection<E> buf = new LinkedList<E>()

    /**
     * Collects value.
     *
     * @param e Value.
     * @return Whether to continue collecting (always {@code true}).
     */
    @Override boolean collect(E e) {
        buf.add(e)

        true
    }

    /**
     * Reduces collected values.
     *
     * @return Reduce result.
     */
    @Override R apply() {
        reduce(buf)
    }

    /**
     * Reduce function (can be specified by Groovy closure).
     *
     * @param c Collection of values to reduce.
     * @return Reduce result.
     */
    abstract R reduce(Collection<E> c);
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */
 
package org.gridgain.scalar.lang

import org.gridgain.grid.lang.GridPredicate2X

/**
 * Wrapping Scala function for `GridPredicate2X`.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class ScalarPredicate2XFunction[T1, T2](val inner: GridPredicate2X[T1, T2]) extends ((T1, T2) => Boolean) {
    assert(inner != null)

    /**
     * Delegates to passed in grid predicate.
     */
    def apply(t1: T1, t2: T2): Boolean = {
        inner.applyx(t1, t2)
    }
}

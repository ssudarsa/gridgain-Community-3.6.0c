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

import org.gridgain.grid.lang.GridInClosure2X

/**
 * Wrapping Scala function for `GridInClosure2X`.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class ScalarInClosure2XFunction[T1, T2](val inner: GridInClosure2X[T1, T2]) extends ((T1, T2) => Unit) {
    assert(inner != null)

    /**
     * Delegates to passed in grid closure.
     */
    def apply(t1: T1, t2: T2) {
        inner.applyx(t1, t2)
    }
}

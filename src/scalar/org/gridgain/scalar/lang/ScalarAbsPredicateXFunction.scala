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

import org.gridgain.grid.lang._

/**
 * Wrapping Scala function for `GridAbsPredicateX`.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class ScalarAbsPredicateXFunction(val inner: GridAbsPredicateX) extends (() => Boolean) {
    assert(inner != null)

    /**
     * Delegates to passed in grid predicate.
     */
    def apply(): Boolean = {
        inner.applyx
    }
}

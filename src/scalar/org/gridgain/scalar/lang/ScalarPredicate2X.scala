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

import org.gridgain.grid.util.{GridUtils => U}
import org.gridgain.grid.lang._
import org.gridgain.grid._

/**
 * Peer deploy aware adapter for Java's `GridPredicate2X`.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class ScalarPredicate2X[T1, T2](private val p: (T1, T2) => Boolean) extends GridPredicate2X[T1, T2] {
    assert(p != null)

    peerDeployLike(U.peerDeployAware(p))

    /**
     * Delegates to passed in function.
     */
    @throws(classOf[GridException])
    def applyx(e1: T1, e2: T2): Boolean = {
        p(e1, e2)
    }
}

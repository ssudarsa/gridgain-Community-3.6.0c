// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */

package org.gridgain.scalar.pimps

import org.gridgain.grid._
import org.jetbrains.annotations._
import org.gridgain.scalar._
import scalar._
import org.gridgain.grid.lang._

/**
 * Companion object.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarRichNodePimp {
    /**
     * Creates new Scalar node pimp with given Java-side implementation.
     *
     * @param value Java-side implementation.
     */
    def apply(impl: GridRichNode) = {
        if (impl == null)
            throw new NullPointerException("impl")

        val pimp = new ScalarRichNodePimp

        pimp.impl = impl

        pimp
    }
}

/**
 * ==Overview==
 * Defines Scalar "pimp" for `GridRichNode` on Java side.
 *
 * Essentially this class extends Java `GridProjection` interface with Scala specific
 * API adapters using primarily implicit conversions defined in `ScalarConversions` object. What
 * it means is that you can use functions defined in this class on object
 * of Java `GridProjection` type. Scala will automatically (implicitly) convert it into
 * Scalar's pimp and replace the original call with a call on that pimp.
 *
 * Note that Scalar provide extensive library of implicit conversion between Java and
 * Scala GridGain counterparts in `ScalarConversions` object
 *
 * ==Suffix '$' In Names==
 * Symbol `$` is used in names when they conflict with the names in the base Java class
 * that Scala pimp is shadowing or with Java package name that your Scala code is importing.
 * Instead of giving two different names to the same function we've decided to simply mark
 * Scala's side method with `$` suffix.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class ScalarRichNodePimp extends ScalarProjectionPimp[GridRichNode]
    with Ordered[GridRichNode]
    with ScalarTaskThreadContext[GridRichNode] {
    /**
     * Compares this rich node with another rich node.
     *
     * @param that Another rich node to compare with.
     */
    def compare(that: GridRichNode): Int = that.id.compareTo(impl.id)

    /**
     * <b>Alias</b> for the same function `call`.
     * This call will block until result is received and ready.
     *
     * @param s Closure to call.
     * @return Result of the given closure execution on this node.
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def #<[R](s: Call[R]): R = {
        assert(s != null)

        impl.call(s)
    }

    /**
     * <b>Alias</b> for the same function `call`.
     *
     * @param s Closure to call.
     * @return Result of the given closure execution on this node.
     * @see `org.gridgain.grid.GridRichNode.call(...)`
     */
    def #?[R](s: Call[R]): GridFuture[R] = {
        assert(s != null)

        impl.callAsync[R](s)
    }

    /**
     * <b>Alias</b> alias for the same function `run$`.
     *
     * @param s Optional sequence of closures to call. If empty or `null` - this
     *      method is no-op.
     * @see `org.gridgain.grid.GridRichNode.run(...)`
     */
    def *<(@Nullable s: Run*) {
        impl.run(toJavaCollection[Run, GridAbsClosure](s, toAbsClosure _))
    }
}

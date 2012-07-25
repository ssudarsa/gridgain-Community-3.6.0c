// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.typedef.*;

/**
 * Defines a convenient {@code side-effect only} closure, i.e. the closure that has {@code void} return type.
 * <h2 class="header">Thread Safety</h2>
 * Note that this interface does not impose or assume any specific thread-safety by its
 * implementations. Each implementation can elect what type of thread-safety it provides,
 * if any.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @param <E1> Type of the free variable, i.e. the element the closure is called or closed on.
 * @see GridFunc
 */
public abstract class GridInClosure<E1> extends GridLambdaAdapter {
    /**
     * In-closure body.
     *
     * @param t Bound free variable, i.t. the element the closure is called or closed on.
     */
    public abstract void apply(E1 t);

    /**
     * Curries this closure with given value. When result closure is called it will
     * be executed with given value.
     *
     * @param t Value to curry with.
     * @return Curried or partially applied closure with given value.
     */
    public GridAbsClosure curry(final E1 t) {
        return withMeta(new CA() {
            {
                peerDeployLike(GridInClosure.this);
            }

            @Override public void apply() {
                GridInClosure.this.apply(t);
            }
        });
    }

    /**
     * Gets closure that ignores its second argument and executes the same way as this
     * in-closure with just one first argument.
     *
     * @param <E2> Type of 2nd argument that is ignored.
     * @return Closure that ignores its second argument and executes the same way as this
     *      in-closure with just one first argument.
     */
    public <E2> GridInClosure2<E1, E2> uncurry2() {
        GridInClosure2<E1, E2> c = new CI2<E1, E2>() {
            @Override public void apply(E1 e1, E2 e2) {
                GridInClosure.this.apply(e1);
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }

    /**
     * Gets closure that ignores its second and third arguments and executes the same
     * way as this in-closure with just one first argument.
     *
     * @param <E2> Type of 2nd argument that is ignored.
     * @param <E3> Type of 3d argument that is ignored.
     * @return Closure that ignores its second and third arguments and executes the same
     *      way as this in-closure with just one first argument.
     */
    public <E2, E3> GridInClosure3<E1, E2, E3> uncurry3() {
        GridInClosure3<E1, E2, E3> c = new CI3<E1, E2, E3>() {
            @Override public void apply(E1 e1, E2 e2, E3 e3) {
                GridInClosure.this.apply(e1);
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }

    /**
     * Gets closure that applies {@code this} closure over the result of given closure.
     *
     * @param c Closure.
     * @return New closure.
     */
    public GridAbsClosure compose(final GridOutClosure<E1> c) {
        return new GridAbsClosure() {
            @Override public void apply() {
                GridInClosure.this.apply(c.apply());
            }
        };
    }

    /**
     * Gets closure that applies {@code this} closure over the result of given closure.
     *
     * @param c Closure.
     * @param <A> Argument type of new closure.
     * @return New closure.
     */
    public <A> GridInClosure<A> compose(final GridClosure<A, E1> c) {
        return new GridInClosure<A>() {
            @Override public void apply(A a) {
                GridInClosure.this.apply(c.apply(a));
            }
        };
    }

    /**
     * Gets closure that applies {@code this} closure over the result of given closure.
     *
     * @param c Closure.
     * @param <A1> First argument type of new closure.
     * @param <A2> Second argument type of new closure.
     * @return New closure.
     */
    public <A1, A2> GridInClosure2<A1, A2> compose(final GridClosure2<A1, A2, E1> c) {
        return new GridInClosure2<A1, A2>() {
            @Override public void apply(A1 a1, A2 a2) {
                GridInClosure.this.apply(c.apply(a1, a2));
            }
        };
    }

    /**
     * Gets closure that applies {@code this} closure over the result of given closure.
     *
     * @param c Closure.
     * @param <A1> First argument type of new closure.
     * @param <A2> Second argument type of new closure.
     * @param <A3> Third argument type of new closure.
     * @return New closure.
     */
    public <A1, A2, A3> GridInClosure3<A1, A2, A3> compose(final GridClosure3<A1, A2, A3, E1> c) {
        return new GridInClosure3<A1, A2, A3>() {
            @Override public void apply(A1 a1, A2 a2, A3 a3) {
                GridInClosure.this.apply(c.apply(a1, a2, a3));
            }
        };
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.spring;

import org.gridgain.grid.gridify.*;
import org.gridgain.grid.typedef.*;

/**
 * Example stateful object that simply prints out its state inside
 * of {@link #sayIt()} method. This method is grid-enabled by a virtue
 * of attaching {@link Gridify} annotation to interface method
 * {@link GridifySpringHelloWorld#sayIt()}. Such technique is
 * required because Spring AOP is proxy-based and only works with
 * interfaces.
 * <p>
 * This object demonstrates a simple example how instances with state
 * can be passed to remote node for execution.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifySpringHelloWorldImpl implements GridifySpringHelloWorld {
    /** Example state. */
    private String state;

    /**
     * Creates a fully initialized instance with given String state.
     *
     * @param state State to use for this example.
     */
    public GridifySpringHelloWorldImpl(String state) {
        this.state = state;
    }

    /** {@inheritDoc} */
    @Override public String getState() {
        return state;
    }

    /**
     * Method grid-enabled with {@link Gridify} annotation. Simply prints
     * out the state of this instance. Note that {@link Gridify} annotation
     * is attached to {@link GridifySpringHelloWorld#sayIt()} method on the
     * interface.
     *
     * @return Number of letters in a phrase.
     */
    @Override public int sayIt() {
        // Simply print out the argument.
        X.println(">>>");
        X.println(">>> Printing '" + state + "' on this node from grid-enabled method.");
        X.println(">>>");

        return state.length();
    }
}

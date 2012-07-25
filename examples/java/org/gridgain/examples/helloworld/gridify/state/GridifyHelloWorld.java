// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.state;

import org.gridgain.grid.gridify.*;
import org.gridgain.grid.typedef.*;

/**
 * Example stateful object that simply prints out its state inside
 * of {@link #sayIt()} method.
 * <p>
 * This object demonstrates a simple example how instances with state
 * can be passed to remote node for execution.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifyHelloWorld {
    /** Example state. */
    private String state;

    /**
     * Gets example state.
     *
     * @return Example state.
     */
    public String getState() {
        return state;
    }

    /**
     * Sets example state.
     *
     * @param state Example state.
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Method grid-enabled with {@link Gridify} annotation and will be
     * executed on the grid. Simply prints out the state of this instance.
     *
     * @return Number of characters in the phrase.
     */
    @Gridify(taskClass = GridifyHelloWorldStateTask.class, timeout = 3000)
    public int sayIt() {
        // Simply print out the argument.
        X.println(">>>");
        X.println(">>> Printing '" + state + "' on this node from grid-enabled method.");
        X.println(">>>");

        return state.length();
    }
}

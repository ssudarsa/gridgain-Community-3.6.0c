// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.helloworld.gridify.springbean;

import org.gridgain.grid.gridify.*;
import org.gridgain.grid.typedef.*;

/**
 * Example stateful bean that simply prints out its state inside
 * of {@link #sayIt()} method. This bean is initialized from
 * {@code "spring-bean.xml"} Spring configuration file.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifySpringBeanHelloWorld {
    /** Example state. */
    private String phrase;

    /**
     * Gets example state.
     *
     * @return Example state.
     */
    public String getPhrase() {
        return phrase;
    }

    /**
     * Sets phrase to print.
     *
     * @param phrase Phrase to print.
     */
    public void setPhrase(String phrase) {
        this.phrase = phrase;
    }

    /**
     * Method grid-enabled with {@link Gridify} annotation and will
     * be executed on the grid. It simply prints out the 'phrase'
     * set in this instance) and returns the number of characters
     * in the phrase.
     *
     * @return Number of characters in the {@code 'phrase'} string.
     */
    @Gridify public int sayIt() {
        // Simply print out the argument.
        X.println(">>>");
        X.println(">>> Printing '" + phrase + "' on this node from grid-enabled method.");
        X.println(">>>");

        return phrase.length();
    }
}

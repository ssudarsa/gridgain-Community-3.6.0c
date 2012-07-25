// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.functional;

import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Demonstrates various functional APIs from {@link org.gridgain.grid.lang.GridFunc} class.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridFunctionReflectiveExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionReflectiveExample() {
        /* No-op. */
    }

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     */
    public static void main(String[] args) {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CI1 -> GridInClosure
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        Collection<String> strs = new ArrayList<String>();

        strs.add("GridGain");
        strs.add("Cloud");
        strs.add("Compute");
        strs.add("Data");
        strs.add("Functional");
        strs.add("Programming");

        X.println("Strings:");
        F.forEach(strs, F.println());

        Collection<Integer> lens = F.transform(strs, F.<String, Integer>cInvoke("length"));

        X.println("\nLengths:");
        F.forEach(lens, F.println("", " characters."));
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.functional;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;

/**
 * Demonstrates various functional APIs from {@link org.gridgain.grid.lang.GridFunc} class.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridFunctionEitherExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionEitherExample() {
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

        GridEither<String, Integer> either = getValue(false);

        if (either.isLeft())
            X.println("Left: " + either.left());
        else
            X.println("Right: " + either.right());
    }

    /**
     * Method returns either {@code String} or {@code Integer} value.
     *
     * @param left Whether return left or right value.
     * @return Either left or right value.
     */
    private static GridEither<String, Integer> getValue(boolean left) {
        return left ? GridEither.<String, Integer>makeLeft("left") : GridEither.<String, Integer>makeRight(20);
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.examples

import org.gridgain.grid.*
import static org.gridgain.grover.Grover.*

/**
 * Demonstrates various starting and stopping ways of grid using Grover.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
class GroverStartStopExample {
    /**
     * Example entry point. No arguments required.
     */
    static void main(String[] args) {
        way1()
        way2()
        way3()
    }

    /**
     * First way to start GridGain.
     */
    static way1() {
        grover { ->
            println("Hurrah - I'm in the grid!")
            println("Local node ID is: " + grid$().localNode().id())
        }
    }

    /**
     * Second way to start GridGain.
     */
    static way2() {
        start()

        try {
            println("Hurrah - I'm in the grid!")
        }
        finally {
            stop()
        }
    }

    /**
     * Third way to start GridGain.
     */
    static way3() {
        grover { Grid g ->
            println("Hurrah - local node ID is: " + g.localNode().id())
        }
    }
}

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
 * Demonstrates use of full grid task API using Grover. Note that using task-based
 * grid enabling gives you all the advanced features of GridGain such as custom topology
 * and collision resolution, custom failover, mapping, reduction, load balancing, etc.
 * As a trade off in such cases the more code needs to be written vs. simple closure execution.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
class GroverTaskExample {
    /**
     * Example entry point. No arguments required.
     */
    static void main(String[] args) {
        grover { ->
            grid$.execute(GridHelloWorld.class, "Hello Cloud World!").get()
        }
    }

    /**
     * This task encapsulates the logic of MapReduce.
     */
    private static class GridHelloWorld extends GridTaskNoReduceSplitAdapter<String> {
        @Override protected Collection<? extends GridJob> split(int gridSize, String arg) {
            arg.split(" ").collect { w ->
                toJob { println("Length of '${w}': ${w.length()}") }
            }
        }
    }
}

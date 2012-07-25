// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */

package org.gridgain.scalar.examples

import org.gridgain.scalar.scalar
import scalar._
import org.gridgain.grid._
import collection.JavaConversions._

/**
 * Demonstrates use of full grid task API using Scalar. Note that using task-based
 * grid enabling gives you all the advanced features of GridGain such as custom topology
 * and collision resolution, custom failover, mapping, reduction, load balancing, etc.
 * As a trade off in such cases the more code needs to be written vs. simple closure execution.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarTaskExample {
    /**
     * Main entry point to application. No arguments required.
     *
     * @param args Command like argument (not used).
     */
    def main(args: Array[String]){
        scalar {
            grid$.execute(classOf[GridHelloWorld], "Hello Cloud World!").get
        }
    }

    /**
     * This task encapsulates the logic of MapReduce.
     */
    class GridHelloWorld extends GridTaskNoReduceSplitAdapter[String] {
        def split(gridSize: Int, arg: String): java.util.Collection[_ <: GridJob] = {
            (for (w <- arg.split(" ")) yield toJob(() => println(w))) toSeq
        }
    }
}

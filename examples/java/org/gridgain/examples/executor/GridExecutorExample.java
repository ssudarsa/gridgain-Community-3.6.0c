// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.executor;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * This example scales and converts images on the grid using JDK encoders.
 * Every submitted command {@link GridExecutorImageScaleCommand} will be transferred
 * to any remote node for execution and will return result (converted image data).
 * Make sure that Grid property {@code peerClassLoadingEnabled}
 * initialized as {@code true} because command will load resources for processing
 * from classpath via P2P class loader.
 * <p>
 * Executor service executes its own {@link GridTask} implementation where {@link GridTask#map(List, Object)}
 * contains only one {@link GridJob} with {@code Serializable} argument
 * {@code GridExecutorImageScaleCommand}.
 * <p>
 * {@code GridExecutorImageScaleCommand} loads image from classpath with P2P class
 * loader from origin node and return result encapsulated in
 * {@link GridExecutorImage} object. By default all converted images will be saved
 * on nodes (involved in processing) in disk folder defined by "java.io.tmpdir"
 * system property.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridExecutorExample {
    /**
     * Ensure singleton.
     */
    private GridExecutorExample() {
        // No-op.
    }

    /**
     * Execute {@code Executor} example on the grid.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws Exception If example execution failed.
     */
    @SuppressWarnings({"TooBroadScope"})
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            G.start();
        }
        else {
            G.start(args[0]);
        }

        try {
            Grid grid = G.grid();

            ExecutorService exec = grid.executor();

            boolean archive = true;

            String rsrc1 = "org/gridgain/examples/executor/example1.jpg";
            String rsrc2 = "org/gridgain/examples/executor/example2.gif";
            String rsrc3 = "org/gridgain/examples/executor/example3.png";

            Future<GridExecutorImage> fut = exec.submit(new GridExecutorImageScaleCommand(0.9d, rsrc1, archive));

            GridExecutorImage img = fut.get();

            X.println();
            X.println("Received execution result for first submitted command: " + img);

            Collection<Callable<GridExecutorImage>> cmds= new ArrayList<Callable<GridExecutorImage>>(2);

            cmds.add(new GridExecutorImageScaleCommand(1.1d, rsrc2, archive));
            cmds.add(new GridExecutorImageScaleCommand(1.5d, rsrc3, archive));

            List<Future<GridExecutorImage>> futs = exec.invokeAll(cmds);

            for (Future<GridExecutorImage> imgFuture : futs) {
                GridExecutorImage img2 = imgFuture.get();

                X.println();
                X.println("Received execution result for commands batch: " + img2);
            }

            X.println(">>>");
            X.println(">>> Finished executing Grid Executor example.");
            X.println(">>> Check all nodes for output (this node is also part of the grid).");
            X.println(">>>");

            exec.shutdown();
        }
        finally {
            G.stop(true);
        }
    }
}

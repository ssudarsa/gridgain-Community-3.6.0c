// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.continuation;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;

import java.math.*;
import java.util.*;

import static org.gridgain.grid.GridClosureCallMode.*;

/**
 * This example recursively calculates {@code 'Fibonacci'} numbers on the grid. This is
 * a powerful design pattern which allows for creation of fully distributively recursive
 * (a.k.a. nested) tasks or closures with continuations. This example also shows
 * usage of {@code 'continuations'}, which allows us to wait for results from remote nodes
 * without blocking threads.
 * <p>
 * Note that because this example utilizes local node storage via {@link GridNodeLocal},
 * it gets faster if you execute it multiple times, as the more you execute it,
 * the more values it will be cached on remote nodes.
 * <p>
 * <h1 class="header">Starting Remote Nodes</h1>
 * To try this example you should (but don't have to) start remote grid instances.
 * You can start as many as you like by executing the following script:
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh}</pre>
 * Once remote instances are started, you can execute this example from
 * Eclipse, IntelliJ IDEA, or NetBeans (and any other Java IDE) by simply hitting run
 * button. You will see that all nodes discover each other and
 * some of the nodes will participate in task execution (check node
 * output).
 * <p>
 * <h1 class="header">XML Configuration</h1>
 * If no specific configuration is provided, GridGain will start with
 * all defaults. For information about GridGain default configuration
 * refer to {@link GridFactory} documentation. If you would like to
 * try out different configurations you should pass a path to Spring
 * configuration file as 1st command line argument into this example.
 * The path can be relative to <tt>GRIDGAIN_HOME</tt> environment variable.
 * You should also pass the same configuration file to all other
 * grid nodes by executing startup script as follows (you will need
 * to change the actual file name):
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh} examples/config/specific-config-file.xml</pre>
 * <p>
 * GridGain examples come with multiple configuration files you can try.
 * All configuration files are located under <tt>GRIDGAIN_HOME/examples/config</tt>
 * folder.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridContinuationExample {
    /**
     * Ensure singleton.
     */
    private GridContinuationExample() {
        /* No-op. */
    }

    /**
     * This example recursively calculates {@code 'Fibonacci'} numbers on the grid. This is
     * a powerful design pattern which allows for creation of distributively recursive
     * tasks or closures with {@code 'continuations'}.
     * <p>
     * Note that because this example utilizes local node storage via {@link GridNodeLocal},
     * it gets faster if you execute it multiple times, as the more you execute it,
     * the more values it will be cached on remote nodes.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      <tt>"examples/config/"</tt> for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CIX1 -> GridInClosureX
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // PN -> GridPredicate<GridRichNode>
        // F -> GridFunc

        G.in(args.length == 0 ? null : args[0], new CIX1<Grid>() {
            @Override public void applyx(final Grid g) throws GridException {
                long N = 100;

                final UUID exampleNodeId = g.localNode().id();

                // Filter to exclude this node from execution.
                final GridPredicate<GridRichNode> p = new PN() {
                    @Override public boolean apply(GridRichNode n) {
                        // Give preference to remote nodes.
                        return g.remoteNodes().isEmpty() || !n.id().equals(exampleNodeId);
                    }
                };

                long start = System.currentTimeMillis();

                BigInteger fib = g.call(UNICAST, new CX1<Long, BigInteger>() {
                    // These fields must be **transient** so they do not get
                    // serialized and sent to remote nodes.
                    // However, these fields will be preserved locally while
                    // this closure is being "held", i.e. while it is suspended
                    // and is waiting to be continued. 
                    private transient GridFuture<BigInteger> fut1, fut2;

                    // Auto-inject job context.
                    @GridJobContextResource
                    private GridJobContext jobCtx;

                    @Nullable @Override public BigInteger applyx(Long n) throws GridException {
                        if (fut1 == null || fut2 == null) {
                            X.println(">>> Starting fibonacci execution for number: " + n);

                            // Make sure n is not negative.
                            n = Math.abs(n);

                            if (n <= 2) {
                                return n == 0 ? BigInteger.ZERO : BigInteger.ONE;
                            }

                            // Node-local storage.
                            GridNodeLocal<Long, GridFuture<BigInteger>> store = g.nodeLocal();

                            // Check if value is cached in node-local store first.
                            fut1 = store.get(n - 1);
                            fut2 = store.get(n - 2);

                            // If future is not cached in node-local store, cache it.
                            // Recursive grid execution.
                            if (fut1 == null) {
                                fut1 = store.addIfAbsent(n - 1, g.callAsync(UNICAST, this, n - 1, p));
                            }

                            // If future is not cached in node-local store, cache it.
                            if (fut2 == null) {
                                fut2 = store.addIfAbsent(n - 2, g.callAsync(UNICAST, this, n - 2, p));
                            }

                            // If futures are not done, then wait asynchronously for the result
                            if (!fut1.isDone() || !fut2.isDone()) {
                                GridInClosure<GridFuture<BigInteger>> lsnr = new CI1<GridFuture<BigInteger>>() {
                                    @Override public void apply(GridFuture<BigInteger> f) {
                                        // If both futures are done, resume the continuation.
                                        if (fut1.isDone() && fut2.isDone()) {
                                            // Resume suspended job execution.
                                            jobCtx.callcc();
                                        }
                                    }
                                };

                                // Attach the same listener to both futures.
                                fut1.listenAsync(lsnr);
                                fut2.listenAsync(lsnr);

                                // Hold (suspend) job execution.
                                // It will be resumed in listener above via 'callcc()' call
                                // once both futures are done.
                                return jobCtx.holdcc();
                            }
                        }

                        assert fut1.isDone() && fut2.isDone();

                        // Return cached results.
                        return fut1.get().add(fut2.get());
                    }
                }, N, p);

                long duration = System.currentTimeMillis() - start;

                X.println(">>>");
                X.println(">>> Finished executing Fibonacci for '" + N + "' in " + duration + " ms.");
                X.println(">>> Fibonacci sequence for input number '" + N + "' is '" + fib + "'.");
                X.println(">>> If you re-run this example w/o stopping remote nodes - the performance will");
                X.println(">>> increase since intermediate results are pre-cache on remote nodes.");
                X.println(">>> You should see prints out every recursive Fibonacci execution on grid nodes.");
                X.println(">>> Check remote nodes for output.");
                X.println(">>>");
            }
        });
    }
}

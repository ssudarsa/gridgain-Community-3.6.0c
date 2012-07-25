// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

    package org.gridgain.examples.helloworld.api30;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridClosureCallMode.*;

/**
 * Demonstrates a simple use of GridGain 3.0 APIs.
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
 * The path can be relative to {@code GRIDGAIN_HOME} environment variable.
 * You should also pass the same configuration file to all other
 * grid nodes by executing startup script as follows (you will need
 * to change the actual file name):
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh} examples/config/specific-config-file.xml</pre>
 * <p>
 * GridGain examples come with multiple configuration files you can try.
 * All configuration files are located under {@code GRIDGAIN_HOME/examples/config}
 * folder.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class      GridHelloWorldExample {
    /**
     * Execute {@code HelloWorld} example on the grid.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        if (args.length == 0) {
            G.start();
        }
        else {
            G.start(args[0]);
        }

        try {
            // Broadcast this message to all nodes using GridClosure.
            broadcastWordsClosure("Broadcasting This Message To All Nodes!");

            // Print individual words from this phrase on different nodes
            // using GridClosure
            spreadWordsClosure("Print Worlds Functional Style!");

            // Print this message using anonymous runnable object.
            unicastWordsRunnable("Printing This Message From Runnable!");

            // Split the message into words and pass them as arguments
            // for remote execution of Callable objects.
            countLettersCallable("Letter Count With Callable!");

            // Split the message into words and pass them as arguments
            // for remote execution of GridClosure objects.
            countLettersClosure("Letter Count With Closure!");

            // Split the message into words and pass them as arguments
            // for remote execution of GridClosure objects and
            // then aggregate results using GridReducer.
            countLettersReducer("Letter Count With Reducer!");
        }
        finally {
            G.stop(true);
        }
    }

    /**
     * Broadcasts a give phrase to all nodes.
     *
     * @param phrase Phrase to broadcast.
     * @throws GridException If failed.
     */
    private static void broadcastWordsClosure(final String phrase) throws GridException {
        X.println(">>> Starting broadcastWordsClosure() example...");

        G.grid().run(BROADCAST, new GridAbsClosure() {
            @Override public void apply() {
                X.println(">>> Printing phrase: " + phrase);
            }
        });

        // NOTE:
        //
        // Alternatively, you can use existing closure 'F.println()' to
        // print any text like so:
        //
        // G.grid().run(BROADCAST, F.println(">>> Printing phrase: " + phrase));
        //

        X.println(">>>");
        X.println(">>> Finished broadcasting a phrase to all grid nodes based on GridGain 3.0 API.");
        X.println(">>> Check all nodes for output (this node is also part of the grid).");
        X.println(">>>");
    }

    /**
     * Prints every word a phrase on different nodes.
     *
     * @param phrase Phrase from which to print words on different nodes.
     * @throws GridException If failed.
     */
    private static void spreadWordsClosure(String phrase) throws GridException {
        X.println(">>> Starting spreadWordsClosure() example...");

        // Splits the passed in phrase into words and prints every word
        // on a individual grid node. If there are more words than nodes -
        // some nodes will print more than one word.
        G.grid().run(SPREAD, F.yield(phrase.split(" "), new GridInClosure<String>() {
            @Override public void apply(String word) {
                X.println(word);
            }
        }));

        // NOTE:
        //
        // Alternatively, you can use existing closure 'F.println()' to
        // print any yield result in 'F.yield()' like so:
        //
        // G.grid().run(SPREAD, F.yield(phrase.split(" "), F.println()));
        //

        X.println(">>>");
        X.println(">>> Finished printing individual words on different nodes based on GridGain 3.0 API.");
        X.println(">>> Check all nodes for output (this node is also part of the grid).");
        X.println(">>>");
    }

    /**
     * Prints a phrase on one of the grid nodes running anonymous runnable.
     *
     * @param phrase Phrase to print on one of the grid nodes.
     * @throws GridException If failed.
     */
    private static void unicastWordsRunnable(final String phrase) throws GridException {
        X.println(">>> Starting unicastWordsRunnable() example...");

        G.grid().run(UNICAST, new GridRunnable() {
            @Override public void run() {
                X.println(">>> Printing phrase: " + phrase);
            }
        });

        // NOTE:
        //
        // Alternatively, you can use existing closure 'F.println()' to
        // print any text like so:
        //
        // G.grid().run(UNICAST, F.println(">>> Printing phrase: " + phrase));
        //

        X.println(">>>");
        X.println(">>> Finished execution of runnable object based on GridGain 3.0 API.");
        X.println(">>> You should see the phrase '" + phrase + "' printed out on one of the nodes.");
        X.println(">>> Check all nodes for output (this node is also part of the grid).");
        X.println(">>>");
    }

    /**
     * Prints a phrase on the grid nodes running anonymous callable objects
     * and calculating total number of letters.
     *
     * @param phrase Phrase to print on of the grid nodes.
     * @throws GridException If failed.
     */
    private static void countLettersCallable(String phrase) throws GridException {
        X.println(">>> Starting countLettersCallable() example...");

        Collection<Callable<Integer>> calls = new HashSet<Callable<Integer>>();

        for (final String word : phrase.split(" "))
            calls.add(new GridCallable<Integer>() { // Create executable logic.
                @Override public Integer call() throws Exception {
                    // Print out a given word, just so we can
                    // see which node is doing what.
                    X.println(">>> Executing word: " + word);

                    // Return the length of a given word, i.e. number of letters.
                    return word.length();
                }
            });

        // Explicitly execute the collection of callable objects and receive a result.
        Collection<Integer> results = G.grid().call(SPREAD, calls);

        // Add up all results using convenience 'sum()' method on GridFunc class.
        int letterCnt = F.sum(results);

        X.println(">>>");
        X.println(">>> Finished execution of counting letters with callables based on GridGain 3.0 API.");
        X.println(">>> You should see the phrase '" + phrase + "' printed out on the nodes.");
        X.println(">>> Total number of letters in the phrase is '" + letterCnt + "'.");
        X.println(">>> Check all nodes for output (this node is also part of the grid).");
        X.println(">>>");
    }

    /**
     * Prints a phrase on the grid nodes running anonymous closure objects
     * and calculating total number of letters.
     *
     * @param phrase Phrase to print on of the grid nodes.
     * @throws GridException If failed.
     */
    private static void countLettersClosure(String phrase) throws GridException {
        X.println(">>> Starting countLettersClosure() example...");

        // Explicitly execute the collection of callable objects and receive a result.
        Collection<Integer> results = G.grid().call(
            SPREAD,
            new GridClosure<String, Integer>() { // Create executable logic.
                @Override public Integer apply(String word) {
                    // Print out a given word, just so we can
                    // see which node is doing what.
                    X.println(">>> Executing word: " + word);

                    // Return the length of a given word, i.e. number of letters.
                    return word.length();
                }
            },
            Arrays.asList(phrase.split(" "))); // Collection of arguments for closures.

        // Add up all results using convenience 'sum()' method.
        int letterCnt = F.sum(results);

        X.println(">>>");
        X.println(">>> Finished execution of counting letters with closure based on GridGain 3.0 API.");
        X.println(">>> You should see the phrase '" + phrase + "' printed out on the nodes.");
        X.println(">>> Total number of letters in the phrase is '" + letterCnt + "'.");
        X.println(">>> Check all nodes for output (this node is also part of the grid).");
        X.println(">>>");
    }

    /**
     * Calculates length of a given phrase on the grid.
     *
     * @param phrase Phrase to count the number of letters in.
     * @throws GridException If failed.
     */
    private static void countLettersReducer(String phrase) throws GridException {
        X.println(">>> Starting countLettersReducer() example...");

        Grid grid = G.grid();

        // Logger to use in your closure. Note that even though we assign it
        // to a local variable, GridGain still allows to use it from remotely
        // executed code.
        final GridLogger log = grid.log();

        // Execute Hello World task.
        int letterCnt = grid.reduce(
            BALANCE,
            new GridClosure<String, Integer>() { // Create executable logic.
                @Override public Integer apply(String word) {
                    // Print out a given word, just so we can
                    // see which node is doing what.
                    log.info(">>> Calculating for word: " + word);

                    // Return the length of a given word, i.e. number of letters.
                    return word.length();
                }
            },
            Arrays.asList(phrase.split(" ")), // Collection of words.
            // Create custom reducer.
            // NOTE: Alternatively, you can use existing reducer: F.sumIntReducer()
            new GridReducer<Integer, Integer>() {
                private int sum;

                @Override public boolean collect(Integer res) {
                    sum += res;

                    return true; // True means continue collecting until last result.
                }

                @Override public Integer apply() {
                    return sum;
                }
            }
        );

        X.println(">>>");
        X.println(">>> Finished execution of counting letters with reducer based on GridGain 3.0 API.");
        X.println(">>> Total number of letters in the phrase is '" + letterCnt + "'.");
        X.println(">>> You should see individual words printed out on different nodes.");
        X.println(">>> Check all nodes for output (this node is also part of the grid).");
        X.println(">>>");
    }

    /**
     * Ensure singleton.
     */
    private GridHelloWorldExample() {
        // No-op.
    }
}

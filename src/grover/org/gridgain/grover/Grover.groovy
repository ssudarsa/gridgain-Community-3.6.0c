// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover

import org.gridgain.grid.*
import static org.gridgain.grid.GridFactoryState.*
import org.gridgain.grid.cache.*
import org.gridgain.grid.typedef.*
import org.jetbrains.annotations.*
import org.springframework.context.*

/**
 * Main entry for <b>Grover</b> DSL.
 *
 * <code><div align="center" style="font-family: monospace; width: 98%; padding: 0px 10px 0px 10px; background-color: #efefef"><pre style="font-family: monospace">
 *  _________                                         ______ _______
 *  __  ____/______________ ___   _______ ________    __<  / __  __ \
 *  _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/    __  /  _  / / /
 *  / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /        _  /___/ /_/ /
 *  \____/   /_/     \____/ _____/  \___/ /_/         /_/ _(_)____/
 * </pre></div></code>
 *
 * <h2 class="header">Overview</h2>
 * <code>Grover</code> is the main object that encapsulates Grover DSL. It includes global functions
 * on <code>"grover"</code> keyword and helper converters. <code>grover</code> also
 * mimics many methods in <code>GridFactory</code> class from Java side.
 * <p>
 * The idea behind Scalar DSL - <i>zero additional logic and only conversions</i> implemented
 * using Groovy++ type inference. Note that most of the Grover DSL development happened on Java
 * side of GridGain 3.0 product line - Java APIs had to be adjusted quite significantly to
 * support natural adaptation of functional APIs. That basically means that all functional
 * logic must be available on Java side and Grover only provides conversions from Grover
 * language constructs to Java constructs. Note that currently GridGain supports Groovy 1.8
 * and up only.
 * <p>
 * This design approach ensures that Java side does not starve and usage paradigm
 * is mostly the same between Java and Groovy++ - yet with full power of Groovy++ behind.
 * In other words, Grover only adds Groovy++ specifics, but not greatly altering semantics
 * of how GridGain APIs work. Most of the time the code in Grover can be written in
 * Java in almost the same number of lines.
 *
 * <h2 class="header">Suffix '$' In Names</h2>
 * Symbol `$` is used in names when they conflict with the names in the base Java class
 * that Grover is shadowing or with Java package name that your Groovy++ code is importing.
 * Instead of giving two different names to the same function we've decided to simply mark
 * Groovy++'s side method with `$` suffix.
 *
 * <h2 class="header">Importing</h2>
 * Grover needs to be imported in a proper way so that necessary objects got available in the scope:
 * <pre name="code" class="groovy">
 * import static org.gridgain.grover.Grover.*
 * import org.gridgain.grover.categories.*
 * import org.gridgain.grover.lang.*
 * </pre>
 * Any class that uses Grover should also be annotated like so:
 * <pre name="code" class="groovy">
 * &#64;Typed
 * &#64;Use(GroverCacheProjectionCategory)
 * class MyClass {
 *     ...
 * }
 * </pre>
 *
 * <h2 class="header">Examples</h2>
 * Here are few short examples of how Scalar can be used to program routine distributed
 * task. All examples below use default GridGain configuration and default grid. All these
 * examples take an implicit advantage of auto-discovery and failover, load balancing and
 * collision resolution, zero deployment and many other underlying technologies in the
 * GridGain - while remaining absolutely distilled to the core domain logic.
 *
 * This code snippet prints out full topology:
 * <pre name="code" class="groovy">
 * grover { -> grid$.each { println("Node: " + it.id8()) } }
 * </pre>
 * The obligatory example - cloud enabled <code>Hello World!</code>. It splits the phrase
 * into multiple words and prints each word on a separate grid node:
 * <pre name="code" class="groovy">
 * grover { -> grid$.run(SPREAD, "Hello World!".split(" ").collect { { -> println(it) } } ) }
 * </pre>
 * This example broadcasts message to all nodes:
 * <pre name="code" class="groovy">
 * grover { -> grid$.run(BROADCAST) { println("Broadcasting!!!") } }
 * </pre>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
abstract class Grover {
    /**
     * Note that grid instance will be stopped with cancel flat set to {@code true}.
     *
     * @param g Grid instance.
     * @param body Closure with grid instance as body's parameter.
     * @return Closure call result.
     */
     private static <T> T init(Grid g, Function1<Grid, T> body) {
         assert g != null
         assert body != null

         def res

         try {
             res = body.call(g)
         }
         finally {
             G.stop(g.name(), true)
         }

         res
    }

    /**
     * Note that grid instance will be stopped with cancel flat set to {@code true}.
     *
     * @param g Grid instance.
     * @param body Passed by name body.
     * @return Closure call result.
     */
    private static <T> T init0(Grid g, Function0<T> body) {
        assert g != null
        assert body != null

        def res

        try {
            res = body.call()
        }
        finally {
            G.stop(g.name(), true)
        }

        res
    }

    /**
     * Executes given closure within automatically managed default grid instance.
     * If default grid is already started the passed in closure will simply
     * execute.
     *
     * @param body Closure to execute within automatically managed default grid instance.
     * @return Closure call result.
     */
    static <T> T grover(Function1<Grid, T> body) {
        if (!isStarted())
            init(G.start(), body)
        else
            body.call(grid$)
    }

    /**
     * Executes given closure within automatically managed default grid instance.
     * If default grid is already started the passed in closure will simply
     * execute.
     *
     * @param body Closure to execute within automatically managed default grid instance.
     * @return Closure call result.
     */
    static <T> T grover(Function0<T> body) {
        if (!isStarted())
            init0(G.start(), body)
        else
            body.call()
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(String springCfgPath, Function1<Grid, T> body) {
        init(G.start(springCfgPath), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(String springCfgPath, Function0<T> body) {
        init0(G.start(springCfgPath), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param cfg Grid configuration instance.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(GridConfiguration cfg, Function1<Grid, T> body) {
        init(G.start(cfg), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param cfg Grid configuration instance.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(GridConfiguration cfg, Function0<T> body) {
        init0(G.start(cfg), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param ctx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(GridConfiguration cfg, ApplicationContext ctx, Function1<Grid, T> body) {
        init(G.start(cfg, ctx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param ctx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(GridConfiguration cfg, ApplicationContext ctx, Function0<T> body) {
        init0(G.start(cfg, ctx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param ctx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(String springCfgPath, ApplicationContext ctx, Function1<Grid, T> body) {
        init(G.start(springCfgPath, ctx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param ctx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(String springCfgPath, ApplicationContext ctx, Function0<T> body) {
        init0(G.start(springCfgPath, ctx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param ctx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(ApplicationContext ctx, Function1<Grid, T> body) {
        init(G.start(ctx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param ctx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(ApplicationContext ctx, Function0<T> body) {
        init0(G.start(ctx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @param ctx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(URL springCfgUrl, ApplicationContext ctx, Function1<Grid, T> body) {
        init(G.start(springCfgUrl, ctx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @param ctx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(URL springCfgUrl, ApplicationContext ctx, Function0<T> body) {
        init0(G.start(springCfgUrl, ctx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(URL springCfgUrl, Function1<Grid, T> body) {
        init(G.start(springCfgUrl), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @param body Closure to execute within automatically managed grid instance.
     * @return Closure call result.
     */
    static <T> T grover(URL springCfgUrl, Function0<T> body) {
        init0(G.start(springCfgUrl), body)
    }

    /**
     * Gets default grid instance.
     *
     * @return Grid instance.
     */
    @Nullable static Grid grid$() {
        try {
            G.grid()
        }
        catch (IllegalStateException ignored) {
            null
        }
    }

    /**
     * Alias of {@code grid$()} method for property-style access.
     *
     * @return Grid instance.
     */
    @Nullable static Grid getGrid$() {
        grid$()
    }

    /**
     * Gets named grid.
     *
     * @param name Grid name.
     * @return Grid instance.
     */
    @Nullable static Grid grid$(@Nullable String name) {
        try {
            G.grid(name)
        }
        catch (IllegalStateException ignored) {
            null
        }
    }

    /**
     * Gets grid for given node ID.
     *
     * @param locNodeId Local node ID for which to get grid instance option.
     * @return Grid instance.
     */
    @Nullable static Grid grid$(UUID locNodeId) {
        assert locNodeId != null

        try {
            G.grid(locNodeId)
        }
        catch (IllegalStateException ignored) {
            null
        }
    }

    /**
     * Gets named cache from specified grid.
     *
     * @param gridName Name of the grid.
     * @param cacheName Name of the cache to get.
     * @return Cache instance.
     */
    @Nullable static <K, V> GridCache<K, V> cache$(@Nullable String gridName, @Nullable String cacheName) {
        GridCache<K, V> c = null

        def g = grid$(gridName)

        if (g != null)
            c = g.cache(cacheName)

        c
    }

    /**
     * Gets named cache from default grid.
     *
     * @param cacheName Name of the cache to get.
     * @return Cache instance.
     */
    @Nullable static <K, V> GridCache<K, V> cache$(@Nullable String cacheName) {
        cache$(null, cacheName)
    }

    /**
     * Gets default cache.
     *
     * @return Cache instance.
     */
    @Nullable static <K, V> GridCache<K, V> cache$() {
        cache$(null, null)
    }

    /**
     * Alias of {@code cache$()} method for property-style access.
     *
     * @return Cache instance.
     */
    @Nullable static <K, V> GridCache<K, V> getCache$() {
        cache$()
    }

    /**
     * Starts default grid. It's no-op if default grid is already started.
     *
     * @return Started grid.
     */
    @Nullable static Grid start() {
        if (!isStarted())
            G.start()
        else
            grid$
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @return Started grid.
     */
    static Grid start(GridConfiguration cfg) {
        assert cfg != null

        G.start(cfg)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @param ctx Optional Spring application context.
     * @return Started grid.
     */
    static Grid start(GridConfiguration cfg, @Nullable ApplicationContext ctx) {
        assert cfg != null

        G.start(cfg, ctx)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     */
    static Grid start(@Nullable String springCfgPath) {
        G.start(springCfgPath)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param ctx Optional Spring application context.
     * @return Started grid.
     */
    static Grid start(String springCfgPath, @Nullable ApplicationContext ctx) {
        assert springCfgPath != null

        G.start(springCfgPath, ctx)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @return Started grid.
     */
    static Grid start(URL springCfgUrl) {
        assert springCfgUrl != null

        G.start(springCfgUrl)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @param ctx Optional Spring application context.
     * @return Started grid.
     */
    static Grid start(URL springCfgUrl, @Nullable ApplicationContext ctx) {
        assert springCfgUrl != null

        G.start(springCfgUrl, ctx)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param ctx Optional Spring application context.
     * @return Started grid.
     */
    static Grid start(@Nullable ApplicationContext ctx) {
        G.start(ctx)
    }

    /**
     * Stops given grid with specified flags.
     * If specified grid is already stopped - it's no-op.
     *
     * @param name Grid name to stop.
     * @param cancel Whether or not to cancel all currently running jobs.
     * @param wait Whether or not wait for all executing tasks to finish.
     */
    static void stop(@Nullable String name, boolean cancel, boolean wait) {
        if (isStarted(name))
            G.stop(name, cancel, wait)
    }

    /**
     * Stops given grid and specified cancel flag.
     * If specified grid is already stopped - it's no-op.
     *
     * @param name Grid name to stop.
     * @param cancel Whether or not to cancel all currently running jobs.
     */
    static void stop(@Nullable String name, boolean cancel) {
        if (isStarted(name))
            G.stop(name, cancel)
    }

    /**
     * Stops given grid with cancel flag set to {@code true}.
     * If specified grid is already stopped - it's no-op.
     *
     * @param name Grid name to stop.
     */
    static void stop(@Nullable String name) {
        if (isStarted(name))
            G.stop(name, true)
    }

    /**
     * Stops default grid with given flags.
     * If default grid is already stopped - it's no-op.
     *
     * @param cancel Whether or not to cancel all currently running jobs.
     * @param wait Whether or not wait for all executing tasks to finish.
     */
    static void stop(boolean cancel, boolean wait) {
        if (isStarted())
            G.stop(cancel, wait)
    }

    /**
     * Stops default grid with given cancel flag.
     * If default grid is already stopped - it's no-op.
     *
     * @param cancel Whether or not to cancel all currently running jobs.
     */
    static void stop(boolean cancel) {
        if (isStarted())
            G.stop(cancel)
    }

    /**
     * Stops default grid with cancel flag set to {@code true}.
     * If default grid is already stopped - it's no-op.
     */
    static void stop() {
        if (isStarted())
            G.stop(true)
    }

    /**
     * Tests if specified grid is started.
     *
     * @param name Grid name.
     * @return Whether grid is started.
     */
    static boolean isStarted(@Nullable String name) {
        G.state(name) == STARTED
    }

    /**
     * Tests if default grid is started.
     *
     * @return Whether grid is started.
     */
    static boolean isStarted() {
        G.state() == STARTED
    }

    /**
     * Tests if specified grid is stopped.
     *
     * @param name Grid name.
     * @return Whether grid is stopped.
     */
    static boolean isStopped(@Nullable String name) {
        G.state(name) == STOPPED
    }

    /**
     * Tests if default grid is stopped.
     *
     * @return Whether grid is stopped.
     */
    static boolean isStopped() {
        G.state() == STOPPED
    }

    /**
     * Sets daemon flag to grid factory. Note that this method should be called
     * before grid instance starts.
     *
     * @param f Daemon flag to set.
     */
    static void daemon(boolean f) {
        G.setDaemon(f)
    }

    /**
     * Gets daemon flag set in the grid factory.
     *
     * @return Whether daemon flag is true.
     */
    static boolean isDaemon() {
        G.isDaemon()
    }

    /**
     * Converts Groovy function to {@code GridJob}.
     *
     * @param f Function.
     * @return Grid job.
     */
    static GridJob toJob(Function0<Object> f) {
        new GridJobAdapterEx() {
            @Override Object execute() {
                f.call()
            }
        }
    }
}

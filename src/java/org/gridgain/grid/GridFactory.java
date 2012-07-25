// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.apache.commons.logging.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.loaders.cmdline.*;
import org.gridgain.grid.loaders.servlet.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.logger.log4j.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.marshaller.optimized.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.checkpoint.*;
import org.gridgain.grid.spi.checkpoint.sharedfs.*;
import org.gridgain.grid.spi.collision.*;
import org.gridgain.grid.spi.collision.fifoqueue.*;
import org.gridgain.grid.spi.communication.*;
import org.gridgain.grid.spi.communication.tcp.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.spi.deployment.local.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.spi.discovery.multicast.*;
import org.gridgain.grid.spi.eventstorage.*;
import org.gridgain.grid.spi.eventstorage.memory.*;
import org.gridgain.grid.spi.failover.*;
import org.gridgain.grid.spi.failover.always.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.spi.loadbalancing.roundrobin.*;
import org.gridgain.grid.spi.metrics.*;
import org.gridgain.grid.spi.metrics.jdk.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.spi.swapspace.file.*;
import org.gridgain.grid.spi.topology.*;
import org.gridgain.grid.spi.topology.basic.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;

import javax.management.*;
import java.io.*;
import java.lang.management.*;
import java.net.*;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridConfiguration.*;
import static org.gridgain.grid.GridFactoryState.*;
import static org.gridgain.grid.GridSystemProperties.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.segmentation.GridSegmentationPolicy.*;

/**
 * This class defines a factory for the main GridGain API. It controls Grid life cycle
 * and allows listening for grid events.
 * <h1 class="header">Grid Loaders</h1>
 * Although user can apply grid factory directly to start and stop grid, grid is
 * often started and stopped by grid loaders. Some examples
 * of Grid loaders are:
 * <ul>
 * <li>{@link GridCommandLineLoader}</li>
 * <li>{@link org.gridgain.grid.loaders.jboss.GridJbossLoader}</li>
 * <li>{@link org.gridgain.grid.loaders.weblogic.GridWeblogicStartup} and {@link org.gridgain.grid.loaders.weblogic.GridWeblogicShutdown}</li>
 * <li>{@link org.gridgain.grid.loaders.websphere.GridWebsphereLoader}</li>
 * <li>{@link org.gridgain.grid.loaders.glassfish.GridGlassfishLoader}</li>
 * <li>{@link GridServletLoader}</li>
 * </ul>
 * <h1 class="header">Examples</h1>
 * Use {@link #start()} method to start grid with default configuration. You can also use
 * {@link GridConfigurationAdapter} to override some default configuration. Below is an
 * example on how to start grid with <strong>URI deployment</strong>.
 * <pre name="code" class="java">
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * GridUriDeployment deploySpi = new GridUriDeployment();
 *
 * deploySpi.setUriList(Collections.singletonList("classes://tmp/output/classes"));
 *
 * cfg.setDeploymentSpi(deploySpi);
 *
 * GridFactory.start(cfg);
 * </pre>
 * Here is how a grid instance can be configured from Spring XML configuration file. The
 * example below configures a grid instance with additional user attributes
 * (see {@link GridNode#attributes()}) and specifies a grid name:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.cfg" class="org.gridgain.grid.GridConfigurationAdapter" scope="singleton"&gt;
 *     ...
 *     &lt;property name="gridName" value="grid"/&gt;
 *     &lt;property name="userAttributes"&gt;
 *         &lt;map&gt;
 *             &lt;entry key="group" value="worker"/&gt;
 *             &lt;entry key="grid.node.benchmark"&gt;
 *                 &lt;bean class="org.gridgain.grid.benchmarks.GridLocalNodeBenchmark" init-method="start"/&gt;
 *             &lt;/entry&gt;
 *         &lt;/map&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * A grid instance with Spring configuration above can be started as following. Note that
 * you do not need to pass path to Spring XML file if you are using
 * {@code GRIDGAIN_HOME/config/default-spring.xml}. Also note, that the path can be
 * absolute or relative to GRIDGAIN_HOME.
 * <pre name="code" class="java">
 * ...
 * G.start("/path/to/spring/xml/file.xml");
 * ...
 * </pre>
 * You can also instantiate grid directly from Spring without using {@code GridFactory}.
 * For more information refer to {@link GridSpringBean} documentation.

 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridFactory {
    /**
     * This is restart code that can be used by external tools, like Shell scripts,
     * to auto-restart the GridGain JVM process. Note that there is no standard way
     * for a JVM to restart itself from Java application and therefore we rely on
     * external tools to provide that capability.
     * <p>
     * Note that standard <tt>ggstart.{sh|bat}</tt> scripts support restarting when
     * JVM process exits with this code.
     */
    public static final int RESTART_EXIT_CODE = 250;

    /**
     * This is kill code that can be used by external tools, like Shell scripts,
     * to auto-stop the GridGain JVM process without restarting.
     */
    public static final int KILL_EXIT_CODE = 130;

    /** Default configuration path relative to GridGain home. */
    private static final String DFLT_CFG = "config/default-spring.xml";

    /** License file name. */
    private static final String LIC_FILE_NAME = "gridgain-license.xml";

    /** Default grid. */
    private static volatile GridNamedInstance dfltGrid;

    /** Map of named grids. */
    private static final Map<String, GridNamedInstance> grids = new HashMap<String, GridNamedInstance>();

    /** Map of grid states ever started in this JVM. */
    private static final Map<String, GridFactoryState> gridStates = new HashMap<String, GridFactoryState>();

    /** List of state listeners. */
    private static final List<GridFactoryListener> lsnrs = new ArrayList<GridFactoryListener>();

    /** Synchronization mutex. */
    private static final Object mux = new Object();

    /** */
    private static volatile boolean daemon;

    /**
     * Checks runtime version to be 1.5.x or 1.6.x or 1.7.x.
     * This will load pretty much first so we must do these checks here.
     */
    static {
        // Check 1.7 just in case for forward compatibility.
        if (!U.jdkVersion().contains("1.6") &&
            !U.jdkVersion().contains("1.7"))
            throw new IllegalStateException("GridGain requires Java 6 or above. Current Java version " +
                "is not supported: " + U.jdkVersion());

        String ggHome = X.getSystemOrEnv(GG_HOME);

        if (ggHome != null)
            System.setProperty(GG_HOME, ggHome);

        // Turn off default logging for Spring Framework.
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", null);
    }

    /**
     * Enforces singleton.
     */
    protected GridFactory() {
        // No-op.
    }

    /**
     * Sets daemon flag.
     * <p>
     * If daemon flag it set then all grid instances created by the factory will be
     * daemon, i.e. the local node for these instances will be a daemon node. Note that
     * if daemon flag is set - it will override the same settings in {@link GridConfiguration#isDaemon()}.
     * Note that you can set on and off daemon flag at will.
     *
     * @param daemon Daemon flag to set.
     */
    public static void setDaemon(boolean daemon) {
        GridFactory.daemon = daemon;
    }

    /**
     * Gets daemon flag.
     * <p>
     * If daemon flag it set then all grid instances created by the factory will be
     * daemon, i.e. the local node for these instances will be a daemon node. Note that
     * if daemon flag is set - it will override the same settings in {@link GridConfiguration#isDaemon()}.
     * Note that you can set on and off daemon flag at will.
     *
     * @return Daemon flag.
     */
    public static boolean isDaemon() {
        return daemon;
    }

    /**
     * Gets state of grid default grid.
     *
     * @return Default grid state.
     */
    public static GridFactoryState state() {
        return state(null);
    }

    /**
     * Gets states of named grid. If name is {@code null}, then state of
     * default no-name grid is returned.
     *
     * @param name Grid name. If name is {@code null}, then state of
     *      default no-name grid is returned.
     * @return Grid state.
     */
    public static GridFactoryState state(@Nullable String name) {
        GridNamedInstance grid;

        synchronized (mux) {
            grid = name == null ? dfltGrid : grids.get(name);
        }

        if (grid == null) {
            GridFactoryState state;

            synchronized (mux) {
                state = gridStates.get(name);
            }

            return state != null ? state : STOPPED;
        }

        return grid.state();
    }

    /**
     * Stops default grid. This method is identical to {@code G.stop(null,cancel)} apply.
     * Note that method does not wait for all tasks to be completed.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      default grid will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     * @return {@code true} if default grid instance was indeed stopped,
     *      {@code false} otherwise (if it was not started).
     */
    public static boolean stop(boolean cancel) {
        return stop(null, cancel);
    }

    /**
     * Stops default grid. This method is identical to {@code G.stop(null,cancel,wait)} apply.
     * If wait parameter is set to {@code true} then it will wait for all
     * tasks to be finished.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      default grid will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @param wait If {@code true} then method will wait for all tasks being
     *      executed until they finish their execution.
     * @return {@code true} if default grid instance was indeed stopped,
     *      {@code false} otherwise (if it was not started).
     */
    public static boolean stop(boolean cancel, boolean wait) {
        return stop(null, cancel, wait);
    }

    /**
     * Stops named grid. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted. If
     * grid name is {@code null}, then default no-name grid will be stopped.
     * It does not wait for the tasks to finish their execution.
     *
     * @param name Grid name. If {@code null}, then default no-name grid will
     *      be stopped.
     * @param cancel If {@code true} then all jobs currently will be cancelled
     *      by calling {@link GridJob#cancel()} method. Note that just like with
     *      {@link Thread#interrupt()}, it is up to the actual job to exit from
     *      execution. If {@code false}, then jobs currently running will not be
     *      canceled. In either case, grid node will wait for completion of all
     *      jobs running on it before stopping.
     * @return {@code true} if named grid instance was indeed found and stopped,
     *      {@code false} otherwise (the instance with given {@code name} was
     *      not found).
     */
    public static boolean stop(@Nullable String name, boolean cancel) {
        return stop(name, cancel, false);
    }

    /**
     * Stops named grid. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted. If
     * grid name is {@code null}, then default no-name grid will be stopped.
     * If wait parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     *
     * @param name Grid name. If {@code null}, then default no-name grid will
     *      be stopped.
     * @param cancel If {@code true} then all jobs currently will be cancelled
     *      by calling {@link GridJob#cancel()} method. Note that just like with
     *      {@link Thread#interrupt()}, it is up to the actual job to exit from
     *      execution. If {@code false}, then jobs currently running will not be
     *      canceled. In either case, grid node will wait for completion of all
     *      jobs running on it before stopping.
     * @param wait If {@code true} then method will wait for all tasks being
     *      executed until they finish their execution.
     * @return {@code true} if named grid instance was indeed found and stopped,
     *      {@code false} otherwise (the instance with given {@code name} was
     *      not found).
     */
    public static boolean stop(@Nullable String name, boolean cancel, boolean wait) {
        GridNamedInstance grid;

        synchronized (mux) {
            grid = name == null ? dfltGrid : grids.get(name);
        }

        if (grid != null && grid.state() == STARTED) {
            grid.stop(cancel, wait);

            boolean fireEvt = false;

            synchronized (mux) {
                if (name == null) {
                    if (dfltGrid == grid) { // This particular grid was just stopped.
                        fireEvt = true;

                        dfltGrid = null;
                    }
                }
                else if (grids.get(name) == grid) { // This particular grid was just stopped.
                    fireEvt = true;

                    grids.remove(name);
                }
            }

            if (fireEvt)
                notifyStateChange(grid.getName(), grid.state());

            return true;
        }

        // We don't have log at this point...
        U.warn(null, "Ignoring stopping grid instance that was already stopped or never started: " + name);

        return false;
    }

    /**
     * Stops <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted.
     * It does not wait for the tasks to finish their execution.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     */
    public static void stopAll(boolean cancel) {
        stopAll(cancel, false);
    }

    /**
     * Stops <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted.
     * If wait parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     * @param wait If {@code true} then method will wait for all tasks being
     *      executed until they finish their execution.
     */
    public static void stopAll(boolean cancel, boolean wait) {
        Collection<GridNamedInstance> copy = new ArrayList<GridNamedInstance>();

        synchronized (mux) {
            if (dfltGrid != null)
                copy.add(dfltGrid);

            copy.addAll(grids.values());
        }

        // Stop the rest and clear grids map.
        for (GridNamedInstance grid : copy) {
            grid.stop(cancel, wait);

            boolean fireEvt;

            synchronized (mux) {
                if (grid.getName() == null) {
                    fireEvt = dfltGrid != null;

                    dfltGrid = null;
                }
                else
                    fireEvt = grids.remove(grid.getName()) != null;
            }

            if (fireEvt)
                notifyStateChange(grid.getName(), grid.state());
        }
    }

    /**
     * Restarts <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on the local node will be interrupted.
     * If {@code wait} parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     * <p>
     * Note also that restarting functionality only works with the tools that specifically
     * support GridGain's protocol for restarting. Currently only standard <tt>ggstart.{sh|bat}</tt>
     * scripts support restarting of JVM GridGain's process.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @param wait If {@code true} then method will wait for all tasks being
     *      executed until they finish their execution.
     * @see #RESTART_EXIT_CODE
     */
    public static void restart(boolean cancel, boolean wait) {
        String file = System.getProperty(GG_SUCCESS_FILE);

        if (file == null)
            U.warn(null, "Cannot restart node when restart not enabled.");
        else {
            try {
                new File(file).createNewFile();
            }
            catch (IOException e) {
                U.error(null, "Failed to create restart marker file (restart aborted): " + e.getMessage());

                return;
            }

            U.log(null, "Restarting node. Will exit(" + RESTART_EXIT_CODE + ").");

            // Set the exit code so that shell process can recognize it and loop
            // the start up sequence again.
            System.setProperty(GG_RESTART_CODE, Integer.toString(RESTART_EXIT_CODE));

            stopAll(cancel, wait);

            // This basically leaves loaders hang - we accept it.
            System.exit(RESTART_EXIT_CODE);
        }
    }

    /**
     * Stops <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on the local node will be interrupted.
     * If {@code wait} parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     * <p>
     * Note that upon completion of this method, the JVM with forcefully exist with
     * exit code {@link #KILL_EXIT_CODE}.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @param wait If {@code true} then method will wait for all tasks being
     *      executed until they finish their execution.
     * @see #KILL_EXIT_CODE
     */
    public static void kill(boolean cancel, boolean wait) {
        stopAll(cancel, wait);

        // This basically leaves loaders hang - we accept it.
        System.exit(KILL_EXIT_CODE);
    }

    /**
     * Starts grid with default configuration. By default this method will
     * use grid configuration defined in {@code GRIDGAIN_HOME/config/default-spring.xml}
     * configuration file. If such file is not found, then all system defaults will be used.
     *
     * @return Started grid.
     * @throws GridException If default grid could not be started. This exception will be thrown
     *      also if default grid has already been started.
     */
    public static Grid start() throws GridException {
        return start((ApplicationContext)null);
    }

    /**
     * Starts grid with default configuration. By default this method will
     * use grid configuration defined in {@code GRIDGAIN_HOME/config/default-spring.xml}
     * configuration file. If such file is not found, then all system defaults will be used.
     *
     * @param springCtx Optional Spring application context.
     * @return Started grid.
     * @throws GridException If default grid could not be started. This exception will be thrown
     *      also if default grid has already been started.
     */
    public static Grid start(@Nullable ApplicationContext springCtx) throws GridException {
        URL url = U.resolveGridGainUrl(DFLT_CFG);

        if (url != null)
            return start(DFLT_CFG, springCtx);

        U.warn(null, "Default Spring XML file not found (is GRIDGAIN_HOME set?): " + DFLT_CFG);

        return start0(new GridConfigurationAdapter(), springCtx).grid();
    }

    /**
     * Starts grid with given configuration. Note that this method is no-op if grid with the name
     * provided in given configuration is already started.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @return Started grid.
     * @throws GridException If grid could not be started. This exception will be thrown
     *      also if named grid has already been started.
     */
    public static Grid start(GridConfiguration cfg) throws GridException {
        return start(cfg, null);
    }

    /**
     * Starts grid with given configuration. Note that this method is no-op if grid with the name
     * provided in given configuration is already started.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @param springCtx Optional Spring application context, possibly {@code null}. If provided, this
     *      context can be injected into grid tasks and grid jobs using
     *      {@link GridSpringApplicationContextResource @GridSpringApplicationContextResource} annotation.
     * @return Started grid.
     * @throws GridException If grid could not be started. This exception will be thrown
     *      also if named grid has already been started.
     */
    public static Grid start(GridConfiguration cfg, @Nullable ApplicationContext springCtx) throws GridException {
        A.notNull(cfg, "cfg");

        return start0(cfg, springCtx).grid();
    }

    /**
     * Starts all grids specified within given Spring XML configuration file. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static Grid start(@Nullable String springCfgPath) throws GridException {
        return springCfgPath == null ? start() : start(springCfgPath, null);
    }

    /**
     * Automatically starts default grid, executes provided closures and stop the grid.
     * Note that grid instance will be stopped with the cancel flag set to {@code true}.
     * If default grid has already been started prior to this method invocation then
     * closures are executed with existing grid and not starting a new one. Accordingly
     * this method does not stop the default grid if it didn't start it.
     * <p>
     * Note also that this method assumes there are <b>no concurrent modifications</b>
     * of default grid state (either via {@code stop()} or {@code start()} methods).
     *
     * @param ps Set of closures to execute. If none provided - this method is no-op.
     * @throws GridException If grid could not be started or Spring XML configuration file
     *      is invalid. It is also thrown if closure produces an exception.
     *      <p>
     *      Note that if a closure produces an exception no further closures will be
     *      executed and grid instance will be stopped (if it was started by this method).
     */
    public static void in(@Nullable GridInClosure<Grid>... ps) throws GridException {
        if (!F.isEmpty(ps)) {
            assert ps != null;

            boolean newGrid;

            synchronized (mux) {
                newGrid = dfltGrid == null;
            }

            Grid g = newGrid ? start() : grid();

            try {
                runIn(g, ps);
            }
            finally {
                if (newGrid)
                    stop(g.name(), true);
            }
        }
    }

    /**
     * Automatically starts specified grid, executes provided closures and stop the grid. Note
     * that grid instance will be stopped with the cancel flag set to {@code true}.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param ps Set of closures to execute. If none provided - this method is no-op.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid. It is also thrown
     *      if closure produces an exception.
     *      <p>
     *      Note that if a closure produces an exception no further closures will be
     *      executed and grid instance will be stopped.
     */
    public static void in(@Nullable String springCfgPath, @Nullable GridInClosure<Grid>... ps) throws GridException {
        if (!F.isEmpty(ps)) {
            assert ps != null;

            Grid g = start(springCfgPath);

            try {
                runIn(g, ps);
            }
            finally {
                stop(g.name(), true);
            }
        }
    }

    /**
     * Automatically starts specified grid, executes provided closures and stop the grid. Note
     * that grid instance will be stopped with the cancel flag set to {@code true}.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param ctx Optional spring application context.
     * @param ps Set of closures to execute. If none provided - this method is no-op.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid. It is also thrown
     *      if closure produces an exception.
     *      <p>
     *      Note that if a closure produces an exception no further closures will be
     *      executed and grid instance will be stopped.
     */
    public static void in(@Nullable String springCfgPath, @Nullable ApplicationContext ctx,
        @Nullable GridInClosure<Grid>... ps) throws GridException {

        if (!F.isEmpty(ps)) {
            assert ps != null;

            Grid g = springCfgPath == null ? start(ctx) : start(springCfgPath, ctx);

            try {
                runIn(g, ps);
            }
            finally {
                stop(g.name(), true);
            }
        }
    }

    /**
     * Automatically starts specified grid, executes provided closures and stop the grid.
     * Note that grid instance will be stopped with the cancel flag set to {@code true}.
     *
     * @param ctx Optional spring application context.
     * @param ps Set of closures to execute. If none provided - this method is no-op.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid. It is also thrown
     *      if closure produces an exception.
     *      <p>
     *      Note that if a closure produces an exception no further closures will be
     *      executed and grid instance will be stopped.
     */
    public static void in(@Nullable ApplicationContext ctx, @Nullable GridInClosure<Grid>... ps) throws GridException {
        if (!F.isEmpty(ps)) {
            assert ps != null;

            Grid g = start(ctx);

            try {
                runIn(g, ps);
            }
            finally {
                stop(g.name(), true);
            }
        }
    }

    /**
     * Automatically starts specified grid, executes provided closures and stop the grid.
     * Note that grid instance will be stopped with the cancel flag set to {@code true}.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @param ps Set of closures to execute. If none provided - this method is no-op.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid. It is also thrown
     *      if closure produces an exception.
     *      <p>
     *      Note that if a closure produces an exception no further closures will be
     *      executed and grid instance will be stopped.
     */
    public static void in(GridConfiguration cfg, @Nullable GridInClosure<Grid>... ps) throws GridException {
        A.notNull(cfg, "cfg");

        if (!F.isEmpty(ps)) {
            assert ps != null;

            Grid g = start(cfg);

            try {
                runIn(g, ps);
            }
            finally {
                stop(g.name(), true);
            }
        }
    }

    /**
     * Automatically starts specified grid, executes provided closures and stop the grid.
     * Note that grid instance will be stopped with the cancel flag set to {@code true}.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @param ctx Optional spring application context.
     * @param ps Set of closures to execute. If none provided - this method is no-op.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid. It is also thrown
     *      if closure produces an exception.
     *      <p>
     *      Note that if a closure produces an exception no further closures will be
     *      executed and grid instance will be stopped.
     */
    public static void in(GridConfiguration cfg, @Nullable ApplicationContext ctx,
        @Nullable GridInClosure<Grid>... ps) throws GridException {
        A.notNull(cfg, "cfg");

        if (!F.isEmpty(ps)) {
            assert ps != null;

            Grid g = start(cfg, ctx);

            try {
                runIn(g, ps);
            }
            finally {
                stop(g.name(), true);
            }
        }
    }

    /**
     * Automatically starts specified grid, executes provided closures and stop the grid.
     * Note that grid instance will be stopped with the cancel flag set to {@code true}.
     *
     * @param springCfgUrl Grid configuration.
     * @param ps Set of closures to execute. If none provided - this method is no-op.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid. It is also thrown
     *      if closure produces an exception.
     *      <p>
     *      Note that if a closure produces an exception no further closures will be
     *      executed and grid instance will be stopped.
     */
    public static void in(@Nullable URL springCfgUrl, @Nullable GridInClosure<Grid>... ps) throws GridException {
        A.notNull(springCfgUrl, "springCfgUrl");

        if (!F.isEmpty(ps)) {
            assert ps != null;

            Grid g = springCfgUrl == null ? start() : start(springCfgUrl);

            try {
                runIn(g, ps);
            }
            finally {
                stop(g.name(), true);
            }
        }
    }

    /**
     * Automatically starts specified grid, executes provided closures and stop the grid.
     * Note that grid instance will be stopped with the cancel flag set to {@code true}.
     *
     * @param springCfgUrl Grid configuration.
     * @param ctx Optional spring application context.
     * @param ps Set of closures to execute. If none provided - this method is no-op.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid. It is also thrown
     *      if closure produces an exception.
     *      <p>
     *      Note that if a closure produces an exception no further closures will be
     *      executed and grid instance will be stopped.
     */
    public static void in(@Nullable URL springCfgUrl, @Nullable ApplicationContext ctx,
        @Nullable GridInClosure<Grid>... ps) throws GridException {
        if (!F.isEmpty(ps)) {
            assert ps != null;

            Grid g = springCfgUrl == null ? start(ctx) : start(springCfgUrl, ctx);

            try {
                runIn(g, ps);
            }
            finally {
                stop(g.name(), true);
            }
        }
    }

    /**
     * Executes given closures with provided grid instance.
     *
     * @param g Grid instance.
     * @param ps Closures to execute.
     * @throws GridException Thrown in case of any error.
     */
    private static void runIn(Grid g, GridInClosure<Grid>... ps) throws GridException {
        assert g != null;
        assert ps != null;

        for (GridInClosure<Grid> p : ps)
            try {
                p.apply(g);
            }
            catch (Throwable e) {
                throw U.cast(e);
            }
    }

    /**
     * Starts all grids specified within given Spring XML configuration file. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgPath Spring XML configuration file path or URL. This cannot be {@code null}.
     * @param ctx Spring application context.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static Grid start(String springCfgPath, @Nullable ApplicationContext ctx) throws GridException {
        A.notNull(springCfgPath, "springCfgPath");

        URL url;

        try {
            url = new URL(springCfgPath);
        }
        catch (MalformedURLException e) {
            url = U.resolveGridGainUrl(springCfgPath);

            if (url == null)
                throw new GridException("Spring XML configuration path is invalid: " + springCfgPath +
                    ". Note that this path should be either absolute or a relative local file system path, " +
                    "relative to META-INF in classpath or valid URL to GRIDGAIN_HOME.", e);
        }

        return start(url, ctx);
    }

    /**
     * Starts all grids specified within given Spring XML configuration file URL. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgUrl Spring XML configuration file URL. This cannot be {@code null}.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static Grid start(URL springCfgUrl) throws GridException {
        return start(springCfgUrl, null);
    }

    /**
     * Starts all grids specified within given Spring XML configuration file URL. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgUrl Spring XML configuration file URL. This cannot be {@code null}.
     * @param ctx Optional Spring application context.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws GridException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    // Warning is due to Spring.
    @SuppressWarnings("unchecked")
    public static Grid start(URL springCfgUrl, @Nullable ApplicationContext ctx) throws GridException {
        A.notNull(springCfgUrl, "springCfgUrl");

        boolean isLog4jUsed = GridFactory.class.getClassLoader().getResource("org/apache/log4j/Appender.class") != null;

        Object rootLogger = null;

        Object nullApp = null;

        if (isLog4jUsed)
            try {
                // Add no-op logger to remove no-appender warning.
                Class loggerCls = Class.forName("org.apache.log4j.Logger");

                rootLogger = loggerCls.getMethod("getRootLogger").invoke(loggerCls);

                nullApp = Class.forName("org.apache.log4j.varia.NullAppender").newInstance();

                Class appCls = Class.forName("org.apache.log4j.Appender");

                rootLogger.getClass().getMethod("addAppender", appCls).invoke(rootLogger, nullApp);
            }
            catch (Exception e) {
                throw new GridException("Failed to add no-op logger for Log4j.", e);
            }

        GenericApplicationContext springCtx;

        try {
            springCtx = new GenericApplicationContext();

            new XmlBeanDefinitionReader(springCtx).loadBeanDefinitions(new UrlResource(springCfgUrl));

            springCtx.refresh();
        }
        catch (BeansException e) {
            throw new GridException("Failed to instantiate Spring XML application context [springUrl=" +
                springCfgUrl + ", err=" + e.getMessage() + ']', e);
        }

        Map cfgMap;

        try {
            // Note: Spring 2.x is not generics-friendly.
            cfgMap = springCtx.getBeansOfType(GridConfiguration.class);
        }
        catch (BeansException e) {
            throw new GridException("Failed to instantiate bean [type=" + GridConfiguration.class + ", err=" +
                e.getMessage() + ']', e);
        }

        if (cfgMap == null) {
            throw new GridException("Failed to find a single grid factory configuration in: " + springCfgUrl);
        }

        if (isLog4jUsed)
            try {
                // Remove previously added no-op logger.
                Class appenderCls = Class.forName("org.apache.log4j.Appender");

                rootLogger.getClass().getMethod("removeAppender", appenderCls).invoke(rootLogger, nullApp);
            }
            catch (Exception e) {
                throw new GridException("Failed to remove previously added no-op logger for Log4j.", e);
            }

        if (cfgMap.isEmpty())
            throw new GridException("Can't find grid factory configuration in: " + springCfgUrl);

        List<GridNamedInstance> grids = new ArrayList<GridNamedInstance>(cfgMap.size());

        try {
            for (GridConfiguration cfg : (Collection<GridConfiguration>)cfgMap.values()) {
                assert cfg != null;

                // Use either user defined context or our one.
                GridNamedInstance grid = start0(cfg, ctx == null ? springCtx : ctx);

                // Add it if it was not stopped during startup.
                if (grid != null)
                    grids.add(grid);
            }
        }
        catch (GridException e) {
            // Stop all instances started so far.
            for (GridNamedInstance grid : grids)
                try {
                    grid.stop(true, false);
                }
                catch (Exception e1) {
                    U.error(grid.log, "Error when stopping grid: " + grid, e1);
                }

            throw e;
        }

        // Return the first grid started.
        GridNamedInstance res = !grids.isEmpty() ? grids.get(0) : null;

        return res != null ? res.grid() : null;
    }

    /**
     * Starts grid with given configuration.
     *
     * @param cfg Grid configuration.
     * @param ctx Optional Spring application context.
     * @return Started grid.
     * @throws GridException If grid could not be started.
     */
    private static GridNamedInstance start0(GridConfiguration cfg, @Nullable ApplicationContext ctx)
        throws GridException {
        assert cfg != null;

        String name = cfg.getGridName();

        GridNamedInstance grid;

        boolean single;

        synchronized (mux) {
            if (name == null) {
                // If default grid is started - throw exception.
                if (dfltGrid != null)
                    throw new GridException("Default grid instance has already been started.");

                grid = dfltGrid = new GridNamedInstance(null);
            }
            else {
                if (name.length() == 0)
                    throw new GridException("Non default grid instances cannot have empty string name.");

                if (grids.containsKey(name))
                    throw new GridException("Grid instance with this name has already been started: " + name);

                grid = new GridNamedInstance(name);

                grids.put(name, grid);
            }

            single = (grids.size() == 1 && dfltGrid == null) || (grids.isEmpty() && dfltGrid != null);
        }

        boolean success = false;

        try {
            grid.start(cfg, single, ctx);

            notifyStateChange(name, STARTED);

            success = true;
        }
        finally {
            if (!success) {
                synchronized (mux) {
                    if (name == null)
                        dfltGrid = null;
                    else
                        grids.remove(name);

                    grid = null;
                }
            }
        }

        if (grid == null)
            throw new GridException("Failed to start grid with configuration: " + cfg);

        return grid;
    }

    /**
     * Gets an instance of default no-name grid. Note that
     * caller of this method should not assume that it will return the same
     * instance every time.
     * <p>
     * This method is identical to {@code G.grid(null)} apply.
     *
     * @return An instance of default no-name grid. This method never returns
     *      {@code null}.
     * @throws IllegalStateException Thrown if default grid was not properly
     *      initialized or grid instance was stopped or was not started.
     */
    public static Grid grid() throws IllegalStateException {
        return grid((String)null);
    }

    /**
     * Gets a list of all grids started so far.
     *
     * @return List of all grids started so far.
     */
    public static List<Grid> allGrids() {
        synchronized (mux) {
            List<Grid> allGrids = new ArrayList<Grid>(grids.size() + (dfltGrid == null ? 0 : 1));

            if (dfltGrid != null) {
                Grid g = dfltGrid.grid();

                if (g != null)
                    allGrids.add(g);
            }

            for (GridNamedInstance grid : grids.values()) {
                Grid g = grid.grid();

                if (g != null)
                    allGrids.add(g);
            }

            return allGrids;
        }
    }

    /**
     * Gets a grid instance for given local node ID. Note that grid instance and local node have
     * one-to-one relationship where node has ID and instance has name of the grid to which
     * both grid instance and its node belong. Note also that caller of this method
     * should not assume that it will return the same instance every time.
     *
     * @param localNodeId ID of local node the requested grid instance is managing.
     * @return An instance of named grid. This method never returns
     *      {@code null}.
     * @throws IllegalStateException Thrown if grid was not properly
     *      initialized or grid instance was stopped or was not started.
     */
    public static Grid grid(UUID localNodeId) throws IllegalStateException {
        A.notNull(localNodeId, "localNodeId");

        synchronized (mux) {
            if (dfltGrid != null) {
                GridKernal g = dfltGrid.grid();

                if(g != null && g.getLocalNodeId().equals(localNodeId))
                    return g;
            }

            for (GridNamedInstance grid : grids.values()) {
                GridKernal g = grid.grid();

                if (g != null && g.getLocalNodeId().equals(localNodeId))
                    return g;
            }
        }

        throw new IllegalStateException("Grid instance with given local node ID was not properly " +
            "started or was stopped: " + localNodeId);
    }

    /**
     * Gets an named grid instance. If grid name is {@code null} or empty string,
     * then default no-name grid will be returned. Note that caller of this method
     * should not assume that it will return the same instance every time.
     * <p>
     * Note that Java VM can run multiple grid instances and every grid instance (and its
     * node) can belong to a different grid. Grid name defines what grid a particular grid
     * instance (and correspondingly its node) belongs to.
     *
     * @param name Grid name to which requested grid instance belongs to. If {@code null},
     *      then grid instance belonging to a default no-name grid will be returned.
     * @return An instance of named grid. This method never returns
     *      {@code null}.
     * @throws IllegalStateException Thrown if default grid was not properly
     *      initialized or grid instance was stopped or was not started.
     */
    public static Grid grid(@Nullable String name) throws IllegalStateException {
        GridNamedInstance grid;

        synchronized (mux) {
            grid = name == null ? dfltGrid : grids.get(name);
        }

        Grid res;

        if (grid == null || (res = grid.grid()) == null)
            throw new IllegalStateException("Grid instance was not properly started or was already stopped: " + name);

        return res;
    }

    /**
     * Adds a lsnr for grid life cycle events.
     * <p>
     * Note that unlike other listeners in GridGain this listener will be
     * notified from the same thread that triggers the state change. Because of
     * that it is the responsibility of the user to make sure that listener logic
     * is light-weight and properly handles (catches) any runtime exceptions, if any
     * are expected.
     *
     * @param lsnr Listener for grid life cycle events. If this listener was already added
     *      this method is no-op.
     */
    public static void addListener(GridFactoryListener lsnr) {
        A.notNull(lsnr, "lsnr");

        synchronized (lsnrs) {
            if (!lsnrs.contains(lsnr))
                lsnrs.add(lsnr);
        }
    }

    /**
     * Removes lsnr added by {@link #addListener(GridFactoryListener)} method.
     *
     * @param lsnr Listener to remove.
     * @return {@code true} if lsnr was added before, {@code false} otherwise.
     */
    public static boolean removeListener(GridFactoryListener lsnr) {
        A.notNull(lsnr, "lsnr");

        synchronized (lsnrs) {
            return lsnrs.remove(lsnr);
        }
    }

    /**
     * @param gridName Grid instance name.
     * @param state Factory state.
     */
    private static void notifyStateChange(String gridName, GridFactoryState state) {
        Collection<GridFactoryListener> localCopy;

        synchronized (lsnrs) {
            localCopy = new ArrayList<GridFactoryListener>(lsnrs);
        }

        synchronized (mux) {
            gridStates.put(gridName, state);
        }

        for (GridFactoryListener lsnr : localCopy)
            lsnr.onStateChange(gridName, state);
    }

    /**
     * Grid data container.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static final class GridNamedInstance {
        /** Map of registered MBeans. */
        private static final Map<MBeanServer, GridMBeanServerData> mbeans =
            new HashMap<MBeanServer, GridMBeanServerData>();

        /** */
        private static final String[] EMPTY_STR_ARR = new String[0];

        /** Empty array of caches. */
        private static final GridCacheConfiguration[] EMPTY_CACHE_CONFIGS = new GridCacheConfiguration[0];

        /** Grid name. */
        private final String name;

        /** Grid instance. */
        private volatile GridKernal grid;

        /** Executor service. */
        private ExecutorService execSvc;

        /** Auto executor service flag. */
        private boolean isAutoExecSvc;

        /** Executor service shutdown flag. */
        private boolean execSvcShutdown;

        /** System executor service. */
        private ExecutorService sysExecSvc;

        /** Auto system service flag. */
        private boolean isAutoSysSvc;

        /** System executor service shutdown flag. */
        private boolean sysSvcShutdown;

        /** P2P executor service. */
        private ExecutorService p2pExecSvc;

        /** Auto P2P service flag. */
        private boolean isAutoP2PSvc;

        /** P2P executor service shutdown flag. */
        private boolean p2pSvcShutdown;

        /** Grid state. */
        private volatile GridFactoryState state = STOPPED;

        /** Shutdown hook. */
        private Thread shutdownHook;

        /** Grid log. */
        private GridLogger log;

        /** Start guard. */
        private final AtomicBoolean startGuard = new AtomicBoolean();

        /** Start latch. */
        private final CountDownLatch startLatch = new CountDownLatch(1);

        /** Thread that starts this named instance. This field can be non-volatile. */
        private Thread starterThread;

        /**
         * Creates un-started named instance.
         *
         * @param name Grid name (possibly {@code null} for default grid).
         */
        GridNamedInstance(@Nullable String name) {
            this.name = name;
        }

        /**
         * Gets grid name.
         *
         * @return Grid name.
         */
        String getName() {
            return name;
        }

        /**
         * Gets grid instance.
         *
         * @return Grid instance.
         */
        GridKernal grid() {
            if (starterThread != Thread.currentThread())
                U.awaitQuiet(startLatch);

            return grid;
        }

        /**
         * Gets grid state.
         *
         * @return Grid state.
         */
        GridFactoryState state() {
            if (starterThread != Thread.currentThread())
                U.awaitQuiet(startLatch);

            return state;
        }

        /**
         * @param spi SPI implementation.
         * @throws GridException Thrown in case if multi-instance is not supported.
         */
        private void ensureMultiInstanceSupport(GridSpi spi) throws GridException {
            GridSpiMultipleInstancesSupport ann = U.getAnnotation(spi.getClass(),
                GridSpiMultipleInstancesSupport.class);

            if (ann == null || !ann.value())
                throw new GridException("SPI implementation doesn't support multiple grid instances in " +
                    "the same VM: " + spi);
        }

        /**
         * @param spis SPI implementations.
         * @throws GridException Thrown in case if multi-instance is not supported.
         */
        private void ensureMultiInstanceSupport(GridSpi[] spis) throws GridException {
            for (GridSpi spi : spis)
                ensureMultiInstanceSupport(spi);
        }

        /**
         * Starts grid with given configuration.
         *
         * @param cfg Grid configuration (possibly {@code null}).
         * @param single Whether or not this is a single grid instance in current VM.
         * @param ctx Optional Spring application context.
         * @throws GridException If start failed.
         */
        synchronized void start(GridConfiguration cfg, boolean single, @Nullable ApplicationContext ctx)
            throws GridException {
            if (startGuard.compareAndSet(false, true)) {
                try {
                    starterThread = Thread.currentThread();

                    start0(cfg, single, ctx);
                }
                finally {
                    startLatch.countDown();
                }
            }
            else
                U.awaitQuiet(startLatch);
        }

        /**
         * @param cfg Grid configuration (possibly {@code null}).
         * @param single Whether or not this is a single grid instance in current VM.
         * @param ctx Optional Spring application context.
         * @throws GridException If start failed.
         */
        private void start0(GridConfiguration cfg, boolean single, ApplicationContext ctx) throws GridException {
            assert grid == null : "Grid is already started: " + name;

            if (cfg == null)
                cfg = new GridConfigurationAdapter();

            GridConfigurationAdapter myCfg = new GridConfigurationAdapter();

            /*
             * Set up all defaults and perform all checks.
             */

            // Ensure invariant.
            // It's a bit dirty - but this is a result of late refactoring
            // and I don't want to reshuffle a lot of code.
            assert F.eq(name, cfg.getGridName());

            myCfg.setGridName(cfg.getGridName());

            UUID nodeId = cfg.getNodeId();

            if (nodeId == null)
                nodeId = UUID.randomUUID();

            GridLogger cfgLog = cfg.getGridLogger();

            if (cfgLog == null) {
                URL url = U.resolveGridGainUrl("config/default-log4j.xml");

                cfgLog = url == null || GridLog4jLogger.isConfigured() ? new GridLog4jLogger() :
                    new GridLog4jLogger(url);
            }

            assert cfgLog != null;

            cfgLog = new GridLoggerProxy(cfgLog, null, name, U.id8(nodeId));

            // Initialize factory's log.
            log = cfgLog.getLogger(G.class);

            String ggHome = cfg.getGridGainHome();

            // Set GridGain home.
            if (ggHome == null)
                ggHome = U.getGridGainHome();
            else
                // If user provided GRIDGAIN_HOME - set it as a
                // system property.
                System.setProperty(GG_HOME, ggHome);

            // Check GridGain home folder.
            if (ggHome == null)
                U.warn(log, "Failed to detect GridGain installation home. It was neither provided in " +
                    "GridConfiguration nor it could be detected from " + GG_HOME +
                    " system property or environmental variable.", "Failed to detect GridGain installation home.");
            else {
                File ggHomeFile = new File(ggHome);

                if (!ggHomeFile.exists() || !ggHomeFile.isDirectory())
                    throw new GridException("Invalid GridGain installation home folder: " + ggHome);
            }

            myCfg.setGridGainHome(ggHome);

            // Handle the license URL only for Enterprise Edition.
            if (U.isEnterprise()) {
                String licUrl = cfg.getLicenseUrl();

                if (licUrl == null) {
                    URL url = U.resolveGridGainUrl(LIC_FILE_NAME);

                    if (url != null)
                        try {
                           licUrl = url.toURI().toASCIIString();
                        }
                        catch (URISyntaxException ignore) {
                            // Ignore here.
                        }

                    if (licUrl == null)
                        throw new GridException("License URL is not provided and cannot be determined.");
                }

                assert licUrl != null;

                myCfg.setLicenseUrl(licUrl);
            }

            // Copy values that don't need extra processing.
            myCfg.setPeerClassLoadingEnabled(cfg.isPeerClassLoadingEnabled());
            myCfg.setDeploymentMode(cfg.getDeploymentMode());
            myCfg.setNetworkTimeout(cfg.getNetworkTimeout());
            myCfg.setDiscoveryStartupDelay(cfg.getDiscoveryStartupDelay());
            myCfg.setMetricsHistorySize(cfg.getMetricsHistorySize());
            myCfg.setMetricsExpireTime(cfg.getMetricsExpireTime());
            myCfg.setLifecycleBeans(cfg.getLifecycleBeans());
            myCfg.setPeerClassLoadingMissedResourcesCacheSize(cfg.getPeerClassLoadingMissedResourcesCacheSize());
            myCfg.setIncludeEventTypes(cfg.getIncludeEventTypes());
            myCfg.setExcludeEventTypes(cfg.getExcludeEventTypes());
            myCfg.setDaemon(cfg.isDaemon());
            myCfg.setIncludeProperties(cfg.getIncludeProperties());
            myCfg.setLifeCycleEmailNotification(cfg.isLifeCycleEmailNotification());
            myCfg.setMetricsLogFrequency(cfg.getMetricsLogFrequency());
            myCfg.setLocalEventListeners(cfg.getLocalEventListeners());

            String ntfStr = X.getSystemOrEnv(GG_LIFECYCLE_EMAIL_NOTIFY);

            if (ntfStr != null)
                myCfg.setLifeCycleEmailNotification(Boolean.parseBoolean(ntfStr));

            // Local host.
            String locHost = X.getSystemOrEnv(GG_LOCAL_HOST);

            myCfg.setLocalHost(F.isEmpty(locHost) ? cfg.getLocalHost() : locHost);

            // Override daemon flag if it was set on the factory.
            if (daemon)
                myCfg.setDaemon(true);

            // Check for deployment mode override.
            String depModeName = X.getSystemOrEnv(GG_DEP_MODE_OVERRIDE);

            if (!F.isEmpty(depModeName)) {
                try {
                    GridDeploymentMode depMode = GridDeploymentMode.valueOf(depModeName);

                    if (myCfg.getDeploymentMode() != depMode)
                        myCfg.setDeploymentMode(depMode);
                }
                catch (IllegalArgumentException e) {
                    throw new GridException("Failed to override deployment mode using system property " +
                        "(are there any misspellings?)" +
                        "[name=" + GG_DEP_MODE_OVERRIDE + ", value=" + depModeName + ']', e);
                }
            }

            Map<String, ?> attrs = cfg.getUserAttributes();

            if (attrs == null)
                attrs = Collections.emptyMap();

            MBeanServer mbSrv = cfg.getMBeanServer();

            GridMarshaller marsh = cfg.getMarshaller();

            String[] p2pExclude = cfg.getPeerClassLoadingClassPathExclude();

            GridCommunicationSpi commSpi = cfg.getCommunicationSpi();
            GridDiscoverySpi discoSpi = cfg.getDiscoverySpi();
            GridEventStorageSpi evtSpi = cfg.getEventStorageSpi();
            GridCollisionSpi colSpi = cfg.getCollisionSpi();
            GridLocalMetricsSpi metricsSpi = cfg.getMetricsSpi();
            GridDeploymentSpi deploySpi = cfg.getDeploymentSpi();
            GridCheckpointSpi[] cpSpi = cfg.getCheckpointSpi();
            GridTopologySpi[] topSpi = cfg.getTopologySpi();
            GridFailoverSpi[] failSpi = cfg.getFailoverSpi();
            GridLoadBalancingSpi[] loadBalancingSpi = cfg.getLoadBalancingSpi();
            GridSwapSpaceSpi[] swapspaceSpi = cfg.getSwapSpaceSpi();

            execSvc = cfg.getExecutorService();
            sysExecSvc = cfg.getSystemExecutorService();
            p2pExecSvc = cfg.getPeerClassLoadingExecutorService();

            if (execSvc == null) {
                isAutoExecSvc = true;

                execSvc = new GridThreadPoolExecutor(cfg.getGridName(), DFLT_PUBLIC_THREAD_CNT,
                    DFLT_PUBLIC_THREAD_CNT, 0, new LinkedBlockingQueue<Runnable>());

                // Pre-start all threads as they are guaranteed to be needed.
                ((ThreadPoolExecutor)execSvc).prestartAllCoreThreads();
            }

            if (sysExecSvc == null) {
                isAutoSysSvc = true;

                // Note that since we use 'LinkedBlockingQueue', number of
                // maximum threads has no effect.
                // Note, that we do not pre-start threads here as system pool may
                // not be needed.
                sysExecSvc = new GridThreadPoolExecutor(cfg.getGridName(), DFLT_SYSTEM_THREAD_CNT,
                    DFLT_SYSTEM_THREAD_CNT, 0, new LinkedBlockingQueue<Runnable>());
            }

            if (p2pExecSvc == null) {
                isAutoP2PSvc = true;

                // Note that since we use 'LinkedBlockingQueue', number of
                // maximum threads has no effect.
                // Note, that we do not pre-start threads here as class loading pool may
                // not be needed.
                p2pExecSvc = new GridThreadPoolExecutor(cfg.getGridName(), DFLT_P2P_THREAD_CNT,
                    DFLT_P2P_THREAD_CNT, 0, new LinkedBlockingQueue<Runnable>());
            }

            execSvcShutdown = cfg.getExecutorServiceShutdown();
            sysSvcShutdown = cfg.getSystemExecutorServiceShutdown();
            p2pSvcShutdown = cfg.getPeerClassLoadingExecutorServiceShutdown();

            if (marsh == null)
                marsh = new GridOptimizedMarshaller();

            myCfg.setUserAttributes(attrs);
            myCfg.setMBeanServer(mbSrv == null ? ManagementFactory.getPlatformMBeanServer() : mbSrv);
            myCfg.setGridLogger(cfgLog);
            myCfg.setMarshaller(marsh);
            myCfg.setExecutorService(execSvc);
            myCfg.setSystemExecutorService(sysExecSvc);
            myCfg.setPeerClassLoadingExecutorService(p2pExecSvc);
            myCfg.setExecutorServiceShutdown(execSvcShutdown);
            myCfg.setSystemExecutorServiceShutdown(sysSvcShutdown);
            myCfg.setPeerClassLoadingExecutorServiceShutdown(p2pSvcShutdown);
            myCfg.setNodeId(nodeId);

            if (p2pExclude == null)
                p2pExclude = EMPTY_STR_ARR;

            myCfg.setPeerClassLoadingLocalClassPathExclude(p2pExclude);

            // Initialize default SPI implementations.

            if (commSpi == null)
                commSpi = new GridTcpCommunicationSpi();

            if (discoSpi == null)
                discoSpi = new GridMulticastDiscoverySpi();

            if (evtSpi == null)
                evtSpi = new GridMemoryEventStorageSpi();

            if (colSpi == null)
                colSpi = new GridFifoQueueCollisionSpi();

            if (metricsSpi == null)
                metricsSpi = new GridJdkLocalMetricsSpi();

            if (deploySpi == null)
                deploySpi = new GridLocalDeploymentSpi();

            if (cpSpi == null)
                cpSpi = new GridCheckpointSpi[] {new GridSharedFsCheckpointSpi()};

            if (topSpi == null)
                topSpi = new GridTopologySpi[] {new GridBasicTopologySpi()};

            if (failSpi == null)
                failSpi = new GridFailoverSpi[] {new GridAlwaysFailoverSpi()};

            if (loadBalancingSpi == null)
                loadBalancingSpi = new GridLoadBalancingSpi[] {new GridRoundRobinLoadBalancingSpi()};

            if (swapspaceSpi == null)
                swapspaceSpi = new GridSwapSpaceSpi[] {new GridFileSwapSpaceSpi()};

            myCfg.setCommunicationSpi(commSpi);
            myCfg.setDiscoverySpi(discoSpi);
            myCfg.setCheckpointSpi(cpSpi);
            myCfg.setEventStorageSpi(evtSpi);
            myCfg.setMetricsSpi(metricsSpi);
            myCfg.setDeploymentSpi(deploySpi);
            myCfg.setTopologySpi(topSpi);
            myCfg.setFailoverSpi(failSpi);
            myCfg.setCollisionSpi(colSpi);
            myCfg.setLoadBalancingSpi(loadBalancingSpi);
            myCfg.setSwapSpaceSpi(swapspaceSpi);

            // Set SMTP configuration.
            myCfg.setSmtpFromEmail(cfg.getSmtpFromEmail());
            myCfg.setSmtpHost(cfg.getSmtpHost());
            myCfg.setSmtpPort(cfg.getSmtpPort());
            myCfg.setSmtpSsl(cfg.isSmtpSsl());
            myCfg.setSmtpUsername(cfg.getSmtpUsername());
            myCfg.setSmtpPassword(cfg.getSmtpPassword());
            myCfg.setAdminEmails(cfg.getAdminEmails());

            // REST configuration.
            myCfg.setRestEnabled(cfg.isRestEnabled());
            myCfg.setRestJettyPath(cfg.getRestJettyPath());
            myCfg.setRestSecretKey(cfg.getRestSecretKey());

            // Validate segmentation configuration.
            if (!F.isEmpty(cfg.getSegmentationResolvers())) {
                // Segment check enabled, validate configuration.
                if (!U.isEnterprise())
                    throw new GridEnterpriseFeatureException("Network Segmentation Check");

                GridDiscoverySpiReconnectSupport ann =
                    U.getAnnotation(discoSpi.getClass(), GridDiscoverySpiReconnectSupport.class);

                if (cfg.getSegmentationPolicy() == RECONNECT && (ann == null || !ann.value())) {
                    throw new GridException("Discovery SPI does not support reconnect (either change segmentation " +
                        "policy or discovery SPI implementation to one that supports reconnect), " +
                        "like GridTcpDiscoverySpi.");
                }

                if ((cfg.getSegmentationPolicy() == RESTART_JVM || cfg.getSegmentationPolicy() == RECONNECT) &&
                    !cfg.isWaitForSegmentOnStart()) {
                    U.warn(log, "Found potential configuration problem (forgot to enable waiting for segment" +
                        "on start?) [segPlc=" + cfg.getSegmentationPolicy() + ", wait=false]");
                }

                if (cfg.getSegmentationPolicy() == RECONNECT) {
                    GridCacheConfiguration[] cacheCfgs = cfg.getCacheConfiguration();

                    if (cacheCfgs != null) {
                        for (GridCacheConfiguration cc : cacheCfgs)
                            if (cc.getCacheMode() == REPLICATED || cc.getCacheMode() == PARTITIONED)
                                throw new GridException("RECONNECT segmentation policy is not supported " +
                                    "when running distributed data grid.");
                    }
                }
            }

            myCfg.setSegmentationResolvers(cfg.getSegmentationResolvers());
            myCfg.setSegmentationPolicy(cfg.getSegmentationPolicy());
            myCfg.setSegmentCheckFrequency(cfg.getSegmentCheckFrequency());
            myCfg.setWaitForSegOnStart(cfg.isWaitForSegmentOnStart());
            myCfg.setAllSegmentationResolversPassRequired(cfg.isAllSegmentationResolversPassRequired());

            // Override SMTP configuration from system properties
            // and environment variables, if specified.
            String fromEmail = X.getSystemOrEnv(GG_SMTP_FROM);

            if (fromEmail != null)
                myCfg.setSmtpFromEmail(fromEmail);

            String smtpHost = X.getSystemOrEnv(GG_SMTP_HOST);

            if (smtpHost != null)
                myCfg.setSmtpHost(smtpHost);

            String smtpUsername = X.getSystemOrEnv(GG_SMTP_USERNAME);

            if (smtpUsername != null)
                myCfg.setSmtpUsername(smtpUsername);

            String smtpPwd = X.getSystemOrEnv(GG_SMTP_PWD);

            if (smtpPwd != null)
                myCfg.setSmtpPassword(smtpPwd);

            String smtpPort = X.getSystemOrEnv(GG_SMTP_PORT);

            if (smtpPort != null)
                try {
                    myCfg.setSmtpPort(Integer.parseInt(smtpPort));
                }
                catch (NumberFormatException e) {
                    U.error(log, "Invalid SMTP port override value (safely ignored): " + smtpPort, e);
                }

            String smtpSsl = X.getSystemOrEnv(GG_SMTP_SSL);

            if (smtpSsl != null)
                myCfg.setSmtpSsl(Boolean.parseBoolean(smtpSsl));

            String adminEmails = X.getSystemOrEnv(GG_ADMIN_EMAILS);

            if (adminEmails != null)
                myCfg.setAdminEmails(adminEmails.split(","));

            GridCacheConfiguration[] cacheCfgs = cfg.getCacheConfiguration();

            if (cacheCfgs != null) {
                if (cacheCfgs.length > 0) {
                    if (!discoOrdered(discoSpi) && !relaxDiscoveryOrdered())
                        throw new GridException("Discovery SPI implementation does not support node ordering and " +
                            "cannot be used with cache (use SPI with @GridDiscoverySpiOrderSupport annotation, " +
                            "like GridTcpDiscoverySpi)");

                    GridCacheConfiguration[] clone = cacheCfgs.clone();

                    for (int i = 0; i < cacheCfgs.length; i++)
                        clone[i] = new GridCacheConfigurationAdapter(cacheCfgs[i]);

                    myCfg.setCacheConfiguration(clone);
                }
                else
                    myCfg.setCacheConfiguration(cacheCfgs);
            }
            else {
                cacheCfgs = discoOrdered(discoSpi) || relaxDiscoveryOrdered() ?
                    new GridCacheConfiguration[] { new GridCacheConfigurationAdapter() } : EMPTY_CACHE_CONFIGS;

                myCfg.setCacheConfiguration(cacheCfgs);
            }

            try {
                Class helperCls = Class.forName("org.gridgain.grid.util.GridConfigurationHelper");

                helperCls.getMethod("overrideConfiguration", GridConfiguration.class, Properties.class,
                    String.class, GridLogger.class).invoke(helperCls, myCfg, System.getProperties(), name, log);
            }
            catch (Exception ignored) {
                // No-op.
            }

            // Ensure that SPIs support multiple grid instances, if required.
            if (!single) {
                ensureMultiInstanceSupport(deploySpi);
                ensureMultiInstanceSupport(commSpi);
                ensureMultiInstanceSupport(discoSpi);
                ensureMultiInstanceSupport(cpSpi);
                ensureMultiInstanceSupport(evtSpi);
                ensureMultiInstanceSupport(topSpi);
                ensureMultiInstanceSupport(colSpi);
                ensureMultiInstanceSupport(failSpi);
                ensureMultiInstanceSupport(metricsSpi);
                ensureMultiInstanceSupport(loadBalancingSpi);
                ensureMultiInstanceSupport(swapspaceSpi);
            }

            // Register GridFactory MBean for current grid instance.
            try {
                registerFactoryMbean(myCfg.getMBeanServer());
            }
            catch (GridException e) {
                stopExecutor(log);

                throw e;
            }
            // Catch Throwable to protect against any possible failure.
            catch (Throwable e) {
                stopExecutor(log);

                throw new GridException("Unexpected exception when starting grid.", e);
            }

            boolean started = false;

            try {
                GridKernal grid0 = new GridKernal(ctx);

                // Init here to make grid available to lifecycle listeners.
                grid = grid0;

                grid0.start(myCfg);

                state = STARTED;

                if (log.isDebugEnabled())
                    log.debug("Grid factory started ok: " + name);

                started = true;
            }
            catch (GridException e) {
                stopExecutor(log);

                unregisterFactoryMBean();

                throw e;
            }
            // Catch Throwable to protect against any possible failure.
            catch (Throwable e) {
                stopExecutor(log);

                unregisterFactoryMBean();

                throw new GridException("Unexpected exception when starting grid.", e);
            }
            finally {
                if (!started)
                    // Grid was not started.
                    grid = null;
            }

            // Do not set it up only if GRIDGAIN_NO_SHUTDOWN_HOOK=TRUE is provided.
            if (!"true".equalsIgnoreCase(X.getSystemOrEnv(GG_NO_SHUTDOWN_HOOK))) {
                try {
                    Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread() {
                        @Override public void run() {
                            if (log.isInfoEnabled())
                                log.info("Invoking shutdown hook...");

                            GridNamedInstance.this.stop(true, false);
                        }
                    });

                    if (log.isDebugEnabled())
                        log.debug("Shutdown hook is installed.");
                }
                catch (IllegalStateException e) {
                    stop(true, false);

                    throw new GridException("Failed to install shutdown hook.", e);
                }
            }
            else {
                if (log.isDebugEnabled())
                    log.debug("Shutdown hook has not been installed because environment " +
                        "or system property " + GG_NO_SHUTDOWN_HOOK + " is set.");
            }
        }

        /**
         * @param discoSpi Discovery SPI.
         * @return {@code True} if ordering is supported.
         */
        private static boolean discoOrdered(GridDiscoverySpi discoSpi) {
            GridDiscoverySpiOrderSupport ann = U.getAnnotation(discoSpi.getClass(), GridDiscoverySpiOrderSupport.class);

            return ann != null && ann.value();
        }

        /**
         * @return Checks if disco ordering should be enforced.
         */
        private static boolean relaxDiscoveryOrdered() {
            return "true".equalsIgnoreCase(System.getProperty(GG_NO_DISCO_ORDER));
        }

        /**
         * Stops grid.
         *
         * @param cancel Flag indicating whether all currently running jobs
         *      should be cancelled.
         * @param wait If {@code true} then method will wait for all task being
         *      executed until they finish their execution.
         */
        void stop(boolean cancel, boolean wait) {
            // Stop cannot be called prior to start from public API,
            // since it checks for STARTED state. So, we can assert here.
            assert startGuard.get();

            stop0(cancel, wait);
        }

        /**
         * @param cancel Flag indicating whether all currently running jobs
         *      should be cancelled.
         * @param wait If {@code true} then method will wait for all task being
         *      executed until they finish their execution.
         */
        private synchronized void stop0(boolean cancel, boolean wait) {
            GridKernal grid0 = grid;

            // Double check.
            if (grid0 == null) {
                if (log != null)
                    U.warn(log, "Attempting to stop an already stopped grid instance (ignore): " + name);

                return;
            }

            if (shutdownHook != null)
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);

                    shutdownHook = null;

                    if (log.isDebugEnabled())
                        log.debug("Shutdown hook is removed.");
                }
                catch (IllegalStateException e) {
                    // Shutdown is in progress...
                    if (log.isDebugEnabled())
                        log.debug("Shutdown is in progress (ignoring): " + e.getMessage());
                }

            // Unregister GridFactory MBean.
            unregisterFactoryMBean();

            try {
                grid0.stop(cancel, wait);

                if (log.isDebugEnabled())
                    log.debug("Grid instance stopped ok: " + name);
            }
            catch (Throwable e) {
                U.error(log, "Failed to properly stop grid instance due to undeclared exception.", e);
            }
            finally {
                state = grid0.context().segmented() ? STOPPED_ON_SEGMENTATION : STOPPED;

                grid = null;

                stopExecutor(log);

                log = null;
            }
        }

        /**
         * Stops executor service if it has been started.
         *
         * @param log Grid logger.
         */
        private void stopExecutor(GridLogger log) {
            assert log != null;
            assert execSvc != null;
            assert sysExecSvc != null;
            assert p2pExecSvc != null;

            /*
             * 1.
             * Attempt to stop all still active grid workers.
             * Note that these runnable should have been stopped
             * by the kernal. We are trying to be defensive here
             * so the logic is repetitive with kernal.
             */
            for (GridWorker w : GridWorkerGroup.instance(name).activeWorkers()) {
                String n1 = w.gridName() == null ? "" : w.gridName();
                String n2 = name == null ? "" : name;

                /*
                 * We should never get a runnable from one grid instance
                 * in the runnable group for another grid instance.
                 */
                assert n1.equals(n2);

                U.warn(log, "Runnable job outlived grid: " + w.name());

                U.cancel(w);
                U.join(w, log);
            }

            // Release memory.
            GridWorkerGroup.removeInstance(name);

            /*
             * 2.
             * If it was us who started the executor services than we
             * stop it. Otherwise, we do no-op since executor service
             * was started before us.
             */
            if (isAutoExecSvc || execSvcShutdown) {
                U.shutdownNow(getClass(), execSvc, log);

                execSvc = null;
            }

            if (isAutoSysSvc || sysSvcShutdown) {
                U.shutdownNow(getClass(), sysExecSvc, log);

                sysExecSvc = null;
            }

            if (isAutoP2PSvc || p2pSvcShutdown) {
                U.shutdownNow(getClass(), p2pExecSvc, log);

                p2pExecSvc = null;
            }
        }

        /**
         * Registers delegate Mbean instance for {@link GridFactory}.
         *
         * @param server MBeanServer where mbean should be registered.
         * @throws GridException If registration failed.
         */
        private void registerFactoryMbean(MBeanServer server) throws GridException {
            synchronized (mbeans) {
                GridMBeanServerData data = mbeans.get(server);

                if (data == null) {
                    try {
                        GridFactoryMBean mbean = new GridFactoryMBeanAdapter();

                        ObjectName objName = U.makeMBeanName(
                            null,
                            "Kernal",
                            GridFactory.class.getSimpleName()
                        );

                        // Make check if MBean was already registered.
                        if (!server.queryMBeans(objName, null).isEmpty())
                            throw new GridException("MBean was already registered: " + objName);
                        else {
                            objName = U.registerMBean(
                                server,
                                null,
                                "Kernal",
                                GridFactory.class.getSimpleName(),
                                mbean,
                                GridFactoryMBean.class
                            );

                            data = new GridMBeanServerData(objName);

                            mbeans.put(server, data);

                            if (log.isDebugEnabled())
                                log.debug("Registered MBean: " + objName);
                        }
                    }
                    catch (JMException e) {
                        throw new GridException("Failed to register MBean.", e);
                    }
                }

                assert data != null;

                data.addGrid(name);
                data.setCounter(data.getCounter() + 1);
            }
        }

        /**
         * Unregister delegate Mbean instance for {@link GridFactory}.
         */
        private void unregisterFactoryMBean() {
            synchronized (mbeans) {
                Iterator<Entry<MBeanServer, GridMBeanServerData>> iter = mbeans.entrySet().iterator();

                while (iter.hasNext()) {
                    Entry<MBeanServer, GridMBeanServerData> entry = iter.next();

                    if (entry.getValue().containsGrid(name)) {
                        GridMBeanServerData data = entry.getValue();

                        assert data != null;

                        // Unregister MBean if no grid instances started for current MBeanServer.
                        if (data.getCounter() == 1) {
                            try {
                                entry.getKey().unregisterMBean(data.getMbean());

                                if (log.isDebugEnabled())
                                    log.debug("Unregistered MBean: " + data.getMbean());
                            }
                            catch (JMException e) {
                                U.error(log, "Failed to unregister MBean.", e);
                            }

                            iter.remove();
                        }
                        else {
                            // Decrement counter.
                            data.setCounter(data.getCounter() - 1);
                            data.removeGrid(name);
                        }
                    }
                }
            }
        }

        /**
         * Grid factory MBean data container.
         * Contains necessary data for selected MBeanServer.
         *
         * @author 2012 Copyright (C) GridGain Systems
         * @version 3.6.0c.13012012
         */
        private static class GridMBeanServerData {
            /** Set of grid names for selected MBeanServer. */
            private Collection<String> gridNames = new HashSet<String>();

            /** */
            private ObjectName mbean;

            /** Count of grid instances. */
            private int cnt;

            /**
             * Create data container.
             *
             * @param mbean Object name of MBean.
             */
            GridMBeanServerData(ObjectName mbean) {
                assert mbean != null;

                this.mbean = mbean;
            }

            /**
             * Add grid name.
             *
             * @param gridName Grid name.
             */
            public void addGrid(String gridName) {
                gridNames.add(gridName);
            }

            /**
             * Remove grid name.
             *
             * @param gridName Grid name.
             */
            public void removeGrid(String gridName) {
                gridNames.remove(gridName);
            }

            /**
             * Returns {@code true} if data contains the specified
             * grid name.
             *
             * @param gridName Grid name.
             * @return {@code true} if data contains the specified grid name.
             */
            public boolean containsGrid(String gridName) {
                return gridNames.contains(gridName);
            }

            /**
             * Gets name used in MBean server.
             *
             * @return Object name of MBean.
             */
            public ObjectName getMbean() {
                return mbean;
            }

            /**
             * Gets number of grid instances working with MBeanServer.
             *
             * @return Number of grid instances.
             */
            public int getCounter() {
                return cnt;
            }

            /**
             * Sets number of grid instances working with MBeanServer.
             *
             * @param cnt Number of grid instances.
             */
            public void setCounter(int cnt) {
                this.cnt = cnt;
            }
        }
    }
}

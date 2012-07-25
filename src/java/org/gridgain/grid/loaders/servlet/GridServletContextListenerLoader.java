// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.servlet;

import org.gridgain.grid.*;
import org.gridgain.grid.loaders.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;

import javax.servlet.*;
import java.net.*;
import java.util.*;

/**
 * This class defines GridGain loader based on servlet context listener.
 * This loader can be used to start GridGain inside any web container.
 * Loader must be defined in {@code web.xml} file.
 * <pre name="code" class="xml">
 * &lt;context-param&gt;
 *     &lt;param-name&gt;cfgFilePath&lt;/param-name&gt;
 *     &lt;param-value&gt;config/default-spring.xml&lt;/param-value&gt;
 * &lt;/context-param&gt;
 *
 * &lt;listener&gt;
 *     &lt;listener-class&gt;org.gridgain.grid.loaders.servlet.GridServletContextListenerLoader&lt;/listener-class&gt;
 * &lt;/listener&gt;
 * </pre>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridLoader(description = "Servlet context listener loader")
public class GridServletContextListenerLoader implements ServletContextListener {
    /** Grid loaded flag. */
    private static boolean loaded;

    /** Configuration file path variable name. */
    private static final String cfgFilePathParam = "cfgFilePath";

    /** */
    private Collection<String> gridNames = new ArrayList<String>();

    /** {@inheritDoc} */
    @Override public void contextInitialized(ServletContextEvent evt) {
        // Avoid multiple servlet instances. GridGain should be loaded once.
        if (loaded)
            return;

        String cfgFile = evt.getServletContext().getInitParameter(cfgFilePathParam);

        if (cfgFile == null)
            throw new GridRuntimeException("Failed to read property: " + cfgFilePathParam);

        URL cfgUrl = U.resolveGridGainUrl(cfgFile);

        if (cfgUrl == null)
            throw new GridRuntimeException("Failed to find Spring configuration file (path provided should be " +
                "either absolute, relative to GRIDGAIN_HOME, or relative to META-INF folder): " + cfgFile);

        GenericApplicationContext springCtx;

        try {
            springCtx = new GenericApplicationContext();

            XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(springCtx);

            xmlReader.loadBeanDefinitions(new UrlResource(cfgUrl));

            springCtx.refresh();
        }
        catch (BeansException e) {
            throw new GridRuntimeException("Failed to instantiate Spring XML application context: " +
                e.getMessage(), e);
        }

        Map cfgMap;

        try {
            // Note: Spring is not generics-friendly.
            cfgMap = springCtx.getBeansOfType(GridConfiguration.class);
        }
        catch (BeansException e) {
            throw new GridRuntimeException("Failed to instantiate bean [type=" + GridConfiguration.class + ", err=" +
                e.getMessage() + ']', e);
        }

        if (cfgMap == null)
            throw new GridRuntimeException("Failed to find a single grid factory configuration in: " + cfgUrl);

        if (cfgMap.isEmpty())
            throw new GridRuntimeException("Can't find grid factory configuration in: " + cfgUrl);

        try {
            for (GridConfiguration cfg : (Collection<GridConfiguration>)cfgMap.values()) {
                assert cfg != null;

                GridConfiguration adapter = new GridConfigurationAdapter(cfg);

                Grid grid = G.start(adapter, springCtx);

                // Test if grid is not null - started properly.
                if (grid != null)
                    gridNames.add(grid.name());
            }
        }
        catch (GridException e) {
            // Stop started grids only.
            for (String name: gridNames)
                G.stop(name, true);

            throw new GridRuntimeException("Failed to start GridGain.", e);
        }

        loaded = true;
    }

    /** {@inheritDoc} */
    @Override public void contextDestroyed(ServletContextEvent evt) {
        // Stop started grids only.
        for (String name: gridNames)
            G.stop(name, true);

        loaded = true;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridServletContextListenerLoader.class, this);
    }
}

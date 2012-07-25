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
import org.gridgain.grid.typedef.*;
import java.util.*;
import static org.gridgain.grid.GridClosureCallMode.*;

/**
 * Demonstrates use of GridGain 3.0 functional APIs. This specific example
 * shows a bit more advance API and usage pattern using pre-defined closures
 * and more functional style of programming.
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
public class GridFunctionalMapReduceExample {
    /**
     * Counts non-whitespace character in the input string on the grid.
     *
     * @param args Input arguments. One string is expected.
     * @throws GridException Thrown in case of any grid errors.
     */
    public static void main(final String[] args) throws GridException {
        if (args.length == 1 && args[0].length() > 0)
            G.in(new GridInClosureX<Grid>() {
                @Override public void applyx(Grid g) throws GridException {
                    X.println("Length of input argument is " + g.reduce(
                        SPREAD,
                        new GridClosure<String, Integer>() {
                            @Override public Integer apply(String s) {
                                System.out.println("Calculating for: " + s);

                                return s.length();
                            }
                        },
                        //F.<String, Integer>cInvoke("length"),
                        Arrays.asList(args[0].split(" ")),
                        F.sumIntReducer()
                    ));
                }
            });
    }

    /**
     * Ensure singleton.
     */
    private GridFunctionalMapReduceExample() {
        // No-op.
    }
}

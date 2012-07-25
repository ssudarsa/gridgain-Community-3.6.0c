// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.deployment.direct;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

import java.util.*;

/**
 * Demonstrates how to explicitly deploy a task. Note that
 * it is very rare when you would need such functionality as tasks are
 * auto-deployed on demand first time you execute them. So in most cases
 * you would just apply any of the {@code Grid.execute(...)} methods directly.
 * However, sometimes a task is not in local class path, so you may not even
 * know the code it will execute, but you still need to execute it. For example,
 * you have two independent components in the system, and one loads the task
 * classes from some external source and deploys it; then another component
 * can execute it just knowing the name of the task.
 * <p>
 * Also note that for simplicity of the example, the task we execute is
 * in system classpath, so even in this case the deployment step is unnecessary.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridDeploymentExample {
    /** Name of the deployed task. */
    static final String TASK_NAME = "GridDeploymentExampleTask";

    /**
     * Ensure singleton.
     */
    private GridDeploymentExample() {
        // No-op.
    }

    /**
     * Deploys, executes and undeploys example task on the grid.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws GridException {
        Grid grid = args.length == 0 ? G.start() : G.start(args[0]);

        try {
            // This task will be deployed on local node and then peer-loaded
            // onto remote nodes on demand. For this example this task is
            // available on the classpath, however in real life that may not
            // always be the case. In those cases you should use explicit
            // 'Grid.deployTask(Class)}' apply and then use 'Grid.execute(String, Object)'
            // method passing your task name as first parameter.
            grid.deployTask(GridDeploymentExampleTask.class);

            for (Map.Entry<String, Class<? extends GridTask<?, ?>>> e : grid.localTasks().entrySet())
                X.println(">>> Found locally deployed task [alias=" + e.getKey() + ", taskCls=" + e.getValue());

            // Execute the task passing its name as a parameter. The system will find
            // the deployed task by its name and execute it.
            grid.execute(TASK_NAME, null).get();

            // Execute the task passing class name as a parameter. The system will find
            // the deployed task by its class name and execute it.
//            grid.execute(GridDeploymentExampleTask.class.getName(), null).get();

            // Undeploy task
            grid.undeployTask(TASK_NAME);

            X.println(
                ">>>",
                ">>> Finished executing Grid Direct Deployment Example.",
                ">>> Check participating nodes output.",
                ">>>");
        }
        finally {
            G.stopAll(true);
        }
    }
}

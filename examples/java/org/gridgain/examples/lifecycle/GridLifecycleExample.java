// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.lifecycle;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

/**
 * This example shows how to provide your own {@link GridLifecycleBean} implementation
 * to be able to hook into GridGain lifecycle. {@link GridLifecycleExampleBean} bean
 * will output occurred lifecycle events to the console.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridLifecycleExample {
    /**
     * Ensure singleton.
     */
    private GridLifecycleExample() {
        // No-op.
    }

    /**
     * Starts grid with configured lifecycle bean and then stop grid.
     *
     * @param args Command line arguments, none required.
     * @throws Exception If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        // Create new configuration.
        GridConfigurationAdapter cfg = new GridConfigurationAdapter();

        GridLifecycleExampleBean bean = new GridLifecycleExampleBean();

        // Provide lifecycle bean to configuration.
        cfg.setLifecycleBeans(bean);

        // Starts grid.
        G.start(cfg);

        try {
            // Make sure that lifecycle bean was notified about grid startup.
            assert bean.isStarted();
        }
        finally {
            // Stops grid.
            G.stop(true);
        }

        // Make sure that lifecycle bean was notified about grid stop.
        assert !bean.isStarted();
    }
}

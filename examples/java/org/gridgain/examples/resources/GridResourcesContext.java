// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.resources;

import org.gridgain.grid.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Context class for adding data in storage.
 * Context store all data in {@link ArrayList} by default.
 * Only one context object will be initialized during task deployment.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridResourcesContext {
    /** */
    @GridInstanceResource
    private Grid grid;

    /** */
    @GridLocalNodeIdResource
    private UUID nodeId;

    /** */
    private List<String> storage;

    /** */
    private String storageName;

    /** */
    private boolean started;

    /** */
    @SuppressWarnings("unused")
    @GridUserResourceOnDeployed private void deploy() {
        assert grid != null;
        assert nodeId != null;

        GridNode node = grid.localNode();

        // Get storage parameters from node attributes.
        storageName = (String)node.attribute("storage.name");

        if (storageName == null) {
            storageName = "test-storage";
        }

        storage = new ArrayList<String>();

        started = true;

        X.println("Starting context.");
    }

    /** */
    @SuppressWarnings("unused")
    @GridUserResourceOnUndeployed private void undeploy() {
        assert grid != null;
        assert nodeId != null;

        X.println("Stopping context.");
        X.println("Storage data: " + storage);
    }

    /**
     * Add data in storage.
     *
     * @param data Data to add.
     * @return Return {@code true} if data added.
     */
    public boolean sendData(String data) {
        if (!started)
            return false;

        assert storage != null;

        storage.add("Data [storageName=" + storageName +
            ", nodeId=" + nodeId.toString() +
            ", date=" + new Date() +
            ", data=" + data +
            ']');

        return true;
    }
}

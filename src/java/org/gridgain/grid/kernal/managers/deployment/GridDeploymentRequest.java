// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Deployment request.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class GridDeploymentRequest implements Externalizable {
    /** Response topic name. Response should be sent back to this topic. */
    private String resTopic;

    /** Requested class name. */
    private String rsrcName;

    /** Class loader ID. */
    private GridUuid ldrId;

    /** Undeploy flag. */
    private boolean isUndeploy;

    /** Nodes participating in request (chain). */
    @GridToStringInclude
    private Collection<UUID> nodeIds;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     * This constructor is not meant to be used for other purposes.
     */
    public GridDeploymentRequest() {
        // No-op.
    }

    /**
     * Creates new request.
     *
     * @param ldrId Class loader ID.
     * @param rsrcName Resource name that should be found and sent back.
     * @param isUndeploy Undeploy property.
     */
    GridDeploymentRequest(GridUuid ldrId, String rsrcName, boolean isUndeploy) {
        assert isUndeploy || ldrId != null;
        assert rsrcName != null;

        this.ldrId = ldrId;
        this.rsrcName = rsrcName;
        this.isUndeploy = isUndeploy;
    }

    /**
     * Sets response topic.
     *
     * @param resTopic New response topic.
     */
    void responseTopic(String resTopic) {
        this.resTopic = resTopic;
    }

    /**
     * Get topic response should be sent to.
     *
     * @return Response topic name.
     */
    String responseTopic() {
        return resTopic;
    }

    /**
     * Class name/resource name that is being requested.
     *
     * @return Resource or class name.
     */
    String resourceName() {
        return rsrcName;
    }

    /**
     * Gets property ldrId.
     *
     * @return Property ldrId.
     */
    GridUuid classLoaderId() {
        return ldrId;
    }

    /**
     * Gets property undeploy.
     *
     * @return Property undeploy.
     */
    boolean isUndeploy() {
        return isUndeploy;
    }

    /**
     * @return Node IDs chain which is updated as request jumps
     *      from node to node.
     */
    public Collection<UUID> nodeIds() {
        return nodeIds;
    }

    /**
     * @param nodeIds Node IDs chain which is updated as request jumps
     *      from node to node.
     */
    public void nodeIds(Collection<UUID> nodeIds) {
        this.nodeIds = nodeIds;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeBoolean(isUndeploy);
        U.writeString(out, resTopic);
        U.writeString(out, rsrcName);
        U.writeGridUuid(out, ldrId);
        U.writeCollection(out, nodeIds);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        isUndeploy = in.readBoolean();
        resTopic = U.readString(in);
        rsrcName = U.readString(in);
        ldrId = U.readGridUuid(in);
        nodeIds = U.readCollection(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentRequest.class, this);
    }
}

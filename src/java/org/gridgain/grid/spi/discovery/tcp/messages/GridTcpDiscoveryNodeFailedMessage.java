// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.messages;

import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Sent by node that has detected node failure to coordinator across the ring,
 * then sent by coordinator across the ring.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridTcpDiscoveryEnsureDelivery
public class GridTcpDiscoveryNodeFailedMessage extends GridTcpDiscoveryAbstractMessage {
    /** ID of the failed node. */
    private UUID failedNodeId;

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryNodeFailedMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNodeId ID of the node that detects nodes failure.
     * @param failedNodeId IDs of the failed nodes.
     */
    public GridTcpDiscoveryNodeFailedMessage(UUID creatorNodeId, UUID failedNodeId) {
        super(creatorNodeId);

        this.failedNodeId = failedNodeId;
    }

    /**
     * Gets ID of the failed node.
     *
     * @return ID of the failed node.
     */
    public UUID failedNodeId() {
        return failedNodeId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeUuid(out, failedNodeId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        failedNodeId = U.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryNodeFailedMessage.class, this, "super", super.toString());
    }
}

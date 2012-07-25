// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.messages;

import org.gridgain.grid.spi.discovery.tcp.internal.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Message telling joining node that new topology already contain
 * different node with same ID.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridTcpDiscoveryDuplicateIdMessage extends GridTcpDiscoveryAbstractMessage {
    /** Node with duplicate ID. */
    private GridTcpDiscoveryNode node;

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryDuplicateIdMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNodeId Creator node ID.
     * @param node Node with same ID.
     */
    public GridTcpDiscoveryDuplicateIdMessage(UUID creatorNodeId, GridTcpDiscoveryNode node) {
        super(creatorNodeId);

        assert node != null;

        this.node = node;
    }

    /**
     * @return Node with duplicate ID.
     */
    public GridTcpDiscoveryNode node() {
        return node;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeObject(node);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        node = (GridTcpDiscoveryNode)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryDuplicateIdMessage.class, this, "super", super.toString());
    }
}

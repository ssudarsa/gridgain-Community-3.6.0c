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
 * Handshake response.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridTcpDiscoveryHandshakeResponse extends GridTcpDiscoveryAbstractMessage {
    /** */
    private long order;

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryHandshakeResponse() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNodeId Creator node ID.
     * @param locNodeOrder Local node order.
     */
    public GridTcpDiscoveryHandshakeResponse(UUID creatorNodeId, long locNodeOrder) {
        super(creatorNodeId);

        order = locNodeOrder;
    }

    /**
     * Gets order of the node sent the response.
     *
     * @return Order of the node sent the response.
     */
    public long order() {
        return order;
    }

    /**
     * Sets order of the node sent the response.
     *
     * @param order Order of the node sent the response.
     */
    public void order(long order) {
        this.order = order;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeLong(order);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        order = in.readLong();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryHandshakeResponse.class, this, "super", super.toString());
    }
}

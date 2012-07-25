// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.nio;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.*;

/**
 * Grid client for NIO server.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridNioClient {
    /** Socket. */
    private final Socket sock = new Socket();

    /** Time when this client was last used. */
    private volatile long lastUsed = System.currentTimeMillis();

    /** Reservations. */
    private final AtomicInteger reserves = new AtomicInteger();

    /**
     * @param addr Address.
     * @param port Port.
     * @param localHost Local address.
     * @throws GridException If failed.
     */
    public GridNioClient(InetAddress addr, int port, InetAddress localHost) throws GridException {
        this(addr, port, localHost, 0);
    }

    /**
     * @param addr Address.
     * @param port Port.
     * @param localHost Local address.
     * @param connTimeout Connect timeout.
     * @throws GridException If failed.
     */
    public GridNioClient(InetAddress addr, int port, InetAddress localHost, int connTimeout) throws GridException {
        assert addr != null;
        assert port > 0 && port < 0xffff;
        assert localHost != null;
        assert connTimeout >= 0;

        boolean success = false;

        try {
            sock.bind(new InetSocketAddress(localHost, 0));

            sock.connect(new InetSocketAddress(addr, port), connTimeout);

            success = true;
        }
        catch (IOException e) {
            throw new GridException("Failed to connect to remote host [addr=" + addr + ", port=" + port +
                ", localHost=" + localHost + ']', e);
        }
        finally {
            if (!success)
                U.closeQuiet(sock);
        }
    }

    /**
     * @return {@code True} if client has been closed by this call,
     *      {@code false} if failed to close client (due to concurrent reservation or concurrent close).
     */
    public boolean close() {
        if (reserves.compareAndSet(0, -1)) {
            // Future reservation is not possible.
            U.closeQuiet(sock);

            return true;
        }

        return false;
    }

    /**
     * Forces client close.
     */
    public void forceClose() {
        // Future reservation is not possible.
        reserves.set(-1);

        U.closeQuiet(sock);
    }

    /**
     * @return {@code True} if client is closed;
     */
    public boolean closed() {
        return reserves.get() == -1;
    }

    /**
     * @return {@code True} if client was reserved, {@code false} otherwise.
     */
    public boolean reserve() {
        while (true) {
            int r = reserves.get();

            if (r == -1)
                return false;

            if (reserves.compareAndSet(r, r + 1))
                return true;
        }
    }

    /**
     * Releases this client by decreasing reservations.
     */
    public void release() {
        while (true) {
            int r = reserves.get();

            if (r == -1)
                return;

            if (reserves.compareAndSet(r, r - 1))
                return;
        }
    }

    /**
     * @return {@code True} if client was reserved.
     */
    public boolean reserved() {
        return reserves.get() > 0;
    }

    /**
     * Gets idle time of this client.
     *
     * @return Idle time of this client.
     */
    public long getIdleTime() {
        return System.currentTimeMillis() - lastUsed;
    }

    /**
     * @param data Data to send.
     * @param len Size of data in bytes.
     * @throws GridException If failed.
     */
    public synchronized void sendMessage(byte[] data, int len) throws GridException {
        if (reserves.get() == -1)
            throw new GridException("Client was closed: " + this);

        lastUsed = System.currentTimeMillis();

        try {
            OutputStream out = sock.getOutputStream();

            // We assume that this call does not return until the message
            // is fully sent.
            out.write(U.intToBytes(len));
            out.write(data, 0, len);
        }
        catch (IOException e) {
            throw new GridException("Failed to send message to remote node: " + sock.getRemoteSocketAddress(), e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNioClient.class, this);
    }
}

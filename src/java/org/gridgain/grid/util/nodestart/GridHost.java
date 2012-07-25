// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.nodestart;

import org.jetbrains.annotations.*;

/**
 * Host data.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridHost {
    /** Hostname. */
    private final String host;

    /** Port number. */
    private final int port;

    /** Username. */
    private final String uname;

    /** Password. */
    private final String passwd;

    /** Number of nodes to start. */
    private final int nodes;

    /**
     * Constructor.
     *
     * @param host Hostname.
     * @param port Port number.
     * @param uname Username.
     * @param passwd Password (can be {@code null} if private key authentication is used.
     * @param nodes Number of nodes to start.
     */
    GridHost(String host, int port, String uname, @Nullable String passwd, int nodes) {
        assert host != null;
        assert port > 0;
        assert uname != null;
        assert nodes > 0;

        this.host = host;
        this.port = port;
        this.uname = uname;
        this.passwd = passwd;
        this.nodes = nodes;
    }

    /**
     * Gets host name.
     *
     * @return Hostname.
     */
    public String host() {
        return host;
    }

    /**
     * Gets port number.
     *
     * @return Port number.
     */
    public int port() {
        return port;
    }

    /**
     * Gets username.
     *
     * @return Username.
     */
    public String uname() {
        return uname;
    }

    /**
     * Gets password.
     *
     * @return Password.
     */
    public String password() {
        return passwd;
    }

    /**
     * Gets number of nodes to start.
     *
     * @return Number of nodes to start.
     */
    public int nodes() {
        return nodes;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object r) {
        return this == r || !(r == null || !(r instanceof GridHost)) &&
            ((GridHost)r).host().equals(host);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return host.hashCode();
    }
}

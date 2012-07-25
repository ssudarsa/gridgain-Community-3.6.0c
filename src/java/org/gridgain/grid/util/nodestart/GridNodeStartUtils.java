// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.nodestart;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Util methods for {@code GridProjection.startNodes(..)} methods.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridNodeStartUtils {
    /** String that specifies range of IPs. */
    private static final String RANGE_SMB = "~";

    /** Default port. */
    private static final int DFLT_PORT = 22;

    /** Private constructor. */
    private GridNodeStartUtils() {
        // No-op.
    }

    /**
     * Parses host string into set of host data holders. Specification for host string:
     * <b>{<uname>{:<passwd>}@}<host>{:<port>}{#<num>}</b>
     *
     * @param hostSpec Host string.
     * @param dfltUname Default username.
     * @param dfltPasswd `Default password.
     * @param nodes Nodes count.
     * @param hasKey Whether private key file is defined.
     * @return Set of {@code Host} instances.
     * @throws GridException In case of error.
     */
    public static Collection<GridHost> mkHosts(String hostSpec, @Nullable String dfltUname,
        @Nullable String dfltPasswd, int nodes, boolean hasKey) throws GridException {
        assert hostSpec != null;
        assert nodes > 0;

        Collection<GridHost> hosts = new HashSet<GridHost>();

        String[] arr = hostSpec.split("#");

        if (arr.length == 2)
            try {
                nodes = Integer.valueOf(arr[1]);
            }
            catch (NumberFormatException e) {
                throw new GridException("Invalid nodes count: " + arr[1], e);
            }
        else if (arr.length > 2)
            throw new GridException("Invalid host string: " + hostSpec);

        if (nodes <= 0)
            throw new GridException("Invalid nodes count: " + nodes);

        arr = arr[0].split("@");

        if (arr.length == 1) {
            GridTuple2<Set<String>, Integer> t = extractHostsPort(arr[0]);

            String uname = dfltUname != null ? dfltUname : System.getProperty("user.name");
            String passwd = !hasKey ? dfltPasswd : null;

            if (!hasKey && passwd == null)
                throw new GridException("Password for " + hostSpec + " is not set.");

            for (String host : t.get1()) {
                hosts.add(new GridHost(host, t.get2(), uname, passwd, nodes));
            }
        }
        else if (arr.length == 2) {
            GridTuple2<Set<String>, Integer> t = extractHostsPort(arr[1]);

            arr = arr[0].split(":");

            String uname = arr[0];

            String passwd = null;

            if (arr.length > 1)
                passwd = arr[1];
            else if (!hasKey)
                passwd = dfltPasswd;

            if (!hasKey && passwd == null)
                throw new GridException("Password for " + hostSpec + " is not set.");

            for (String host : t.get1()) {
                hosts.add(new GridHost(host, t.get2(), uname, passwd, nodes));
            }
        }
        else
            throw new GridException("Invalid host string: " + hostSpec);

        return hosts;
    }

    /**
     * Extracts host and port.
     *
     * @param s Host String.
     * @return Tuple with host and port.
     * @throws GridException In case of error.
     */
    private static GridTuple2<Set<String>, Integer> extractHostsPort(String s)
        throws GridException {
        String[] hostPort = s.split(":");

        Set<String> hosts = expandHost(hostPort[0]);

        int port = DFLT_PORT;

        try {
            if (hostPort.length > 1)
                port = Integer.valueOf(hostPort[1]);
        }
        catch (NumberFormatException e) {
            throw new GridException("Invalid port number: " + hostPort[1], e);
        }

        if (port <= 0)
            throw new GridException("Invalid port number: " + port);

        return new GridTuple2<Set<String>, Integer>(hosts, port);
    }

    /**
     * Parses and expands range of IPs, if needed. Host names without the range
     * returned as is.
     *
     * @param addr Host host with or without `~` range.
     * @return Set of individual host names (IPs).
     * @throws GridException In case of error.
     */
    private static Set<String> expandHost(String addr) throws GridException {
        assert addr != null;

        Set<String> addrs = new HashSet<String>();

        if (addr.contains(RANGE_SMB)) {
            String[] parts = addr.split(RANGE_SMB);

            if (parts.length != 2)
                throw new GridException("Invalid IP range: " + addr);

            int lastDot = parts[0].lastIndexOf('.');

            if (lastDot < 0)
                throw new GridException("Invalid IP range: " + addr);

            String base = parts[0].substring(0, lastDot);
            String begin = parts[0].substring(lastDot + 1);
            String end = parts[1];

            try {
                int a = Integer.valueOf(begin);
                int b = Integer.valueOf(end);

                if (a > b)
                    throw new GridException("Invalid IP range: " + addr);

                for (int i = a; i <= b; i++)
                    addrs.add(base + "." + i);
            }
            catch (NumberFormatException e) {
                throw new GridException("Invalid IP range: " + addr, e);
            }
        }
        else
            addrs.add(addr);

        return addrs;
    }
}

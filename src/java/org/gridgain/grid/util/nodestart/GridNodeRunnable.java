// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.nodestart;

import com.jcraft.jsch.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * SSH-based node starter.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridNodeRunnable implements Runnable {
    /** Default GridGain home path for Windows (taken from environment variable). */
    private static final String DFLT_GG_HOME_WIN = "%GRIDGAIN_HOME%";

    /** Default GridGain home path for Linux (taken from environment variable). */
    private static final String DFLT_GG_HOME_LINUX = "$GRIDGAIN_HOME";

    /** Default start script path for Windows. */
    private static final String DFLT_SCRIPT_WIN = "bin\\ggstart.bat -v";

    /** Default start script path for Linux. */
    private static final String DFLT_SCRIPT_LINUX = "bin/ggstart.sh -v";

    /** Default log location for Windows. */
    private static final String DFLT_LOG_PATH_WIN = "work\\log\\gridgain.log";

    /** Default log location for Linux. */
    private static final String DFLT_LOG_PATH_LINUX = "work/log/gridgain.log";

    /** Node number. */
    private final int i;

    /** Hostname. */
    private final String host;

    /** Port number. */
    private final int port;

    /** Username. */
    private final String uname;

    /** Password. */
    private final String passwd;

    /** Private key file. */
    private final File key;

    /** GridGain installation path. */
    private String ggHome;

    /** Start script path. */
    private String script;

    /** Configuration file path. */
    private String cfg;

    /** Log file path. */
    private String log;

    /** Start results. */
    private final Collection<GridTuple3<String, Boolean, String>> res;

    /**
     * Constructor.
     *
     * @param i Node number.
     * @param host Hostname.
     * @param port Port number.
     * @param uname Username.
     * @param passwd Password.
     * @param key Private key file.
     * @param ggHome GridGain installation path.
     * @param script Start script path.
     * @param cfg Configuration file path.
     * @param log Log file path.
     * @param res Start results.
     */
    public GridNodeRunnable(int i, String host, int port, String uname, @Nullable String passwd,
        @Nullable File key, @Nullable String ggHome, @Nullable String script, @Nullable String cfg,
        @Nullable String log, Collection<GridTuple3<String, Boolean, String>> res) {
        assert host != null;
        assert port > 0;
        assert uname != null;
        assert res != null;

        this.i = i;
        this.host = host;
        this.port = port;
        this.uname = uname;
        this.passwd = passwd;
        this.key = key;
        this.ggHome = ggHome;
        this.script = script;
        this.cfg = cfg;
        this.log = log;
        this.res = res;
    }

    /** {@inheritDoc} */
    @Override public void run() {
        JSch ssh = new JSch();

        Session ses = null;

        try {
            if (key != null)
                ssh.addIdentity(key.getAbsolutePath());

            ses = ssh.getSession(uname, host, port);

            if (passwd != null)
                ses.setPassword(passwd);

            ses.setConfig("StrictHostKeyChecking", "no");

            ses.connect();

            ChannelExec ch = (ChannelExec)ses.openChannel("exec");

            boolean win = isWindows(ses);

            String separator = win ? "\\" : "/";

            if (ggHome == null)
                ggHome = win ? DFLT_GG_HOME_WIN : DFLT_GG_HOME_LINUX;

            if (script == null)
                script = win ? DFLT_SCRIPT_WIN : DFLT_SCRIPT_LINUX;

            if (cfg == null)
                cfg = "";

            if (log == null)
                log = win ? DFLT_LOG_PATH_WIN : DFLT_LOG_PATH_LINUX;

            SB sb = new SB();

            String cmd = sb.
                a(ggHome).a(separator).a(script).a(" ").a(cfg).a(" > ").
                a(ggHome).a(separator).a(log).a(".").a(i).
                a(win ? "" : " 2>& 1 &").
                toString();

            ch.setCommand(cmd);

            try {
                ch.connect();
            }
            finally {
                if (ch.isConnected())
                    ch.disconnect();
            }

            synchronized (res) {
                res.add(new GridTuple3<String, Boolean, String>(host, true, null));
            }
        }
        catch (JSchException e) {
            synchronized (res) {
                res.add(new GridTuple3<String, Boolean, String>(host, false, e.getMessage()));
            }
        }
        finally {
            if (ses.isConnected())
                ses.disconnect();
        }
    }

    /**
     * Checks whether host is running Windows OS.
     *
     * @param ses SSH session.
     * @return Whether host is running Windows OS.
     * @throws JSchException In case of SSH error.
     */
    private boolean isWindows(Session ses) throws JSchException {
        ChannelExec ch = (ChannelExec)ses.openChannel("exec");

        ch.setCommand("cmd.exe");

        try {
            ch.connect();

            return new BufferedReader(new InputStreamReader(ch.getInputStream())).readLine() != null;
        }
        catch (JSchException ignored) {
            return false;
        }
        catch (IOException ignored) {
            return false;
        }
        finally {
            if (ch.isConnected())
                ch.disconnect();
        }
    }
}

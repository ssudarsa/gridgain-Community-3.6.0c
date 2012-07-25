package org.gridgain.grid.spi.communication.tcp;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.communication.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.nio.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * <tt>GridTcpCommunicationSpi</tt> is default communication SPI which uses
 * TCP/IP protocol and Java NIO to communicate with other nodes.
 * <p>
 * To enable communication with other nodes, this SPI adds {@link #ATTR_ADDR}
 * and {@link #ATTR_PORT} local node attributes (see {@link GridNode#attributes()}.
 * <p>
 * At startup, this SPI tries to start listening to local port specified by
 * {@link #setLocalPort(int)} method. If local port is occupied, then SPI will
 * automatically increment the port number until it can successfully bind for
 * listening. {@link #setLocalPortRange(int)} configuration parameter controls
 * maximum number of ports that SPI will try before it fails. Port range comes
 * very handy when starting multiple grid nodes on the same machine or even
 * in the same VM. In this case all nodes can be brought up without a single
 * change in configuration.
 * <p>
 * This SPI caches connections to remote nodes so it does not have to reconnect every
 * time a message is sent. By default, idle connections are kept active for
 * {@link #DFLT_IDLE_CONN_TIMEOUT} period and then are closed. Use
 * {@link #setIdleConnectionTimeout(long)} configuration parameter to configure
 * you own idle connection timeout. In some cases network performance may be increased
 * if more that one active TCP connection is used. By default, SPI uses {@link #DFLT_MAX_OPEN_CLIENTS}
 * as maximum open clients. Use {@link #setMaxOpenClients(int)} configuration parameter
 * to configure maximum count of open clients per remote node.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * The following configuration parameters are optional:
 * <ul>
 * <li>Node local IP address (see {@link #setLocalAddress(String)})</li>
 * <li>Node local port number (see {@link #setLocalPort(int)})</li>
 * <li>Local port range (see {@link #setLocalPortRange(int)}</li>
 * <li>Port resolver (see {@link #setSpiPortResolver(GridSpiPortResolver)}</li>
 * <li>Number of threads used for handling NIO messages (see {@link #setMessageThreads(int)})</li>
 * <li>Idle connection timeout (see {@link #setIdleConnectionTimeout(long)})</li>
 * <li>Direct or heap buffer allocation (see {@link #setDirectBuffer(boolean)})</li>
 * <li>Count of selectors and selector threads for NIO server (see {@link #setSelectorsCount(int)})</li>
 * <li>Maximum count of open clients per remote node (see {@link #setMaxOpenClients(int)})</li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * GridTcpCommunicationSpi is used by default and should be explicitly configured
 * only if some SPI configuration parameters need to be overridden.
 * <pre name="code" class="java">
 * GridTcpCommunicationSpi commSpi = new GridTcpCommunicationSpi();
 *
 * // Override local port.
 * commSpi.setLocalPort(4321);
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default communication SPI.
 * cfg.setCommunicationSpi(commSpi);
 *
 * // Start grid.
 * GridFactory.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridTcpCommunicationSpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *         ...
 *         &lt;property name="communicationSpi"&gt;
 *             &lt;bean class="org.gridgain.grid.spi.communication.tcp.GridTcpCommunicationSpi"&gt;
 *                 &lt;!-- Override local port. --&gt;
 *                 &lt;property name="localPort" value="4321"/&gt;
 *             &lt;/bean&gt;
 *         &lt;/property&gt;
 *         ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridCommunicationSpi
 */
@SuppressWarnings({"deprecation"}) @GridSpiInfo(
    author = "GridGain Project",
    url = "www.gridgain.org",
    email = "support@gridgain.com",
    version = "3.0")
@GridSpiMultipleInstancesSupport(true)
public class GridTcpCommunicationSpi extends GridSpiAdapter implements GridCommunicationSpi,
    GridTcpCommunicationSpiMBean {
    /** Number of threads responsible for handling messages. */
    public static final int DFLT_MSG_THREADS = 20;

    /** Node attribute that is mapped to node IP address (value is <tt>comm.tcp.addr</tt>). */
    public static final String ATTR_ADDR = "comm.tcp.addr";

    /** Node attribute that is mapped to node port number (value is <tt>comm.tcp.port</tt>). */
    public static final String ATTR_PORT = "comm.tcp.port";

    /** Node attribute that is mapped to node's external ports numbers (value is <tt>comm.tcp.ext-ports</tt>). */
    public static final String ATTR_EXT_PORTS = "comm.tcp.ext-ports";

    /** Default port which node sets listener to (value is <tt>47100</tt>). */
    public static final int DFLT_PORT = 47100;

    /** Default idle connection timeout (value is <tt>30000</tt>ms). */
    public static final int DFLT_IDLE_CONN_TIMEOUT = 30000;

    /** Default connection timeout (value is <tt>1000</tt>ms). */
    public static final int DFLT_CONN_TIMEOUT = 1000;

    /** Default maximum count of simultaneously open client for one node (value is <tt>1</tt>). */
    public static final int DFLT_MAX_OPEN_CLIENTS = 1;

    /** Default count of selectors for tcp server equals to the count of processors in system. */
    public static final int DFLT_SELECTORS_CNT = Runtime.getRuntime().availableProcessors();

    /**
     * Default local port range (value is <tt>100</tt>).
     * See {@link #setLocalPortRange(int)} for details.
     */
    public static final int DFLT_PORT_RANGE = 100;

    /** Logger. */
    @GridLoggerResource
    private GridLogger log;

    /** Node ID. */
    @GridLocalNodeIdResource
    private UUID nodeId;

    /** Marshaller. */
    @GridMarshallerResource
    private GridMarshaller marsh;

    /** Local IP address. */
    private String localAddr;

    /** Complex variable that represents this node IP address. */
    private volatile InetAddress localHost;

    /** Local port which node uses. */
    private int localPort = DFLT_PORT;

    /** Local port range. */
    private int localPortRange = DFLT_PORT_RANGE;

    /** Grid name. */
    @GridNameResource
    private String gridName;

    /** Allocate direct buffer or heap buffer. */
    private boolean directBuf = true;

    /** Idle connection timeout. */
    private long idleConnTimeout = DFLT_IDLE_CONN_TIMEOUT;

    /** Connect timeout. */
    private int connTimeout = DFLT_CONN_TIMEOUT;

    /** NIO server. */
    private GridNioServer nioSrvr;

    /** Number of threads responsible for handling messages. */
    private int msgThreads = DFLT_MSG_THREADS;

    /** Idle client worker. */
    private IdleClientWorker idleClientWorker;

    /** Clients. */
    private final ConcurrentMap<UUID, GridNioClientPool> clients = GridConcurrentFactory.newMap();

    /** SPI listener. */
    private volatile GridMessageListener lsnr;

    /** Bound port. */
    private int boundTcpPort = -1;

    /** Maximum count of open clients to one node. */
    private int maxOpenClients = DFLT_MAX_OPEN_CLIENTS;

    /** Count of selectors to use in TCP server. */
    private int selectorsCnt = DFLT_SELECTORS_CNT;

    /** NIO pool. */
    private ThreadPoolExecutor nioExec;

    /** Port resolver. */
    private GridSpiPortResolver portRsvr;

    /** Received messages count. */
    private final AtomicInteger rcvdMsgsCnt = new AtomicInteger();

    /** Sent messages count.*/
    private final AtomicInteger sentMsgsCnt = new AtomicInteger();

    /** Received bytes count. */
    private final AtomicLong rcvdBytesCnt = new AtomicLong();

    /** Sent bytes count. */
    private final AtomicLong sentBytesCnt = new AtomicLong();

    /** Discovery listener. */
    private final GridLocalEventListener discoLsnr = new GridLocalEventListener() {
        @Override public void onEvent(GridEvent evt) {
            assert evt instanceof GridDiscoveryEvent;
            assert evt.type() == EVT_NODE_LEFT || evt.type() == EVT_NODE_FAILED;

            onNodeLeft(((GridDiscoveryEvent)evt).eventNodeId());
        }
    };

    /**
     * Sets local host address for socket binding. Note that one node could have
     * additional addresses beside the loopback one. This configuration
     * parameter is optional.
     *
     * @param localAddr IP address. Default value is any available local
     *      IP address.
     */
    @GridSpiConfiguration(optional = true)
    @GridLocalHostResource
    public void setLocalAddress(String localAddr) {
        // Injection should not override value already set by Spring or user.
        if (this.localAddr == null)
            this.localAddr = localAddr;
    }

    /** {@inheritDoc} */
    @Override public String getLocalAddress() {
        return localAddr;
    }

    /**
     * Number of threads used for handling messages received by NIO server.
     * This number usually should be no less than number of CPUs.
     * <p>
     * If not provided, default value is {@link #DFLT_MSG_THREADS}.
     *
     * @param msgThreads Number of threads.
     */
    @GridSpiConfiguration(optional = true)
    public void setMessageThreads(int msgThreads) {
        this.msgThreads = msgThreads;
    }

    /** {@inheritDoc} */
    @Override public int getMessageThreads() {
        return msgThreads;
    }

    /**
     * Sets local port for socket binding.
     * <p>
     * If not provided, default value is {@link #DFLT_PORT}.
     *
     * @param localPort Port number.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPort() {
        return localPort;
    }

    /**
     * Sets local port range for local host ports (value must greater than or equal to <tt>0</tt>).
     * If provided local port (see {@link #setLocalPort(int)}} is occupied,
     * implementation will try to increment the port number for as long as it is less than
     * initial value plus this range.
     * <p>
     * If port range value is <tt>0</tt>, then implementation will try bind only to the port provided by
     * {@link #setLocalPort(int)} method and fail if binding to this port did not succeed.
     * <p>
     * Local port range is very useful during development when more than one grid nodes need to run
     * on the same physical machine.
     * <p>
     * If not provided, default value is {@link #DFLT_PORT_RANGE}.
     *
     * @param localPortRange New local port range.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPortRange(int localPortRange) {
        this.localPortRange = localPortRange;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPortRange() {
        return localPortRange;
    }

    /**
     * Sets port resolver for ports mapping determination.
     *
     * @param portRsvr Port resolver.
     */
    @GridSpiConfiguration(optional = true)
    public void setSpiPortResolver(GridSpiPortResolver portRsvr) {
        this.portRsvr = portRsvr;
    }

    /** {@inheritDoc} */
    @Override public GridSpiPortResolver getSpiPortResolver() {
        return portRsvr;
    }

    /**
     * Sets maximum idle connection timeout upon which a connection
     * to client will be closed.
     * <p>
     * If not provided, default value is {@link #DFLT_IDLE_CONN_TIMEOUT}.
     *
     * @param idleConnTimeout Maximum idle connection time.
     */
    @GridSpiConfiguration(optional = true)
    public void setIdleConnectionTimeout(long idleConnTimeout) {
        this.idleConnTimeout = idleConnTimeout;
    }

    /** {@inheritDoc} */
    @Override public long getIdleConnectionTimeout() {
        return idleConnTimeout;
    }

    /**
     * Sets connect timeout used when establishing connection
     * with remote nodes.
     * <p>
     * {@code 0} is interpreted as infinite timeout.
     * <p>
     * If not provided, default value is {@link #DFLT_CONN_TIMEOUT}.
     *
     * @param connTimeout Connect timeout.
     */
    @GridSpiConfiguration(optional = true)
    public void setConnectTimeout(int connTimeout) {
        this.connTimeout = connTimeout;
    }

    /** {@inheritDoc} */
    @Override public int getConnectTimeout() {
        return connTimeout;
    }

    /**
     * Sets flag to allocate direct or heap buffer in SPI.
     * If value is {@code true}, then SPI will use {@link ByteBuffer#allocateDirect(int)} call.
     * Otherwise, SPI will use {@link ByteBuffer#allocate(int)} call.
     * <p>
     * If not provided, default value is {@code true}.
     *
     * @param directBuf Flag indicates to allocate direct or heap buffer in SPI.
     */
    @GridSpiConfiguration(optional = true)
    public void setDirectBuffer(boolean directBuf) {
        this.directBuf = directBuf;
    }

    /** {@inheritDoc} */
    @Override public boolean isDirectBuffer() {
        return directBuf;
    }

    /**
     * Sets the maximum count of simultaneously open clients per remote node.
     * <p/>
     * If not provided, default value is {@link #DFLT_MAX_OPEN_CLIENTS}.
     *
     * @param maxOpenClients Maximum count of open TCP clients per node.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxOpenClients(int maxOpenClients) {
        this.maxOpenClients = maxOpenClients;
    }

    /** {@inheritDoc} */
    @Override public int getMaxOpenClients() {
        return maxOpenClients;
    }

    /**
     * Sets the count of selectors te be used in TCP server.
     * <p/>
     * If not provided, default value is {@link #DFLT_SELECTORS_CNT}.
     *
     * @param selectorsCnt Selectors count.
     */
    @GridSpiConfiguration(optional = true)
    public void setSelectorsCount(int selectorsCnt) {
        this.selectorsCnt = selectorsCnt;
    }

    /** {@inheritDoc} */
    @Override public int getSelectorsCount() {
        return selectorsCnt;
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridMessageListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public int getNioActiveThreadCount() {
        assert nioExec != null;

        return nioExec.getActiveCount();
    }

    /** {@inheritDoc} */
    @Override public long getNioTotalCompletedTaskCount() {
        assert nioExec != null;

        return nioExec.getCompletedTaskCount();
    }

    /** {@inheritDoc} */
    @Override public int getNioCorePoolSize() {
        assert nioExec != null;

        return nioExec.getCorePoolSize();
    }

    /** {@inheritDoc} */
    @Override public int getNioLargestPoolSize() {
        assert nioExec != null;

        return nioExec.getLargestPoolSize();
    }

    /** {@inheritDoc} */
    @Override public int getNioMaximumPoolSize() {
        assert nioExec != null;

        return nioExec.getMaximumPoolSize();
    }

    /** {@inheritDoc} */
    @Override public int getNioPoolSize() {
        assert nioExec != null;

        return nioExec.getPoolSize();
    }

    /** {@inheritDoc} */
    @Override public long getNioTotalScheduledTaskCount() {
        assert nioExec != null;

        return nioExec.getTaskCount();
    }

    /** {@inheritDoc} */
    @Override public int getNioTaskQueueSize() {
        assert nioExec != null;

        return nioExec.getQueue().size();
    }

    /** {@inheritDoc} */
    @Override public int getSentMessagesCount() {
        return sentMsgsCnt.get();
    }

    /** {@inheritDoc} */
    @Override public long getSentBytesCount() {
        return sentBytesCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getReceivedMessagesCount() {
        return rcvdMsgsCnt.get();
    }

    /** {@inheritDoc} */
    @Override public long getReceivedBytesCount() {
        return rcvdBytesCnt.get();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> getNodeAttributes() throws GridSpiException {
        assertParameter(localPort > 1023, "localPort > 1023");
        assertParameter(localPort <= 0xffff, "localPort < 0xffff");
        assertParameter(localPortRange >= 0, "localPortRange >= 0");
        assertParameter(msgThreads > 0, "msgThreads > 0");

        nioExec = new ThreadPoolExecutor(msgThreads, msgThreads, Long.MAX_VALUE, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(), new GridSpiThreadFactory(gridName, "grid-nio-msg-handler", log));

        try {
            localHost = F.isEmpty(localAddr) ? U.getLocalHost() : InetAddress.getByName(localAddr);
        }
        catch (IOException e) {
            throw new GridSpiException("Failed to initialize local address: " + localAddr, e);
        }

        try {
            // This method potentially resets local port to the value
            // local node was bound to.
            nioSrvr = resetServer();
        }
        catch (GridException e) {
            throw new GridSpiException("Failed to initialize TCP server: " + localHost, e);
        }

        Collection<Integer> extPorts = null;

        if (portRsvr != null) {
            try {
                extPorts = portRsvr.getExternalPorts(boundTcpPort);
            }
            catch (GridException e) {
                throw new GridSpiException("Failed to get mapped external ports for bound port: [portRsvr=" + portRsvr +
                    ", boundTcpPort=" + boundTcpPort + ']', e);
            }
        }

        // Set local node attributes.
        return F.asMap(
            createSpiAttributeName(ATTR_ADDR), localHost,
            createSpiAttributeName(ATTR_PORT), boundTcpPort,
            createSpiAttributeName(ATTR_EXT_PORTS), extPorts);
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        assert localHost != null;

        // Start SPI start stopwatch.
        startStopwatch();

        assertParameter(idleConnTimeout > 0, "idleConnTimeout > 0");

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("localAddr", localAddr));
            log.debug(configInfo("msgThreads", msgThreads));
            log.debug(configInfo("localPort", localPort));
            log.debug(configInfo("localPortRange", localPortRange));
            log.debug(configInfo("idleConnTimeout", idleConnTimeout));
            log.debug(configInfo("directBuf", directBuf));
        }

        registerMBean(gridName, this, GridTcpCommunicationSpiMBean.class);

        nioSrvr.start();

        idleClientWorker = new IdleClientWorker();

        idleClientWorker.start();

        // Ack start.
        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} }*/
    @Override public void onContextInitialized(GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        getSpiContext().registerPort(boundTcpPort, GridPortProtocol.TCP);

        getSpiContext().addLocalEventListener(discoLsnr, EVT_NODE_LEFT, EVT_NODE_FAILED);
    }

    /**
     * Recreates tpcSrvr socket instance.
     *
     * @return Server socket.
     * @throws GridException Thrown if it's not possible to create tpcSrvr socket.
     */
    private GridNioServer resetServer() throws GridException {
        int maxPort = localPort + localPortRange;

        GridNioServerListener lsnr = new GridNioServerListener() {
            /** Cached class loader. */
            private final ClassLoader clsLdr = getClass().getClassLoader();

            /** {@inheritDoc} */
            @Override public void onMessage(byte[] data) {
                try {
                    GridTcpCommunicationMessage msg = U.unmarshal(marsh,
                        new GridByteArrayList(data, data.length), clsLdr);

                    rcvdMsgsCnt.incrementAndGet();

                    rcvdBytesCnt.addAndGet(data.length);

                    notifyListener(msg);
                }
                catch (GridException e) {
                    U.error(log, "Failed to deserialize TCP message.", e);
                }
            }
        };

        GridNioServer srvr = null;

        // If bound TPC port was not set yet, then find first
        // available port.
        if (boundTcpPort < 0)
            for (int port = localPort; port < maxPort; port++)
                try {
                    srvr = new GridNioServer(localHost, port, lsnr, log, nioExec, selectorsCnt, gridName,
                        directBuf, false);

                    boundTcpPort = port;

                    // Ack Port the TCP server was bound to.
                    if (log.isInfoEnabled())
                        log.info("Successfully bound to TCP port [port=" + boundTcpPort +
                            ", localHost=" + localHost + ']');

                    break;
                }
                catch (GridException e) {
                    if (port + 1 < maxPort) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to bind to local port (will try next port within range) [port=" + port +
                                ", localHost=" + localHost + ']');
                    }
                    else
                        throw new GridException("Failed to bind to any port within range [startPort=" + localPort +
                            ", portRange=" + localPortRange + ", localHost=" + localHost + ']', e);
                }
        else
            throw new GridException("Tcp NIO server was already created on port " + boundTcpPort);

        assert srvr != null;

        return srvr;
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        unregisterMBean();

        // Stop TCP server.
        if (nioSrvr != null)
            nioSrvr.stop();

        // Stop NIO thread pool.
        U.shutdownNow(getClass(), nioExec, log);

        U.interrupt(idleClientWorker);
        U.join(idleClientWorker, log);

        // Close all client connections.
        for (GridNioClientPool pool : clients.values())
            pool.forceClose();

        // Clear resources.
        nioSrvr = null;
        idleClientWorker = null;

        boundTcpPort = -1;

        // Ack stop.
        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        getSpiContext().deregisterPorts();

        getSpiContext().removeLocalEventListener(discoLsnr);

        super.onContextDestroyed();
    }

    /**
     * @param nodeId Left node ID.
     */
    private void onNodeLeft(UUID nodeId) {
        assert nodeId != null;

        GridNioClientPool pool = clients.get(nodeId);

        if (pool != null) {
            if (log.isDebugEnabled())
                log.debug("Forcing NIO client close since node has left [nodeId=" + nodeId +
                    ", client=" + pool + ']');

            pool.forceClose();

            clients.remove(nodeId, pool);
        }
    }

    /** {@inheritDoc} */
    @Override protected void checkConfigurationConsistency(GridNode node, boolean starting) {
        super.checkConfigurationConsistency(node, starting);

        // These attributes are set on node startup in any case, so we MUST receive them.
        checkAttributePresence(node, createSpiAttributeName(ATTR_ADDR));
        checkAttributePresence(node, createSpiAttributeName(ATTR_PORT));
    }

    /**
     * Checks that node has specified attribute and prints warning if it does not.
     *
     * @param node Node to check.
     * @param attrName Name of the attribute.
     */
    private void checkAttributePresence(GridNode node, String attrName) {
        if (node.attribute(attrName) == null)
            U.warn(log, "Remote node has inconsistent configuration (required attribute was not found) " +
                "[attrName=" + attrName + ", nodeId=" + node.id() +
                "spiCls=" + U.getSimpleName(GridTcpCommunicationSpi.class) + ']');
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(GridNode destNode, Serializable msg) throws GridSpiException {
        assert destNode != null;
        assert msg != null;

        send0(destNode, msg);
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(Collection<? extends GridNode> destNodes, Serializable msg) throws
        GridSpiException {
        assert destNodes != null;
        assert msg != null;
        assert !destNodes.isEmpty();

        for (GridNode node : destNodes)
            send0(node, msg);
    }

    /**
     * Sends message to certain node. This implementation uses {@link GridMarshaller}
     * as stable and fast stream implementation.
     *
     * @param node Node message should be sent to.
     * @param msg Message that should be sent.
     * @throws GridSpiException Thrown if any socket operation fails.
     */
    private void send0(GridNode node, Serializable msg) throws GridSpiException {
        assert node != null;
        assert msg != null;

        if (log.isDebugEnabled())
            log.debug("Sending message to node [node=" + node + ", msg=" + msg + ']');

        //Shortcut for the local node
        if (node.id().equals(nodeId))
            // Call listener directly. The manager will execute this
            // callback in a different thread, so there should not be
            // a deadlock.
            notifyListener(new GridTcpCommunicationMessage(nodeId, msg));
        else {
            GridNioClient client = null;

            try {
                client = reserveClient(node);

                GridByteArrayList buf = U.marshal(marsh, new GridTcpCommunicationMessage(nodeId, msg));

                client.sendMessage(buf.getInternalArray(), buf.getSize());

                sentMsgsCnt.incrementAndGet();

                sentBytesCnt.addAndGet(buf.getSize());
            }
            catch (GridException e) {
                throw new GridSpiException("Failed to send message to remote node: " + node, e);
            }
            finally {
                releaseClient(node, client);
            }
        }
    }

    /**
     * Returns existing or just created client to node.
     *
     * @param node Node to which client should be open.
     * @return The existing or just created client.
     * @throws GridException Thrown if any exception occurs.
     */
    @SuppressWarnings("unchecked")
    private GridNioClient reserveClient(GridNode node) throws GridException {
        assert node != null;

        UUID nodeId = node.id();

        while (true) {
            GridNioClientPool pool = clients.get(nodeId);

            if (pool == null) {
                GridNioClientPool old = clients.putIfAbsent(nodeId, pool = new GridNioClientPool(maxOpenClients, node));

                if (old != null)
                    pool = old;

                if (getSpiContext().node(nodeId) == null) {
                    pool.forceClose();

                    clients.remove(nodeId, pool);

                    throw new GridSpiException("Destination node is not in topology: " + node.id());
                }
            }

            GridNioClient client = pool.acquireClient();

            if (client != null)
                return client;
            else
                // Pool has just been closed by idle thread. Help it and try again.
                clients.remove(nodeId, pool);
        }
    }

    /**
     * Releases a client for remote node to the client pool.
     *
     * @param node Node for which this client was requested.
     * @param client Client to release.
     */
    private void releaseClient(GridNode node, GridNioClient client) {
        if (client != null) {
            GridNioClientPool pool = clients.get(node.id());

            if (pool == null) {
                U.warn(log, "NIO client pool for remote node was released while client is in use: nodeId=" + node.id());

                client.forceClose();
            }
            else
                pool.releaseClient(client);
        }
    }

    /**
     * @param node Node to create client for.
     * @return Client.
     * @throws GridException If failed.
     */
    private GridNioClient createNioClient(GridNode node) throws GridException {
        assert node != null;

        Collection<String> addrs = new LinkedHashSet<String>();

        // Try to connect first on bound address.
        InetAddress boundAddr = (InetAddress)node.attribute(createSpiAttributeName(ATTR_ADDR));

        if (boundAddr != null)
            addrs.add(boundAddr.getHostAddress());

        Collection<String> addrs1;

        // Then on internal addresses.
        if ((addrs1 = node.internalAddresses()) != null)
            addrs.addAll(addrs1);

        // And finally, try external addresses.
        if ((addrs1 = node.externalAddresses()) != null)
            addrs.addAll(addrs1);

        if (addrs.isEmpty())
            throw new GridException("Node doesn't have any bound, internal or external IP addresses: " + node.id());

        Collection<Integer> ports = new LinkedHashSet<Integer>();

        // Try to connect first on bound port.
        Integer boundPort = (Integer)node.attribute(createSpiAttributeName(ATTR_PORT));

        if (boundPort != null)
            ports.add(boundPort);

        // Then on mapped external ports, if any.
        Collection<Integer> extPorts = (Collection<Integer>)node.attribute(
            createSpiAttributeName(ATTR_EXT_PORTS));

        if (extPorts != null)
            ports.addAll(extPorts);

        if (addrs.isEmpty() || ports.isEmpty())
            throw new GridSpiException("Failed to send message to the destination node. " +
                "Node does not have IP address or port set up. Check configuration and make sure " +
                "that you use the same communication SPI on all nodes. Remote node id: " + node.id());

        boolean conn = false;

        GridNioClient client = null;

        for (String addr : addrs) {
            for (Integer port : ports) {
                try {
                    client = new GridNioClient(InetAddress.getByName(addr), port, localHost, connTimeout);

                    conn = true;

                    break;
                }
                catch (Exception e) {
                    if (log.isDebugEnabled())
                        log.debug("Client creation failed [addr=" + addr + ", port=" + port +
                            ", err=" + e + ']');

                    if (X.hasCause(e, SocketTimeoutException.class))
                        LT.warn(log, null, "Connect timed out. Consider changing 'connTimeout' " +
                            "configuration property.");
                }
            }

            if (conn)
                break;
        }

        if (client == null)
            throw new GridException("Failed to connect to node (did node left grid?): " + node.id());

        return client;
    }

    /**
     * @param msg Communication message.
     */
    protected void notifyListener(GridTcpCommunicationMessage msg) {
        GridMessageListener lsnr = this.lsnr;

        if (lsnr != null)
            // Notify listener of a new message.
            lsnr.onMessage(msg.getNodeId(), msg.getMessage());
        else if (log.isDebugEnabled())
            log.debug("Received communication message without any registered listeners (will ignore, " +
                "is node stopping?) [senderNodeId=" + msg.getNodeId() + ", msg=" + msg.getMessage() + ']');
    }

    /**
     *
     */
    private class IdleClientWorker extends GridSpiThread {
        /**
         *
         */
        IdleClientWorker() {
            super(gridName, "nio-idle-client-collector", log);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"BusyWait"})
        @Override protected void body() throws InterruptedException {
            while (!isInterrupted()) {
                for (Map.Entry<UUID, GridNioClientPool> e : clients.entrySet()) {
                    UUID nodeId = e.getKey();

                    GridNioClientPool pool = e.getValue();

                    if (getSpiContext().node(nodeId) == null) {
                        if (log.isDebugEnabled())
                            log.debug("Forcing close of non-existent node connection: " + nodeId);

                        pool.forceClose();

                        clients.remove(nodeId, pool);

                        continue;
                    }

                    pool.checkIdleClients();

                    if (pool.close() || pool.closed())
                        clients.remove(nodeId, pool);
                }

                Thread.sleep(idleConnTimeout);
            }
        }
    }

    /**
     * Client pool. Ensures that at most maxOpenClients can be used at any moment.
     * Acquire method of this pool will either block until client is available if
     * count of already created clients reached max value, or return new or existing client.
     */
    private class GridNioClientPool {
        /** Acquire semaphore. */
        private Semaphore semaphore;

        /** Available clients. */
        private GridConcurrentLinkedDeque<GridNioClient> availableClients =
            new GridConcurrentLinkedDeque<GridNioClient>();

        /** Open clients. */
        private Collection<GridNioClient> openClients = new GridConcurrentHashSet<GridNioClient>();

        /** Count of total created clients and count of pending requests. */
        private GridNioClientCounter clientsCnt = new GridNioClientCounter();

        /** Grid node. */
        private GridNode node;

        /**
         * Creates new client pool.
         *
         * @param maxOpenClients Maximum count of simultaneously open clients for this pool,
         * @param node Node for which client is created.
         */
        GridNioClientPool(int maxOpenClients, GridNode node) {
            assert node != null;

            semaphore = new Semaphore(maxOpenClients);

            this.node = node;
        }

        /**
         * Checks that pool is not closed. If not, tries to take first available client or create if
         * count of created clients does not exceed the maximum count of created clients. This method would
         * block if count of already reserved clients reached maximum value.
         *
         * @return Reserved client or {@code null} if this pool was closed.
         * @throws GridException If thread was interrupted or it was unable to create a client.
         */
        @Nullable private GridNioClient acquireClient() throws GridException {
            // Check if pool is not closed.
            if (!checkAcquireClient())
                return null;

            boolean success = false;

            try {
                GridNioClient client;

                // Take first available client.
                while (true) {
                    client = availableClients.poll();

                    if (client == null || client.reserve())
                        break;
                }

                // Create new client if there is no clients available.
                if (client == null) {
                    client = createNioClient(node);

                    openClients.add(client);

                    int cnt = clientsCnt.incrementAndGetOpenClients();

                    assert cnt > 0 : "Non-positive client count after client creation";
                    assert cnt <= maxOpenClients;

                    // forceClose may have been called concurrently.
                    if (closed()) {
                        client.forceClose();

                        client = null;

                        success = false;
                    }
                    else
                        client.reserve();
                }

                success = true;

                return client;
            }
            finally {
                if (!success)
                    checkReleaseClient();
            }
        }

        /**
         * Returns a client into the clients pool.
         *
         * @param client Previously acquired client.
         */
        private void releaseClient(GridNioClient client) {
            assert openClients.contains(client) : "Attempted to release a client that is not present in pool";

            client.release();

            availableClients.offer(client);

            checkReleaseClient();
        }

        /**
         * Checks all available clients for idle time and closes them if idle time is too big.
         */
        private void checkIdleClients() {
            Iterator<GridNioClient> it = availableClients.iterator();

            while (it.hasNext()) {
                GridNioClient client = it.next();

                long idleTime = client.getIdleTime();

                if (idleTime >= idleConnTimeout) {
                    if (log.isDebugEnabled())
                        log.debug("Closing idle node connection: " + nodeId);

                    if (client.close() || client.closed()) {
                        it.remove();

                        openClients.remove(client);

                        int cnt = clientsCnt.decrementAndGetOpenClients();

                        assert cnt >= 0 : "Open client count become negative";
                    }
                }
            }
        }

        /**
         * Moves this pool to a closed state and closes all open clients.
         */
        private void forceClose() {
            clientsCnt.clientReservations(-1);

            for (GridNioClient client : openClients) {
                client.forceClose();
            }
        }

        /**
         * Tries to move the pool into a closed state. This method will succeed only if count of open clients
         * is {@code 0} and count of pending client requests is {@code 0}.
         *
         * @return {@code True} if close was successful.
         */
        private boolean close() {
            return clientsCnt.compareAndSet(0, 0, 0, -1);
        }

        /**
         * @return {@code True} if this pool was closed by idle check thread.
         */
        private boolean closed() {
            return clientsCnt.clientReservations() == -1;
        }

        /**
         * Checks that pool was not closed, increments request counter and acquires semaphore.
         * Note that semaphore is acquired uninterruptibly because message sending should proceed
         * without any dependency on thread interrupted status.
         *
         * @return {@code True} if acquired semaphore, {@code false} if pool was closed.
         */
        private boolean checkAcquireClient() {
            while (true) {
               int prev = clientsCnt.clientReservations();

               // Pool was released and should not be used.
               if (prev == -1)
                   return false;

               if (clientsCnt.compareAndSetClientReservations(prev, prev + 1)) {
                   semaphore.acquireUninterruptibly();

                   return true;
               }
            }
        }

        /**
         * Releases a semaphore and decrements requests counter with additional asserted checks.
         */
        private void checkReleaseClient() {
            semaphore.release();

            while (true) {
                int prev = clientsCnt.clientReservations();

                if (prev == -1) {
                    U.warn(log, "NIO client pool was released before active client was returned to the pool");

                    return;
                }

                if (clientsCnt.compareAndSetClientReservations(prev, prev - 1))
                    return;
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(2);

        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_CLASS));
        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_VER));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpCommunicationSpi.class, this);
    }

    /**
     * Simple wrapper for more clear code reading.
     * <p/>
     * First int in pair contains total number of open (i.e. created) clients.
     * Second int in pair contains number of client reservations.
     */
    private static class GridNioClientCounter extends GridAtomicIntegerPair {
        /**
         * Increments and returns number of open clients.
         *
         * @return Number of open clients after increment.
         */
        public int incrementAndGetOpenClients() {
            return incrementAndGetFirst();
        }

        /**
         * Decrements and returns number of open clients.
         *
         * @return Number of open clients after decrement.
         */
        public int decrementAndGetOpenClients() {
            return decrementAndGetFirst();
        }

        /**
         * Gets number of client reservations (i.e. calls to acquireClient made before pool was closed)
         *
         * @return Number of successfull or pending reservations.
         */
        public int clientReservations() {
            return second();
        }

        /**
         * Unconditionally sets number of reservations to a given value.
         *
         * @param reservations Count of reservations to be set.
         */
        public void clientReservations(int reservations) {
            second(reservations);
        }

        /**
         * Performs CAS operation on pair with client reservations.
         *
         * @param prev Expected value.
         * @param updated New value.
         * @return {@code True} if operation succeeded.
         */
        public boolean compareAndSetClientReservations(int prev, int updated) {
            return compareAndSetSecond(prev, updated);
        }
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.spi.discovery.tcp.internal.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.spi.discovery.tcp.messages.*;
import org.gridgain.grid.spi.discovery.tcp.metricsstore.*;
import org.gridgain.grid.spi.discovery.tcp.metricsstore.vm.*;
import org.gridgain.grid.spi.discovery.tcp.topologystore.*;
import org.gridgain.grid.spi.discovery.tcp.topologystore.vm.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.processors.port.GridPortProtocol.*;
import static org.gridgain.grid.spi.discovery.tcp.internal.GridTcpDiscoverySpiState.*;
import static org.gridgain.grid.spi.discovery.tcp.messages.GridTcpDiscoveryStatusCheckMessage.*;
import static org.gridgain.grid.spi.discovery.tcp.topologystore.GridTcpDiscoveryTopologyStoreNodeState.*;

/**
 * Discovery SPI implementation that uses TCP/IP for node discovery.
 * <p>
 * Node are organized in ring. So almost all network exchange (except few cases) is
 * done across it.
 * <p>
 * At startup SPI tries to send messages to random IP taken from
 * {@link GridTcpDiscoveryIpFinder} about self start (stops when send succeeds)
 * and then this info goes to coordinator. When coordinator processes join request
 * and issues node added messages and all other nodes then receive info about new node.
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * <ul>
 * <li>IP finder to share info about nodes IP addresses
 * (see {@link #setIpFinder(GridTcpDiscoveryIpFinder)}).
 * See the following IP finder implementations for details on configuration:
 * <ul>
 * <li>{@link org.gridgain.grid.spi.discovery.tcp.ipfinder.sharedfs.GridTcpDiscoverySharedFsIpFinder} -
 *      available in Enterprise edition only.</li>
 * <li>{@link org.gridgain.grid.spi.discovery.tcp.ipfinder.s3.GridTcpDiscoveryS3IpFinder} -
 *      available in Enterprise edition only.</li>
 * <li>{@link org.gridgain.grid.spi.discovery.tcp.ipfinder.jdbc.GridTcpDiscoveryJdbcIpFinder} -
 *      available in Enterprise edition only.</li>
 * <li>{@link GridTcpDiscoveryVmIpFinder}</li>
 * </ul>
 * </li>
 * </ul>
 * <h2 class="header">Optional</h2>
 * The following configuration parameters are optional:
 * <ul>
 * <li>Metrics store (see {@link #setMetricsStore(GridTcpDiscoveryMetricsStore)})</li>
 * See the following metrics store implementations for details on configuration:
 * <ul>
 * <li>{@link org.gridgain.grid.spi.discovery.tcp.metricsstore.sharedfs.GridTcpDiscoverySharedFsMetricsStore} -
 *      available in Enterprise edition only.</li>
 * <li>{@link org.gridgain.grid.spi.discovery.tcp.metricsstore.s3.GridTcpDiscoveryS3MetricsStore} -
 *      available in Enterprise edition only.</li>
 * <li>{@link org.gridgain.grid.spi.discovery.tcp.metricsstore.jdbc.GridTcpDiscoveryJdbcMetricsStore} -
 *      available in Enterprise edition only.</li>
 * <li>{@link GridTcpDiscoveryVmMetricsStore}</li>
 * </ul>
 * </li>
 * <li>Topology store (see {@link #setTopologyStore(GridTcpDiscoveryTopologyStore)})</li>
 * See the following topology store implementations for details on configuration:
 * <ul>
 * <li>{@link org.gridgain.grid.spi.discovery.tcp.topologystore.sharedfs.GridTcpDiscoverySharedFsTopologyStore} -
 *      available in Enterprise edition only.</li>
 * <li>{@link org.gridgain.grid.spi.discovery.tcp.topologystore.jdbc.GridTcpDiscoveryJdbcTopologyStore} -
 *      available in Enterprise edition only.</li>
 * <li>{@link GridTcpDiscoveryVmTopologyStore}</li>
 * </ul>
 * </li>
 * <li>Local address (see {@link #setLocalAddress(String)})</li>
 * <li>Local port to bind to (see {@link #setLocalPort(int)})</li>
 * <li>Local port range to try binding to if previous ports are in use
 *      (see {@link #setLocalPortRange(int)})</li>
 * <li>Heartbeat frequency (see {@link #setHeartbeatFrequency(long)})</li>
 * <li>Max missed heartbeats (see {@link #setMaxMissedHeartbeats(int)})</li>
 * <li>Number of times node tries to (re)establish connection to another node
 *      (see {@link #setReconnectCount(int)})</li>
 * <li>Network timeout (see {@link #setNetworkTimeout(long)})</li>
 * <li>Socket timeout (see {@link #setSocketTimeout(long)})</li>
 * <li>Message acknowledgement timeout (see {@link #setAckTimeout(long)})</li>
 * <li>Join timeout (see {@link #setJoinTimeout(long)})</li>
 * <li>Thread priority for threads started by SPI (see {@link #setThreadPriority(int)})</li>
 * <li>IP finder and Metrics Store clean frequency (see {@link #setStoresCleanFrequency(long)})</li>
 * <li>Status print frequency (see {@link #setStatisticsPrintFrequency(long)}</li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * <pre name="code" class="java">
 * GridTcpDiscoverySpi spi = new GridTcpDiscoverySpi();
 *
 * GridTcpDiscoveryVmIpFinder finder =
 *     new GridTcpDiscoveryVmIpFinder();
 *
 * spi.setIpFinder(finder);
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default discovery SPI.
 * cfg.setDiscoverySpi(spi);
 *
 * // Start grid.
 * GridFactory.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridTcpDiscoverySpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *         ...
 *         &lt;property name="discoverySpi"&gt;
 *             &lt;bean class="org.gridgain.grid.spi.discovery.tcp.GridTcpDiscoverySpi"&gt;
 *                 &lt;property name="ipFinder"&gt;
 *                     &lt;bean class="org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.GridTcpDiscoveryVmIpFinder" /&gt;
 *                 &lt;/property&gt;
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
 * @see GridDiscoverySpi
 */
@GridSpiInfo(
    author = "GridGain Systems",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.6.0c.13012012")
@GridSpiMultipleInstancesSupport(true)
@GridDiscoverySpiOrderSupport(true)
@GridDiscoverySpiReconnectSupport(true)
public class GridTcpDiscoverySpi extends GridSpiAdapter implements GridDiscoverySpi, GridTcpDiscoverySpiMBean {
    /** Default port to listen (value is <tt>47500</tt>). */
    public static final int DFLT_PORT = 47500;

    /** Default local port range (value is <tt>100</tt>). */
    public static final int DFLT_PORT_RANGE = 100;

    /** Default network timeout in milliseconds (value is <tt>5,000ms</tt>). */
    public static final long DFLT_NETWORK_TIMEOUT = 5000;

    /** Default socket operations timeout in milliseconds (value is <tt>2,000ms</tt>). */
    public static final long DFLT_SOCK_TIMEOUT = 2000;

    /** Default timeout for receiving message acknowledgement in milliseconds (value is <tt>2,000ms</tt>). */
    public static final long DFLT_ACK_TIMEOUT = 2000;

    /** Default timeout for joining topology (value is <tt>0</tt>). */
    public static final long DFLT_JOIN_TIMEOUT = 0;

    /** Default reconnect attempts count (value is <tt>2</tt>). */
    public static final int DFLT_RECONNECT_CNT = 2;

    /** Default heartbeat messages issuing frequency (value is <tt>2,000ms</tt>). */
    public static final long DFLT_HEARTBEAT_FREQ = 2000;

    /** Default max heartbeats count node can miss without initiating status check (value is <tt>1</tt>). */
    public static final int DFLT_MAX_MISSED_HEARTBEATS = 1;

    /** Default value for thread priority (value is <tt>10</tt>). */
    public static final int DFLT_THREAD_PRI = 10;

    /** Default stores (IP finder clean and metrics store) frequency in milliseconds (value is <tt>60,000ms</tt>). */
    public static final long DFLT_STORES_CLEAN_FREQ = 60 * 1000;

    /** Default statistics print frequency in milliseconds (value is <tt>0ms</tt>). */
    public static final long DFLT_STATS_PRINT_FREQ = 0;

    /** Response OK. */
    private static final int RES_OK = 1;

    /** Response CONTINUE JOIN. */
    private static final int RES_CONTINUE_JOIN = 100;

    /** Response WAIT. */
    private static final int RES_WAIT = 200;

    /** Predicate to filter visible nodes. */
    private static final GridPredicate<GridTcpDiscoveryNode> VISIBLE_NODES = new P1<GridTcpDiscoveryNode>() {
        @Override public boolean apply(GridTcpDiscoveryNode node) {
            return node.visible();
        }
    };

    /** Local port which node uses. */
    private int locPort = DFLT_PORT;

    /** Local port range. */
    private int locPortRange = DFLT_PORT_RANGE;

    /** Statistics print frequency. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private long statsPrintFreq = DFLT_STATS_PRINT_FREQ;

    /** Network timeout. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private long netTimeout = DFLT_NETWORK_TIMEOUT;

    /** Socket operations timeout. */
    private long sockTimeout = DFLT_SOCK_TIMEOUT;

    /** Message acknowledgement timeout. */
    private long ackTimeout = DFLT_ACK_TIMEOUT;

    /** Join timeout. */
    private long joinTimeout = DFLT_JOIN_TIMEOUT;

    /** Heartbeat messages issuing frequency. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private long hbFreq = DFLT_HEARTBEAT_FREQ;

    /** Max heartbeats count node can miss without initiating status check. */
    private int maxMissedHbs = DFLT_MAX_MISSED_HEARTBEATS;

    /** Thread priority for all threads started by SPI. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int threadPri = DFLT_THREAD_PRI;

    /** Stores clean frequency. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private long storesCleanFreq = DFLT_STORES_CLEAN_FREQ;

    /** Reconnect attempts count. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int reconCnt = DFLT_RECONNECT_CNT;

    /** Name of the grid. */
    @GridNameResource
    private String gridName;

    /** Grid logger. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLoggerResource
    private GridLogger log;

    /** Marshaller. */
    @GridMarshallerResource
    private GridMarshaller marsh;

    /** Local node Id. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLocalNodeIdResource
    private UUID locNodeId;

    /**
     * Local node (although, it may be reassigned on segmentation, it may be non-volatile,
     * since all internal threads are restarted).
     */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridTcpDiscoveryNode locNode;

    /** Local IP address. */
    private String locAddr;

    /** Complex variable that represents this node IP address. */
    private InetAddress locHost;

    /** Grid discovery listener. */
    private volatile GridDiscoverySpiListener lsnr;

    /** Metrics provider. */
    private GridDiscoveryMetricsProvider metricsProvider;

    /** Local node attributes. */
    private Map<String, Object> nodeAttrs;

    /** IP finder. */
    private GridTcpDiscoveryIpFinder ipFinder;

    /** Metrics store. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridTcpDiscoveryMetricsStore metricsStore;

    /** Topology store. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridTcpDiscoveryTopologyStore topStore;

    /** Nodes ring. */
    private final GridTcpDiscoveryNodesRing ring = new GridTcpDiscoveryNodesRing();

    /** Discovery state. */
    private GridTcpDiscoverySpiState spiState = DISCONNECTED;

    /** Socket readers. */
    private final Collection<SocketReader> readers = new LinkedList<SocketReader>();

    /** TCP server for discovery SPI. */
    private TcpServer tcpSrvr;

    /** Message worker. */
    private MessageWorker msgWorker;

    /** Metrics sender. */
    private HeartbeatsSender hbsSnd;

    /** Status checker. */
    private CheckStatusSender chkStatusSnd;

    /** Metrics update notifier. */
    private MetricsUpdateNotifier metricsUpdateNtf;

    /** Stores cleaner. */
    private StoresCleaner storesCleaner;

    /** Topology store worker. */
    private TopologyStoreWorker topStoreWorker;

    /** Statistics printer thread. */
    private StatisticsPrinter statsPrinter;

    /** Socket timeout worker. */
    private SocketTimeoutWorker sockTimeoutWorker;

    /** Failed nodes (but still in topology). */
    private Collection<GridTcpDiscoveryNode> failedNodes = new HashSet<GridTcpDiscoveryNode>();

    /** Leaving nodes (but still in topology). */
    private Collection<GridTcpDiscoveryNode> leavingNodes = new HashSet<GridTcpDiscoveryNode>();

    /** Statistics. */
    private final GridTcpDiscoveryStatistics stats = new GridTcpDiscoveryStatistics();

    /** If non-shared IP finder is used this flag shows whether IP finder contains local address. */
    private boolean ipFinderHasLocAddr;

    /** Addresses that do not respond during join requests send (for resolving concurrent start). */
    private final Collection<InetSocketAddress> noResAddrs = new GridConcurrentHashSet<InetSocketAddress>();

    /** Addresses that incoming join requests send were send from (for resolving concurrent start). */
    private final Collection<InetSocketAddress> fromAddrs = new GridConcurrentHashSet<InetSocketAddress>();

    /** SPI reconnect flag to filter initial node connected event. */
    private volatile boolean recon;

    /** Default class loader for SPI. */
    private final ClassLoader dfltClsLdr = GridTcpDiscoverySpi.class.getClassLoader();

    /** Existing node in topology. */
    private final GridTuple<GridTcpDiscoveryNode> dupIdNode = F.t1();

    /** Mutex. */
    private final Object mux = new Object();

    /**
     * Sets local host IP address that discovery SPI uses.
     * <p>
     * If not provided, by default a first found non-loopback address
     * will be used. If there is no non-loopback address available,
     * then {@link InetAddress#getLocalHost()} will be used.
     *
     * @param locAddr IP address.
     */
    @GridSpiConfiguration(optional = true)
    @GridLocalHostResource
    public void setLocalAddress(String locAddr) {
        // Injection should not override value already set by Spring or user.
        if (this.locAddr == null)
            this.locAddr = locAddr;
    }

    /**
     * Gets local address that was set to SPI with {@link #setLocalAddress(String)} method.
     *
     * @return local address.
     */
    public String getLocalAddress() {
        return locAddr;
    }

    /** {@inheritDoc} */
    @Override public int getReconnectCount() {
        return reconCnt;
    }

    /**
     * Number of times node tries to (re)establish connection to another node.
     * <p>
     * If not specified, default is {@link #DFLT_RECONNECT_CNT}.
     *
     * @param reconCnt Number of retries during message sending.
     */
    @GridSpiConfiguration(optional = true)
    public void setReconnectCount(int reconCnt) {
        this.reconCnt = reconCnt;
    }

    /** {@inheritDoc} */
    @Override public long getNetworkTimeout() {
        return netTimeout;
    }

    /**
     * Sets maximum network timeout to use for network operations.
     * <p>
     * If not specified, default is {@link #DFLT_NETWORK_TIMEOUT}.
     *
     * @param netTimeout Network timeout.
     */
    @GridSpiConfiguration(optional = true)
    public void setNetworkTimeout(long netTimeout) {
        this.netTimeout = netTimeout;
    }

    /** {@inheritDoc} */
    @Override public long getAckTimeout() {
        return ackTimeout;
    }

    /**
     * Sets timeout for receiving acknowledgement for sent message.
     * <p>
     * If acknowledgement is not received within this timeout, sending is considered as failed
     * and SPI tries to repeat message sending.
     * <p>
     * If not specified, default is {@link #DFLT_ACK_TIMEOUT}.
     *
     * @param ackTimeout Acknowledgement timeout.
     */
    @GridSpiConfiguration(optional = true)
    public void setAckTimeout(long ackTimeout) {
        this.ackTimeout = ackTimeout;
    }

    /** {@inheritDoc} */
    @Override public long getSocketTimeout() {
        return sockTimeout;
    }

    /**
     * Sets socket operations timeout. This timeout is used to limit connection time and
     * write-to-socket time.
     * <p>
     * Note that when running GridGain on Amazon EC2, socket timeout must be set to a value
     * signficantly larger tahn datault (e.g. to {@code 30000}).
     * <p>
     * If not specified, default is {@link #DFLT_SOCK_TIMEOUT}.
     *
     * @param sockTimeout Socket connection timeout.
     */
    @GridSpiConfiguration(optional = true)
    public void setSocketTimeout(long sockTimeout) {
        this.sockTimeout = sockTimeout;
    }

    /** {@inheritDoc} */
    @Override public long getJoinTimeout() {
        return joinTimeout;
    }

    /**
     * Sets join timeout.
     * <p>
     * If non-shared IP finder is used and node fails to connect to
     * any address from IP finder, node keeps trying to join within this
     * timeout. If all addresses are still unresponsive, exception is thrown
     * and node startup fails.
     * <p>
     * If not specified, default is {@link #DFLT_JOIN_TIMEOUT}.
     *
     * @param joinTimeout Join timeout ({@code 0} means wait forever).
     *
     * @see GridTcpDiscoveryIpFinder#isShared()
     */
    @GridSpiConfiguration(optional = true)
    public void setJoinTimeout(long joinTimeout) {
        this.joinTimeout = joinTimeout;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPort() {
        return locPort;
    }

    /**
     * Sets local port to listen to.
     * <p>
     * If not specified, default is {@link #DFLT_PORT}.
     *
     * @param locPort Local port to bind.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPort(int locPort) {
        this.locPort = locPort;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPortRange() {
        return locPortRange;
    }

    /**
     * Range for local ports. Local node will try to bind on first available port
     * starting from {@link #getLocalPort()} up until
     * <tt>{@link #getLocalPort()} {@code + locPortRange}</tt>.
     * <p>
     * If not specified, default is {@link #DFLT_PORT_RANGE}.
     *
     * @param locPortRange Local port range to bind.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPortRange(int locPortRange) {
        this.locPortRange = locPortRange;
    }

    /** {@inheritDoc} */
    @Override public long getHeartbeatFrequency() {
        return hbFreq;
    }

    /**
     * Sets delay between issuing of heartbeat messages. SPI sends heartbeat messages
     * in configurable time interval to other nodes to notify them about its state.
     * <p>
     * If not provided, default value is {@link #DFLT_HEARTBEAT_FREQ}.
     *
     * @param hbFreq Heartbeat frequency in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setHeartbeatFrequency(long hbFreq) {
        this.hbFreq = hbFreq;
    }

    /** {@inheritDoc} */
    @Override public int getMaxMissedHeartbeats() {
        return maxMissedHbs;
    }

    /**
     * Sets max heartbeats count node can miss without initiating status check.
     * <p>
     * If not provided, default value is {@link #DFLT_MAX_MISSED_HEARTBEATS}.
     *
     * @param maxMissedHbs Max missed heartbeats.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxMissedHeartbeats(int maxMissedHbs) {
        this.maxMissedHbs = maxMissedHbs;
    }

    /** {@inheritDoc} */
    @Override public long getStatisticsPrintFrequency() {
        return statsPrintFreq;
    }

    /**
     * Sets statistics print frequency.
     * <p>
     * If not set default value is {@link #DFLT_STATS_PRINT_FREQ}.
     * 0 indicates that no print is required. If value is greater than 0 and log is
     * not quiet then statistics are printed out with INFO level.
     *
     * @param statsPrintFreq Statistics print frequency in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setStatisticsPrintFrequency(long statsPrintFreq) {
        this.statsPrintFreq = statsPrintFreq;
    }

    /**
     * Sets IP finder for IP addresses sharing and storing.
     *
     * @param ipFinder IP finder.
     */
    @GridSpiConfiguration(optional = false)
    public void setIpFinder(GridTcpDiscoveryIpFinder ipFinder) {
        this.ipFinder = ipFinder;
    }

    /**
     * Sets topology store.
     * <p>
     * If provided, SPI gets topology change notifications by querying store.
     * It is recommended to provide topology store when working with large
     * topologies.
     *
     * @param topStore Topology store.
     */
    @GridSpiConfiguration(optional = true)
    public void setTopologyStore(GridTcpDiscoveryTopologyStore topStore) {
        this.topStore = topStore;
    }

    /** {@inheritDoc} */
    @Override public int getThreadPriority() {
        return threadPri;
    }

    /**
     * Sets thread priority. All threads within SPI will be started with it.
     * <p>
     * If not provided, default value is {@link #DFLT_THREAD_PRI}
     *
     * @param threadPri Thread priority.
     */
    @GridSpiConfiguration(optional = true)
    public void setThreadPriority(int threadPri) {
        this.threadPri = threadPri;
    }

    /** {@inheritDoc} */
    @Override public long getStoresCleanFrequency() {
        return storesCleanFreq;
    }

    /**
     * Sets stores (IP finder and metrics store) clean frequency in milliseconds.
     * <p>
     * If not provided, default value is {@link #DFLT_STORES_CLEAN_FREQ}
     *
     * @param storesCleanFreq Stores clean frequency.
     */
    @GridSpiConfiguration(optional = true)
    public void setStoresCleanFrequency(long storesCleanFreq) {
        this.storesCleanFreq = storesCleanFreq;
    }

    /** {@inheritDoc} */
    @Override public String getSpiState() {
        synchronized (mux) {
            return spiState.name();
        }
    }

    /** {@inheritDoc} */
    @Override public String getIpFinderName() {
        return ipFinder.toString();
    }

    /** {@inheritDoc} */
    @Override @Nullable public String getMetricsStoreName() {
        return metricsStore != null ? metricsStore.toString() : null;
    }

    /** {@inheritDoc} */
    @Override @Nullable public String getTopologyStoreName() {
        return topStore != null ? topStore.toString() : null;
    }

    /** {@inheritDoc} */
    @Override public int getMessageWorkerQueueSize() {
        return msgWorker.queue.size();
    }

    /** {@inheritDoc} */
    @Override public long getNodesJoined() {
        return stats.joinedNodesCount();
    }

    /** {@inheritDoc} */
    @Override public long getNodesLeft() {
        return stats.leftNodesCount();
    }

    /** {@inheritDoc} */
    @Override public long getNodesFailed() {
        return stats.failedNodesCount();
    }

    /** {@inheritDoc} */
    @Override public long getPendingMessagesRegistered() {
        return stats.pendingMessagesRegistered();
    }

    /** {@inheritDoc} */
    @Override public long getPendingMessagesDiscarded() {
        return stats.pendingMessagesDiscarded();
    }

    /** {@inheritDoc} */
    @Override public long getAvgMessageProcessingTime() {
        return stats.avgMessageProcessingTime();
    }

    /** {@inheritDoc} */
    @Override public long getMaxMessageProcessingTime() {
        return stats.maxMessageProcessingTime();
    }

    /** {@inheritDoc} */
    @Override public int getTotalReceivedMessages() {
        return stats.totalReceivedMessages();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Integer> getReceivedMessages() {
        return stats.receivedMessages();
    }

    /** {@inheritDoc} */
    @Override public int getTotalProcessedMessages() {
        return stats.totalProcessedMessages();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Integer> getProcessedMessages() {
        return stats.processedMessages();
    }

    /** {@inheritDoc} */
    @Override public long getCoordinatorSinceTimestamp() {
        return stats.coordinatorSinceTimestamp();
    }

    /** {@inheritDoc} */
    @Nullable @Override public UUID getCoordinator() {
        GridTcpDiscoveryNode crd = resolveCoordinator();

        return crd != null ? crd.id() : null;
    }

    /**
     * Sets metrics store.
     * <p>
     * If provided, SPI does not send metrics across the ring and uses metrics
     * store to exchange metrics. It is recommended to provide metrics store when
     * working with large topologies.
     *
     * @param metricsStore Metrics store.
     */
    @GridSpiConfiguration(optional = true)
    public void setMetricsStore(GridTcpDiscoveryMetricsStore metricsStore) {
        this.metricsStore = metricsStore;
    }

    /** {@inheritDoc} */
    @Override public GridNode getLocalNode() {
        return locNode;
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridNode getNode(UUID nodeId) {
        assert nodeId != null;

        UUID locNodeId0 = locNodeId;

        if (locNodeId0 != null && locNodeId0.equals(nodeId))
            // Return local node directly.
            return locNode;

        GridTcpDiscoveryNode node = ring.node(nodeId);

        if (node != null && !node.visible())
            return null;

        return node;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridNode> getRemoteNodes() {
        return new ArrayList<GridNode>(F.view(ring.remoteNodes(), VISIBLE_NODES));
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridDiscoverySpiListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public void setMetricsProvider(GridDiscoveryMetricsProvider metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    /** {@inheritDoc} */
    @Override public void setNodeAttributes(Map<String, Object> attrs) {
        assert nodeAttrs == null;

        if (log.isDebugEnabled())
            log.debug("Node attributes to set: " + attrs);

        nodeAttrs = attrs;
    }

    /** {@inheritDoc} */
    @Override public Collection<Object> injectables() {
        Collection<Object> res = new LinkedList<Object>();

        if (metricsStore != null)
            res.add(metricsStore);

        if (topStore != null)
            res.add(topStore);

        if (ipFinder != null)
            res.add(ipFinder);

        return res;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        spiStart0(false);
    }

    /**
     * Starts or restarts SPI after stop (to reconnect).
     *
     * @param restart {@code True} if SPI is restarted after stop.
     * @throws GridSpiException If failed.
     */
    private void spiStart0(boolean restart) throws GridSpiException {
        if (!restart)
            // It is initial start.
            onSpiStart();

        synchronized (mux) {
            spiState = DISCONNECTED;
        }

        // Clear addresses collections.
        fromAddrs.clear();
        noResAddrs.clear();

        sockTimeoutWorker = new SocketTimeoutWorker();
        sockTimeoutWorker.start();

        msgWorker = new MessageWorker();
        msgWorker.start();

        tcpSrvr = new TcpServer();

        // Init local node.
        locNode = new GridTcpDiscoveryNode(locNodeId, new InetSocketAddress(locHost, tcpSrvr.port),
            metricsProvider);

        locNode.setAttributes(nodeAttrs);

        if (log.isDebugEnabled())
            log.debug("Local node initialized: " + locNode);

        // Start TCP server thread after local node is initialized.
        tcpSrvr.start();

        ring.localNode(locNode);

        if (ipFinder.isShared())
            registerLocalNodeAddress();
        else {
            if (ipFinder.getRegisteredAddresses().isEmpty())
                throw new GridSpiException("Non-shared IP finder should have configured addresses.");

            ipFinderHasLocAddr = ipFinderHasLocalAddress();
        }

        if (statsPrintFreq > 0 && log.isInfoEnabled() && !log.isQuiet()) {
            statsPrinter = new StatisticsPrinter();
            statsPrinter.start();
        }

        stats.onJoinStarted();

        joinTopology();

        stats.onJoinFinished();

        hbsSnd = new HeartbeatsSender();
        hbsSnd.start();

        chkStatusSnd = new CheckStatusSender();
        chkStatusSnd.start();

        if (metricsStore != null) {
            metricsUpdateNtf = new MetricsUpdateNotifier();
            metricsUpdateNtf.start();
        }

        if (ipFinder.isShared() || metricsStore != null) {
            storesCleaner = new StoresCleaner();
            storesCleaner.start();
        }

        if (topStore != null) {
            topStoreWorker = new TopologyStoreWorker();
            topStoreWorker.start();
        }

        if (log.isDebugEnabled() && !restart)
            log.debug(startInfo());

        if (restart)
            getSpiContext().registerPort(tcpSrvr.port, TCP);
    }

    /**
     * @throws GridSpiException If failed.
     */
    @SuppressWarnings("BusyWait")
    private void registerLocalNodeAddress() throws GridSpiException {
        // Make sure address registration succeeded.
        while (true) {
            try {
                ipFinder.registerAddresses(Arrays.asList(locNode.address()));

                // Success.
                break;
            }
            catch (GridSpiException e) {
                LT.error(log, e, "Failed to register local node address in IP finder on start " +
                    "(retrying every 2000 ms).");
            }

            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException e) {
                throw new GridSpiException("Thread has been interrupted.", e);
            }
        }
    }

    /**
     * @throws GridSpiException If failed.
     */
    private void onSpiStart() throws GridSpiException {
        startStopwatch();

        assertParameter(ipFinder != null, "ipFinder != null");
        assertParameter(storesCleanFreq > 0, "ipFinderCleanFreq > 0");
        assertParameter(locPort > 1023, "localPort > 1023");
        assertParameter(locPortRange >= 0, "localPortRange >= 0");
        assertParameter(locPort + locPortRange <= 0xffff, "locPort + locPortRange <= 0xffff");
        assertParameter(netTimeout > 0, "networkTimeout > 0");
        assertParameter(sockTimeout > 0, "sockTimeout > 0");
        assertParameter(ackTimeout > 0, "ackTimeout > 0");
        assertParameter(reconCnt > 0, "reconnectCnt > 0");
        assertParameter(hbFreq > 0, "heartbeatFreq > 0");
        assertParameter(maxMissedHbs > 0, "maxMissedHeartbeats > 0");
        assertParameter(threadPri > 0, "threadPri > 0");
        assertParameter(statsPrintFreq >= 0, "statsPrintFreq >= 0");

        try {
            locHost = F.isEmpty(locAddr) ? U.getLocalHost() : InetAddress.getByName(locAddr);
        }
        catch (IOException e) {
            throw new GridSpiException("Unknown local address: " + locAddr, e);
        }

        if (log.isDebugEnabled()) {
            log.debug(configInfo("localHost", locHost.getHostAddress()));
            log.debug(configInfo("localPort", locPort));
            log.debug(configInfo("localPortRange", locPortRange));
            log.debug(configInfo("threadPri", threadPri));
            log.debug(configInfo("networkTimeout", netTimeout));
            log.debug(configInfo("sockTimeout", sockTimeout));
            log.debug(configInfo("ackTimeout", ackTimeout));
            log.debug(configInfo("reconnectCount", reconCnt));
            log.debug(configInfo("ipFinder", ipFinder));
            log.debug(configInfo("ipFinderCleanFreq", storesCleanFreq));
            log.debug(configInfo("heartbeatFreq", hbFreq));
            log.debug(configInfo("maxMissedHeartbeats", maxMissedHbs));
            log.debug(configInfo("metricsStore", metricsStore));
            log.debug(configInfo("topStore", topStore));
            log.debug(configInfo("statsPrintFreq", statsPrintFreq));
        }

        // Warn on odd network timeout.
        if (netTimeout < 3000)
            U.warn(log, "Network timeout is too low (at least 3000 ms): " + netTimeout);

        // Warn on odd heartbeat frequency.
        if (hbFreq < 2000)
            U.warn(log, "Heartbeat frequency is too low (at least 2000 ms): " + hbFreq);

        registerMBean(gridName, this, GridTcpDiscoverySpiMBean.class);
    }

    /** {@inheritDoc} }*/
    @Override public void onContextInitialized(GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        getSpiContext().registerPort(tcpSrvr.port, TCP);
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        spiStop0(false);
    }

    /**
     * Stops SPI finally or stops SPI for restart.
     *
     * @param restart {@code True} if SPI is about to be restarted.
     * @throws GridSpiException If failed.
     */
    private void spiStop0(boolean restart) throws GridSpiException {
        if (log.isDebugEnabled()) {
            if (restart)
                log.debug("Restarting SPI.");
            else
                log.debug("Preparing to start local node stop procedure.");
        }

        if (restart) {
            synchronized (mux) {
                spiState = DISCONNECTING;
            }
        }

        if (msgWorker != null && msgWorker.isAlive() && !restart) {
            // Send node left message only if it is final stop.
            msgWorker.addMessage(new GridTcpDiscoveryNodeLeftMessage(locNodeId));

            synchronized (mux) {
                long threshold = System.currentTimeMillis() + netTimeout;

                long timeout = netTimeout;

                while (spiState != LEFT && timeout > 0) {
                    try {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }
                    catch (InterruptedException e) {
                        throw new GridSpiException("Thread has been interrupted.", e);
                    }
                }

                if (spiState == LEFT) {
                    if (log.isDebugEnabled())
                        log.debug("Verification for local node leave has been received from coordinator" +
                            " (continuing stop procedure).");
                }
                else if (log.isInfoEnabled()) {
                    log.info("No verification for local node leave has been received from coordinator" +
                        " (will stop node anyway).");
                }
            }
        }

        U.interrupt(tcpSrvr);
        U.join(tcpSrvr, log);

        Collection<SocketReader> tmp;

        synchronized (mux) {
            tmp = new ArrayList<SocketReader>(readers);
        }

        U.interrupt(tmp);
        U.joinThreads(tmp, log);

        U.interrupt(hbsSnd);
        U.join(hbsSnd, log);

        U.interrupt(chkStatusSnd);
        U.join(chkStatusSnd, log);

        U.interrupt(storesCleaner);
        U.join(storesCleaner, log);

        U.interrupt(metricsUpdateNtf);
        U.join(metricsUpdateNtf, log);

        U.interrupt(topStoreWorker);
        U.join(topStoreWorker, log);

        U.interrupt(msgWorker);
        U.join(msgWorker, log);

        U.interrupt(sockTimeoutWorker);
        U.join(sockTimeoutWorker, log);

        U.interrupt(statsPrinter);
        U.join(statsPrinter, log);

        Collection<GridTcpDiscoveryNode> rmts = null;

        if (!restart) {
            // This is final stop.
            unregisterMBean();

            printStatistics();

            if (log.isDebugEnabled())
                log.debug(stopInfo());
        }
        else {
            getSpiContext().deregisterPorts();

            rmts = ring.remoteNodes();
        }

        ring.clear();

        if (rmts != null && !rmts.isEmpty()) {
            // This is restart and remote nodes are not empty.
            // We need to fire FAIL event for each.
            GridDiscoverySpiListener lsnr = this.lsnr;

            long topVer = ring.topologyVersion();

            if (lsnr != null) {
                for (GridTcpDiscoveryNode n : rmts) {
                    if (n.visible())
                        lsnr.onDiscovery(EVT_NODE_FAILED, ++topVer, n);
                }
            }
        }

        synchronized (mux) {
            // Clear stored data.
            leavingNodes.clear();
            failedNodes.clear();

            spiState = DISCONNECTED;
        }
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        getSpiContext().deregisterPorts();

        super.onContextDestroyed();
    }

    /**
     * @throws GridSpiException If any error occurs.
     * @return {@code true} if IP finder contains local address.
     */
    private boolean ipFinderHasLocalAddress() throws GridSpiException {
        InetSocketAddress locAddr = locNode.address();

        for (InetSocketAddress addr : registeredAddresses())
            try {
                int port = addr.getPort();

                InetSocketAddress resolved = addr.isUnresolved() ?
                    new InetSocketAddress(InetAddress.getByName(addr.getHostName()), port) :
                    new InetSocketAddress(addr.getAddress(), port);

                if (resolved.equals(locAddr))
                    return true;
            }
            catch (UnknownHostException ignored) {
                // No-op.
            }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean pingNode(UUID nodeId) {
        assert nodeId != null;

        if (nodeId == locNodeId)
            return true;

        GridTcpDiscoveryNode node = ring.node(nodeId);

        if (node != null) {
            assert node.visible() : "Invisible node has been requested to ping: " + node;

            return pingNode(node);
        }

        return false;
    }

    /**
     * Pings the remote node to see if it's alive.
     *
     * @param node Node.
     * @return {@code True} if ping succeeds.
     */
    private boolean pingNode(GridTcpDiscoveryNode node) {
        assert node != null;

        if (node.id().equals(locNodeId))
            return true;

        try {
            // ID returned by the node should be the same as ID of the parameter for ping to succeed.
            return node.id().equals(pingNode(node.address()));
        }
        catch (GridSpiException e) {
            if (log.isDebugEnabled())
                log.debug("Failed to ping node [node=" + node + ", err=" + e.getMessage() + ']');
        }

        return false;
    }

    /**
     * Pings the remote node by its address to see if it's alive.
     *
     * @param addr Address of the node.
     * @return ID of the remote node if node alive, {@code null} otherwise.
     * @throws GridSpiException If an error occurs.
     */
    @Nullable private UUID pingNode(InetSocketAddress addr) throws GridSpiException {
        assert addr != null;

        if (addr.equals(locNode.address()))
            return locNodeId;

        Exception err = null;

        Socket sock = null;

        for (int i = 0; i < reconCnt; i++) {
            try {
                if(addr.isUnresolved())
                    addr = new InetSocketAddress(InetAddress.getByName(addr.getHostName()), addr.getPort());

                long tstamp = System.currentTimeMillis();

                sock = openSocket(addr.getAddress(), addr.getPort());

                // Handshake response will act as ping response.
                writeToSocket(sock, new GridTcpDiscoveryHandshakeRequest(locNodeId));

                GridTcpDiscoveryHandshakeResponse res = readMessage(sock);

                stats.onClientSocketInitialized(System.currentTimeMillis() - tstamp);

                return res.creatorNodeId();
            }
            catch (IOException e) {
                if (err == null)
                    err = e;
            }
            catch (GridException e) {
                if (err == null)
                    err = e;
            }
            finally {
                U.closeQuiet(sock);
            }
        }

        throw new GridSpiException("Failed to ping node by address:" + addr, err);
    }

    /** {@inheritDoc} */
    @Override public void disconnect() throws GridSpiException {
        spiStop0(true);

    }

    /** {@inheritDoc} */
    @Override public void reconnect() throws GridSpiException {
        spiStart0(true);
    }

    /**
     * Tries to join this node to topology.
     *
     * @throws GridSpiException If any error occurs.
     */
    private void joinTopology() throws GridSpiException {
        synchronized (mux) {
            assert spiState == CONNECTING || spiState == DISCONNECTED;

            spiState = CONNECTING;
        }

        while (true) {
            if (!sendJoinRequestMessage()) {
                if (log.isDebugEnabled())
                    log.debug("Join request message has not been sent (local node is the first in the topology).");

                locNode.order(1);
                locNode.internalOrder(1);

                locNode.visible(true);

                ring.topologyVersion(1);

                ring.clear();

                if (topStore != null) {
                    locNode.state(ONLINE);

                    locNode.topologyVersion(1);

                    // Clear the store and put local node to.
                    topStore.clear();

                    long tstamp = System.currentTimeMillis();

                    topStore.put(locNode);

                    stats.onTopologyStoreNodePut(System.currentTimeMillis() - tstamp);
                }

                synchronized (mux) {
                    spiState = CONNECTED;

                    mux.notifyAll();
                }

                // Alter flag here and fire event here, since it has not been done in msgWorker.
                if (recon)
                    // Node has reconnected and it is the first.
                    notifyDiscovery(EVT_NODE_RECONNECTED, 1, locNode);
                else
                    // This is initial start, node is the first.
                    recon = true;

                break;
            }

            if (log.isDebugEnabled())
                log.debug("Join request message has been sent (waiting for coordinator response).");

            synchronized (mux) {
                long threshold = System.currentTimeMillis() + netTimeout;

                long timeout = netTimeout;

                while (spiState != CONNECTED && timeout > 0) {
                    try {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }
                    catch (InterruptedException ignored) {
                        throw new GridSpiException("Thread has been interrupted.");
                    }
                }

                if (spiState == CONNECTED)
                    break;
                else if (spiState == DUPLICATE_ID)
                    throw new GridSpiException("Local node has the same ID as existing node in topology " +
                        "(fix configuration and restart local node) " +
                        "[localNode=" + locNode + ", existingNode=" + dupIdNode.get() + ']');
                else
                    LT.warn(log, null, "Node has not been connected to topology and will repeat join process. " +
                        "Note that large topology may require significant time to start. " +
                        "Increase 'netTimeout' configuration property if getting this message on starting nodes.");
            }
        }

        assert locNode.order() != 0;
        assert locNode.internalOrder() != 0;

        if (log.isDebugEnabled())
            log.debug("Discovery SPI has been connected to topology with order: " + locNode.internalOrder());
    }

    /**
     * Tries to send join request message to a random node presenting in topology.
     * Address is provided by {@link GridTcpDiscoveryIpFinder} and message is
     * sent to first node connection succeeded to.
     *
     * @return {@code true} if send succeeded.
     * @throws GridSpiException If any error occurs.
     */
    @SuppressWarnings({"BusyWait"})
    private boolean sendJoinRequestMessage() throws GridSpiException {
        GridTcpDiscoveryAbstractMessage joinReq = new GridTcpDiscoveryJoinRequestMessage(locNode);

        // Time when it has been detected, that addresses from IP finder do not respond.
        long noResStart = 0;

        while (true) {
            Collection<InetSocketAddress> addrs = resolvedAddresses();

            if (addrs.isEmpty())
                return false;

            List<InetSocketAddress> shuffled = new ArrayList<InetSocketAddress>(addrs);

            // Shuffle addresses to send join request to different nodes.
            Collections.shuffle(shuffled);

            boolean retry = false;

            for (InetSocketAddress addr : shuffled) {
                try {
                    Integer res = sendMessageDirectly(joinReq, addr);

                    assert res != null;

                    noResAddrs.remove(addr);

                    // Address is responsive, reset period start.
                    noResStart = 0;

                    switch (res) {
                        case RES_WAIT:
                            // Concurrent startup, try sending join request again or wait if no success.
                            retry = true;

                            break;
                        case RES_OK:
                            if (log.isDebugEnabled())
                                log.debug("Join request message has been sent to address [addr=" + addr +
                                    ", req=" + joinReq + ']');

                            // Join request sending succeeded, wait for response from topology.
                            return true;

                        default:
                            // Concurrent startup, try next node.
                            if (res == RES_CONTINUE_JOIN) {
                                if (!fromAddrs.contains(addr))
                                    retry = true;
                            }
                            else {
                                if (log.isDebugEnabled())
                                    log.debug("Unexpected response to join request: " + res);

                                retry = true;
                            }

                            break;
                    }
                }
                catch (GridSpiException e) {
                    if (log.isDebugEnabled())
                        log.debug("Failed to send join request message: " + e.getMessage());

                    noResAddrs.add(addr);
                }
            }

            if (retry) {
                if (log.isDebugEnabled())
                    log.debug("Concurrent discovery SPI start has been detected (local node should wait).");

                try {
                    Thread.sleep(2000);
                }
                catch (InterruptedException ignored) {
                    throw new GridSpiException("Thread has been interrupted.");
                }
            }
            else if (!ipFinder.isShared() && !ipFinderHasLocAddr) {
                LT.warn(log, null, "Failed to connect to any address from IP finder (local node should wait " +
                    "until one of the addresses responds): " + addrs);

                if (joinTimeout > 0) {
                    if (noResStart == 0)
                        noResStart = System.currentTimeMillis();

                    else if (System.currentTimeMillis() - noResStart > joinTimeout)
                        throw new GridSpiException("Failed to connect to any address from IP finder within " +
                            "join timeout. Consider changing 'joinTimeout' configuration property.");
                }

                try {
                    Thread.sleep(2000);
                }
                catch (InterruptedException ignored) {
                    throw new GridSpiException("Thread has been interrupted.");
                }
            }
            else
                break;
        }

        return false;
    }

    /**
     * Establishes connection to an address, sends message and returns the response (if any).
     *
     * @param msg Message to send.
     * @param addr Address to send message to.
     * @return Response read from the recipient or {@code null} if no response is supposed.
     * @throws GridSpiException If an error occurs.
     */
    @Nullable private Integer sendMessageDirectly(GridTcpDiscoveryAbstractMessage msg, InetSocketAddress addr)
        throws GridSpiException {
        assert msg != null;
        assert addr != null;

        Exception err = null;

        Socket sock = null;

        for (int i = 0; i < reconCnt; i++) {
            try {
                long tstamp = System.currentTimeMillis();

                sock = openSocket(addr.getAddress(), addr.getPort());

                // Handshake.
                writeToSocket(sock, new GridTcpDiscoveryHandshakeRequest(locNodeId));

                GridTcpDiscoveryHandshakeResponse res = readMessage(sock);

                stats.onClientSocketInitialized(System.currentTimeMillis() - tstamp);

                // Send message.
                tstamp = System.currentTimeMillis();

                writeToSocket(sock, msg);

                stats.onMessageSent(msg, System.currentTimeMillis() - tstamp);

                if (log.isDebugEnabled())
                    log.debug("Message has been sent directly to address [msg=" + msg + ", addr=" + addr +
                        ", rmtNodeId=" + res.creatorNodeId() + ']');

                return readReceipt(sock);
            }
            catch (ClassCastException e) {
                // This issue is rarely reproducible on AmazonEC2, but never
                // on dedicated machines.
                if (log.isDebugEnabled())
                    log.debug("Class cast exception on join request send: " + e.getMessage());

                if (err == null)
                    err = e;
            }
            catch (IOException e) {
                if (log.isDebugEnabled())
                    log.debug("IO exception on join request send: " + e.getMessage());

                if (err == null || err instanceof ClassCastException)
                    err = e;
            }
            catch (GridException e) {
                if (log.isDebugEnabled())
                    log.debug("Grid exception on join request send: " + e.getMessage());

                if (err == null || err instanceof ClassCastException)
                    err = e;
            }
            finally {
                U.closeQuiet(sock);
            }
        }

        throw new GridSpiException("Failed to send message directly to address [addr=" + addr +
            ", msg=" + msg + ']', err);
    }

    /**
     * @param addr Remote address.
     * @param port Remote port.
     * @return Opened socket.
     * @throws IOException If failed.
     */
    private Socket openSocket(InetAddress addr, int port) throws IOException {
        Socket sock = new Socket();

        sock.bind(new InetSocketAddress(locHost, 0));

        try {
            sock.connect(new InetSocketAddress(addr, port), (int)sockTimeout);
        }
        catch (SocketTimeoutException e) {
            LT.warn(log, null, "Connect timed out. Consider changing 'sockTimeout' configuration property.");

            throw e;
        }

        return sock;
    }

    /**
     * Writes message to the socket limiting write time to {@link #getSocketTimeout()}.
     *
     * @param sock Socket.
     * @param msg Message.
     * @throws IOException If IO failed or write timed out.
     * @throws GridException If marshalling failed.
     */
    @SuppressWarnings("ThrowFromFinallyBlock")
    private void writeToSocket(Socket sock, GridTcpDiscoveryAbstractMessage msg) throws IOException, GridException {
        assert sock != null;
        assert msg != null;

        // Marshall message first to perform only write after.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        marsh.marshal(msg, bout);

        SocketTimeoutObject obj = new SocketTimeoutObject(sock, System.currentTimeMillis() + sockTimeout);

        sockTimeoutWorker.addTimeoutObject(obj);

        IOException err = null;

        try {
            bout.writeTo(sock.getOutputStream());

            sock.getOutputStream().flush();
        }
        catch (IOException e) {
            err = e;
        }
        finally {
            boolean cancelled = obj.cancel();

            if (cancelled)
                sockTimeoutWorker.removeTimeoutObject(obj);

            // Throw original exception.
            if (err != null)
                throw err;

            if (!cancelled)
                throw new SocketTimeoutException("Write timed out (socket was concurrently closed).");
        }
    }

    /**
     * Writes response to the socket limiting write time to {@link #getSocketTimeout()}.
     *
     * @param sock Socket.
     * @param res Integer response.
     * @throws IOException If IO failed or write timed out.
     */
    @SuppressWarnings("ThrowFromFinallyBlock")
    private void writeToSocket(Socket sock, int res) throws IOException {
        assert sock != null;

        SocketTimeoutObject obj = new SocketTimeoutObject(sock, System.currentTimeMillis() + sockTimeout);

        sockTimeoutWorker.addTimeoutObject(obj);

        OutputStream out = sock.getOutputStream();

        IOException err = null;

        try {
            out.write(res);

            out.flush();
        }
        catch (IOException e) {
            err = e;
        }
        finally {
            boolean cancelled = obj.cancel();

            if (cancelled)
                sockTimeoutWorker.removeTimeoutObject(obj);

            // Throw original exception.
            if (err != null)
                throw err;

            if (!cancelled)
                throw new SocketTimeoutException("Write timed out (socket was concurrently closed).");
        }
    }

    /**
     * Reads message delivery receipt from the socket.
     *
     * @param sock Socket.
     * @return Receipt.
     * @throws IOException If IO failed or read timed out.
     */
    private int readReceipt(Socket sock) throws IOException {
        assert sock != null;

        int timeout = sock.getSoTimeout();

        try {
            sock.setSoTimeout((int)ackTimeout);

            return sock.getInputStream().read();
        }
        catch (SocketTimeoutException e) {
            LT.warn(log, null, "Timed out waiting for message delivery receipt. Consider changing 'ackTimeout' " +
                "configuration property.");

            throw e;
        }
        finally {
            // Quietly restore timeout.
            try {
                sock.setSoTimeout(timeout);
            }
            catch (SocketException ignored) {
                // No-op.
            }
        }
    }

    /**
     * Reads message from the socket limiting read time to
     *
     * @param sock Socket.
     * @return Message.
     * @throws IOException If IO failed or read timed out.
     * @throws GridException If unmarshalling failed.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    private <T> T readMessage(Socket sock) throws IOException, GridException {
        assert sock != null;

        int timeout = sock.getSoTimeout();

        try {
            sock.setSoTimeout((int)netTimeout);

            return marsh.<T>unmarshal(sock.getInputStream(), dfltClsLdr);
        }
        finally {
            // Quietly restore timeout.
            try {
                sock.setSoTimeout(timeout);
            }
            catch (SocketException ignored) {
                // No-op.
            }
        }
    }

    /**
     * Notify external listener on discovery event.
     *
     * @param type Discovery event type. See {@link GridDiscoveryEvent} for more details.
     * @param topVer Topology version.
     * @param node Remote node this event is connected with.
     */
    private void notifyDiscovery(int type, long topVer, GridTcpDiscoveryNode node) {
        assert type > 0;
        assert node != null;

        GridDiscoverySpiListener lsnr = this.lsnr;

        if (lsnr != null && node.visible() && spiStateCopy() == CONNECTED)
            lsnr.onDiscovery(type, topVer, node);
    }

    /**
     * Resolves addresses registered in the IP finder, removes duplicates and local host
     * address and returns the collection of.
     *
     * @return Resolved addresses without duplicates and local address (potentially
     * empty but never null).
     * @throws GridSpiException If an error occurs.
     */
    @SuppressWarnings("BusyWait")
    private Collection<InetSocketAddress> resolvedAddresses() throws GridSpiException {
        Collection<InetSocketAddress> res = new LinkedHashSet<InetSocketAddress>();

        Collection<InetSocketAddress> addrs;

        // Get consistent addresses collection.
        while (true) {
            try {
                addrs = registeredAddresses();

                break;
            }
            catch (GridSpiException e) {
                LT.error(log, e, "Failed to get registered addresses from IP finder on start " +
                    "(retrying every 2000 ms).");
            }

            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException e) {
                throw new GridSpiException("Thread has been interrupted.", e);
            }
        }

        for (InetSocketAddress addr : addrs) {
            assert addr != null;

            try {
                InetSocketAddress resolved = addr.isUnresolved() ?
                    new InetSocketAddress(InetAddress.getByName(addr.getHostName()), addr.getPort()) : addr;

                if ((!locHost.equals(resolved.getAddress()) || resolved.getPort() != tcpSrvr.port))
                    res.add(resolved);
            }
            catch (UnknownHostException ignored) {
                LT.warn(log, null, "Failed to resolve address from IP finder (host is unknown): " + addr);

                // Add address in any case.
                res.add(addr);
            }
        }

        return res;
    }

    /**
     * Gets addresses registered in the IP finder, initializes addresses having no
     * port (or 0 port) with {@link #DFLT_PORT}.
     *
     * @return Registered addresses.
     * @throws GridSpiException If an error occurs.
     */
    private Collection<InetSocketAddress> registeredAddresses() throws GridSpiException {
        Collection<InetSocketAddress> res = new LinkedList<InetSocketAddress>();

        for (InetSocketAddress addr : ipFinder.getRegisteredAddresses()) {
            if (addr.getPort() == 0)
                addr = addr.isUnresolved() ? new InetSocketAddress(addr.getHostName(), DFLT_PORT) :
                    new InetSocketAddress(addr.getAddress(), DFLT_PORT);

            res.add(addr);
        }

        return res;
    }

    /**
     * Checks whether local node is coordinator. Nodes that are leaving or failed
     * (but are still in topology) are removed from search.
     *
     * @return {@code true} if local node is coordinator.
     */
    private boolean isLocalNodeCoordinator() {
        synchronized (mux) {
            boolean crd = spiState == CONNECTED && locNode.equals(resolveCoordinator());

            if (crd)
                stats.onBecomingCoordinator();

            return crd;
        }
    }

    /**
     * @return Spi state copy.
     */
    private GridTcpDiscoverySpiState spiStateCopy() {
        GridTcpDiscoverySpiState state;

        synchronized (mux) {
            state = spiState;
        }

        return state;
    }

    /**
     * Resolves coordinator. Nodes that are leaving or failed (but are still in
     * topology) are removed from search.
     *
     * @return Coordinator node or {@code null} if there are no coordinator
     * (i.e. local node is the last one and is currently stopping).
     */
    @Nullable private GridTcpDiscoveryNode resolveCoordinator() {
        return resolveCoordinator(null);
    }

    /**
     * Resolves coordinator. Nodes that are leaving or failed (but are still in
     * topology) are removed from search as well as provided filter.
     *
     * @param filter Nodes to exclude when resolving coordinator (optional).
     * @return Coordinator node or {@code null} if there are no coordinator
     * (i.e. local node is the last one and is currently stopping).
     */
    @Nullable private GridTcpDiscoveryNode resolveCoordinator(
        @Nullable Collection<GridTcpDiscoveryNode> filter) {
        synchronized (mux) {
            Collection<GridTcpDiscoveryNode> excluded = F.concat(false, failedNodes, leavingNodes);

            if (!F.isEmpty(filter))
                excluded = F.concat(false, excluded, filter);

            return ring.coordinator(excluded);
        }
    }

    /**
     * Prints SPI statistics.
     */
    private void printStatistics() {
        if (log.isInfoEnabled() && !log.isQuiet() && statsPrintFreq > 0)
            log.info("Discovery SPI statistics [statistics=" + stats + ", spiState=" + spiStateCopy() +
                ", topSize=" + ring.allNodes().size() +
                ", msgWorker.queue.size=" + (msgWorker != null ? msgWorker.queue.size() : "N/A") +
                ", lastUpdate=" + (locNode != null ? locNode.lastUpdateTime() : "N/A") + ']');
    }

    /**
     * <strong>FOR TEST ONLY!!!</strong>
     * <p>
     * Simulates this node failure by stopping service threads. So, node will become
     * unresponsive.
     * <p>
     * This method is intended for test purposes only.
     */
    void simulateNodeFailure() {
        U.warn(log, "Simulating node failure: " + locNodeId);

        U.interrupt(tcpSrvr);
        U.join(tcpSrvr, log);

        U.interrupt(hbsSnd);
        U.join(hbsSnd, log);

        U.interrupt(chkStatusSnd);
        U.join(chkStatusSnd, log);

        U.interrupt(storesCleaner);
        U.join(storesCleaner, log);

        U.interrupt(metricsUpdateNtf);
        U.join(metricsUpdateNtf, log);

        Collection<SocketReader> tmp;

        synchronized (mux) {
            tmp = new ArrayList<SocketReader>(readers);
        }

        U.interrupt(tmp);
        U.joinThreads(tmp, log);

        U.interrupt(msgWorker);
        U.join(msgWorker, log);

        U.interrupt(statsPrinter);
        U.join(statsPrinter, log);
    }

    /**
     * <strong>FOR TEST ONLY!!!</strong>
     * <p>
     * This method is intended for test purposes only.
     *
     * @param msg Message.
     */
    void onBeforeMessageSentAcrossRing(Serializable msg) {
        // No-op.
    }

    /**
     * <strong>FOR TEST ONLY!!!</strong>
     * <p>
     * This method is intended for test purposes only.
     *
     * @return Nodes ring.
     */
    GridTcpDiscoveryNodesRing ring() {
        return ring;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoverySpi.class, this);
    }

    /**
     * Thread that sends heartbeats.
     */
    private class HeartbeatsSender extends GridSpiThread {
        /**
         * Constructor.
         */
        private HeartbeatsSender() {
            super(gridName, "tcp-disco-metric-sender", log);

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            synchronized (mux) {
                while (!isLocalNodeCoordinator())
                    mux.wait(hbFreq);
            }

            if (log.isDebugEnabled())
                log.debug("Heartbeats sender has been started.");

            while (!isInterrupted()) {
                GridTcpDiscoveryHeartbeatMessage msg = new GridTcpDiscoveryHeartbeatMessage(locNodeId);

                if (topStore != null)
                    msg.topologyVersion(ring.topologyVersion());

                msgWorker.addMessage(msg);

                synchronized (mux) {
                    long threshold = System.currentTimeMillis() + hbFreq;

                    long timeout = hbFreq;

                    while (timeout > 0 && spiState == CONNECTED) {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }

                    if (spiState != CONNECTED) {
                        if (log.isDebugEnabled())
                            log.debug("Stopping heartbeats sender (SPI is not connected to topology).");

                        return;
                    }
                }
            }
        }
    }

    /**
     * Thread that sends status check messages to next node if local node has not
     * been receiving heartbeats ({@link GridTcpDiscoveryHeartbeatMessage})
     * for {@link GridTcpDiscoverySpi#getMaxMissedHeartbeats()} *
     * {@link GridTcpDiscoverySpi#getHeartbeatFrequency()}.
     */
    private class CheckStatusSender extends GridSpiThread {
        /**
         * Constructor.
         */
        private CheckStatusSender() {
            super(gridName, "tcp-disco-status-check-sender", log);

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @SuppressWarnings("BusyWait")
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Status check sender has been started.");

            // Only 1 heartbeat missing is acceptable. 1 sec is added to avoid false alarm.
            long checkTimeout = (long) maxMissedHbs * hbFreq + 1000;

            long lastSent = 0;

            while (!isInterrupted()) {
                // 1. Determine timeout.
                if (lastSent < locNode.lastUpdateTime())
                    lastSent = locNode.lastUpdateTime();

                long timeout = (lastSent + checkTimeout) - System.currentTimeMillis();

                if (timeout > 0)
                    Thread.sleep(timeout);

                // 2. Check if SPI is still connected.
                if (spiStateCopy() != CONNECTED) {
                    if (log.isDebugEnabled())
                        log.debug("Stopping status check sender (SPI is not connected to topology).");

                    return;
                }

                // 3. Was there an update?
                if (locNode.lastUpdateTime() > lastSent || !ring.hasRemoteNodes())
                    continue;

                // 4. Send status check message.
                lastSent = System.currentTimeMillis();

                msgWorker.addMessage(new GridTcpDiscoveryStatusCheckMessage(locNode));
            }
        }
    }

    /**
     * Thread that cleans SPI stores (IP finder and metrics store) and keeps them in
     * the correct state, unregistering addresses and metrics of the nodes that has
     * left the topology.
     * <p>
     * This thread should run only on coordinator node and will clean IP finder
     * if and only if {@link GridTcpDiscoveryIpFinder#isShared()} is {@code true}.
     */
    private class StoresCleaner extends GridSpiThread {
        /**
         * Constructor.
         */
        private StoresCleaner() {
            super(gridName, "tcp-disco-stores-cleaner", log);

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @SuppressWarnings("BusyWait")
        @Override protected void body() throws InterruptedException {
            while (!isLocalNodeCoordinator())
                Thread.sleep(2000);

            if (log.isDebugEnabled())
                log.debug("Stores cleaner has been started.");

            while (!isInterrupted()) {
                Thread.sleep(storesCleanFreq);

                if (spiStateCopy() != CONNECTED) {
                    if (log.isDebugEnabled())
                        log.debug("Stopping stores cleaner (SPI is not connected to topology).");

                    return;
                }

                if (ipFinder.isShared())
                    cleanIpFinder();

                if (metricsStore != null)
                    cleanMetricsStore();
            }
        }

        /**
         * Cleans IP finder.
         */
        private void cleanIpFinder() {
            assert ipFinder.isShared();

            try {
                // Addresses that belongs to nodes in topology.
                Collection<InetSocketAddress> currAddrs = F.viewReadOnly(
                    ring.allNodes(),
                    new C1<GridTcpDiscoveryNode, InetSocketAddress>() {
                        @Override public InetSocketAddress apply(GridTcpDiscoveryNode node) {
                            return node.address();
                        }
                    }
                );

                // Addresses registered in IP finder.
                Collection<InetSocketAddress> regAddrs = registeredAddresses();

                // Remove all addresses that belong to alive nodes, leave dead-node addresses.
                Collection<InetSocketAddress> rmvAddrs = F.view(
                    regAddrs,
                    F.notContains(currAddrs),
                    new P1<InetSocketAddress>() {
                        private final Map<InetSocketAddress, Boolean> pingResMap =
                            new HashMap<InetSocketAddress, Boolean>();

                        @Override public boolean apply(InetSocketAddress addr) {
                            Boolean res = pingResMap.get(addr);

                            if (res == null)
                                try {
                                    res = pingNode(addr) != null;
                                }
                                catch (GridSpiException e) {
                                    if (log.isDebugEnabled())
                                        log.debug("Failed to ping node [addr=" + addr +
                                            ", err=" + e.getMessage() + ']');

                                    res = false;
                                }
                                finally {
                                    pingResMap.put(addr, res);
                                }

                            return !res;
                        }
                    }
                );

                // Unregister dead-nodes addresses.
                if (!rmvAddrs.isEmpty()) {
                    ipFinder.unregisterAddresses(rmvAddrs);

                    if (log.isDebugEnabled())
                       log.debug("Unregistered addresses from IP finder: " + rmvAddrs);
                }

                // Addresses that were removed by mistake (e.g. on segmentation).
                Collection<InetSocketAddress> missingAddrs = F.view(
                    currAddrs,
                    F.notContains(regAddrs)
                );

                // Re-register missing addresses.
                if (!missingAddrs.isEmpty()) {
                    ipFinder.registerAddresses(missingAddrs);

                    if (log.isDebugEnabled())
                       log.debug("Registered missing addresses in IP finder: " + missingAddrs);
                }
            }
            catch (GridSpiException e) {
                LT.error(log, e, "Failed to clean IP finder up.");
            }
        }

        /**
         * Cleans metrics store.
         */
        private void cleanMetricsStore() {
            assert metricsStore != null;

            try {
                Collection<UUID> ids = F.view(metricsStore.allNodeIds(), F.notContains(
                    F.viewReadOnly(ring.allNodes(), F.node2id())));

                if (!ids.isEmpty())
                    metricsStore.removeMetrics(ids);
            }
            catch (GridSpiException e) {
                LT.error(log, e, "Failed to clean metrics store up.");
            }
        }
    }

    /**
     * Message worker thread for messages processing.
     */
    private class MessageWorker extends GridSpiThread {
        /** Socket to next node. */
        private Socket nextNodeSock;

        /** Next node. */
        @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
        private GridTcpDiscoveryNode next;

        /** First queue message gets in. */
        private final BlockingDeque<GridTcpDiscoveryAbstractMessage> queue =
            new LinkedBlockingDeque<GridTcpDiscoveryAbstractMessage>();

        /** Pending messages. */
        private final Map<GridUuid, GridTcpDiscoveryAbstractMessage> pendingMsgs =
            new LinkedHashMap<GridUuid, GridTcpDiscoveryAbstractMessage>();

        /** Last evicted topology version. */
        private long lastEvictedTopVer;

        /** Max topology version received from the store. */
        private long maxTopVerRcvd;

        /** Backed interrupted flag. */
        private volatile boolean interrupted;

        /** Constructor. */
        private MessageWorker() {
            super(gridName, "tcp-disco-msg-worker", log);

            setPriority(threadPri);
        }

        /**
         * Adds message to queue.
         *
         * @param msg Message to add.
         */
        void addMessage(GridTcpDiscoveryAbstractMessage msg) {
            assert msg != null;

            if (msg instanceof GridTcpDiscoveryHeartbeatMessage)
                queue.addFirst(msg);

            else
                queue.add(msg);

            if (log.isDebugEnabled())
                log.debug("Message has been added to queue: " + msg);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            while (!isInterrupted()) {
                GridTcpDiscoveryAbstractMessage msg = queue.poll(2000, TimeUnit.MILLISECONDS);

                if (msg == null)
                    continue;

                if (log.isDebugEnabled())
                    log.debug("Processing message [cls=" + msg.getClass().getSimpleName() + ", id=" + msg.id() + ']');

                stats.onMessageProcessingStarted(msg);

                if (msg instanceof GridTcpDiscoveryJoinRequestMessage)
                    processJoinRequestMessage((GridTcpDiscoveryJoinRequestMessage)msg);

                else if (msg instanceof GridTcpDiscoveryNodeAddedMessage)
                    processNodeAddedMessage((GridTcpDiscoveryNodeAddedMessage)msg);

                else if (msg instanceof GridTcpDiscoveryNodeAddFinishedMessage)
                    processNodeAddFinishedMessage((GridTcpDiscoveryNodeAddFinishedMessage)msg);

                else if (msg instanceof GridTcpDiscoveryNodeLeftMessage)
                    processNodeLeftMessage((GridTcpDiscoveryNodeLeftMessage)msg);

                else if (msg instanceof GridTcpDiscoveryNodeFailedMessage)
                    processNodeFailedMessage((GridTcpDiscoveryNodeFailedMessage)msg);

                else if (msg instanceof GridTcpDiscoveryHeartbeatMessage) {
                    if (metricsStore != null)
                        processHeartbeatMessageMetricsStore((GridTcpDiscoveryHeartbeatMessage)msg);
                    else
                        processHeartbeatMessage((GridTcpDiscoveryHeartbeatMessage)msg);
                }
                else if (msg instanceof GridTcpDiscoveryStatusCheckMessage)
                    processStatusCheckMessage((GridTcpDiscoveryStatusCheckMessage)msg);

                else if (msg instanceof GridTcpDiscoveryDiscardMessage)
                    processDiscardMessage((GridTcpDiscoveryDiscardMessage)msg);

                else if (msg instanceof GridTcpDiscoveryUpdateTopologyMessage)
                    processUpdateTopologyMessage((GridTcpDiscoveryUpdateTopologyMessage)msg);

                else
                    assert false : "Unknown message type: " + msg.getClass().getSimpleName();

                stats.onMessageProcessingFinished(msg);
            }
        }

        /** {@inheritDoc} */
        @Override protected void cleanup() {
            super.cleanup();

            U.closeQuiet(nextNodeSock);
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            interrupted = true;

            super.interrupt();
        }

        /** {@inheritDoc} */
        @Override public boolean isInterrupted() {
            return interrupted || super.isInterrupted();
        }

        /**
         * Sends message across the ring.
         *
         * @param msg Message to send
         */
        private void sendMessageAcrossRing(GridTcpDiscoveryAbstractMessage msg) {
            assert msg != null;

            assert ring.hasRemoteNodes();

            onBeforeMessageSentAcrossRing(msg);

            Collection<GridTcpDiscoveryNode> failedNodes;

            GridTcpDiscoverySpiState state;

            synchronized (mux) {
                failedNodes = new ArrayList<GridTcpDiscoveryNode>(GridTcpDiscoverySpi.this.failedNodes);

                state = spiState;
            }

            Exception err = null;

            boolean sent = false;

            boolean searchNext = true;

            while (true) {
                if (searchNext) {
                    GridTcpDiscoveryNode newNext = ring.nextNode(failedNodes);

                    if (newNext == null) {
                        if (log.isDebugEnabled())
                            log.debug("No next node in topology.");

                        break;
                    }

                    if (!newNext.equals(next)) {
                        if (log.isDebugEnabled())
                            log.debug("New next node [newNext=" + newNext + ", formerNext=" + next + ']');

                        U.closeQuiet(nextNodeSock);

                        nextNodeSock = null;

                        next = newNext;
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Next node remains the same [nextId=" + next.id() +
                            ", nextOrder=" + next.internalOrder() + ']');
                }

                // Flag that shows whether next node exists and accepts incoming connections.
                boolean nextNodeExists = nextNodeSock != null;

                for (int i = 0; i < reconCnt; i++) {
                    if (nextNodeSock == null) {
                        nextNodeExists = false;

                        boolean success = false;

                        // Restore ring.
                        try {
                            long tstamp = System.currentTimeMillis();

                            nextNodeSock = openSocket(next.address().getAddress(), next.address().getPort());

                            // Handshake.
                            writeToSocket(nextNodeSock, new GridTcpDiscoveryHandshakeRequest(locNodeId));

                            GridTcpDiscoveryHandshakeResponse res = readMessage(nextNodeSock);

                            stats.onClientSocketInitialized(System.currentTimeMillis() - tstamp);

                            UUID nextId = res.creatorNodeId();

                            long nextOrder = res.order();

                            if (!next.id().equals(nextId)) {
                                // Node with different ID has bounded to the same port.
                                if (log.isDebugEnabled())
                                    log.debug("Failed to restore ring because next node ID received is not as " +
                                        "expected [expectedId=" + next.id() + ", rcvdId=" + nextId + ']');

                                break;
                            }
                            else {
                                // ID is as expected. Check node order.
                                if (nextOrder != next.internalOrder()) {
                                    // Is next currently being added?
                                    boolean nextNew = (msg instanceof GridTcpDiscoveryNodeAddedMessage &&
                                        ((GridTcpDiscoveryNodeAddedMessage)msg).node().id().equals(nextId)) ||
                                        // If topology store is used...
                                        (msg instanceof GridTcpDiscoveryUpdateTopologyMessage &&
                                        ((GridTcpDiscoveryUpdateTopologyMessage)msg).addedNodeIds().contains(nextId));

                                    if (!nextNew) {
                                        if (log.isDebugEnabled())
                                            log.debug("Failed to restore ring because next node order received " +
                                                "is not as expected [expected=" + next.internalOrder() +
                                                ", rcvd=" + nextOrder + ", id=" + next.id() + ']');

                                        break;
                                    }
                                }

                                if (log.isDebugEnabled())
                                    log.debug("Initialized connection with next node: " + next.id());

                                err = null;

                                success = true;
                            }
                        }
                        catch (IOException e) {
                            if (err == null)
                                err = e;

                            if (log.isDebugEnabled())
                                log.debug("Failed to connect to next node [msg=" + msg + ", err=" + e + ']');

                            continue;
                        }
                        catch (GridException e) {
                            if (err == null)
                                err = e;

                            if (log.isDebugEnabled())
                                log.debug("Failed to connect to next node [msg=" + msg + ", err=" + e + ']');

                            continue;
                        }
                        finally {
                            if (!success) {
                                U.closeQuiet(nextNodeSock);

                                nextNodeSock = null;
                            }
                            else
                                // Next node exists and accepts incoming messages.
                                nextNodeExists = true;
                        }
                    }

                    try {
                        boolean failure;

                        synchronized (mux) {
                            failure = GridTcpDiscoverySpi.this.failedNodes.size() < failedNodes.size();
                        }

                        boolean sendPending = true;

                        if (msg instanceof GridTcpDiscoveryNodeAddedMessage) {
                            GridTcpDiscoveryNodeAddedMessage nodeAddedMsg =
                                (GridTcpDiscoveryNodeAddedMessage)msg;

                            assert topStore == null;

                            // If new node is next, then send topology to and all pending messages
                            // as a part of message.
                            if (nodeAddedMsg.node().equals(next)) {
                                nodeAddedMsg.topology(F.view(ring.allNodes(), F.notEqualTo(nodeAddedMsg.node())));

                                nodeAddedMsg.topologyVersion(ring.topologyVersion());

                                nodeAddedMsg.messages(pendingMsgs.values());

                                sendPending = false;
                            }
                        }

                        if (failure && sendPending) {
                            for (GridTcpDiscoveryAbstractMessage pendingMsg : pendingMsgs.values()) {
                                if (pendingMsg instanceof GridTcpDiscoveryNodeAddedMessage) {
                                    final GridTcpDiscoveryNodeAddedMessage nodeAddedMsg =
                                        (GridTcpDiscoveryNodeAddedMessage)pendingMsg;

                                    // If new node is next, we don't know whether pending node added message
                                    // reached it, we should send topology (all preceding nodes) and
                                    // all pending messages as a part of message.
                                    if (nodeAddedMsg.node().equals(next)) {
                                        nodeAddedMsg.topology(F.view(ring.allNodes(),
                                            new P1<GridTcpDiscoveryNode>() {
                                                @Override public boolean apply(GridTcpDiscoveryNode n) {
                                                    return n.internalOrder() < nodeAddedMsg.node().internalOrder();
                                                }
                                            }));

                                        nodeAddedMsg.topologyVersion(ring.topologyVersion());

                                        nodeAddedMsg.messages(pendingMsgs.values());
                                    }
                                }

                                long tstamp = System.currentTimeMillis();

                                writeToSocket(nextNodeSock, pendingMsg);

                                stats.onMessageSent(pendingMsg, System.currentTimeMillis() - tstamp);

                                int res = readReceipt(nextNodeSock);

                                if (log.isDebugEnabled())
                                    log.debug("Pending message has been sent to next node [msg=" + msg.id() +
                                        ", next=" + next.id() + "res=" + res + ']');
                            }
                        }

                        long tstamp = System.currentTimeMillis();

                        writeToSocket(nextNodeSock, msg);

                        stats.onMessageSent(msg, System.currentTimeMillis() - tstamp);

                        int res = readReceipt(nextNodeSock);

                        if (log.isDebugEnabled())
                            log.debug("Message has been sent to next node [msg=" + msg + ", next=" + next.id() +
                                ", res=" + res + ']');

                        registerPendingMessage(msg);

                        sent = true;

                        break;
                    }
                    catch (IOException e) {
                        if (err == null)
                            err = e;

                        if (log.isDebugEnabled())
                            log.debug("Failed to send message to next node [next=" + next.id() + ", msg=" + msg +
                                ", err=" + e + ']');
                    }
                    catch (GridException e) {
                        if (err == null)
                            err = e;

                        if (log.isDebugEnabled())
                            log.debug("Failed to send message to next node [next=" + next.id() + ", msg=" + msg +
                                ", err=" + e + ']');
                    }
                    finally {
                        if (!sent) {
                            U.closeQuiet(nextNodeSock);

                            nextNodeSock = null;

                            if (log.isDebugEnabled())
                                log.debug("Message has not been sent [next=" + next.id() + ", msg=" + msg +
                                    ", i=" + i + ']');
                        }
                    }
                }

                if (!sent) {
                    if (topStore != null) {
                        try {
                            long tstamp = System.currentTimeMillis();

                            GridTcpDiscoveryTopologyStoreNodeState nextNodeState = topStore.state(next.id());

                            stats.onTopologyStoreGetNodeState(System.currentTimeMillis() - tstamp);

                            if (log.isDebugEnabled())
                                log.debug("Checked next node state in topology store [next=" + next +
                                    ", state=" + nextNodeState + ']');

                            if (nextNodeState == FAILED || nextNodeState == LEAVING) {
                                synchronized (mux) {
                                    GridTcpDiscoverySpi.this.failedNodes.add(next);
                                }

                                failedNodes.add(next);
                            }
                        }
                        catch (GridSpiException e) {
                            U.error(log, "Failed to get node state from topology store: " + next.id(), e);
                        }
                    }

                    if (!failedNodes.contains(next)) {
                        failedNodes.add(next);

                        if (state == CONNECTED)
                            // If node existed on connection initialization we should check
                            // whether it has not gone yet.
                            if (nextNodeExists && pingNode(next))
                                U.error(log, "Failed to send message to next node [msg=" + msg +
                                    ", next=" + next + ']', err);
                            else
                                if (log.isDebugEnabled())
                                    log.debug("Failed to send message to next node [msg=" + msg + ", next=" + next +
                                        ", errMsg=" + (err != null ? err.getMessage() : "N/A") + ']');
                    }

                    searchNext = true;

                    next = null;

                    err = null;
                }
                else
                    break;
            }

            synchronized (mux) {
                failedNodes.removeAll(GridTcpDiscoverySpi.this.failedNodes);
            }

            if (!failedNodes.isEmpty()) {
                if (state == CONNECTED) {
                    if (!sent && log.isDebugEnabled())
                        // Message has not been sent due to some problems.
                        log.debug("Message has not been sent: " + msg);

                    if (log.isDebugEnabled())
                        log.debug("Detected failed nodes: " + failedNodes);
                }

                synchronized (mux) {
                    GridTcpDiscoverySpi.this.failedNodes.addAll(failedNodes);
                }

                for (GridTcpDiscoveryNode n : failedNodes)
                    msgWorker.addMessage(new GridTcpDiscoveryNodeFailedMessage(locNodeId, n.id()));
            }
        }

        /**
         * Registers pending message.
         *
         * @param msg Message to register.
         */
        private void registerPendingMessage(GridTcpDiscoveryAbstractMessage msg) {
            assert msg != null;

            if (U.getAnnotation(msg.getClass(), GridTcpDiscoveryEnsureDelivery.class) != null) {
                // Remove then put again to manage order.
                GridTcpDiscoveryAbstractMessage prev = pendingMsgs.remove(msg.id());

                pendingMsgs.put(msg.id(), msg);

                if (prev == null) {
                    stats.onPendingMessageRegistered();

                    if (log.isDebugEnabled())
                        log.debug("Pending message has been registered: " + msg.id());
                }
            }
        }

        /**
         * Processes join request message.
         *
         * @param msg Join request message.
         */
        private void processJoinRequestMessage(GridTcpDiscoveryJoinRequestMessage msg) {
            assert msg != null;

            if (isLocalNodeCoordinator()) {
                GridTcpDiscoveryNode node = msg.node();

                GridTcpDiscoveryNode existingNode = ring.node(node.id());

                if (existingNode != null) {
                    if (!node.address().equals(existingNode.address())) {
                        if (!pingNode(existingNode)) {
                            addMessage(new GridTcpDiscoveryNodeFailedMessage(locNodeId, existingNode.id()));

                            // Ignore this join request since existing node is about to fail
                            // and new node can continue.
                            return;
                        }

                        // Node with the same ID exists in topology and responds to ping.
                        try {
                            sendMessageDirectly(new GridTcpDiscoveryDuplicateIdMessage(locNodeId, existingNode),
                                node.address());
                        }
                        catch (GridSpiException e) {
                            if (log.isDebugEnabled())
                                log.debug("Failed to send duplicate ID message to node " +
                                    "[node=" + node + ", existingNode=" + existingNode +
                                    ", err=" + e.getMessage() + ']');
                        }

                        // Ignore join request.
                        return;
                    }

                    // Node added message will be issued one more time.
                    node = existingNode;
                }
                else
                    node.internalOrder(ring.nextNodeOrder());

                if (topStore == null)
                    processNodeAddedMessage(new GridTcpDiscoveryNodeAddedMessage(locNodeId, node));
                else {
                    try {
                        long tstamp = System.currentTimeMillis();

                        GridTcpDiscoveryTopologyStoreNodeState state = topStore.state(node.id());

                        stats.onTopologyStoreGetNodeState(System.currentTimeMillis() - tstamp);

                        if (state == null) {
                            // Node has not yet been added to topology store.
                            node.state(ONLINE);

                            tstamp = System.currentTimeMillis();

                            topStore.put(node);

                            stats.onTopologyStoreNodePut(System.currentTimeMillis() - tstamp);
                        }

                        addMessage(new GridTcpDiscoveryUpdateTopologyMessage(locNodeId));
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to put node to topology store: " + node, e);
                    }
                }
            }
            else if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
        }

        /**
         * Processes node added message.
         *
         * @param msg Node added message.
         */
        private void processNodeAddedMessage(GridTcpDiscoveryNodeAddedMessage msg) {
            assert msg != null;

            GridTcpDiscoveryNode node = msg.node();

            assert node != null;

            if (isLocalNodeCoordinator() && topStore == null) {
                if (msg.verified()) {
                    stats.onRingMessageReceived(msg);

                    addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                    addMessage(new GridTcpDiscoveryNodeAddFinishedMessage(locNodeId, node.id()));

                    return;
                }

                msg.verify(locNodeId);
            }

            if (msg.verified() && !locNodeId.equals(node.id())) {
                if (metricsStore != null) {
                    node.metricsStore(metricsStore);

                    node.logger(log);
                }

                boolean topChanged = ring.add(node);

                if (topChanged)
                    assert !node.visible() : "Added visible node [node=" + node + ", locNode=" + locNode + ']';

                if (log.isDebugEnabled())
                    log.debug("Added node to local ring [added=" + topChanged + ", node=" + node +
                        ", ring=" + ring + ']');
            }

            if (msg.verified() && locNodeId.equals(node.id())) {
                synchronized (mux) {
                    if (spiState == CONNECTING) {
                        if (topStore != null) {
                            for (GridTcpDiscoveryNode n : ring.remoteNodes()) {
                                if (metricsStore != null) {
                                    n.metricsStore(metricsStore);

                                    n.logger(log);
                                }

                                // Make all preceding nodes and local node visible.
                                n.visible(true);
                            }

                            locNode.visible(true);

                            // Artificial "restore" topology to order nodes in the ring.
                            ring.restoreTopology(ring.remoteNodes(), node.internalOrder());

                            if (log.isDebugEnabled())
                                log.debug("Restored topology: " + ring);

                            locNode.topologyVersion(msg.topologyVersion());
                        }
                        else {
                            // Initialize topology.
                            Collection<GridTcpDiscoveryNode> top = msg.topology();

                            assert top != null;
                            assert !top.isEmpty();

                            for (GridTcpDiscoveryNode n : top) {
                                if (metricsStore != null) {
                                    n.metricsStore(metricsStore);

                                    n.logger(log);
                                }

                                // Make all preceding nodes and local node visible.
                                n.visible(true);
                            }

                            locNode.visible(true);

                            // Restore topology with all nodes visible.
                            ring.restoreTopology(top, node.internalOrder());

                            if (log.isDebugEnabled())
                                log.debug("Restored topology from node added message: " + ring);

                            // Initialize pending messages using info from previous node.
                            Collection<GridTcpDiscoveryAbstractMessage> msgs = msg.messages();

                            if (msgs != null && !msgs.isEmpty())
                                for (GridTcpDiscoveryAbstractMessage m : msgs)
                                    registerPendingMessage(m);

                            // Clear data to minimize message size.
                            msg.messages(null);

                            msg.topology(null);
                        }
                    }
                }
            }

            if (ring.hasRemoteNodes() && topStore == null)
                sendMessageAcrossRing(msg);
        }

        /**
         * Processes node add finished message.
         *
         * @param msg Node add finished message.
         */
        private void processNodeAddFinishedMessage(GridTcpDiscoveryNodeAddFinishedMessage msg) {
            assert msg != null;

            UUID nodeId = msg.nodeId();

            assert nodeId != null;

            GridTcpDiscoveryNode node = ring.node(nodeId);

            if (log.isDebugEnabled())
                log.debug("Node to finish add: " + node);

            boolean locNodeCoord = isLocalNodeCoordinator();

            if (locNodeCoord) {
                if (msg.verified()) {
                    stats.onRingMessageReceived(msg);

                    addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                    return;
                }

                if (topStore == null)
                    msg.topologyVersion(ring.incrementTopologyVersion());
                else {
                    // Otherwise take version assigned by topology store.
                    try {
                        msg.topologyVersion(topStore.topologyVersion());
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to get topology version from store.", e);
                    }
                }

                msg.verify(locNodeId);
            }

            long topVer = msg.topologyVersion();

            boolean fireEvent = false;

            if (node != null) {
                if (node.order() == 0)
                    node.order(topVer);

                if (!node.visible()) {
                    node.visible(true);

                    fireEvent = true;
                }
            }

            if (msg.verified() && !locNodeId.equals(nodeId) && spiStateCopy() == CONNECTED && fireEvent) {
                stats.onNodeJoined();

                // Make sure that node with greater order will never get EVT_NODE_JOINED
                // on node with less order.
                assert node.internalOrder() > locNode.internalOrder();

                if (!locNodeCoord && topStore == null) {
                    boolean b = ring.topologyVersion(topVer);

                    assert b : "Topology version has not been updated: [ring=" + ring + ", msg=" + msg +
                            ", spiState=" + spiStateCopy() + ']';

                    if (log.isDebugEnabled())
                        log.debug("Topology version has been updated: [ring=" + ring + ", msg=" + msg + ']');
                }

                notifyDiscovery(EVT_NODE_JOINED, topVer, node);

                try {
                    if (ipFinder.isShared() && locNodeCoord)
                        ipFinder.registerAddresses(Collections.singletonList(node.address()));
                }
                catch (GridSpiException e) {
                    if (log.isDebugEnabled())
                        log.debug("Failed to register new node address [node=" + node +
                            ", err=" + e.getMessage() + ']');
                }
            }

            if (msg.verified() && locNodeId.equals(nodeId) && spiStateCopy() == CONNECTING) {
                assert node != null;

                ring.topologyVersion(topVer);

                node.order(topVer);

                synchronized (mux) {
                    spiState = CONNECTED;

                    mux.notifyAll();
                }

                if (recon)
                    notifyDiscovery(EVT_NODE_RECONNECTED, topVer, locNode);
                else
                    recon = true;
            }

            if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
        }


        /**
         * Processes node left message.
         *
         * @param msg Node left message.
         */
        @SuppressWarnings({"ReturnInsideFinallyBlock", "BusyWait"})
        private void processNodeLeftMessage(GridTcpDiscoveryNodeLeftMessage msg) {
            assert msg != null;

            UUID leavingNodeId = msg.creatorNodeId();

            if (locNodeId.equals(leavingNodeId)) {
                if (msg.senderNodeId() == null) {
                    synchronized (mux) {
                        if (log.isDebugEnabled())
                            log.debug("Starting local node stop procedure.");

                        spiState = STOPPING;

                        mux.notifyAll();
                    }
                }

                if (msg.verified() || !ring.hasRemoteNodes()) {
                    if (ipFinder.isShared() && !ring.hasRemoteNodes()) {
                        try {
                            ipFinder.unregisterAddresses(Collections.singletonList(locNode.address()));
                        }
                        catch (GridSpiException e) {
                            U.error(log, "Failed to unregister local node address from IP finder.", e);
                        }
                    }

                    if (metricsStore != null && !ring.hasRemoteNodes()) {
                        try {
                            metricsStore.removeMetrics(Collections.singletonList(locNodeId));
                        }
                        catch (GridSpiException e) {
                            U.error(log, "Failed to remove local node metrics from metrics store.", e);
                        }
                    }

                    synchronized (mux) {
                        if (spiState == STOPPING) {
                            spiState = LEFT;

                            mux.notifyAll();
                        }
                    }

                    return;
                }

                sendMessageAcrossRing(msg);

                return;
            }

            GridTcpDiscoveryNode leavingNode = ring.node(leavingNodeId);

            if (leavingNode != null) {
                synchronized (mux) {
                    leavingNodes.add(leavingNode);
                }
            }

            boolean locNodeCoord = isLocalNodeCoordinator();

            if (locNodeCoord) {
                if (topStore != null) {
                    if (!msg.verified()) {
                        GridTcpDiscoveryNode leftNode = ring.node(leavingNodeId);

                        if (leftNode != null) {
                            try {
                                leftNode.state(LEAVING);

                                // Empty attributes to make node thin.
                                leftNode.setAttributes(Collections.<String, Object>emptyMap());

                                long tstamp = System.currentTimeMillis();

                                topStore.put(leftNode);

                                stats.onTopologyStoreNodePut(System.currentTimeMillis() - tstamp);

                                addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                                addMessage(new GridTcpDiscoveryUpdateTopologyMessage(locNodeId));
                            }
                            catch (GridSpiException e) {
                                U.error(log, "Failed to update left node in topology store: " + leftNode, e);
                            }
                        }

                        return;
                    }
                }
                else {
                    if (msg.verified()) {
                        stats.onRingMessageReceived(msg);

                        addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                        return;
                    }

                    msg.verify(locNodeId);
                }
            }

            if (msg.verified() && !locNodeId.equals(leavingNodeId)) {
                GridTcpDiscoveryNode leftNode = ring.removeNode(leavingNodeId);

                if (leftNode != null) {
                    long topVer;

                    if (locNodeCoord) {
                        if (topStore == null)
                            topVer = ring.incrementTopologyVersion();
                        else {
                            topVer = msg.topologyVersion();

                            assert topVer > 0 : "Topology version is empty for message: " + msg;

                            boolean b = ring.topologyVersion(topVer);

                            assert b : "Topology version has not been updated: [ring=" + ring + ", msg=" + msg +
                                    ", spiState=" + spiStateCopy() + ']';

                            if (log.isDebugEnabled())
                                log.debug("Topology version has been updated: [ring=" + ring + ", msg=" + msg + ']');
                        }

                        msg.topologyVersion(topVer);
                    }
                    else {
                        topVer = msg.topologyVersion();

                        boolean b = ring.topologyVersion(topVer);

                        assert b : "Topology version has not been updated: [ring=" + ring + ", msg=" + msg +
                            ", spiState=" + spiStateCopy() + ']';

                        if (log.isDebugEnabled())
                            log.debug("Topology version has been updated: [ring=" + ring + ", msg=" + msg + ']');
                    }

                    if (leftNode.equals(next) && topStore == null && nextNodeSock != null) {
                        try {
                            writeToSocket(nextNodeSock, msg);

                            if (log.isDebugEnabled())
                                log.debug("Sent verified node left message to leaving node: " + msg);
                        }
                        catch (GridException e) {
                            if (log.isDebugEnabled())
                                log.debug("Failed to send verified node left message to leaving node [msg=" + msg +
                                    ", err=" + e.getMessage() + ']');
                        }
                        catch (IOException e) {
                            if (log.isDebugEnabled())
                                log.debug("Failed to send verified node left message to leaving node [msg=" + msg +
                                    ", err=" + e.getMessage() + ']');
                        }
                        finally {
                            long elapsed = 0;

                            while (pingNode(next)) {
                                try {
                                    Thread.sleep(100);

                                    elapsed += 100;

                                    if (elapsed > netTimeout) {
                                        if (log.isDebugEnabled())
                                            log.debug("Node has not left: " + next);

                                        break;
                                    }
                                }
                                catch (InterruptedException ignored) {
                                    if (log.isDebugEnabled())
                                        log.debug("Message worker has been interrupted.");

                                    interrupt();

                                    return;
                                }
                            }

                            next = null;

                            U.closeQuiet(nextNodeSock);
                        }
                    }

                    if (spiStateCopy() == CONNECTED)
                        onNodeLeft(leftNode, topVer);
                }
            }

            if (ring.hasRemoteNodes()) {
                if (topStore == null || !msg.verified())
                    sendMessageAcrossRing(msg);
            }
            else {
                if (log.isDebugEnabled())
                    log.debug("Unable to send message across the ring (topology has no remote nodes): " + msg);

                U.closeQuiet(nextNodeSock);
            }
        }

        /**
         * Processes node failed message.
         *
         * @param msg Node failed message.
         */
        private void processNodeFailedMessage(GridTcpDiscoveryNodeFailedMessage msg) {
            assert msg != null;

            UUID nodeId = msg.failedNodeId();

            synchronized (mux) {
                GridTcpDiscoveryNode node = ring.node(nodeId);

                if (node != null)
                    failedNodes.add(node);
            }

            boolean locNodeCoord = isLocalNodeCoordinator();

            if (locNodeCoord) {
                if (topStore != null) {
                    if (!msg.verified()) {
                        GridTcpDiscoveryNode failedNode = ring.node(nodeId);

                        if (failedNode != null) {
                            try {
                                failedNode.state(FAILED);

                                // Empty attributes to make node thin.
                                failedNode.setAttributes(Collections.<String, Object>emptyMap());

                                long tstamp = System.currentTimeMillis();

                                topStore.put(failedNode);

                                stats.onTopologyStoreNodePut(System.currentTimeMillis() - tstamp);
                            }
                            catch (GridSpiException e) {
                                U.error(log, "Failed to update failed node in topology store: " + failedNode, e);
                            }
                        }

                        addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                        addMessage(new GridTcpDiscoveryUpdateTopologyMessage(locNodeId));

                        // Message will be added from topology store worker.
                        return;
                    }
                }
                else {
                    if (msg.verified()) {
                        stats.onRingMessageReceived(msg);

                        addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                        return;
                    }

                    msg.verify(locNodeId);
                }
            }

            if (msg.verified()) {
                GridTcpDiscoveryNode node = ring.removeNode(nodeId);

                if (node != null) {
                    long topVer;

                    if (locNodeCoord) {
                        if (topStore == null)
                            topVer = ring.incrementTopologyVersion();
                        else {
                            topVer = msg.topologyVersion();

                            assert topVer > 0 : "Topology version is empty for message: " + msg;

                            boolean b = ring.topologyVersion(topVer);

                            assert b : "Topology version has not been updated: [ring=" + ring + ", msg=" + msg +
                                    ", spiState=" + spiStateCopy() + ']';

                            if (log.isDebugEnabled())
                                log.debug("Topology version has been updated: [ring=" + ring + ", msg=" + msg + ']');
                        }

                        msg.topologyVersion(topVer);
                    }
                    else {
                        topVer = msg.topologyVersion();

                        boolean b = ring.topologyVersion(topVer);

                        assert b : "Topology version has not been updated: [ring=" + ring + ", msg=" + msg +
                            ", spiState=" + spiStateCopy() + ']';

                        if (log.isDebugEnabled())
                            log.debug("Topology version has been updated: [ring=" + ring + ", msg=" + msg + ']');
                    }

                    if (spiStateCopy() == CONNECTED)
                        onNodeFailed(node, topVer);
                }
            }

            if (ring.hasRemoteNodes()) {
                if (topStore == null || !msg.verified())
                    sendMessageAcrossRing(msg);
            }
            else {
                if (log.isDebugEnabled())
                    log.debug("Unable to send message across the ring (topology has no remote nodes): " + msg);

                U.closeQuiet(nextNodeSock);
            }
        }

        /**
         * Processes status check message.
         *
         * @param msg Status check message.
         */
        private void processStatusCheckMessage(GridTcpDiscoveryStatusCheckMessage msg) {
            assert msg != null;

            if (isLocalNodeCoordinator() && !locNodeId.equals(msg.creatorNodeId())) {
                // Local node is real coordinator, it should respond and discard message.
                if (ring.node(msg.creatorNodeId()) != null) {
                    // Sender is in topology, send message via ring.
                    msg.status(STATUS_OK);

                    sendMessageAcrossRing(msg);
                }
                else {
                    // Sender is not in topology, it should reconnect.
                    msg.status(STATUS_RECON);

                    try {
                        sendMessageDirectly(msg, msg.creatorNode().address());

                        if (log.isDebugEnabled())
                            log.debug("Responded to status check message " +
                                "[recipient=" + msg.creatorNodeId() + ", status=" + msg.status() + ']');
                    }
                    catch (GridSpiException e) {
                        if (e.hasCause(SocketException.class)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Failed to respond to status check message (connection refused) " +
                                    "[recipient=" + msg.creatorNodeId() + ", status=" + msg.status() + ']');
                            }
                        }
                        else {
                            if (pingNode(msg.creatorNode())) {
                                // Node exists and accepts incoming connections.
                                U.error(log, "Failed to respond to status check message " +
                                    "[recipient=" + msg.creatorNodeId() + ", status=" + msg.status() + ']', e);
                            }
                            else if (log.isDebugEnabled()) {
                                log.debug("Failed to respond to status check message (did the node stop?) " +
                                    "[recipient=" + msg.creatorNodeId() + ", status=" + msg.status() + ']');
                            }
                        }
                    }
                }

                return;
            }

            if (locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() == null &&
                System.currentTimeMillis() - locNode.lastUpdateTime() < hbFreq) {
                if (log.isDebugEnabled())
                    log.debug("Status check message discarded (local node receives updates).");

                return;
            }

            if (locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() == null && spiStateCopy() != CONNECTED) {
                if (log.isDebugEnabled())
                    log.debug("Status check message discarded (local node is not connected to topology).");

                return;
            }

            if (locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() != null) {
                if (spiStateCopy() != CONNECTED)
                    return;

                if (msg.status() == STATUS_OK) {
                    if (log.isDebugEnabled())
                        log.debug("Received OK status response from coordinator: " + msg);
                }
                else if (msg.status() == STATUS_RECON) {
                    U.warn(log, "Node is out of topology (probably, due to short-time network problems).");

                    notifyDiscovery(EVT_NODE_SEGMENTED, ring.topologyVersion(), locNode);

                    return;
                }
                else if (log.isDebugEnabled())
                    log.debug("Status value was not updated in status response: " + msg);

                // Discard the message.
                return;
            }

            if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
        }

        /**
         * Processes regular heartbeat message.
         *
         * @param msg Heartbeat message.
         */
        private void processHeartbeatMessage(GridTcpDiscoveryHeartbeatMessage msg) {
            assert msg != null;

            if (ring.node(msg.creatorNodeId()) == null) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by unknown node [msg=" + msg +
                        ", ring=" + ring + ']');

                return;
            }

            if (isLocalNodeCoordinator() && !locNodeId.equals(msg.creatorNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by non-coordinator node: " + msg);

                return;
            }

            if (!isLocalNodeCoordinator() && locNodeId.equals(msg.creatorNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by local node (node is no more coordinator): " +
                        msg);

                return;
            }

            if (locNodeId.equals(msg.creatorNodeId()) && msg.metrics().get(locNodeId) == null &&
                msg.senderNodeId() != null) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message that has made two passes: " + msg);

                if (topStore != null) {
                    // Check if local node is still coordinator and heartbeat arrived from previous node
                    // to evict nodes properly.
                    GridTcpDiscoveryNode prev = ring.previousNode();

                    if (isLocalNodeCoordinator() && prev != null && msg.senderNodeId().equals(prev.id()))
                        evictNodes(msg.topologyVersion());
                }

                return;
            }

            long tstamp = System.currentTimeMillis();

            if (!msg.metrics().isEmpty() && spiStateCopy() == CONNECTED)
                for (Map.Entry<UUID, GridNodeMetrics> e : msg.metrics().entrySet()) {
                    GridTcpDiscoveryNode node = ring.node(e.getKey());

                    if (node != null) {
                        node.setMetrics(e.getValue());

                        node.lastUpdateTime(tstamp);

                        notifyDiscovery(EVT_NODE_METRICS_UPDATED, ring.topologyVersion(), node);
                    }
                    else
                        if (log.isDebugEnabled())
                            log.debug("Received metrics from unknown node: " + e.getKey());
                }

            if (ring.hasRemoteNodes()) {
                if ((locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() == null ||
                    msg.metrics().get(locNodeId) == null) && spiStateCopy() == CONNECTED)

                    // Message is on its first ring or just created on coordinator.
                    msg.setMetrics(locNodeId, metricsProvider.getMetrics());
                else
                    // Message is on its second ring.
                    msg.removeMetrics(locNodeId);

                if (topStore != null) {
                    long topVer = ring.topologyVersion();

                    if (msg.topologyVersion() > topVer)
                        msg.topologyVersion(topVer);
                }

                sendMessageAcrossRing(msg);
            }
            else {
                locNode.lastUpdateTime(tstamp);

                notifyDiscovery(EVT_NODE_METRICS_UPDATED, ring.topologyVersion(), locNode);

                // If ring does not have remote nodes, evict with current topology version.
                if (topStore != null)
                    evictNodes(ring.topologyVersion());
            }
        }

        /**
         * Processes heartbeat message when working with metrics store.
         *
         * @param msg Heartbeat message.
         */
        private void processHeartbeatMessageMetricsStore(GridTcpDiscoveryHeartbeatMessage msg) {
            assert msg != null;
            assert metricsStore != null;

            assert msg.metrics().isEmpty();

            if (ring.node(msg.creatorNodeId()) == null) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by unknown node [msg=" + msg +
                        ", ring=" + ring + ']');

                return;
            }

            if (isLocalNodeCoordinator() && !locNodeId.equals(msg.creatorNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by non-coordinator node: " + msg);

                return;
            }

            if (!isLocalNodeCoordinator() && locNodeId.equals(msg.creatorNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by local node (node is no more coordinator): " +
                        msg);

                return;
            }

            if (locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() != null) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message that has made full ring pass: " + msg);

                if (topStore != null) {
                    // Check if local node is still coordinator and heartbeat arrived from previous node
                    // to evict nodes properly.
                    GridTcpDiscoveryNode prev = ring.previousNode();

                    if (isLocalNodeCoordinator() && prev != null && msg.senderNodeId().equals(prev.id()))
                        evictNodes(msg.topologyVersion());
                }

                return;
            }

            long tstamp = System.currentTimeMillis();

            try {
                if (spiStateCopy() == CONNECTED) {
                    // Cache metrics in node.
                    GridNodeMetrics metrics = locNode.metrics();

                    if (ring.hasRemoteNodes())
                        // Send metrics to store only if there are remote nodes.
                        metricsStore.updateLocalMetrics(locNodeId, metrics);

                    locNode.lastUpdateTime(tstamp);

                    notifyDiscovery(EVT_NODE_METRICS_UPDATED, ring.topologyVersion(), locNode);
                }
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to update local node metrics in metrics store.", e);
            }

            if (ring.hasRemoteNodes()) {
                if (topStore != null) {
                    long topVer = ring.topologyVersion();

                    if (msg.topologyVersion() > topVer)
                        msg.topologyVersion(topVer);
                }

                sendMessageAcrossRing(msg);
            }
            else if (topStore != null)
                // If ring does not have remote nodes, evict with current topology version.
                evictNodes(ring.topologyVersion());
        }

        /**
         * Processes discard message and discards previously registered pending messages.
         *
         * @param msg Discard message.
         */
        private void processDiscardMessage(GridTcpDiscoveryDiscardMessage msg) {
            assert msg != null;

            GridUuid msgId = msg.msgId();

            assert msgId != null;

            if (isLocalNodeCoordinator()) {
                if (!locNodeId.equals(msg.verifierNodeId()))
                    // Message is not verified or verified by former coordinator.
                    msg.verify(locNodeId);
                else
                    // Discard the message.
                    return;
            }

            if (msg.verified())
                if (pendingMsgs.containsKey(msgId)) {
                    for (Iterator<Map.Entry<GridUuid, GridTcpDiscoveryAbstractMessage>>
                        iterator = pendingMsgs.entrySet().iterator(); iterator.hasNext();) {
                        Map.Entry<GridUuid, GridTcpDiscoveryAbstractMessage> e = iterator.next();

                        iterator.remove();

                        stats.onPendingMessageDiscarded();

                        if (log.isDebugEnabled())
                            log.debug("Removed pending message from map: " + e.getValue());

                        if (msgId.equals(e.getValue().id()))
                            break;
                    }
                }
                else
                    if (log.isDebugEnabled())
                        log.debug("Pending messages map does not contain received id: " + msgId);

            if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
        }

        /**
         * Processes update topology message.
         *
         * @param msg Update topology message.
         */
        private void processUpdateTopologyMessage(GridTcpDiscoveryUpdateTopologyMessage msg) {
            assert msg != null;
            assert topStore != null;

            if (isLocalNodeCoordinator()) {
                if (msg.verified() && msg.senderNodeId() != null) {
                    // Message has finished the pass.
                    addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                    for (GridTcpDiscoveryNode node : ring.allNodes()) {
                        if (!node.visible() && node.topologyVersion() <= msg.topologyVersion())
                            addMessage(new GridTcpDiscoveryNodeAddFinishedMessage(locNodeId, node.id()));

                        if (node.topologyVersion() > msg.topologyVersion())
                            break;
                    }

                    return;
                }

                if (!msg.verified()) {
                    // Init topology version and verify the message.
                    msg.verify(locNodeId);

                    try {
                        msg.topologyVersion(topStore.topologyVersion());
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to get topology version from the store.", e);
                    }
                }
            }

            if (!msg.processed()) {
                long maxTopVerRcvd0 = maxTopVerRcvd;

                if (maxTopVerRcvd < msg.topologyVersion()) {
                    try {
                        long tstamp = System.currentTimeMillis();

                        Collection<GridTcpDiscoveryTopologyStoreNode> nodes = topStore.nodes(maxTopVerRcvd,
                            msg.topologyVersion());

                        stats.onTopologyStoreGetNodes(System.currentTimeMillis() - tstamp);

                        for (GridTcpDiscoveryTopologyStoreNode node : nodes) {
                            if (log.isDebugEnabled())
                                log.debug("Read node from topology store: " + node);

                            GridTcpDiscoveryAbstractMessage msg1 = null;

                            switch (node.state()) {
                                case ONLINE:
                                    GridTcpDiscoveryNode n = (GridTcpDiscoveryNode) node;

                                    // In case VM store is used, forcibly set visible to false.
                                    n.visible(false);

                                    msg1 = new GridTcpDiscoveryNodeAddedMessage(locNodeId, n);

                                    // Save node ID to reference during handshake.
                                    if (node.internalOrder() > locNode.internalOrder())
                                        msg.addNodeId(node.id());

                                    break;

                                case LEAVING:
                                    msg1 = new GridTcpDiscoveryNodeLeftMessage(node.id());

                                    break;

                                case FAILED:
                                    msg1 = new GridTcpDiscoveryNodeFailedMessage(locNodeId, node.id());

                                    break;

                                default:
                                    assert false : "Unexpected node state: " + node.state();
                            }

                            assert node.topologyVersion() > maxTopVerRcvd;

                            msg1.topologyVersion(node.topologyVersion());

                            msg1.verify(locNodeId);

                            addMessage(msg1);

                            maxTopVerRcvd = node.topologyVersion();
                        }
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to get topology snapshot from the store.", e);
                    }

                    assert maxTopVerRcvd >= msg.topologyVersion() : "Unexpected topology version [maxTopVerRcvd=" +
                        maxTopVerRcvd + ", msg=" + msg + ']';
                }

                if (maxTopVerRcvd > maxTopVerRcvd0) {
                    // Add message back to queue, if and only if new nodes have been read from top store.
                    msg.processed(true);

                    addMessage(msg);
                }
            }
            else
                // All topology updates intended by this message have been processed.
                if (ring.hasRemoteNodes())
                    sendMessageAcrossRing(msg);
        }

        /**
         * Evicts nodes from topology store.
         *
         * @param topVer Topology version.
         */
        private void evictNodes(long topVer) {
            assert topStore != null;
            assert topVer >= 0;

            if (topVer == 0 || lastEvictedTopVer >= topVer)
                return;

            try {
                long tstamp = System.currentTimeMillis();

                topStore.evict(topVer);

                stats.onTopologyStoreEvict(System.currentTimeMillis() - tstamp);

                lastEvictedTopVer = topVer;

                if (log.isDebugEnabled())
                    log.debug("Evicted nodes using topology version: " + topVer);
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to evict nodes using topology version: " + topVer, e);
            }
        }

        /**
         * Performs necessary actions on some node leaving topology.
         *
         * @param node Left node.
         * @param topVer Topology version.
         */
        private void onNodeLeft(GridTcpDiscoveryNode node, long topVer) {
            assert node != null;
            assert topVer > 0;

            assert spiStateCopy() == CONNECTED;

            stats.onNodeLeft();

            notifyDiscovery(EVT_NODE_LEFT, topVer, node);

            synchronized (mux) {
                failedNodes.remove(node);

                leavingNodes.remove(node);
            }

            if (isLocalNodeCoordinator()) {
                if (ipFinder.isShared()) {
                    try {
                        ipFinder.unregisterAddresses(Collections.singletonList(node.address()));
                    }
                    catch (GridSpiException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to unregister left node address: " + node);
                    }
                }

                if (metricsStore != null) {
                    try {
                        metricsStore.removeMetrics(Collections.singletonList(node.id()));
                    }
                    catch (GridSpiException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to remove left node metrics from store: " + node.id());
                    }
                }
            }
        }

        /**
         * Performs necessary actions on some nodes failure.
         *
         * @param node Failed node.
         * @param topVer Topology version.
         */
        private void onNodeFailed(GridTcpDiscoveryNode node, long topVer) {
            assert node != null;
            assert topVer > 0;

            assert spiStateCopy() == CONNECTED;

            notifyDiscovery(EVT_NODE_FAILED, topVer, node);

            stats.onNodeFailed();

            synchronized (mux) {
                failedNodes.remove(node);

                leavingNodes.remove(node);
            }

            if (isLocalNodeCoordinator()) {
                if (ipFinder.isShared()) {

                    try {
                        ipFinder.unregisterAddresses(Collections.singletonList(node.address()));
                    }
                    catch (GridSpiException e) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to unregister failed node address [node=" + node +
                                ", err=" + e.getMessage() + ']');
                    }
                }

                if (metricsStore != null) {
                    Collection<UUID> ids = Collections.singletonList(node.id());

                    try {
                        metricsStore.removeMetrics(ids);
                    }
                    catch (GridSpiException e) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to remove failed node metrics from store [node=" + node +
                                ", err=" + e.getMessage() + ']');
                    }
                }
            }
        }
    }

    /**
     * Thread that accepts incoming TCP connections.
     * <p>
     * Tcp server will call provided closure when accepts incoming connection.
     * From that moment server is no more responsible for the socket.
     */
    private class TcpServer extends GridSpiThread {
        /** Socket TCP server listens to. */
        private ServerSocket srvrSock;

        /** Port to listen. */
        private int port;

        /**
         * Constructor.
         *
         * @throws GridSpiException In case of error.
         */
        private TcpServer() throws GridSpiException {
            super(gridName, "tcp-disco-srvr", log);

            setPriority(threadPri);

            for (port = locPort; port < locPort + locPortRange; port++) {
                try {
                    srvrSock = new ServerSocket(port, 0, locHost);

                    break;
                }
                catch (IOException e) {
                    if (port < locPort + locPortRange - 1) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to bind to local port (will try next port within range) " +
                                "[port=" + port + ", localHost=" + locHost + ']');
                    }
                    else {
                        throw new GridSpiException("Failed to bind TCP server socket (possibly all ports in range " +
                            "are in use) [firstPort=" + locPort + ", lastPort=" + (locPort + locPortRange - 1) +
                            ", addr=" + locHost + ']', e);
                    }
                }
            }

            if (log.isInfoEnabled())
                log.info("Successfully bound to TCP port [port=" + port + ", localHost=" + locHost + ']');
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            try {
                while (!isInterrupted()) {
                    Socket sock = srvrSock.accept();

                    long tstamp = System.currentTimeMillis();

                    if (log.isDebugEnabled())
                        log.debug("Accepted incoming connection from addr: " + sock.getRemoteSocketAddress());

                    SocketReader reader = new SocketReader(sock);

                    synchronized (mux) {
                        readers.add(reader);

                        reader.start();
                    }

                    stats.onServerSocketInitialized(System.currentTimeMillis() - tstamp);
                }
            }
            catch (IOException e) {
                if (!isInterrupted())
                    U.error(log, "Failed to accept TCP connection.", e);
            }
            finally {
                U.closeQuiet(srvrSock);
            }
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            super.interrupt();

            U.close(srvrSock, log);
        }
    }

    /**
     * Thread that reads messages from the socket created for incoming connections.
     */
    private class SocketReader extends GridSpiThread {
        /** Socket to read data from. */
        private final Socket sock;

        /**
         * Constructor.
         *
         * @param sock Socket to read data from.
         */
        private SocketReader(Socket sock) {
            super(gridName, "tcp-disco-sock-reader", log);

            this.sock = sock;

            setPriority(threadPri);

            stats.onSocketReaderCreated();
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            try {
                UUID nodeId;

                try {
                    sock.setKeepAlive(true);

                    // Handshake.
                    GridTcpDiscoveryHandshakeRequest req = readMessage(sock);

                    nodeId = req.creatorNodeId();

                    writeToSocket(sock, new GridTcpDiscoveryHandshakeResponse(locNodeId, locNode.internalOrder()));

                    if (log.isDebugEnabled())
                        log.debug("Initialized connection with remote node: " + nodeId);
                }
                catch (IOException e) {
                    if (!sock.isClosed())
                        U.error(log, "Failed to initialize connection.", e);

                    return;
                }
                catch (GridException e) {
                    if (!sock.isClosed() && !e.hasCause(IOException.class))
                        U.error(log, "Failed to initialize connection.", e);

                    return;
                }

                while (!isInterrupted()) {
                    try {
                        GridTcpDiscoveryAbstractMessage msg = marsh.unmarshal(sock.getInputStream(), dfltClsLdr);

                        msg.senderNodeId(nodeId);

                        if (log.isDebugEnabled())
                            log.debug("Message has been received: " + msg);

                        stats.onMessageReceived(msg);

                        if (msg instanceof GridTcpDiscoveryJoinRequestMessage) {
                            GridTcpDiscoveryJoinRequestMessage req = (GridTcpDiscoveryJoinRequestMessage)msg;

                            if (log.isDebugEnabled())
                                log.debug("Received join request [node=" + req.node() +
                                    ", nodeAttrs=" + req.node().attributes() + ']');

                            // Direct join request requires special processing.
                            if (!req.responded()) {
                                processJoinRequestMessage(req);

                                continue;
                            }
                        }
                        else if (msg instanceof GridTcpDiscoveryDuplicateIdMessage) {
                            // Send receipt back.
                            writeToSocket(sock, RES_OK);

                            GridTcpDiscoveryDuplicateIdMessage msg0 = (GridTcpDiscoveryDuplicateIdMessage)msg;

                            synchronized (mux) {
                                dupIdNode.set(msg0.node());

                                spiState = DUPLICATE_ID;

                                mux.notifyAll();
                            }

                            continue;
                        }

                        msgWorker.addMessage(msg);

                        // Send receipt back.
                        writeToSocket(sock, RES_OK);
                    }
                    catch (GridException e) {
                        if (isInterrupted() || sock.isClosed())
                            return;

                        boolean err = nodeAlive(nodeId) && spiStateCopy() == CONNECTED &&
                            !X.hasCause(e, IOException.class);

                        if (err)
                            U.error(log, "Failed to read message [sock=" + sock + ", locNodeId=" + locNodeId +
                                ", rmtNodeId=" + nodeId + ']', e);
                        else if (log.isDebugEnabled())
                            log.debug("Socket was closed [sock=" + sock + ", locNodeId=" + locNodeId +
                                ", rmtNodeId=" + nodeId + ']');

                        return;
                    }
                    catch (IOException e) {
                        if (isInterrupted() || sock.isClosed())
                            return;

                        boolean err = nodeAlive(nodeId) && spiStateCopy() == CONNECTED;

                        if (err)
                            U.error(log, "Failed to send receipt on message [sock=" + sock +
                                ", locNodeId=" + locNodeId + ", rmtNodeId=" + nodeId + ']', e);
                        else if (log.isDebugEnabled())
                            log.debug("Socket was closed [sock=" + sock + ", locNodeId=" + locNodeId +
                                ", rmtNodeId=" + nodeId + ']');

                        return;
                    }
                }
            }
            finally {
                U.closeQuiet(sock);
            }
        }

        /**
         * @param nodeId Node ID.
         * @return {@code True} if node is in the ring and is not being removed from.
         */
        private boolean nodeAlive(UUID nodeId) {
            // Is node alive or about to be removed from the ring?
            GridTcpDiscoveryNode node = ring.node(nodeId);

            boolean nodeAlive = node != null && node.visible();

            if (nodeAlive) {
                synchronized (mux) {
                    nodeAlive = !F.transform(failedNodes, F.node2id()).contains(nodeId) &&
                        !F.transform(leavingNodes, F.node2id()).contains(nodeId);
                }
            }

            if (nodeAlive && topStore != null) {
                try {
                    long tstamp = System.currentTimeMillis();

                    GridTcpDiscoveryTopologyStoreNodeState state = topStore.state(nodeId);

                    stats.onTopologyStoreGetNodeState(System.currentTimeMillis() - tstamp);

                    nodeAlive = state == ONLINE;
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to get node state from topology store: " + nodeId, e);
                }
            }

            return nodeAlive;
        }

        /**
         * @param msg Join request message.
         * @throws IOException If IO failed.
         */
        @SuppressWarnings({"IfMayBeConditional"})
        private void processJoinRequestMessage(GridTcpDiscoveryJoinRequestMessage msg) throws IOException {
            assert msg != null;
            assert !msg.responded();

            GridTcpDiscoverySpiState state = spiStateCopy();

            if (state == CONNECTED) {
                // Direct join request - socket should be closed after handling.
                try {
                    writeToSocket(sock, RES_OK);

                    if (log.isDebugEnabled())
                        log.debug("Responded to join request message [msg=" + msg + ", res=" + RES_OK + ']');

                    msg.responded(true);

                    msgWorker.addMessage(msg);
                }
                finally {
                    U.closeQuiet(sock);
                }
            }
            else {
                // Direct join request - socket should be closed after handling.
                try {
                    stats.onMessageProcessingStarted(msg);

                    Integer res;

                    if (state == CONNECTING) {
                        if (noResAddrs.contains(msg.node().address()) || locNodeId.compareTo(msg.creatorNodeId()) < 0)
                            // Remote node node has not responded to join request or loses UUID race.
                            res = RES_WAIT;
                        else
                            // Remote node responded to join request and wins UUID race.
                            res = RES_CONTINUE_JOIN;
                    }
                    else
                        // Local node is stopping. Remote node should try next one.
                        res = RES_CONTINUE_JOIN;

                    writeToSocket(sock, res);

                    if (log.isDebugEnabled())
                        log.debug("Responded to join request message [msg=" + msg + ", res=" + res + ']');

                    fromAddrs.add(msg.node().address());

                    stats.onMessageProcessingFinished(msg);
                }
                finally {
                    U.closeQuiet(sock);
                }
            }
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            super.interrupt();

            U.closeQuiet(sock);
        }

        /** {@inheritDoc} */
        @Override protected void cleanup() {
            super.cleanup();

            synchronized (mux) {
                readers.remove(this);
            }

            stats.onSocketReaderRemoved();
        }
    }

    /**
     * Metrics update notifier.
     */
    private class MetricsUpdateNotifier extends GridSpiThread {
        /** Constructor. */
        private MetricsUpdateNotifier() {
            super(gridName, "tcp-disco-metrics-update-notifier", log);

            assert metricsStore != null;

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @SuppressWarnings("BusyWait")
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Metrics update notifier has been started.");

            while (!isInterrupted()) {
                Thread.sleep(metricsStore.getMetricsExpireTime());

                if (spiStateCopy() != CONNECTED) {
                    if (log.isDebugEnabled())
                        log.debug("Stopping metrics update notifier (SPI is not connected to topology).");

                    return;
                }

                long tstamp = System.currentTimeMillis();

                // Event is fired for all nodes in the topology since all alive nodes should update their metrics
                // on time. If it is not so, most probably, nodes have failed and failure will be detected by common
                // failure detection logic.
                for (GridTcpDiscoveryNode node : ring.remoteNodes()) {
                    node.lastUpdateTime(tstamp);

                    notifyDiscovery(EVT_NODE_METRICS_UPDATED, ring.topologyVersion(), node);
                }
            }
        }
    }

    /**
     * Topology store worker is started when node is about to leave the topology.
     * It generates {@link GridTcpDiscoveryNodeLeftMessage} when the state of
     * the local node is changed in the store and then adds the message to message
     * worker.
     */
    private class TopologyStoreWorker extends GridSpiThread {
        /**
         * Constructor.
         */
        private TopologyStoreWorker() {
            super(gridName, "tcp-disco-top-store-worker", log);

            assert topStore != null;

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"BusyWait"})
        @Override protected void body() throws InterruptedException {
            synchronized (mux) {
                while (spiState != STOPPING)
                    mux.wait(netTimeout);
            }

            if (log.isDebugEnabled())
                log.debug("Topology store worker has been started.");

            while (!isInterrupted()) {
                Thread.sleep(2000);

                try {
                    long tstamp = System.currentTimeMillis();

                    GridTcpDiscoveryTopologyStoreNodeState state = topStore.state(locNodeId);

                    stats.onTopologyStoreGetNodeState(System.currentTimeMillis() - tstamp);

                    if (state == LEAVING || state == FAILED || state == null) {
                        // Node has been updated in the store or already evicted.
                        GridTcpDiscoveryNodeLeftMessage nodeLeftMsg = new GridTcpDiscoveryNodeLeftMessage(locNodeId);

                        nodeLeftMsg.verify(locNodeId);

                        msgWorker.addMessage(nodeLeftMsg);

                        if (log.isDebugEnabled())
                            log.debug("Stopping topology store worker (node left message has been added).");

                        return;
                    }
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to get local node state from topology store.", e);
                }
            }
        }
    }

    /**
     * SPI Statistics printer.
     */
    private class StatisticsPrinter extends GridSpiThread {
        /**
         * Constructor.
         */
        private StatisticsPrinter() {
            super(gridName, "tcp-disco-stats-printer", log);

            assert statsPrintFreq > 0;

            assert log.isInfoEnabled() && !log.isQuiet();

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"BusyWait"})
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Statistics printer has been started.");

            while (!isInterrupted()) {
                Thread.sleep(statsPrintFreq);

                printStatistics();
            }
        }
    }

    /**
     * Handles sockets timeouts.
     */
    private class SocketTimeoutWorker extends GridSpiThread {
        /** Time-based sorted set for timeout objects. */
        private final GridConcurrentSkipListSet<SocketTimeoutObject> timeoutObjs =
            new GridConcurrentSkipListSet<SocketTimeoutObject>(new Comparator<SocketTimeoutObject>() {
                @Override public int compare(SocketTimeoutObject o1, SocketTimeoutObject o2) {
                    long time1 = o1.endTime();
                    long time2 = o2.endTime();

                    long id1 = o1.id();
                    long id2 = o2.id();

                    return time1 < time2 ? -1 : time1 > time2 ? 1 :
                        id1 < id2 ? -1 : id1 > id2 ? 1 : 0;
                }
            });

        /** Mutex. */
        private final Object mux0 = new Object();

        /**
         *
         */
        SocketTimeoutWorker() {
            super(gridName, "tcp-disco-sock-timeout-worker", log);

            setPriority(threadPri);
        }

        /**
         * @param timeoutObj Timeout object to add.
         */
        @SuppressWarnings({"NakedNotify"})
        public void addTimeoutObject(SocketTimeoutObject timeoutObj) {
            assert timeoutObj != null && timeoutObj.endTime() > 0 && timeoutObj.endTime() != Long.MAX_VALUE;

            timeoutObjs.add(timeoutObj);

            if (timeoutObjs.firstx() == timeoutObj) {
                synchronized (mux0) {
                    mux0.notifyAll();
                }
            }
        }

        /**
         * @param timeoutObj Timeout object to remove.
         */
        public void removeTimeoutObject(SocketTimeoutObject timeoutObj) {
            assert timeoutObj != null;

            timeoutObjs.remove(timeoutObj);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Socket timeout worker has been started.");

            while (!isInterrupted()) {
                long now = System.currentTimeMillis();

                for (Iterator<SocketTimeoutObject> iter = timeoutObjs.iterator(); iter.hasNext();) {
                    SocketTimeoutObject timeoutObj = iter.next();

                    if (timeoutObj.endTime() <= now) {
                        iter.remove();

                        if (timeoutObj.onTimeout())
                            LT.warn(log, null, "Socket was closed on timeout. Consider changing " +
                                "'sockTimeout' configuration property.");
                    }
                    else
                        break;
                }

                synchronized (mux0) {
                    while (true) {
                        // Access of the first element must be inside of
                        // synchronization block, so we don't miss out
                        // on thread notification events sent from
                        // 'addTimeoutObject(..)' method.
                        SocketTimeoutObject first = timeoutObjs.firstx();

                        if (first != null) {
                            long waitTime = first.endTime() - System.currentTimeMillis();

                            if (waitTime > 0)
                                mux0.wait(waitTime);
                            else
                                break;
                        }
                        else
                            mux0.wait(5000);
                    }
                }
            }
        }
    }

    /**
     *
     */
    private static class SocketTimeoutObject {
        /** */
        private static final AtomicLong idGen = new AtomicLong();

        /** */
        private final long id = idGen.incrementAndGet();

        /** */
        private final Socket sock;

        /** */
        private final long endTime;

        /** */
        private final AtomicBoolean done = new AtomicBoolean();

        /**
         * @param sock Socket.
         * @param endTime End time.
         */
        private SocketTimeoutObject(Socket sock, long endTime) {
            assert sock != null;
            assert endTime > 0;

            this.sock = sock;
            this.endTime = endTime;
        }

        /**
         * @return {@code True} if object has not yet been processed.
         */
        boolean cancel() {
            return done.compareAndSet(false, true);
        }

        /**
         * @return {@code True} if object has not yet been canceled.
         */
        boolean onTimeout() {
            if (done.compareAndSet(false, true)) {
                // Close socket - timeout occurred.
                U.closeQuiet(sock);

                return true;
            }

            return false;
        }

        /**
         * @return End time.
         */
        long endTime() {
            return endTime;
        }

        /**
         * @return ID.
         */
        long id() {
            return id;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SocketTimeoutObject.class, this);
        }
    }
}

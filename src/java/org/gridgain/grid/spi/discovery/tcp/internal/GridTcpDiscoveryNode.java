// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.internal;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.spi.discovery.tcp.*;
import org.gridgain.grid.spi.discovery.tcp.metricsstore.*;
import org.gridgain.grid.spi.discovery.tcp.topologystore.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Node for {@link GridTcpDiscoverySpi}.
 * <p>
 * <strong>This class is not intended for public use</strong> and has been made
 * <tt>public</tt> due to certain limitations of Java technology.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridTcpDiscoveryNode extends GridMetadataAwareAdapter implements GridNode,
    GridTcpDiscoveryTopologyStoreNode, Comparable<GridTcpDiscoveryNode>, Externalizable {
    /** Node ID. */
    private UUID id;

    /** Node attributes. */
    private Map<String, Object> attrs;

    /** Address that is used for discovery. */
    private InetSocketAddress addr;

    /** Internal discovery addresses as strings. */
    @GridToStringInclude
    private Collection<String> strIntAddrs;

    /** Node metrics. */
    @GridToStringExclude
    private volatile GridNodeMetrics metrics;

    /** Node order in the topology. */
    private long order;

    /** Node order in the topology (internal). */
    @GridToStringExclude
    private long intOrder;

    /** The most recent time when heartbeat message was received from the node. */
    @GridToStringExclude
    private volatile long lastUpdateTime = System.currentTimeMillis();

    /** Metrics provider (transient). */
    @GridToStringExclude
    private GridDiscoveryMetricsProvider metricsProvider;

    /** Metrics store (transient). */
    @GridToStringExclude
    private GridTcpDiscoveryMetricsStore metricsStore;

    /** Grid logger (transient). */
    @GridToStringExclude
    private GridLogger log;

    /** Node state (if topology store is used). */
    @GridToStringExclude
    private GridTcpDiscoveryTopologyStoreNodeState state;

    /** Topology version of the node (if topology store is used). */
    @GridToStringExclude
    private long topVer;

    /** Visible flag. */
    @GridToStringExclude
    private boolean visible;

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryNode() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param id Node Id.
     * @param addr IP address that is used for discovery.
     * @param metricsProvider Metrics provider.
     */
    public GridTcpDiscoveryNode(UUID id, InetSocketAddress addr, GridDiscoveryMetricsProvider metricsProvider) {
        assert id != null;
        assert addr != null;
        assert metricsProvider != null;

        this.id = id;
        this.addr = addr;
        this.metricsProvider = metricsProvider;

        metrics = metricsProvider.getMetrics();

        strIntAddrs = Arrays.asList(addr.getAddress().getHostAddress());
    }

    /**
     * Sets metrics store.
     *
     * @param metricsStore Metrics store.
     */
    public void metricsStore(GridTcpDiscoveryMetricsStore metricsStore) {
        assert metricsStore != null;

        this.metricsStore = metricsStore;
    }

    /**
     * Sets log.
     *
     * @param log Grid logger.
     */
    public void logger(GridLogger log) {
        this.log = log;
    }

    /** {@inheritDoc} */
    @Override public UUID id() {
        return id;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <T> T attribute(String name) {
        return (T)attrs.get(name);
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> attributes() {
        return attrs;
    }

    /**
     * Sets node attributes.
     *
     * @param attrs Node attributes.
     */
    public void setAttributes(Map<String, Object> attrs) {
        this.attrs = U.sealMap(new HashMap<String, Object>(attrs));
    }

    /** {@inheritDoc} */
    @Override public GridNodeMetrics metrics() {
        if (metricsProvider != null)
            metrics = metricsProvider.getMetrics();
        else if (metricsStore != null)
            try {
                GridNodeMetrics metrics = metricsStore.metrics(Collections.singletonList(id)).get(id);

                if (metrics != null)
                    this.metrics = metrics;
            }
            catch (GridSpiException e) {
                LT.error(log, e, "Failed to get metrics from metrics store for node: " + this);
            }

        return metrics;
    }

    /**
     * Sets node metrics.
     *
     * @param metrics Node metrics.
     */
    public void setMetrics(GridNodeMetrics metrics) {
        assert metrics != null;

        this.metrics = metrics;
    }

    /**
     * @return Internal order.
     */
    @Override public long internalOrder() {
        return intOrder;
    }

    /**
     * @param intOrder Internal order of the node.
     */
    public void internalOrder(long intOrder) {
        assert intOrder >= 0;

        this.intOrder = intOrder;
    }

    /**
     * @return Order.
     */
    @Override public long order() {
        return order;
    }

    /**
     * @param order Order of the node.
     */
    public void order(long order) {
        assert order > 0;

        this.order = order;
    }

    /**
     * Gets address used for discovery.
     *
     * @return Discovery address.
     */
    public InetSocketAddress address() {
        return addr;
    }

    /** {@inheritDoc} */
    @Override public Collection<String> internalAddresses() {
        return strIntAddrs;
    }

    /** {@inheritDoc} */
    @Override public Collection<String> externalAddresses() {
        return Collections.emptyList();
    }

    /**
     * Gets node last update time.
     *
     * @return Time of the last heartbeat.
     */
    public long lastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Sets node last update.
     *
     * @param lastUpdateTime Time of last metrics update.
     */
    public void lastUpdateTime(long lastUpdateTime) {
        assert lastUpdateTime > 0;

        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * Gets visible flag.
     *
     * @return {@code true} if node is in visible state.
     */
    public boolean visible() {
        return visible;
    }

    /**
     * Sets visible flag.
     *
     * @param visible {@code true} if node is in visible state.
     */
    public void visible(boolean visible) {
        this.visible = visible;
    }

    /** {@inheritDoc} */
    @Override public GridTcpDiscoveryTopologyStoreNodeState state() {
        return state;
    }

    /**
     * Sets node state.
     * <p>
     * This method and the underlying field is used only if topology store is used.
     *
     * @param state Node state.
     */
    public void state(GridTcpDiscoveryTopologyStoreNodeState state) {
        this.state = state;
    }

    /** {@inheritDoc} */
    @Override public long topologyVersion() {
        return topVer;
    }

    /** {@inheritDoc} */
    @Override public void topologyVersion(long topVer) {
        this.topVer = topVer;
    }

    /** {@inheritDoc} */
    @Override public int compareTo(@Nullable GridTcpDiscoveryNode node) {
        if (node == null)
            return 1;

        if (internalOrder() == node.internalOrder())
            assert id().equals(node.id()) : "Duplicate order [this=" + this + ", other=" + node + ']';

        return internalOrder() < node.internalOrder() ? -1 : internalOrder() > node.internalOrder() ? 1 :
            id().compareTo(node.id());
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeUuid(out, id);
        U.writeMap(out, attrs);
        U.writeString(out, addr.getAddress().getHostAddress() + ":" + addr.getPort());

        byte[] mtr = null;

        if (metrics != null) {
            mtr = new byte[GridDiscoveryMetricsHelper.METRICS_SIZE];

            GridDiscoveryMetricsHelper.serialize(mtr, 0, metrics());
        }

        U.writeByteArray(out, mtr);

        out.writeLong(order);
        out.writeLong(intOrder);

        U.writeEnum(out, state);

        out.writeLong(topVer);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        id = U.readUuid(in);

        attrs = U.sealMap(U.<String, Object>readMap(in));

        addr = parseAddress(U.readString(in));

        byte[] mtr = U.readByteArray(in);

        if (mtr != null)
            metrics = GridDiscoveryMetricsHelper.deserialize(mtr, 0);

        order = in.readLong();
        intOrder = in.readLong();

        state = U.readEnum(in, GridTcpDiscoveryTopologyStoreNodeState.class);

        topVer = in.readLong();

        strIntAddrs = Arrays.asList(addr.getAddress().getHostAddress());
    }

    /**
     * Parses address from string.
     *
     * @param addrStr Address in {@code hostAddress:port} format.
     * @return Inet socket address.
     * @throws UnknownHostException If unable to resolve host.
     */
    private InetSocketAddress parseAddress(String addrStr) throws UnknownHostException {
        assert !F.isEmpty(addrStr);

        StringTokenizer st = new StringTokenizer(addrStr, ":");

        assert st.countTokens() == 2;

        return new InetSocketAddress(InetAddress.getByName(st.nextToken()), Integer.parseInt(st.nextToken()));
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id.hashCode();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return F.eqNodes(this, o);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryNode.class, this);
    }
}

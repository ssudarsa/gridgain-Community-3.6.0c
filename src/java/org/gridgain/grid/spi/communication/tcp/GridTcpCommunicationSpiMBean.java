package org.gridgain.grid.spi.communication.tcp;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;

/**
 * MBean provide access to TCP-based communication SPI.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridMBeanDescription("MBean provide access to TCP-based communication SPI.")
public interface GridTcpCommunicationSpiMBean extends GridSpiManagementMBean {
    /**
     * Returns the approximate number of threads that are actively processing
     * NIO tasks.
     *
     * @return Approximate number of threads that are actively processing
     *         NIO tasks.
     */
    @GridMBeanDescription("Approximate number of threads that are actively processing NIO tasks.")
    public int getNioActiveThreadCount();

    /**
     * Returns the approximate total number of NIO tasks that have completed
     * execution. Because the states of tasks and threads may change dynamically
     * during computation, the returned value is only an approximation, but one
     * that does not ever decrease across successive calls.
     *
     * @return Approximate total number of NIO tasks that have completed execution.
     */
    @GridMBeanDescription("Approximate total number of NIO tasks that have completed execution.")
    public long getNioTotalCompletedTaskCount();

    /**
     * Gets current size of the NIO queue size. NIO queue buffers NIO tasks when
     * there are not threads available for processing in the pool.
     *
     * @return Current size of the NIO queue size.
     */
    @GridMBeanDescription("Current size of the NIO queue size.")
    public int getNioTaskQueueSize();

    /**
     * Returns the core number of NIO threads.
     *
     * @return Core number of NIO threads.
     */
    @GridMBeanDescription("Core number of NIO threads.")
    public int getNioCorePoolSize();

    /**
     * Returns the largest number of NIO threads that have ever simultaneously
     * been in the pool.
     *
     * @return Largest number of NIO threads that have ever simultaneously
     *      been in the pool.
     */
    @GridMBeanDescription("Largest number of NIO threads that have ever simultaneously been in the pool.")
    public int getNioLargestPoolSize();

    /**
     * Returns the maximum allowed number of NIO threads.
     *
     * @return Maximum allowed number of NIO threads.
     */
    @GridMBeanDescription("Maximum allowed number of NIO threads.")
    public int getNioMaximumPoolSize();

    /**
     * Returns the current number of NIO threads in the pool.
     *
     * @return Current number of NIO threads in the pool.
     */
    @GridMBeanDescription("Current number of NIO threads in the pool.")
    public int getNioPoolSize();

    /**
     * Returns the approximate total number of NIO tasks that have been scheduled
     * for execution. Because the states of tasks and threads may change dynamically
     * during computation, the returned value is only an approximation, but one that
     * does not ever decrease across successive calls.
     *
     * @return Approximate total number of NIO tasks that have been scheduled for execution.
     */
    @GridMBeanDescription("Approximate total number of NIO tasks that have been scheduled for execution.")
    public long getNioTotalScheduledTaskCount();

    /**
     * Gets local host address for socket binding.
     * Beside loopback address physical node could have
     * several other ones, but only one is assigned to grid node.
     *
     * @return Grid node IP address.
     */
    @GridMBeanDescription("Grid node IP address.")
    public String getLocalAddress();

    /**
     * Gets local port for socket binding.
     *
     * @return Port number.
     */
    @GridMBeanDescription("Port number.")
    public int getLocalPort();

    /**
     * Gets maximum number of local ports tried if all previously
     * tried ports are occupied.
     *
     * @return Local port range.
     */
    @GridMBeanDescription("Local port range.")
    public int getLocalPortRange();

    /**
     * Gets maximum idle connection time upon which idle connections
     * will be closed.
     *
     * @return Maximum idle connection time.
     */
    @GridMBeanDescription("Maximum idle connection time.")
    public long getIdleConnectionTimeout();

    /**
     * Gets flag that indicates whether direct or heap allocated buffer is used.
     *
     * @return Flag that indicates whether direct or heap allocated buffer is used.
     */
    @GridMBeanDescription("Flag that indicates whether direct or heap allocated buffer is used.")
    public boolean isDirectBuffer();

    /**
     * Gets maximum count of simultaneously open TCP clients
     * for one remote node.
     *
     * @return Maximum count of open TCP clients per node.
     */
    @GridMBeanDescription("Maximum count of simultaneously open clients for one remote node.")
    public int getMaxOpenClients();

    /**
     * Gets count of selectors used in TCP server. Default value equals to the
     * number of CPUs available in the system.
     *
     * @return Count of selectors in TCP server.
     */
    @GridMBeanDescription("Count of selectors used in TCP server.")
    public int getSelectorsCount();

    /**
     * Gets number of threads used for handling NIO messages.
     *
     * @return Number of threads used for handling NIO messages.
     */
    @GridMBeanDescription("Number of threads used for handling NIO messages.")
    public int getMessageThreads();

    /**
     * Gets sent messages count.
     *
     * @return Sent messages count.
     */
    @GridMBeanDescription("Sent messages count.")
    public int getSentMessagesCount();

    /**
     * Gets sent bytes count.
     *
     * @return Sent bytes count.
     */
    @GridMBeanDescription("Sent bytes count.")
    public long getSentBytesCount();

    /**
     * Gets received messages count.
     *
     * @return Received messages count.
     */
    @GridMBeanDescription("Received messages count.")
    public int getReceivedMessagesCount();

    /**
     * Gets received bytes count.
     *
     * @return Received bytes count.
     */
    @GridMBeanDescription("Received bytes count.")
    public long getReceivedBytesCount();

    /**
     * Gets port resolver for ports mapping determination.
     *
     * @return Port resolver for ports mapping determination.
     */
    @GridMBeanDescription("Port resolver for ports mapping determination.")
    public GridSpiPortResolver getSpiPortResolver();

    /**
     * Gets connect timeout used when establishing connection
     * with remote nodes.
     *
     * @return Connect timeout.
     */
    @GridMBeanDescription("Connect timeout.")
    public int getConnectTimeout();
}

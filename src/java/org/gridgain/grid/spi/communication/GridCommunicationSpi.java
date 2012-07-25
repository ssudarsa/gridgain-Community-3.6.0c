// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.communication.tcp.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;

/**
 * Communication SPI is responsible for data exchange between nodes.
 * <p>
 * Communication SPI is one of the most important SPI in GridGain. It is used
 * heavily throughout the system and provides means for all data exchanges
 * between nodes, such as internal implementation details and user driven
 * messages.
 * <p>
 * Functionality to this SPI is exposed directly in {@link Grid} interface:
 * <ul>
 *      <li>{@link Grid#send(Object, org.gridgain.grid.lang.GridPredicate[])}</li>
 *      <li>{@link Grid#send(Collection, GridPredicate[])}
 * </ul>
 * <p>
 * GridGain comes with built-in communication SPI implementations:
 * <ul>
 *      <li>{@link GridTcpCommunicationSpi}</li>
 * </ul>
 * <b>NOTE:</b> this SPI (i.e. methods in this interface) should never be used directly. SPIs provide
 * internal view on the subsystem and is used internally by GridGain kernal. In rare use cases when
 * access to a specific implementation of this SPI is required - an instance of this SPI can be obtained
 * via {@link Grid#configuration()} method to check its configuration properties or call other non-SPI
 * methods. Note again that calling methods from this interface on the obtained instance can lead
 * to undefined behavior and explicitly not supported.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridCommunicationSpi extends GridSpi, GridSpiJsonConfigurable {
    /**
     * Sends given message to destination node. Note that characteristics of the
     * exchange such as durability, guaranteed delivery or error notification is
     * dependant on SPI implementation.
     *
     * @param destNode Destination node.
     * @param msg Message to send.
     * @throws GridSpiException Thrown in case of any error during sending the message.
     *      Note that this is not guaranteed that failed communication will result
     *      in thrown exception as this is dependant on SPI implementation.
     */
    public void sendMessage(GridNode destNode, Serializable msg) throws GridSpiException;

    /**
     * Sends given message to destination nodes. Note that characteristics of the
     * exchange such as durability, guaranteed delivery or error notification is
     * dependant on SPI implementation.
     *
     * @param destNodes Destination nodes.
     * @param msg Message to send.
     * @throws GridSpiException Thrown in case of any error during sending the message.
     *      Note that this is not guaranteed that failed communication will result
     *      in thrown exception as this is dependant on SPI implementation.
     */
    public void sendMessage(Collection<? extends GridNode> destNodes, Serializable msg) throws GridSpiException;

    /**
     * Set communication listener.
     *
     * @param lsnr Listener to set or {@code null} to unset the listener.
     */
    @SuppressWarnings("deprecation")
    public void setListener(@Nullable GridMessageListener lsnr);
}

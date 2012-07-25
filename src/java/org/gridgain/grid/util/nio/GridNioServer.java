// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.nio;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * NIO server with improved functionality. There can be several selectors and several reading threads.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridNioServer {
    /** Time, which server will wait before retry operation. */
    private static final long ERR_WAIT_TIME = 2000;

    /** Accept worker thread. */
    private GridThread acceptThread;

    /** Read worker threads. */
    private GridThread[] readThreads;

    /** Read workers. */
    private final GridNioReadWorker[] readWorkers;

    /** Message listener. */
    private final GridNioServerListener listener;

    /** Logger. */
    private final GridLogger log;

    /** Grid name. */
    private final String gridName;

    /** Worker pool. */
    private final GridWorkerPool workerPool;

    /** Closed flag. */
    private volatile boolean closed;

    /** Flag indicating if this server should use direct buffers. */
    private boolean directBuf;

    /** Address, to which this server is bound. */
    private InetAddress addr;

    /** Port, to which this server is bound. */
    private int port;

    /** If true, listener will be notified within NIO thread */
    private final boolean syncNotification;

    /** Index to select which thread will serve next socket channel. Using round-robin balancing. */
    private int balanceIdx;

    /**
     *
     * @param addr Address.
     * @param port Port.
     * @param listener Listener.
     * @param log Log.
     * @param exec Executor.
     * @param selectorCnt Count of selectors and selecting threads.
     * @param gridName Grid name.
     * @param directBuf Direct buffer flag.
     * @param syncNotification {@code true} if listener should be notified within NIO thread.
     * @throws GridException If failed.
     */
    public GridNioServer(InetAddress addr, int port, GridNioServerListener listener, GridLogger log, Executor exec,
        int selectorCnt, String gridName, boolean directBuf, boolean syncNotification) throws GridException {
        assert addr != null;
        assert port > 0 && port < 0xffff;
        assert listener != null;
        assert log != null;
        assert exec != null;
        assert selectorCnt > 0;

        this.listener = listener;
        this.log = log;
        this.gridName = gridName;
        this.directBuf = directBuf;
        this.syncNotification = syncNotification;

        workerPool = new GridWorkerPool(exec, log);

        // This method will throw exception if address already in use.
        Selector acceptSelector = createSelector(addr, port);

        // Once bind, we will not change the port in future.
        this.addr = addr;
        this.port = port;

        acceptThread = new GridThread(new GridNioAcceptWorker(gridName, "nio-acceptor", log, acceptSelector));

        readWorkers = new GridNioReadWorker[selectorCnt];
        readThreads = new GridThread[selectorCnt];

        for (int i = 0; i < readWorkers.length; i++) {
            Selector readSelector = createSelector(null, 0);

            readWorkers[i] = new GridNioReadWorker(gridName, "nio-reader-" + i, log, readSelector);

            readThreads[i] = new GridThread(readWorkers[i]);
        }
    }

    /**
     * Starts all associated threads to perform accept and read activities.
     */
    public void start() {
        acceptThread.start();

        for (GridThread thread : readThreads)
            thread.start();
    }

    /**
     * Closes all threads.
     */
    public void stop() {
        if (!closed) {
            closed = true;

            acceptThread.interrupt();

            for (GridThread thread : readThreads)
                thread.interrupt();

            U.join(acceptThread, log);

            U.joinThreads(Arrays.asList(readThreads), log);

            workerPool.join(false);
        }
    }

    /**
     * Creates selector and binds server socket to a given address and port. If address is null
     * then will not bind any address and just creates a selector.
     *
     * @param addr Local address to listen on.
     * @param port Local port to listen on.
     * @return Created selector.
     * @throws GridException If selector could not be created or port is already in use.
     */
    private Selector createSelector(@Nullable InetAddress addr, int port) throws GridException {
        Selector selector = null;

        ServerSocketChannel srvrCh = null;

        try {
            // Create a new selector
            selector = SelectorProvider.provider().openSelector();

            if (addr != null) {
                // Create a new non-blocking server socket channel
                srvrCh = ServerSocketChannel.open();

                srvrCh.configureBlocking(false);

                // Bind the server socket to the specified address and port
                srvrCh.socket().bind(new InetSocketAddress(addr, port));

                // Register the server socket channel, indicating an interest in
                // accepting new connections
                srvrCh.register(selector, SelectionKey.OP_ACCEPT);
            }

            return selector;
        }
        catch (IOException e) {
            U.close(srvrCh, log);
            U.close(selector, log);

            throw new GridException("Failed to initialize NIO selector.", e);
        }
    }

    /**
     * Adds registration request for a given socket channel to the next selector. Next selector
     * is selected according to a round-robin algorithm.
     *
     * @param sockCh Socket channel to be registered on one of the selectors.
     */
    private void addRegistrationReq(SocketChannel sockCh) {
        readWorkers[balanceIdx].offer(sockCh);

        balanceIdx++;

        if (balanceIdx == readWorkers.length)
            balanceIdx = 0;
    }

    /**
     * Thread performing only read operations from the channel.
     */
    private class GridNioReadWorker extends GridWorker {
        /** Channel registration requests for this handler. */
        private GridConcurrentLinkedDeque<SocketChannel> registrationRequests =
            new GridConcurrentLinkedDeque<SocketChannel>();

        /** Buffer for reading. */
        private final ByteBuffer readBuf;

        /** Selector to select read events. */
        private Selector selector;

        /**
         * @param gridName Grid name.
         * @param name Worker name.
         * @param log Logger.
         * @param selector Read selector.
         */
        protected GridNioReadWorker(String gridName, String name, GridLogger log, Selector selector) {
            super(gridName, name, log);

            this.selector = selector;

            readBuf = directBuf ? ByteBuffer.allocateDirect(8 << 10) : ByteBuffer.allocate(8 << 10);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            boolean reset = false;

            while (!closed) {
                try {
                    if (reset)
                        selector = createSelector(null, 0);

                    read();
                }
                catch (GridException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        U.error(log, "Failed to read data from remote connection (will wait for " +
                            ERR_WAIT_TIME + "ms).", e);

                        U.sleep(ERR_WAIT_TIME);

                        reset = true;
                    }
                }
            }
        }

        /**
         * Adds socket channel to the registration queue and wakes up reading thread.
         *
         * @param sockCh Socket channel to be registered for read by this thread.
         */
        private void offer(SocketChannel sockCh) {
            registrationRequests.offer(sockCh);

            selector.wakeup();
        }

        /**
         * Processes read events and registration requests.
         *
         * @throws GridException If IOException occurred or thread was unable to add worker to workers pool.
         */
        private void read() throws GridException {
            try {
                while (!closed && selector.isOpen()) {
                    // Wake up every 2 seconds to check if closed.
                    if (selector.select(2000) > 0)
                        // Walk through the ready keys collection and process network events.
                        processSelectedKeys(selector.selectedKeys());

                    SocketChannel sockCh;

                    while ((sockCh = registrationRequests.poll()) != null) {
                        try {
                            sockCh.register(selector, SelectionKey.OP_READ, new GridNioServerBuffer());
                        }
                        catch (ClosedChannelException e) {
                            log().warning("Client connection was unexpectedly closed: " + sockCh.socket()
                                .getRemoteSocketAddress(), e);
                        }
                    }
                }
            }
            // Ignore this exception as thread interruption is equal to 'close' call.
            catch (ClosedByInterruptException e) {
                if (log.isDebugEnabled())
                    log.debug("Closing selector due to thread interruption: " + e.getMessage());
            }
            catch (ClosedSelectorException e) {
                throw new GridException("Selector got closed while active.", e);
            }
            catch (IOException e) {
                throw new GridException("Failed to select events on selector.", e);
            }
            finally {
                if (selector.isOpen()) {
                    if (log.isDebugEnabled())
                        log.debug("Closing all connected client sockets.");

                    // Close all channels registered with selector.
                    for (SelectionKey key : selector.keys())
                        U.close(key.channel(), log);

                    if (log.isDebugEnabled())
                        log.debug("Closing NIO selector.");

                    U.close(selector, log);
                }
            }
        }

        /**
         * Processes keys selected by a selector.
         *
         * @param keys Selected keys.
         * @throws GridException If executor has thrown an exception.
         * @throws ClosedByInterruptException If this thread was interrupted while reading data.
         */
        private void processSelectedKeys(Set<SelectionKey> keys) throws GridException, ClosedByInterruptException {
            for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
                SelectionKey key = iter.next();

                iter.remove();

                // Was key closed?
                if (!key.isValid())
                    continue;

                if (key.isReadable()) {
                    SocketChannel sockCh = (SocketChannel)key.channel();

                    SocketAddress rmtAddr = sockCh.socket().getRemoteSocketAddress();

                    try {
                        // Reset buffer to read bytes up to its capacity.
                        readBuf.clear();

                        // Attempt to read off the channel
                        int cnt = sockCh.read(readBuf);

                        if (log.isDebugEnabled())
                            log.debug("Read bytes from client socket [cnt=" + cnt +
                                ", rmtAddr=" + rmtAddr + ']');

                        if (cnt == -1) {
                            if (log.isDebugEnabled())
                                log.debug("Remote client closed connection: " + rmtAddr);

                            U.close(key, log);

                            continue;
                        }
                        else if (cnt == 0)
                            continue;

                        // Sets limit to current position and
                        // resets position to 0.
                        readBuf.flip();

                        GridNioServerBuffer nioBuf = (GridNioServerBuffer)key.attachment();

                        // We have size let's test if we have object
                        while (readBuf.remaining() > 0) {
                            // Always write into the buffer.
                            nioBuf.read(readBuf);

                            if (nioBuf.isFilled()) {
                                if (log.isDebugEnabled())
                                    log.debug("Read full message from client socket: " + rmtAddr);

                                // Copy array so we can keep reading into the same buffer.
                                final byte[] data = nioBuf.getMessageBytes().getArray();

                                nioBuf.reset();

                                if (syncNotification)
                                    listener.onMessage(data);
                                else {
                                    workerPool.execute(new GridWorker(gridName, "grid-nio-worker", log) {
                                        @Override protected void body() {
                                            listener.onMessage(data);
                                        }
                                    });
                                }
                            }
                        }
                    }
                    catch (ClosedByInterruptException e) {
                        // This exception will be handled below.
                        throw e;
                    }
                    catch (IOException e) {
                        if (!closed) {
                            U.error(log, "Failed to read data from client: " + rmtAddr, e);

                            U.close(key, log);
                        }
                    }
                }
            }
        }
    }

    /**
     * A separate thread that will accept incoming connections and schedule read to some worker.
     */
    private class GridNioAcceptWorker extends GridWorker {
        /** Selector for this thread. */
        private Selector selector;

        /**
         * @param gridName Grid name.
         * @param name Thread name.
         * @param log Log.
         * @param selector Which will accept incoming connections.
         */
        protected GridNioAcceptWorker(String gridName, String name, GridLogger log, Selector selector) {
            super(gridName, name, log);

            this.selector = selector;
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            boolean reset = false;

            while (!closed && !Thread.currentThread().isInterrupted()) {
                try {
                    if (reset)
                        selector = createSelector(addr, port);

                    accept();
                }
                catch (GridException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        U.error(log, "Failed to accept remote connection (will wait for " + ERR_WAIT_TIME + "ms).", e);

                        U.sleep(ERR_WAIT_TIME);

                        reset = true;
                    }
                }
            }
        }

        /**
         * Accepts connections and schedules them for processing by one of read workers.
         *
         * @throws GridException If failed.
         */
        private void accept() throws GridException {
            try {
                while (!closed && selector.isOpen() && !Thread.currentThread().isInterrupted()) {
                    // Wake up every 2 seconds to check if closed.
                    if (selector.select(2000) > 0)
                        // Walk through the ready keys collection and process date requests.
                        processSelectedKeys(selector.selectedKeys());
                }
            }
            // Ignore this exception as thread interruption is equal to 'close' call.
            catch (ClosedByInterruptException e) {
                if (log.isDebugEnabled())
                    log.debug("Closing selector due to thread interruption: " + e.getMessage());
            }
            catch (ClosedSelectorException e) {
                throw new GridException("Selector got closed while active.", e);
            }
            catch (IOException e) {
                throw new GridException("Failed to accept connection.", e);
            }
            finally {
                if (selector.isOpen()) {
                    if (log.isDebugEnabled())
                        log.debug("Closing all listening sockets.");

                    // Close all channels registered with selector.
                    for (SelectionKey key : selector.keys())
                        U.close(key.channel(), log);

                    if (log.isDebugEnabled())
                        log.debug("Closing NIO selector.");

                    U.close(selector, log);
                }
            }
        }

        /**
         * Processes selected accept requests for server socket.
         *
         * @param keys Selected keys from acceptor.
         * @throws IOException If accept failed or IOException occurred while configuring channel.
         */
        private void processSelectedKeys(Set<SelectionKey> keys) throws IOException {
            for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
                SelectionKey key = iter.next();

                iter.remove();

                // Was key closed?
                if (!key.isValid())
                    continue;

                if (key.isAcceptable()) {
                    // The key indexes into the selector so we
                    // can retrieve the socket that's ready for I/O
                    ServerSocketChannel srvrCh = (ServerSocketChannel)key.channel();

                    SocketChannel sockCh = srvrCh.accept();

                    sockCh.configureBlocking(false);

                    if (log.isDebugEnabled())
                        log.debug("Accepted new client connection: " + sockCh.socket().getRemoteSocketAddress());

                    addRegistrationReq(sockCh);
                }
            }
        }
    }
}

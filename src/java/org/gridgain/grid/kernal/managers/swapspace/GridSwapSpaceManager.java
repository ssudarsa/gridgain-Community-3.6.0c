// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.swapspace;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridSwapSpaceManager extends GridManagerAdapter<GridSwapSpaceSpi> {
    /** Local node ID. */
    private UUID locNodeId;

    /** */
    private final ClassLoader dfltLdr = GridSwapSpaceManager.class.getClassLoader();

    /** */
    private final EventWorker evtWrk = new EventWorker();

    /** */
    private GridThread evtWrkThread;

    /**
     * @param ctx Grid kernal context.
     */
    public GridSwapSpaceManager(GridKernalContext ctx) {
        super(GridSwapSpaceSpi.class, ctx, ctx.config().getSwapSpaceSpi());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        evtWrkThread = new GridThread(evtWrk);

        evtWrkThread.start();

        getSpi().setListener(new GridSwapSpaceSpiListener() {
            @Override public void onSwapEvent(int evtType, @Nullable String spaceName, @Nullable byte[] keyBytes) {
                evtWrk.addEvent(evtType, spaceName, keyBytes);
            }
        });

        startSpi();

        locNodeId = ctx.localNodeId();

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override protected void onKernalStop0(boolean cancel, boolean wait) {
        getSpi().setListener(null);

        U.cancel(evtWrk);
        U.join(evtWrkThread, log);
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        stopSpi();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * Reads value from swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param ldr Class loader (optional).
     * @return Value.
     * @throws GridException If failed.
     */
    @Nullable public byte[] read(@Nullable String space, GridSwapKey key, @Nullable ClassLoader ldr)
        throws GridException {
        assert key != null;

        try {
            return getSpi().read(space, key, context(ldr));
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to read from swap space [space=" + space + ", key=" + key + ']', e);
        }
    }

    /**
     * Reads value from swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param ldr Class loader (optional).
     * @return Value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public <T> T read(@Nullable String space, Object key, @Nullable ClassLoader ldr) throws GridException {
        assert key != null;

        return (T)unmarshal(read(space, new GridSwapKey(key), ldr), null);
    }

    /**
     * Writes value to swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param val Value.
     * @param ldr Class loader (optional).
     * @throws GridException If failed.
     */
    public void write(@Nullable String space, GridSwapKey key, byte[] val, @Nullable ClassLoader ldr)
        throws GridException {
        assert key != null;
        assert val != null;

        try {
            getSpi().store(space, key, val, context(ldr));
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to write to swap space [space=" + space + ", key=" + key +
                ", val=" + val + ']', e);
        }
    }

    /**
     * Writes value to swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param val Value.
     * @param ldr Class loader (optional).
     * @throws GridException If failed.
     */
    public void write(@Nullable String space, Object key, @Nullable Object val, @Nullable ClassLoader ldr)
        throws GridException {
        assert key != null;

        write(space, new GridSwapKey(key), marshal(val), ldr);
    }

    /**
     * Removes value from swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param c Optional closure that takes removed value and executes after actual
     *      removing. If there was no value in storage the closure is executed given
     *      {@code null} value as parameter.
     * @param ldr Class loader (optional).
     * @throws GridException If failed.
     */
    public void remove(@Nullable String space, GridSwapKey key, @Nullable GridInClosure<byte[]> c,
        @Nullable ClassLoader ldr) throws GridException {
        assert key != null;

        try {
            getSpi().remove(space, key, c, context(ldr));
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to remove from swap space [space=" + space + ", key=" + key + ']', e);
        }
    }

    /**
     * Removes value from swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param c Optional closure that takes removed value and executes after actual
     *      removing. If there was no value in storage the closure is executed given
     *      {@code null} value as parameter.
     * @param ldr Class loader (optional).
     * @throws GridException If failed.
     */
    public void remove(@Nullable String space, Object key, @Nullable GridInClosure<byte[]> c,
        @Nullable ClassLoader ldr) throws GridException {
        assert key != null;

        remove(space, new GridSwapKey(key), c, ldr);
    }

    /**
     * @param space Space name.
     * @return Swap size.
     * @throws GridException If failed.
     */
    public long swapSize(@Nullable String space) throws GridException {
        assert space != null;

        try {
            return getSpi().size(space);
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to get swap size for space: " + space, e);
        }
    }

    /**
     * @param space Space name.
     * @throws GridException If failed.
     */
    public void clear(@Nullable String space) throws GridException {
        assert space != null;

        try {
            getSpi().clear(space);
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to clear swap space [space=" + space + ']', e);
        }
    }

    /**
     * @param swapBytes Swap bytes to unmarshal.
     * @param ldr Class loader.
     * @return Unmarshalled value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    private <T> T unmarshal(byte[] swapBytes, @Nullable ClassLoader ldr) throws GridException {
        return (T)U.unmarshal(ctx.config().getMarshaller(), new GridByteArrayList(swapBytes),
            ldr != null ? ldr : dfltLdr);
    }

    /**
     * Marshals object.
     *
     * @param obj Object to marshal.
     * @return Marshalled array.
     * @throws GridException If failed.
     */
    private byte[] marshal(Object obj) throws GridException {
        return U.marshal(ctx.config().getMarshaller(), obj).getEntireArray();
    }

    /**
     * @param clsLdr Class loader.
     * @return Swap context.
     */
    private GridSwapContext context(@Nullable ClassLoader clsLdr) {
        GridSwapContext ctx = new GridSwapContext();

        ctx.classLoader(clsLdr != null ? clsLdr : dfltLdr);

        return ctx;
    }

    /**
     *
     */
    private class EventWorker extends GridWorker {
        /** */
        private final BlockingQueue<GridTuple3<Integer, String, byte[]>> evts =
            new LinkedBlockingDeque<GridTuple3<Integer, String, byte[]>>();

        /**
         *
         */
        private EventWorker() {
            super(ctx.gridName(), "swap-mgr-evt-worker", log);
        }

        /**
         * @param evtType Event type.
         * @param spaceName Space name (can be {@code null}).
         * @param keyBytes Key bytes (for evicted entries only).
         */
        void addEvent(Integer evtType, @Nullable String spaceName, @Nullable byte[] keyBytes) {
            evts.add(F.t(evtType, spaceName, keyBytes));
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            while (!isCancelled()) {
                GridTuple3<Integer, String, byte[]> t = evts.take();

                recordEvent(t.get1(), t.get2());

                // TODO: notify grid cache query manager on entry evict.
            }
        }

        /**
         * Creates and records swap space event.
         *
         * @param evtType Event type.
         * @param space Swap space name.
         */
        private void recordEvent(int evtType, @Nullable String space) {
            if (ctx.event().isRecordable(evtType)) {
                String msg = null;

                switch (evtType) {
                    case EVT_SWAP_SPACE_DATA_READ: {
                        msg = "Swap space data read [space=" + space + ']';

                        break;
                    }

                    case EVT_SWAP_SPACE_DATA_STORED: {
                        msg = "Swap space data stored [space=" + space + ']';

                        break;
                    }

                    case EVT_SWAP_SPACE_DATA_REMOVED: {
                        msg = "Swap space data removed [space=" + space + ']';

                        break;
                    }

                    case EVT_SWAP_SPACE_CLEARED: {
                        msg = "Swap space cleared [space=" + space + ']';

                        break;
                    }

                    case EVT_SWAP_SPACE_DATA_EVICTED: {
                        msg = "Swap entry evicted [space=" + space + ']';

                        break;
                    }

                    default: {
                        assert false : "Unknown event type: " + evtType;
                    }
                }

                ctx.event().record(new GridSwapSpaceEvent(locNodeId, msg, evtType, space));
            }
        }
    }
}

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.checkpoint;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.checkpoint.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;

/**
 * This class defines a checkpoint manager.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "deprecation"})
public class GridCheckpointManager extends GridManagerAdapter<GridCheckpointSpi> {
    /** */
    private final GridMessageListener lsnr = new CheckpointRequestListener();

    /** */
    private final ConcurrentMap<GridUuid, CheckpointSet> keyMap = new ConcurrentHashMap<GridUuid, CheckpointSet>();

    /** Grid marshaller. */
    private final GridMarshaller marshaller;

    /**
     * @param ctx Grid kernal context.
     */
    public GridCheckpointManager(GridKernalContext ctx) {
        super(GridCheckpointSpi.class, ctx, ctx.config().getCheckpointSpi());

        marshaller = ctx.config().getMarshaller();
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        for (GridCheckpointSpi spi : getSpis()) {
            spi.setCheckpointListener(new GridCheckpointListener() {
                @Override public void onCheckpointRemoved(String key) {
                    record(EVT_CHECKPOINT_REMOVED, key);
                }
            });
        }

        startSpi();

        ctx.io().addMessageListener(TOPIC_CHECKPOINT, lsnr);

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        GridIoManager comm = ctx.io();

        if (comm != null)
            comm.removeMessageListener(TOPIC_CHECKPOINT, lsnr);

        stopSpi();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * @return Session IDs.
     */
    public Collection<GridUuid> sessionIds() {
        return new ArrayList<GridUuid>(keyMap.keySet());
    }

    /**
     * @param ses Task session.
     * @param key Checkpoint key.
     * @param state Checkpoint state to save.
     * @param scope Checkpoint scope.
     * @param timeout Checkpoint timeout.
     * @param override Whether or not override checkpoint if it already exists.
     * @return {@code true} if checkpoint has been actually saved, {@code false} otherwise.
     * @throws GridException Thrown in case of any errors.
     */
    public boolean storeCheckpoint(GridTaskSessionInternal ses, String key, Object state, GridTaskSessionScope scope,
        long timeout, boolean override) throws GridException {
        assert ses != null;
        assert key != null;

        long now = System.currentTimeMillis();

        boolean saved = false;

        try {
            switch (scope) {
                case GLOBAL_SCOPE: {
                    byte[] data = state == null ? null : U.marshal(marshaller, state).getArray();

                    saved = getSpi(ses.getCheckpointSpi()).saveCheckpoint(key, data, timeout, override);

                    if (saved)
                        record(EVT_CHECKPOINT_SAVED, key);

                    break;
                }

                case SESSION_SCOPE: {
                    if (now > ses.getEndTime()) {
                        U.warn(log, "Checkpoint will not be saved due to session timeout [key=" + key +
                            ", val=" + state + ", ses=" + ses + ']',
                            "Checkpoint will not be saved due to session timeout.");

                        break;
                    }

                    if (now + timeout > ses.getEndTime() || now + timeout < 0)
                        timeout = ses.getEndTime() - now;

                    // Save it first to avoid getting null value on another node.
                    byte[] data = state == null ? null : U.marshal(marshaller, state).getArray();

                    Set<String> keys = keyMap.get(ses.getId());

                    if (log.isDebugEnabled())
                        log.debug("Resolved keys for session [keys=" + keys + ", ses=" + ses +
                            ", keyMap=" + keyMap + ']');

                    // Note: Check that keys exists because session may be invalidated during saving
                    // checkpoint from GridFuture.
                    if (keys != null) {
                        saved = getSpi(ses.getCheckpointSpi()).saveCheckpoint(key, data, timeout, override);

                        if (saved) {
                            keys.add(key);

                            if (ses.getJobId() != null) {
                                GridNode node = ctx.discovery().node(ses.getTaskNodeId());

                                if (node != null)
                                    ctx.io().send(
                                        node,
                                        TOPIC_CHECKPOINT,
                                        new GridCheckpointRequest(ses.getId(), key, ses.getCheckpointSpi()),
                                        GridIoPolicy.PUBLIC_POOL);
                            }

                            record(EVT_CHECKPOINT_SAVED, key);
                        }
                    }
                    else
                        U.warn(log, "Checkpoint will not be saved due to session invalidation [key=" + key +
                            ", val=" + state + ", ses=" + ses + ']',
                            "Checkpoint will not be saved due to session invalidation.");

                    break;
                }

                default:
                    assert false : "Unknown checkpoint scope: " + scope;
            }
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to save checkpoint [key=" + key + ", val=" + state + ", scope=" +
                scope + ", timeout=" + timeout + ']', e);
        }

        return saved;
    }

    /**
     * @param key Checkpoint key.
     * @return Whether or not checkpoint was removed.
     */
    public boolean removeCheckpoint(String key) {
        assert key != null;

        boolean rmv = false;

        for (GridCheckpointSpi spi : getSpis())
            if (spi.removeCheckpoint(key))
                rmv = true;

        return rmv;
    }

    /**
     * @param ses Task session.
     * @param key Checkpoint key.
     * @return Whether or not checkpoint was removed.
     */
    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    public boolean removeCheckpoint(GridTaskSessionInternal ses, String key) {
        assert ses != null;
        assert key != null;

        Set<String> keys = keyMap.get(ses.getId());

        boolean rmv = false;

        // Note: Check that keys exists because session may be invalidated during removing
        // checkpoint from GridFuture.
        if (keys != null) {
            keys.remove(key);

            rmv = getSpi(ses.getCheckpointSpi()).removeCheckpoint(key);
        }
        else
            U.warn(log, "Checkpoint will not be removed due to session invalidation [key=" + key +
                ", ses=" + ses + ']', "Checkpoint will not be removed due to session invalidation.");

        return rmv;
    }

    /**
     * @param ses Task session.
     * @param key Checkpoint key.
     * @return Loaded checkpoint.
     * @throws GridException Thrown in case of any errors.
     */
    @Nullable public Serializable loadCheckpoint(GridTaskSessionInternal ses, String key) throws GridException {
        assert ses != null;
        assert key != null;

        try {
            byte[] data = getSpi(ses.getCheckpointSpi()).loadCheckpoint(key);

            Serializable state = null;

            // Always deserialize with task/session class loader.
            if (data != null)
                state = U.unmarshal(marshaller, new GridByteArrayList(data), ses.getClassLoader());

            record(EVT_CHECKPOINT_LOADED, key);

            return state;
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to load checkpoint: " + key, e);
        }
    }

    /**
     * @param ses Task session.
     */
    @SuppressWarnings({"TypeMayBeWeakened", "MismatchedQueryAndUpdateOfCollection"})
    public void onSessionStart(GridTaskSessionInternal ses) {
        Set<String> keys = keyMap.get(ses.getId());

        if (keys == null)
            keyMap.putIfAbsent(ses.getId(), new CheckpointSet(ses.session()));

        if (log.isDebugEnabled())
            log.debug("Session started signal processed [ses=" + ses + ", keyMap=" + keyMap + ']');
    }

    /**
     * @param ses Task session.
     * @param cleanup Whether cleanup or not.
     */
    public void onSessionEnd(GridTaskSessionInternal ses, boolean cleanup) {
        // If on task node.
        if (ses.getJobId() == null) {
            Set<String> keys = keyMap.remove(ses.getId());

            if (keys != null)
                for (String key : new HashSet<String>(keys))
                    getSpi(ses.getCheckpointSpi()).removeCheckpoint(key);
        }
        // If on job node.
        else if (cleanup) {
            // Clean up memory.
            CheckpointSet keys = keyMap.get(ses.getId());

            // Make sure that we don't remove checkpoint set that
            // was created by newly created session.
            if (keys != null && keys.session() == ses.session()) {
                if (!keyMap.remove(ses.getId(), keys))
                    keys = null;
            }
            else
                keys = null;

            if (keys != null)
                for (String key : keys)
                    getSpi(ses.getCheckpointSpi()).removeCheckpoint(key);
        }
    }

    /**
     * @param type Event type.
     * @param key Checkpoint key.
     */
    private void record(int type, String key) {
        if (ctx.event().isRecordable(type)) {
            String msg;

            if (type == EVT_CHECKPOINT_SAVED)
                msg = "Checkpoint saved: " + key;
            else if (type == EVT_CHECKPOINT_LOADED)
                msg = "Checkpoint loaded: " + key;
            else {
                assert type == EVT_CHECKPOINT_REMOVED : "Invalid event type: " + type;

                msg = "Checkpoint removed: " + key;
            }

            ctx.event().record(new GridCheckpointEvent(ctx.config().getNodeId(), msg, type, key));
        }
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        X.println(">>>");
        X.println(">>> Checkpoint manager memory stats [grid=" + ctx.gridName() + ']');
        X.println(">>>  keyMap: " + keyMap.size());
    }

    /**
     * Checkpoint set.
     */
    private static class CheckpointSet extends GridConcurrentHashSet<String> {
        /** Session. */
        @GridToStringInclude
        private final GridTaskSessionInternal ses;

        /**
         * @param ses Session.
         */
        private CheckpointSet(GridTaskSessionInternal ses) {
            this.ses = ses;
        }

        /**
         * @return Session.
         */
        GridTaskSessionInternal session() {
            return ses;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(CheckpointSet.class, this);
        }
    }

    /** */
    private class CheckpointRequestListener implements GridMessageListener {
        /**
         * @param nodeId ID of the node that sent this message.
         * @param msg Received message.
         */
        @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
        @Override public void onMessage(UUID nodeId, Object msg) {
            GridCheckpointRequest req = (GridCheckpointRequest)msg;

            if (log.isDebugEnabled())
                log.debug("Received checkpoint request: " + req);

            Set<String> keys = keyMap.get(req.getSessionId());

            if (keys != null)
                keys.add(req.getKey());
            // Session is over, simply remove stored checkpoint.
            else
                getSpi(req.getCheckpointSpi()).removeCheckpoint(req.getKey());
        }
    }
}

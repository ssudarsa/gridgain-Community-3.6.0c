// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.eventstorage;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Event storage message.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class GridEventStorageMessage implements Serializable {
    /** */
    private final String resTopic;

    /** */
    private final GridByteArrayList filter;

    /** */
    private final Collection<GridEvent> evts;

    /** */
    private final Throwable ex;

    /** */
    private final GridUuid clsLdrId;

    /** */
    private final GridDeploymentMode depMode;

    /** */
    private final String filterClsName;

    /** */
    private final long seqNum;

    /** */
    private final String userVer;

    /** Node class loader participants. */
    @GridToStringInclude
    private Map<UUID, GridTuple2<GridUuid, Long>> ldrParties;

    /**
     * @param resTopic Response topic,
     * @param filter Query filter.
     * @param filterClsName Filter class name.
     * @param clsLdrId Class loader ID.
     * @param depMode Deployment mode.
     * @param seqNum Sequence number.
     * @param userVer User version.
     * @param ldrParties Node loader participant map.
     */
    GridEventStorageMessage(
        String resTopic,
        GridByteArrayList filter,
        String filterClsName,
        GridUuid clsLdrId,
        GridDeploymentMode depMode,
        long seqNum,
        String userVer,
        Map<UUID, GridTuple2<GridUuid, Long>> ldrParties) {
        this.resTopic = resTopic;
        this.filter = filter;
        this.filterClsName = filterClsName;
        this.depMode = depMode;
        this.seqNum = seqNum;
        this.clsLdrId = clsLdrId;
        this.userVer = userVer;
        this.ldrParties = ldrParties;

        evts = null;
        ex = null;
    }

    /**
     * @param evts Grid events.
     * @param ex Exception occurred during processing.
     */
    GridEventStorageMessage(Collection<GridEvent> evts, Throwable ex) {
        this.evts = evts;
        this.ex = ex;

        resTopic = null;
        filter = null;
        filterClsName = null;
        depMode = null;
        seqNum = 0;
        clsLdrId = null;
        userVer = null;
    }

    /**
     * @return Response topic.
     */
    String responseTopic() {
        return resTopic;
    }

    /**
     * @return Filter.
     */
    GridByteArrayList filter() {
        return filter;
    }

    /**
     * @return Events.
     */
    @Nullable Collection<GridEvent> events() {
        return evts != null ? Collections.unmodifiableCollection(evts) : null;
    }

    /**
     * @return the Class loader ID.
     */
    public GridUuid classLoaderId() {
        return clsLdrId;
    }

    /**
     * @return Deployment mode.
     */
    public GridDeploymentMode deploymentMode() {
        return depMode;
    }

    /**
     * @return Filter class name.
     */
    public String filterClassName() {
        return filterClsName;
    }

    /**
     * @return Sequence number.
     */
    public long sequenceNumber() {
        return seqNum;
    }

    /**
     * @return User version.
     */
    public String userVersion() {
        return userVer;
    }

    /**
     * @return Node class loader participant map.
     */
    @Nullable public Map<UUID, GridTuple2<GridUuid, Long>> loaderParticipants() {
        return ldrParties != null ? Collections.unmodifiableMap(ldrParties) : null;
    }

    /**
     * @param ldrParties Node class loader participant map.
     */
    public void loaderParticipants(Map<UUID, GridTuple2<GridUuid, Long>> ldrParties) {
        this.ldrParties = ldrParties;
    }

    /**
     * Gets property ex.
     *
     * @return Property ex.
     */
    public Throwable exception() {
        return ex;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridEventStorageMessage.class, this);
    }
}

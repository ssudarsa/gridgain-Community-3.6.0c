// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers.affinity.impl;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Object wrapper containing serialized byte array of original object and deployment information.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class GridAffinityMessage implements Externalizable {
    /** */
    private GridByteArrayList src;

    /** */
    private GridUuid clsLdrId;

    /** */
    private GridDeploymentMode depMode;

    /** */
    private String srcClsName;

    /** */
    private long seqNum;

    /** */
    private String userVer;

    /** Node class loader participants. */
    @GridToStringInclude
    private Map<UUID, GridTuple2<GridUuid, Long>> ldrParties;

    /**
     * @param src Source object.
     * @param srcClsName Source object class name.
     * @param clsLdrId Class loader ID.
     * @param depMode Deployment mode.
     * @param seqNum Sequence number.
     * @param userVer User version.
     * @param ldrParties Node loader participant map.
     */
    GridAffinityMessage(
        GridByteArrayList src,
        String srcClsName,
        GridUuid clsLdrId,
        GridDeploymentMode depMode,
        long seqNum,
        String userVer,
        Map<UUID, GridTuple2<GridUuid, Long>> ldrParties) {
        this.src = src;
        this.srcClsName = srcClsName;
        this.depMode = depMode;
        this.seqNum = seqNum;
        this.clsLdrId = clsLdrId;
        this.userVer = userVer;
        this.ldrParties = ldrParties;
    }

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridAffinityMessage() {
        // No-op.
    }

    /**
     * @return Source object.
     */
    public GridByteArrayList source() {
        return src;
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
     * @return Source message class name.
     */
    public String sourceClassName() {
        return srcClsName;
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
    public Map<UUID, GridTuple2<GridUuid, Long>> loaderParticipants() {
        return ldrParties != null ? Collections.unmodifiableMap(ldrParties) : null;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(src);
        out.writeInt(depMode.ordinal());
        out.writeLong(seqNum);

        U.writeGridUuid(out, clsLdrId);
        U.writeString(out, srcClsName);
        U.writeString(out, userVer);
        U.writeMap(out, ldrParties);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        src = (GridByteArrayList)in.readObject();
        depMode = GridDeploymentMode.fromOrdinal(in.readInt());
        seqNum = in.readLong();

        clsLdrId = U.readGridUuid(in);
        srcClsName = U.readString(in);
        userVer = U.readString(in);
        ldrParties = U.readMap(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridAffinityMessage.class, this);
    }
}

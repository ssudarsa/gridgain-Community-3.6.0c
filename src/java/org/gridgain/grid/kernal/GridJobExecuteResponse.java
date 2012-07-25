// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.util.tostring.*;
import java.io.*;
import java.util.*;

/**
 * This class defines externalizable job execution response.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridJobExecuteResponse implements GridTaskMessage, Externalizable {
    /** */
    private GridUuid sesId;

    /** */
    private GridUuid jobId;

    /** */
    private GridByteArrayList res;

    /** */
    private GridByteArrayList gridEx;

    /** */
    private GridByteArrayList jobAttrs;

    /** */
    @GridToStringExclude private transient GridException fakeEx;

    /** */
    private boolean isCancelled;

    /** */
    private UUID nodeId;

    /**
     * No-op constructor to support {@link Externalizable} interface. This
     * constructor is not meant to be used for other purposes.
     */
    public GridJobExecuteResponse() {
        // No-op.
    }

    /**
     * @param nodeId Sender node ID.
     * @param sesId Task session ID.
     * @param jobId Job ID.
     * @param gridEx Serialized grid exception.
     * @param res Serialized result.
     * @param jobAttrs Job attributes.
     * @param isCancelled Whether job was cancelled or not.
     */
    public GridJobExecuteResponse(UUID nodeId, GridUuid sesId, GridUuid jobId, GridByteArrayList gridEx,
        GridByteArrayList res, GridByteArrayList jobAttrs, boolean isCancelled) {
        assert nodeId != null;
        assert sesId != null;
        assert jobId != null;

        this.nodeId = nodeId;
        this.sesId = sesId;
        this.jobId = jobId;
        this.gridEx = gridEx;
        this.res = res;
        this.jobAttrs = jobAttrs;
        this.isCancelled = isCancelled;
    }

    /**
     * @return Task session ID.
     */
    @Override public GridUuid getSessionId() {
        return sesId;
    }

    /**
     * @return Job ID.
     */
    public GridUuid getJobId() {
        return jobId;
    }

    /**
     * @return Job result.
     */
    public GridByteArrayList getJobResult() {
        return res;
    }

    /**
     * @return Job exception.
     */
    public GridByteArrayList getException() {
        return gridEx;
    }

    /**
     * @return Job attributes.
     */
    public GridByteArrayList getJobAttributes() {
        return jobAttrs;
    }


    /**
     * @return Job cancellation status.
     */
    public boolean isCancelled() {
        return isCancelled;
    }

    /**
     * @return Sender node ID.
     */
    public UUID getNodeId() {
        return nodeId;
    }

    /**
     * @return Fake exception.
     */
    public GridException getFakeException() {
        return fakeEx;
    }

    /**
     * @param fakeEx Fake exception.
     */
    public void setFakeException(GridException fakeEx) {
        this.fakeEx = fakeEx;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeBoolean(isCancelled);

        out.writeObject(gridEx);
        out.writeObject(res);
        out.writeObject(jobAttrs);

        U.writeUuid(out, nodeId);
        U.writeGridUuid(out, sesId);
        U.writeGridUuid(out, jobId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        isCancelled = in.readBoolean();

        gridEx = (GridByteArrayList)in.readObject();
        res = (GridByteArrayList)in.readObject();
        jobAttrs = (GridByteArrayList)in.readObject();

        nodeId = U.readUuid(in);
        sesId = U.readGridUuid(in);
        jobId = U.readGridUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobExecuteResponse.class, this);
    }
}

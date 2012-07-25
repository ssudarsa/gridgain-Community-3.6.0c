// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Task session request.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridTaskSessionRequest implements GridTaskMessage, Externalizable {
    /** Changed attributes. */
    private GridByteArrayList attrs;

    /** Task session ID. */
    private GridUuid sesId;

    /** ID of job within a task. */
    private GridUuid jobId;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridTaskSessionRequest() {
        // No-op.
    }

    /**
     * @param sesId Session ID.
     * @param jobId Job ID within the session.
     * @param attrs Changed attribute.
     */
    public GridTaskSessionRequest(GridUuid sesId, GridUuid jobId, GridByteArrayList attrs) {
        assert sesId != null;
        assert attrs != null;

        this.sesId = sesId;
        this.attrs = attrs;
        this.jobId = jobId;
    }

    /**
     * @return Changed attributes.
     */
    public GridByteArrayList getAttributes() {
        return attrs;
    }

    /**
     * @return Session ID.
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

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(attrs);

        U.writeGridUuid(out, sesId);
        U.writeGridUuid(out, jobId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        attrs = (GridByteArrayList)in.readObject();

        sesId = U.readGridUuid(in);
        jobId = U.readGridUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTaskSessionRequest.class, this);
    }
}

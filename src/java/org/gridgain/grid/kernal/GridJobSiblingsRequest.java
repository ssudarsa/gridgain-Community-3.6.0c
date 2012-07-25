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
 * Job siblings request.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridJobSiblingsRequest implements Externalizable {
    /** */
    private GridUuid sesId;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridJobSiblingsRequest() {
        //No-op.
    }

    /**
     * @param sesId Session ID.
     */
    public GridJobSiblingsRequest(GridUuid sesId) {
        assert sesId != null;

        this.sesId = sesId;
    }

    public GridUuid getSessionId() {
        return sesId;
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        sesId = U.readGridUuid(in);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeGridUuid(out, sesId);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobSiblingsRequest.class, this);
    }
}

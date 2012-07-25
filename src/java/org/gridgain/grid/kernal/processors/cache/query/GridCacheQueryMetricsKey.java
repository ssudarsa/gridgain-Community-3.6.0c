// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.typedef.*;

import java.io.*;

/**
 * Immutable query metrics key used to group metrics.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class GridCacheQueryMetricsKey implements Externalizable {
    /** */
    private GridCacheQueryType type;

    /** */
    private String clsName;

    /** */
    private String clause;

    /**
     * Constructs key.
     *
     * @param type Query type.
     * @param clsName Query return type.
     * @param clause Query clause.
     */
    GridCacheQueryMetricsKey(GridCacheQueryType type, String clsName, String clause) {
        assert type != null;

        this.type = type;
        this.clsName = clsName;
        this.clause = clause;
    }

    /**
     * Required by {@link Externalizable}.
     */
    public GridCacheQueryMetricsKey() {
        // No-op.
    }

    /**
     * @return Query type.
     */
    GridCacheQueryType type() {
        return type;
    }

    /**
     * @return Query return type.
     */
    String className() {
        return clsName;
    }

    /**
     * @return Query clause.
     */
    String clause() {
        return clause;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (!(obj instanceof GridCacheQueryMetricsKey))
            return false;

        GridCacheQueryMetricsKey oth = (GridCacheQueryMetricsKey)obj;

        return oth.type() == type && F.eq(oth.className(), clsName) && F.eq(oth.clause(), clause);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return type.ordinal() + 31 * (clsName != null ? clsName.hashCode() : 0) +
            31 * 31 * (clause != null ? clause.hashCode() : 0);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeByte(type.ordinal());
        out.writeUTF(clsName);
        out.writeUTF(clause);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type = GridCacheQueryType.fromOrdinal(in.readByte());
        clsName = in.readUTF();
        clause = in.readUTF();
    }
}

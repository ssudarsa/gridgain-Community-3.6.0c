// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated.preloader;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Initial request for preload data from a remote node.
 * Once the remote node has received this request it starts sending
 * cache entries split into batches.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class GridReplicatedPreloadDemandMessage<K, V> extends GridCacheMessage<K, V> {
    /** Partition. */
    private int part;

    /** Mod. */
    private int mod;

    /** Node count. */
    private int cnt;

    /** Topic. */
    private String topic;

    /** Timeout. */
    private long timeout;

    /** Worker ID. */
    private int workerId = -1;

    /**
     * Required by {@link Externalizable}.
     */
    public GridReplicatedPreloadDemandMessage() {
        // No-op.
    }

    /**
     * @param part Partition.
     * @param mod Mod to use.
     * @param cnt Size to use.
     * @param topic Topic.
     * @param timeout Timeout.
     * @param workerId Worker ID.
     */
    GridReplicatedPreloadDemandMessage(int part, int mod, int cnt, String topic, long timeout, int workerId) {
        this.part = part;
        this.mod = mod;
        this.cnt = cnt;
        this.topic = topic;
        this.timeout = timeout;
        this.workerId = workerId;
    }

    /**
     * @param msg Message to copy from.
     */
    GridReplicatedPreloadDemandMessage(GridReplicatedPreloadDemandMessage msg) {
        part = msg.partition();
        mod = msg.mod();
        cnt = msg.nodeCount();
        topic = msg.topic();
        timeout = msg.timeout();
        workerId = msg.workerId();
    }

    /** {@inheritDoc} */
    @Override public boolean allowForStartup() {
        return true;
    }

    /**
     * @return Partition.
     */
    int partition() {
        return part;
    }

    /**
     * @return Mod to use for key selection.
     */
    int mod() {
        return mod;
    }

    /**
     *
     * @return Number to use as a denominator when calculating a mod.
     */
    int nodeCount() {
        return cnt;
    }

    /**
     * @return Topic.
     */
    String topic() {
        return topic;
    }

    /**
     * @param topic Topic.
     */
    void topic(String topic) {
        this.topic = topic;
    }

    /**
     * @return Timeout.
     */
    long timeout() {
        return timeout;
    }

    /**
     * @param timeout Timeout.
     */
    void timeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * @return Worker ID.
     */
    int workerId() {
        return workerId;
    }

    /**
     * @param workerId Worker ID.
     */
    void workerId(int workerId) {
        this.workerId = workerId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(part);
        out.writeInt(mod);
        out.writeInt(cnt);
        U.writeString(out, topic);
        out.writeLong(timeout);
        out.writeInt(workerId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        part = in.readInt();
        mod = in.readInt();
        cnt = in.readInt();
        topic = U.readString(in);
        timeout = in.readLong();
        workerId = in.readInt();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridReplicatedPreloadDemandMessage.class, this);
    }
}

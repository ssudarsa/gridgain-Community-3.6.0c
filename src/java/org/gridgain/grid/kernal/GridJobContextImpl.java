// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.kernal.processors.job.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Remote job context implementation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridJobContextImpl extends GridMetadataAwareAdapter implements GridJobContext {
    /** Kernal context ({@code null} for job result context). */
    private GridKernalContext ctx;

    /** */
    private GridUuid jobId;

    /** Job worker. */
    private transient GridJobWorker job;

    /** */
    @GridToStringInclude private final Map<Object, Object> attrs = new HashMap<Object, Object>(1);

    /**
     * @param ctx Kernal context.
     * @param jobId Job ID.
     */
    public GridJobContextImpl(@Nullable GridKernalContext ctx, GridUuid jobId) {
        assert jobId != null;

        this.ctx = ctx;
        this.jobId = jobId;
    }

    /**
     * @param ctx Kernal context.
     * @param jobId Job ID.
     * @param attrs Job attributes.
     */
    public GridJobContextImpl(GridKernalContext ctx, GridUuid jobId,
        Map<? extends Serializable, ? extends Serializable> attrs) {
        this(ctx, jobId);

        synchronized (this.attrs) {
            this.attrs.putAll(attrs);
        }
    }

    /**
     * @param job Job worker.
     */
    public void job(GridJobWorker job) {
        assert job != null;

        this.job = job;
    }

    /** {@inheritDoc} */
    @Override public GridUuid getJobId() {
        return jobId;
    }

    /** {@inheritDoc} */
    @Override public void setAttribute(Object key, @Nullable Object val) {
        A.notNull(key, "key");

        synchronized (attrs) {
            attrs.put(key, val);
        }
    }

    /** {@inheritDoc} */
    @Override public void setAttributes(Map<?, ?> attrs) {
        A.notNull(attrs, "attrs");

        synchronized (this.attrs) {
            this.attrs.putAll(attrs);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <K, V> V getAttribute(K key) {
        A.notNull(key, "key");

        synchronized (attrs) {
            return (V)attrs.get(key);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<Object, Object> getAttributes() {
        synchronized (attrs) {
            return U.sealMap(attrs);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean heldcc() {
        if (ctx == null)
            return false;

        if (job == null)
            job = ctx.job().activeJob(jobId);

        return job != null && job.held();
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <T> T holdcc() {
        return this.<T>holdcc(0);
    }

    /** {@inheritDoc} */
    @Override public <T> T holdcc(long timeout) {
        if (ctx != null) {
            if (job == null)
                job = ctx.job().activeJob(jobId);

            // Completed?
            if (job != null) {
                if (timeout > 0 && !job.isDone()) {
                    final long endTime = System.currentTimeMillis() + timeout;

                    // Overflow.
                    if (endTime > 0) {
                        ctx.timeout().addTimeoutObject(new GridTimeoutObject() {
                            private final GridUuid id = GridUuid.randomUuid();

                            @Override public GridUuid timeoutId() {
                                return id;
                            }

                            @Override public long endTime() {
                                return endTime;
                            }

                            @Override public void onTimeout() {
                                callcc();
                            }
                        });
                    }
                }

                job.hold();
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public void callcc() {
        if (ctx != null) {
            if (job == null)
                job = ctx.job().activeJob(jobId);

            if (job != null)
                // Execute in the same thread.
                job.execute();
        }
    }

    /** {@inheritDoc} */
    @Override public String cacheName() {
        try {
            return (String)job.getDeployment().annotatedValue(job.getJob(), GridCacheName.class);
        }
        catch (GridException e) {
            throw F.wrap(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <T> T affinityKey() {
        try {
            return (T)job.getDeployment().annotatedValue(job.getJob(), GridCacheAffinityMapped.class);
        }
        catch (GridException e) {
            throw F.wrap(e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobContextImpl.class, this);
    }
}

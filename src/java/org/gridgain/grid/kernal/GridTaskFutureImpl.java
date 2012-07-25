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
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * This class provide implementation for task future.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
 */
public class GridTaskFutureImpl<R> extends GridFutureAdapter<R> implements GridTaskFuture<R> {
    /** */
    private transient GridTaskSession ses;

    /** Mapped flag. */
    private AtomicBoolean mapped = new AtomicBoolean();

    /** Mapped latch. */
    private CountDownLatch mappedLatch = new CountDownLatch(1);

    /** */
    private transient GridKernalContext ctx;

    /**
     * Required by {@link Externalizable}.
     */
    public GridTaskFutureImpl() {
        // No-op.
    }

    /**
     * @param ses Task session instance.
     * @param ctx Kernal context.
     */
    public GridTaskFutureImpl(GridTaskSession ses, GridKernalContext ctx) {
        super(ctx);

        assert ses != null;
        assert ctx != null;

        this.ses = ses;
        this.ctx = ctx;
    }

    /**
     * Gets task timeout.
     *
     * @return Task timeout.
     */
    @Override public GridTaskSession getTaskSession() {
        if (ses == null)
            throw new IllegalStateException("Cannot access task session after future has been deserialized.");

        return ses;
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws GridException {
        checkValid();

        if (onCancelled()) {
            assert ctx != null;

            ctx.io().send(ctx.discovery().allNodes(), TOPIC_CANCEL, new GridJobCancelRequest(ses.getId()), SYSTEM_POOL);

            return true;
        }

        return isCancelled();
    }

    /** {@inheritDoc} */
    @Override public boolean isMapped() {
        return mapped.get();
    }

    /**
     * Callback for completion of task mapping stage.
     */
    public void onMapped() {
        if (mapped.compareAndSet(false, true))
            mappedLatch.countDown();
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(@Nullable R res, @Nullable Throwable err) {
        if (super.onDone(res, err)) {
            mappedLatch.countDown();

            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean onCancelled() {
        if (super.onCancelled()) {
            mappedLatch.countDown();

            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean waitForMap() throws GridException {
        boolean b = mapped.get();

        if (!b && !isDone()) {
            try {
                mappedLatch.await();

                b = mapped.get();
            }
            catch (InterruptedException e) {
                throw new GridInterruptedException("Got interrupted while waiting for map completion.", e);
            }
        }

        return b;
    }

    /** {@inheritDoc} */
    @Override public boolean waitForMap(long timeout) throws GridException {
        return waitForMap(timeout, MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override public boolean waitForMap(long timeout, TimeUnit unit) throws GridException {
        boolean b = mapped.get();

        if (!b && !isDone()) {
            try {
                mappedLatch.await(timeout, unit);

                b = mapped.get();
            }
            catch (InterruptedException e) {
                throw new GridInterruptedException("Got interrupted while waiting for map completion.", e);
            }
        }

        return b;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        if (isDone())
            out.writeBoolean(isMapped());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        if (isValid()) {
            boolean mapped = in.readBoolean();

            this.mapped.set(mapped);
        }

        ses = null;
        ctx = null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTaskFutureImpl.class, this);
    }
}

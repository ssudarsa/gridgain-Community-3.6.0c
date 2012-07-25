// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.future;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.stopwatch.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.*;

/**
 * Future adapter.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridFutureAdapter<R> extends GridMetadataAwareAdapter implements GridFuture<R>, Externalizable {
    /** Logger reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Synchronous notification flag. */
    private static final boolean SYNC_NOTIFY = U.isFutureNotificationSynchronous("true");

    /** Concurrent notification flag. */
    private static final boolean CONCUR_NOTIFY = U.isFutureNotificationConcurrent("false");

    /** Done flag. */
    private AtomicBoolean done = new AtomicBoolean();

    /** Cancelled flag. */
    private AtomicBoolean cancelled = new AtomicBoolean();

    /** */
    private CountDownLatch doneLatch = new CountDownLatch(1);

    /** Result. */
    @GridToStringInclude
    private R res;

    /** Error. */
    private Throwable err;

    /** Set to {@code false} on deserialization whenever incomplete future is serialized. */
    private boolean valid = true;

    /** Asynchronous listener. */
    private final Collection<GridInClosure<? super GridFuture<R>>>
        lsnrs = new GridConcurrentLinkedDeque<GridInClosure<? super GridFuture<R>>>();

    /** Creator thread. */
    private Thread thread = Thread.currentThread();

    /** Context. */
    protected GridKernalContext ctx;

    /** Logger. */
    protected GridLogger log;

    /** Future start time. */
    protected final long startTime = System.currentTimeMillis();

    /** Synchronous notification flag. */
    private volatile boolean syncNotify = SYNC_NOTIFY;

    /** Concurrent notification flag. */
    private volatile boolean concurNotify = CONCUR_NOTIFY;

    /** Future end time. */
    private volatile long endTime;

    /** Watch. */
    protected GridStopwatch watch;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridFutureAdapter() {
        // No-op.
    }

    /**
     * @param ctx Kernal context.
     */
    public GridFutureAdapter(GridKernalContext ctx) {
        this(ctx, SYNC_NOTIFY);
    }

    /**
     * @param syncNotify Synchronous notify flag.
     * @param ctx Kernal context.
     */
    public GridFutureAdapter(GridKernalContext ctx, boolean syncNotify) {
        assert ctx != null;

        this.syncNotify = syncNotify;

        this.ctx = ctx;

        log = U.logger(ctx, logRef, GridFutureAdapter.class);
    }

    /** {@inheritDoc} */
    @Override public long startTime() {
        return startTime;
    }

    /** {@inheritDoc} */
    @Override public long duration() {
        long endTime = this.endTime;

        return endTime == 0 ? System.currentTimeMillis() - startTime : endTime - startTime;
    }

    /** {@inheritDoc} */
    @Override public boolean concurrentNotify() {
        return concurNotify;
    }

    /** {@inheritDoc} */
    @Override public void concurrentNotify(boolean concurNotify) {
        this.concurNotify = concurNotify;
    }

    /** {@inheritDoc} */
    @Override public boolean syncNotify() {
        return syncNotify;
    }

    /** {@inheritDoc} */
    @Override public void syncNotify(boolean syncNotify) {
        this.syncNotify = syncNotify;
    }

    /**
     * Adds a watch to this future.
     *
     * @param name Name of the watch.
     */
    public void addWatch(String name) {
        assert name != null;

        watch = W.stopwatch(name);
    }

    /**
     * Adds a watch to this future.
     *
     * @param watch Watch to add.
     */
    public void addWatch(GridStopwatch watch) {
        assert watch != null;

        this.watch = watch;
    }

    /**
     * Checks that future is in usable state.
     */
    protected void checkValid() {
        if (!valid)
            throw new IllegalStateException("Incomplete future was serialized and cannot " +
                "be used after deserialization.");
    }

    /**
     * @return Valid flag.
     */
    protected boolean isValid() {
        return valid;
    }

    /**
     * Await for done signal.
     *
     * @throws InterruptedException If interrupted.
     */
    private void latchAwait() throws InterruptedException {
        doneLatch.await();
    }

    /**
     * Signal all waiters for done condition.
     */
    private void latchCountDown() {
        doneLatch.countDown();
    }

    /**
     * Await for done signal for a given period of time (in milliseconds).
     *
     * @param ms Time in milliseconds to wait for done signal.
     * @return {@code True} if signal was sent, {@code false} otherwise.
     * @throws InterruptedException If interrupted.
     */
    protected final boolean latchAwait(long ms) throws InterruptedException {
        return doneLatch.await(ms, TimeUnit.MILLISECONDS);
    }

    /**
     * Await for done signal for a given period of time (in milliseconds).
     *
     * @param time Time to wait for done signal.
     * @param unit Time unit.
     * @return {@code True} if signal was sent, {@code false} otherwise.
     * @throws InterruptedException If interrupted.
     */
    protected final boolean latchAwait(long time, TimeUnit unit) throws InterruptedException {
        return doneLatch.await(time, unit);
    }

    /**
     * @return Value of error.
     */
    protected Throwable error() {
        checkValid();

        return err;
    }

    /**
     * @return Value of result.
     */
    protected R result() {
        checkValid();

        return res;
    }

    /** {@inheritDoc} */
    @Override public R call() throws Exception {
        return get();
    }

    /** {@inheritDoc} */
    @Override public R get(long timeout) throws GridException {
        return get(timeout, MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override public R get() throws GridException {
        checkValid();

        try {
            if (doneLatch.getCount() != 0)
                latchAwait();

            if (done.get()) {
                Throwable err = this.err;

                if (err != null)
                    throw U.cast(err);

                return res;
            }

            throw new GridFutureCancelledException("Future was cancelled: " + this);
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public R get(long timeout, TimeUnit unit) throws GridException {
        A.ensure(timeout >= 0, "timeout cannot be negative: " + timeout);
        A.notNull(unit, "unit");

        checkValid();

        try {
            if (doneLatch.getCount() != 0)
                latchAwait(timeout, unit);

            if (done.get()) {
                if (err != null)
                    throw U.cast(err);

                return res;
            }

            if (cancelled.get())
                throw new GridFutureCancelledException("Future was cancelled: " + this);

            throw new GridFutureTimeoutException("Timeout was reached before computation completed [duration=" +
                duration() + "ms, timeout=" + unit.toMillis(timeout) + "ms]");
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException("Got interrupted while waiting for future to complete [duration=" +
                duration() + "ms, timeout=" + unit.toMillis(timeout) + "ms]", e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "TooBroadScope"})
    @Override public void listenAsync(@Nullable final GridInClosure<? super GridFuture<R>> lsnr) {
        if (lsnr != null) {
            checkValid();

            boolean done;

            GridInClosure<? super GridFuture<R>> lsnr0 = lsnr;

            done = this.done.get();

            if (!done) {
                lsnr0 = new GridInClosure<GridFuture<R>>() {
                    private final AtomicBoolean called = new AtomicBoolean();

                    @Override public void apply(GridFuture<R> t) {
                        if (called.compareAndSet(false, true))
                            lsnr.apply(t);
                    }

                    @Override public boolean equals(Object o) {
                        return o != null && (o == this || o == lsnr || o.equals(lsnr));
                    }

                    @Override public String toString() {
                        return lsnr.toString();
                    }
                };

                lsnrs.add(lsnr0);

                done = this.done.get(); // Double check.
            }

            if (done) {
                try {
                    if (syncNotify)
                        notifyListener(lsnr0);
                    else {
                        final GridInClosure<? super GridFuture<R>> lsnr1 = lsnr0;

                        ctx.closure().runLocalSafe(new GPR() {
                            @Override public void run() {
                                notifyListener(lsnr1);
                            }
                        }, true);
                    }
                }
                catch (IllegalStateException ignore) {
                    U.warn(null, "Future notification will not proceed because grid is stopped: " + ctx.gridName());
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void stopListenAsync(@Nullable GridInClosure<? super GridFuture<R>>... lsnr) {
        if (lsnr == null || lsnr.length == 0)
            lsnrs.clear();
        else {
            // Iterate through the whole list, removing all occurrences, if any.
            for (Iterator<GridInClosure<? super GridFuture<R>>> it = lsnrs.iterator(); it.hasNext();) {
                GridInClosure<? super GridFuture<R>> l1 = it.next();

                for (GridInClosure<? super GridFuture<R>> l2 : lsnr)
                    // Must be l1.equals(l2), not l2.equals(l1), because of the way listeners are added.
                    if (l1.equals(l2))
                        it.remove();
            }
        }
    }

    /**
     * Notifies all registered listeners.
     */
    @SuppressWarnings({"TooBroadScope"})
    private void notifyListeners() {
        if (concurNotify) {
            for (final GridInClosure<? super GridFuture<R>> lsnr : lsnrs)
                ctx.closure().runLocalSafe(new GPR() {
                    @Override public void run() {
                        notifyListener(lsnr);
                    }
                }, true);
        }
        else {
            // Always notify in the thread different from start thread.
            if (!syncNotify && Thread.currentThread() == thread) {
                ctx.closure().runLocalSafe(new GPR() {
                    @Override public void run() {
                        // Since concurrent notifications are off, we notify
                        // all listeners in one thread.
                        for (GridInClosure<? super GridFuture<R>> lsnr : lsnrs)
                            notifyListener(lsnr);
                    }
                }, true);
            }
            else
                for (GridInClosure<? super GridFuture<R>> lsnr : lsnrs)
                    notifyListener(lsnr);
        }
    }

    /**
     * Notifies single listener.
     *
     * @param lsnr Listener.
     */
    private void notifyListener(GridInClosure<? super GridFuture<R>> lsnr) {
        assert lsnr != null;

        try {
            lsnr.apply(this);
        }
        catch (IllegalStateException e) {
            U.warn(null, "Failed to notify listener (is grid stopped?) [grid=" + ctx.gridName() +
                ", lsnr=" + lsnr + ", err=" + e.getMessage() + ']');
        }
        catch (RuntimeException e) {
            U.error(log, "Failed to notify listener: " + lsnr, e);

            throw e;
        }
        catch (Error e) {
            U.error(log, "Failed to notify listener: " + lsnr, e);

            throw e;
        }
    }

    /**
     * Default no-op implementation that always returns {@code false}.
     * Futures that do support cancellation should override this method
     * and call {@link #onCancelled()} callback explicitly if cancellation
     * indeed did happen.
     */
    @Override public boolean cancel() throws GridException {
        checkValid();

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isDone() {
        // Don't check for "valid" here, as "done" flag can be read
        // even in invalid state.
        return done.get() || cancelled.get();
    }

    /** {@inheritDoc} */
    @Override public GridAbsPredicate predicate() {
        return new PA() {
            @Override public boolean apply() {
                return isDone();
            }
        };
    }

    /** {@inheritDoc} */
    @Override public boolean isCancelled() {
        checkValid();

        return cancelled.get();
    }

    /**
     * Callback to notify that future is finished with {@code null} result.
     * This method must delegate to {@link #onDone(Object, Throwable)} method.
     *
     * @return {@code True} if result was set by this call.
     */
    public final boolean onDone() {
        return onDone(null, null);
    }

    /**
     * Callback to notify that future is finished.
     * This method must delegate to {@link #onDone(Object, Throwable)} method.
     *
     * @param res Result.
     * @return {@code True} if result was set by this call.
     */
    public final boolean onDone(@Nullable R res) {
        return onDone(res, null);
    }

    /**
     * Callback to notify that future is finished.
     * This method must delegate to {@link #onDone(Object, Throwable)} method.
     *
     * @param err Error.
     * @return {@code True} if result was set by this call.
     */
    public final boolean onDone(@Nullable Throwable err) {
        return onDone(null, err);
    }

    /**
     * Callback to notify that future is finished. Note that if non-{@code null} exception is passed in
     * the result value will be ignored.
     *
     * @param res Optional result.
     * @param err Optional error.
     * @return {@code True} if result was set by this call.
     */
    public boolean onDone(@Nullable R res, @Nullable Throwable err) {
        checkValid();

        boolean notify = false;

        boolean gotDone = false;

        try {
            if (done.compareAndSet(false, true)) {
                gotDone = true;

                endTime = System.currentTimeMillis();

                this.res = res;
                this.err = err;

                notify = true;

                latchCountDown();

                return true;
            }

            return false;
        }
        finally {
            if (gotDone) {
                GridStopwatch w = watch;

                if (w != null)
                    w.stop();
            }

            if (notify)
                notifyListeners();
        }
    }

    /**
     * Callback to notify that future is cancelled.
     *
     * @return {@code True} if cancel flag was set by this call.
     */
    public boolean onCancelled() {
        checkValid();

        boolean c = cancelled.get();

        if (c || done.get())
            return false;

        if (cancelled.compareAndSet(false, true)) {
            latchCountDown();

            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"TooBroadScope"})
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeBoolean(done.get());
        out.writeBoolean(syncNotify);
        out.writeBoolean(concurNotify);

        // Don't write any further if not done, as deserialized future
        // will be invalid anyways.
        if (done.get()) {
            out.writeBoolean(cancelled.get());
            out.writeObject(res);
            out.writeObject(err);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        boolean done = in.readBoolean();

        syncNotify = in.readBoolean();
        concurNotify = in.readBoolean();

        if (!done)
            valid = false;
        else {
            boolean cancelled = in.readBoolean();

            R res = (R)in.readObject();

            Throwable err = (Throwable)in.readObject();

            this.done.set(done);
            this.cancelled.set(cancelled);
            this.res = res;
            this.err = err;
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridFutureAdapter.class, this);
    }
}

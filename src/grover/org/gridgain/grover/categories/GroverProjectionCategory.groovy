// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.categories

import java.util.concurrent.*
import org.gridgain.grid.*
import org.gridgain.grid.lang.*
import org.gridgain.grover.lang.*
import org.jetbrains.annotations.*

/**
 * Extensions for {@link GridProjection}.
 * <p>
 * To access methods from the category target class has
 * to be annotated: {@code @Use(GroverProjectionCategory)}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
class GroverProjectionCategory {
    /**
     * Runs job that doesn't produce any result with given argument on this projection
     * using given distribution mode.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param arg Job argument.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @throws GridException Thrown in case of any failure.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     */
    static <T> void run$(GridProjection p, GridClosureCallMode mode, @Nullable GridInClosure<? super T> c,
        @Nullable T arg, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null

        p.run(mode, c, arg, f)
    }

    /**
     * Runs job that doesn't produce any result with given argument on this projection
     * using given distribution mode.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param arg Job argument.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @return Future for job execution.
     */
    static <T> GridFuture<?> runAsync$(GridProjection p, GridClosureCallMode mode,
        @Nullable GridInClosure<? super T> c, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null

        p.runAsync(mode, c, arg, f)
    }

    /**
     * Asynchronous closure call on this projection with return value.
     * This call will block until all results are received and ready.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If {@code null} - this method is no-op and returns {@code null}.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Sequence of result values from all nodes where given closures were executed
     *      or {@code null} (see above).
     */
    static <R> GridFuture<Collection<R>> callAsync$(GridProjection p, GridClosureCallMode mode,
        @Nullable GridOutClosure<R> c, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null

        p.callAsync(mode, [c], f)
    }

    /**
     * Synchronous closure call on this projection with return value.
     * This call will block until all results are received and ready.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If {@code null} - this method is no-op and returns {@code null}.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Sequence of result values from all nodes where given closures were executed
     *      or {@code null} (see above).
     */
    static <R> Collection<R> call$(GridProjection p, GridClosureCallMode mode,
        @Nullable GridOutClosure<R> c, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null

        callAsync$(p, mode, c, f).get()
    }

    /**
     * Synchronous closures call on this projection with return value.
     * This call will block until all results are received and ready. If this projection
     * is empty than {@code dflt} closure will be executed and its result returned.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this
     *      method is no-op and returns {@code null}.
     * @param dflt Closure to execute if projection is empty.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Sequence of result values from all nodes where given closures were executed
     *      or {@code null} (see above).
     */
    static <R> Collection<R> callSafe(GridProjection p, GridClosureCallMode mode,
        @Nullable GridOutClosure<R> c, Callable<Collection<R>> dflt,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert dflt != null

        try {
            call$(p, mode, c, f)
        }
        catch (GridEmptyProjectionException ignored) {
            dflt.call()
        }
    }

    /**
     * Synchronous closures call on this projection with return value.
     * This call will block until all results are received and ready. If this projection
     * is empty than {@code dflt} closure will be executed and its result returned.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this
     *      method is no-op and returns {@code null}.
     * @param dflt Closure to execute if projection is empty.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Sequence of result values from all nodes where given closures were executed
     *      or {@code null} (see above).
     */
    static <R> Collection<R> callSafe(GridProjection p, GridClosureCallMode mode,
        @Nullable Collection<GridOutClosure<R>> c, Callable<Collection<R>> dflt,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert dflt != null

        try {
            p.call(mode, c, f)
        }
        catch (GridEmptyProjectionException ignored) {
            dflt.call()
        }
    }

    /**
     * Synchronous closure call on this projection without return value.
     * This call will block until all executions are complete. If this projection
     * is empty than {@code dflt} closure will be executed.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If empty or {@code null} - this method is no-op.
     * @param dflt Closure to execute if projection is empty.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     */
    static void runSafe(GridProjection p, GridClosureCallMode mode, @Nullable GridAbsClosure c,
        Runnable dflt, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert dflt != null

        try {
            p.run(mode, c, f)
        }
        catch (GridEmptyProjectionException ignored) {
            dflt.run()
        }
    }

    /**
     * Synchronous closures call on this projection without return value.
     * This call will block until all executions are complete. If this projection
     * is empty than {@code dflt} closure will be executed.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this
     *      method is no-op.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @param dflt Closure to execute if projection is empty.
     */
    static void runSafe(GridProjection p, GridClosureCallMode mode, @Nullable Collection<GridAbsClosure> c,
        Runnable dflt, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert dflt != null

        try {
            p.run(mode, c, f)
        }
        catch (GridEmptyProjectionException ignored) {
            dflt.run()
        }
    }

    /**
     * Asynchronous closures execution on this projection with reduction. This call will
     * return immediately with the future that can be used to wait asynchronously for the results.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this method
     *      is no-op and will return finished future over {@code null}.
     * @param r Optional reduction function. If {@code null} - this method
     *      is no-op and will return finished future over {@code null}.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Future over the reduced result or {@code null} (see above).
     */
    static <R1, R2> GridFuture<R2> reduceAsync$(GridProjection p, GridClosureCallMode mode,
        @Nullable Collection<GridOutClosure<R1>> c, @Nullable GroverReducer<R1, R2> r,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null

        p.reduceAsync(mode, c, r, f)
    }

    /**
     * Synchronous closures execution on this projection with reduction.
     * This call will block until all results are reduced.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param r Optional reduction function. If {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Reduced result or {@code null} (see above).
     */
    static <R1, R2> R2 reduce$(GridProjection p, GridClosureCallMode mode,
        @Nullable Collection<GridOutClosure<R1>> c, @Nullable GroverReducer<R1, R2> r,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null

        p.reduceAsync(mode, c, r, f).get()
    }

    /**
     * Synchronous closures execution on this projection with reduction.
     * This call will block until all results are reduced. If this projection
     * is empty than {@code dflt} closure will be executed and its result returned.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param r Optional reduction function. If {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param dflt Closure to execute if projection is empty.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Reduced result or {@code null} (see above).
     */
    static <R1, R2> R2 reduceSafe(GridProjection p, GridClosureCallMode mode,
        @Nullable Collection<GridOutClosure<R1>> c, @Nullable GroverReducer<R1, R2> r,
        Callable<R2> dflt, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert dflt != null

        try {
            reduce$(p, mode, c, r, f)
        }
        catch (GridEmptyProjectionException ignored) {
            dflt.call()
        }
    }

    /**
     * Asynchronous closures execution on this projection with mapping and reduction.
     * This call will return immediately with the future that can be used to wait asynchronously for
     * the results.
     *
     * @param m Mapping function.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param r Optional reduction function. If {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Future over the reduced result or {@code null} (see above).
     */
    static <R1, R2> GridFuture<R2> mapreduceAsync$(GridProjection p, GroverMapper<GridOutClosure<R1>, GridRichNode> m,
        @Nullable Collection<GridOutClosure<R1>> c, @Nullable GroverReducer<R1, R2> r,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert m != null

        p.mapreduceAsync(m, c, r, f)
    }

    /**
     * Synchronous closures execution on this projection with mapping and reduction.
     * This call will block until all results are reduced.
     *
     * @param m Mapping function.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param r Optional reduction function. If {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Reduced result or {@code null} (see above).
     */
    static <R1, R2> R2 mapreduce$(GridProjection p, GroverMapper<GridOutClosure<R1>, GridRichNode> m,
        @Nullable Collection<GridOutClosure<R1>> c, @Nullable GroverReducer<R1, R2> r,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert m != null

        mapreduceAsync$(p, m, c, r, f).get()
    }

    /**
     * Synchronous closures execution on this projection with mapping and reduction.
     * This call will block until all results are reduced. If this projection
     * is empty than {@code dflt} closure will be executed and its result returned.
     *
     * @param m Mapping function.
     * @param s Optional sequence of closures to call. If empty or {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param r Optional reduction function. If {@code null} - this method
     *      is no-op and will return {@code null}.
     * @param dflt Closure to execute if projection is empty.
     * @param p Optional node filter predicate. If none provided or {@code null} - all
     *      nodes in projection will be used.
     * @return Reduced result or {@code null} (see above).
     */
    static <R1, R2> R2 mapreduceSafe(GridProjection p, GroverMapper<GridOutClosure<R1>, GridRichNode> m,
        @Nullable Collection<GridOutClosure<R1>> c, @Nullable GroverReducer<R1, R2> r, Callable<R2> dflt,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert m != null
        assert dflt != null

        try {
            mapreduce$(p, m, c, r, f)
        }
        catch (GridEmptyProjectionException ignored) {
            dflt.call()
        }
    }

    /**
     * Executes given closure on the node where data for provided affinity key is located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key).
     * <p>
     * This method will block until its execution is complete or an exception is thrown.
     * All default SPI implementations configured for this grid instance will be
     * used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKey Affinity key. If {@code null} - this method is no-op.
     * @param job Closure to affinity co-located on the node with given affinity key and execute.
     *      If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} thrown if computation was cancelled.
     */
    static void affinityRunOneKey(GridProjection p, String cacheName, @Nullable Object affKey,
        @Nullable GridAbsClosure job, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert cacheName != null

        p.affinityRun(cacheName, affKey, job, f)
    }

    /**
     * Executes given closure on the node where data for provided affinity key is located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key).
     * <p>
     * Unlike its sibling method
     * {@link #affinityRunOneKey(GridProjection, String, Object, GridAbsClosure, GridPredicate[])}
     * this method does not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKey Affinity key. If {@code null} - this method is no-op.
     * @param job Closure to affinity co-located on the node with given affinity key and execute.
     *      If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @return Non-cancellable future of this execution.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} thrown if computation was cancelled.
     */
    static GridFuture<?> affinityRunOneKeyAsync(GridProjection p, String cacheName, @Nullable Object affKey,
        @Nullable GridAbsClosure job, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert cacheName != null

        p.affinityRunAsync(cacheName, affKey, job, f)
    }

    /**
     * Executes given closure on the nodes where data for provided affinity keys are located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key). Note that implementation of multiple executions of the same closure will
     * be wrapped as a single task that splits into multiple {@code job}s that will be mapped to nodes
     * with provided affinity keys.
     * <p>
     * This method will block until its execution is complete or an exception is thrown.
     * All default SPI implementations configured for this grid instance will be
     * used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKeys Collection of affinity keys. All dups will be ignored. If {@code null} or empty
     *      this method is no-op.
     * @param job Closure to affinity co-located on the node with given affinity key and execute.
     *      If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} thrown if computation was cancelled.
     */
    static void affinityRunManyKeys(GridProjection p, String cacheName, @Nullable Collection<?> affKeys,
        @Nullable GridAbsClosure job, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert cacheName != null

        p.affinityRun(cacheName, affKeys, job, f)
    }

    /**
     * Executes given closure on the nodes where data for provided affinity keys are located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key). Note that implementation of multiple executions of the same closure will
     * be wrapped as a single task that splits into multiple {@code job}s that will be mapped to nodes
     * with provided affinity keys.
     * <p>
     * Unlike its sibling method
     * {@link #affinityRunManyKeys(GridProjection, String, Collection, GridAbsClosure, GridPredicate[])}
     * this method does not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKeys Collection of affinity keys. All dups will be ignored. If {@code null} or
     *      empty - this method is no-op.
     * @param job Closure to affinity co-located on the node with given affinity key and execute.
     *      If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @return Non-cancellable future of this execution.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} thrown if computation was cancelled.
     */
    static GridFuture<?> affinityRunManyKeysAsync(GridProjection p, String cacheName, @Nullable Collection<?> affKeys,
        @Nullable GridAbsClosure job, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert cacheName != null

        p.affinityRunAsync(cacheName, affKeys, job, f)
    }

    /**
     * Executes given closure on the node where data for provided affinity key is located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key).
     * <p>
     * This method will block until its execution is complete or an exception is thrown.
     * All default SPI implementations configured for this grid instance will be
     * used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKey Affinity key. If {@code null} - this method is no-op.
     * @param job Closure to affinity co-located on the node with given affinity key and execute.
     *      If {@code null} or empty - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @return Closure execution result.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} thrown if computation was cancelled.
     */
    static <R> R affinityCallOneKey(GridProjection p, String cacheName, @Nullable Object affKey,
        @Nullable GridOutClosure<R> job, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert cacheName != null

        p.affinityCall(cacheName, affKey, job, f)
    }

    /**
     * Executes given closure on the node where data for provided affinity key is located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key).
     * <p>
     * Unlike its sibling method
     * {@link #affinityCallOneKey(GridProjection, String, Object, GridOutClosure, GridPredicate[])}
     * this method does not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKey Affinity key. If {@code null} - this method is no-op.
     * @param job Closure to affinity co-located on the node with given affinity key and execute.
     *      If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @return Non-cancellable closure result future.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} thrown if computation was cancelled.
     */
    static <R> GridFuture<R> affinityCallOneKeyAsync(GridProjection p, String cacheName, @Nullable Object affKey,
        @Nullable GridOutClosure<R> job, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert cacheName != null

        p.affinityCallAsync(cacheName, affKey, job, f)
    }

    /**
     * Executes given closure on the nodes where data for provided affinity keys are located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key). Note that implementation of multiple executions of the same closure will
     * be wrapped as a single task that splits into multiple {@code job}s that will be mapped to nodes
     * with provided affinity keys.
     * <p>
     * This method will block until its execution is complete or an exception is thrown.
     * All default SPI implementations configured for this grid instance will be
     * used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKeys Collection of affinity keys. All dups will be ignored. If {@code null}
     *      or empty - this method is no-op.
     * @param job Closure to affinity co-located on the node with given affinity key and execute.
     *      If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @return Collection of closure execution results.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} thrown if computation was cancelled.
     */
    static <R> Collection<R> affinityCallManyKeys(GridProjection p, String cacheName, @Nullable Collection<?> affKeys,
        @Nullable GridOutClosure<R> job, @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert cacheName != null

        p.affinityCall(cacheName, affKeys, job, f)
    }

    /**
     * Executes given closure on the nodes where data for provided affinity keys are located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key). Note that implementation of multiple executions of the same closure will
     * be wrapped as a single task that splits into multiple {@code job}s that will be mapped to nodes
     * with provided affinity keys.
     * <p>
     * Unlike its sibling method
     * {@link #affinityCallManyKeys(GridProjection, String, Collection, GridOutClosure, GridPredicate[])}
     * this method does not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKeys Collection of affinity keys. All dups will be ignored.
     *      If {@code null} or empty - this method is no-op.
     * @param job Closure to affinity co-located on the node with given affinity key and execute.
     *      If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @return Non-cancellable future of closure results. Upon successful execution number of results
     *      will be equal to number of affinity keys provided.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     */
    static <R> GridFuture<Collection<R>> affinityCallManyKeysAsync(GridProjection p, String cacheName,
        @Nullable Collection<?> affKeys, @Nullable GridOutClosure<R> job,
        @Nullable GridPredicate<? super GridRichNode>... f) {
        assert p != null
        assert cacheName != null

        p.affinityCallAsync(cacheName, affKeys, job, f)
    }
}

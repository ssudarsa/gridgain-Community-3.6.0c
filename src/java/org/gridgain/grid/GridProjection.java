// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Defines grid projection interface and monadic set of operations on a set of grid nodes.
 * <p>
 * All main grid entities such as grid and a node instances can be viewed as
 * collection of grid nodes (in case of the grid node this collection consist of only one
 * element). As such they all share the same set of operations that can be performed on a set
 * grid nodes. These operations are defined in {@link GridProjection} interface and called
 * <tt>monadic</tt> as they are equally defined on any arbitrary set of nodes.
 * <h2 class="header">Nullable and Monads</h2>
 * Many methods in this interface accepts nullable parameters. Although it may seem counter intuitive
 * for some of them - it is done to promote monadic usage of this interface. Java doesn't natively support
 * concepts like <tt>Option</tt> in Scala and returning, accepting, and properly handling
 * {@code null} values is Java's way to support such monadic invocations.
 * <p>
 * All methods that accept {@code null} values (for monadic purposes) will gracefully handle it by
 * either returning a finished future, or empty collection, {@code null} value, or combination of the
 * above. Most method calls therefore can be chained without an explicit checks for {@code null}s.
 * <p>
 * The downside of this approach that inadvertent errors of passing {@code null} will not result
 * in {@link NullPointerException} and may be harder to catch.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public interface GridProjection extends Iterable<GridRichNode>, GridMetadataAware {
    /**
     * Executes given closure locally in optimistic topology transaction, i.e. ensuring that projection
     * topology doesn't change during the closure execution. If projection topology did change - optional
     * rollback closure will be executed and given closure will be re-executed again up to specified
     * number of attempts.
     * <p>
     * Note that this method will block until closure execution is complete (successfully or not).
     * Note also that transactional semantic of this method is optimistic. No locking of grid topology is performed
     * but the topology version is checked before and after the closure execution and compared to be the same.
     * <p>
     * Note also that if given closure or rollback closure throw runtime exception it will be propagated
     * to the caller without any special handling. Specifically, a rollback closure won't be called if the
     * executing closure throws runtime exception.
     *
     * @param c Closure to execute on unchanged topology.
     * @param attempts Number of re-execution attempts. Must be greater than zero.
     * @param rollback Optional rollback closure to execute in case when topology did indeed change during
     *      closure execution. If not provided - the given closure will simply execute again.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @return {@code True} if closure successfully executed on unchanged topology - {@code false} if number
     *      of attempts is exceeded.
     * @see #runOptimisticAsync(GridAbsClosure, int, GridAbsClosure, GridPredicate[])
     */
    public boolean runOptimistic(GridAbsClosure c, int attempts, @Nullable GridAbsClosure rollback,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given closure in optimistic topology transaction, i.e. ensuring that grid topology doesn't
     * change during the closure execution. If it did - optional rollback closure will be executed
     * and given closure will be re-executed again up to specified number of attempts.
     * <p>
     * Note that this method will  block until closure execution is complete (successfully or not).
     * Note also that transactional semantic of this method is optimistic. No locking of grid topology is performed
     * but the topology version is checked before and after the closure execution and compared to be the same.
     * <p>
     * Note also that if given closure or rollback closure throw runtime exception it will be propagated
     * to the caller without any special handling. Specifically, a rollback closure won't be called if the
     * executing closure throws runtime exception.
     *
     * @param c Closure to execute on unchanged topology.
     * @param attempts Number of re-execution attempts. Must be greater than zero.
     * @param dfltVal Default to return when number of attempts is exceeded.
     * @param rollback Optional rollback closure to execute in case when topology did indeed change during
     *      closure execution. If not provided - the given closure will simply execute again.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @return Closure return value.
     * @see #callOptimisticAsync(GridOutClosure, int, Object, GridAbsClosure, GridPredicate[])
     * @param <R> Type of the closure return value.
     */
    public <R> R callOptimistic(GridOutClosure<R> c, int attempts, R dfltVal, @Nullable GridAbsClosure rollback,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given closure in optimistic topology transaction, i.e. ensuring that grid topology doesn't
     * change during the closure execution. If it did - optional rollback closure will be executed
     * and given closure will be re-executed again up to specified number of attempts.
     * <p>
     * Note that this method will not block until closure execution is complete (successfully or not).
     * Note also that transactional semantic of this method is optimistic. No locking of grid topology is performed
     * but the topology version is checked before and after the closure execution and compared to be the same.
     * <p>
     * Note also that if given closure or rollback closure throw runtime exception it will be propagated
     * to the caller without any special handling. Specifically, a rollback closure won't be called if the
     * executing closure throws runtime exception.
     *
     * @param c Closure to execute on unchanged topology.
     * @param attempts Number of re-execution attempts. Must be greater than zero.
     * @param rollback Optional rollback closure to execute in case when topology did indeed change during
     *      closure execution. If not provided - the given closure will simply execute again.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @return Future with either {@code true} if closure successfully executed on unchanged
     *      topology, or {@code false} if number of attempts is exceeded.
     * @see #runOptimistic(GridAbsClosure, int, GridAbsClosure, GridPredicate[])
     */
    public GridFuture<Boolean> runOptimisticAsync(GridAbsClosure c, int attempts, @Nullable GridAbsClosure rollback,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given closure locally in optimistic topology transaction, i.e. ensuring that grid topology doesn't
     * change during the closure execution. If it did - optional rollback closure will be executed
     * and given closure will be re-executed again up to specified number of attempts.
     * <p>
     * Note that this method will not block until closure execution is complete (successfully or not).
     * Note also that transactional semantic of this method is optimistic. No locking of grid topology is performed
     * but the topology version is checked before and after the closure execution and compared to be the same.
     * <p>
     * Note also that if given closure or rollback closure throw runtime exception it will be propagated
     * to the caller without any special handling. Specifically, a rollback closure won't be called if the
     * executing closure throws runtime exception.
     *
     * @param c Closure to execute on unchanged topology.
     * @param attempts Number of re-execution attempts. Must be greater than zero.
     * @param dfltVal Default to return when number of attempts is exceeded.
     * @param rollback Optional rollback closure to execute in case when topology did indeed change during
     *      closure execution. If not provided - the given closure will simply execute again.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @return Future with return value.
     * @see #callOptimistic(GridOutClosure, int, Object, GridAbsClosure, GridPredicate[])
     * @param <R> Type of the closure return value.
     */
    public <R> GridFuture<R> callOptimisticAsync(GridOutClosure<R> c, int attempts, R dfltVal,
        @Nullable GridAbsClosure rollback, @Nullable GridPredicate<? super GridRichNode>... p);

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
     * @see #affinityRunAsync(String, Object, Runnable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     * @see GridJobContext#cacheName()
     * @see GridJobContext#affinityKey()
     */
    public void affinityRun(
        String cacheName,
        @Nullable Object affKey,
        @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

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
     * @see #affinityRunAsync(String, Collection, Runnable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     * @see GridJobContext#cacheName()
     * @see GridJobContext#affinityKey()
     */
    public void affinityRun(
        String cacheName,
        @Nullable Collection<?> affKeys,
        @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Executes given closure on the node where data for provided affinity key is located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key).
     * <p>
     * Unlike its sibling method {@link #affinityRun(String, Object, Runnable, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations
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
     * @see #affinityRun(String, Object, Runnable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     * @see GridJobContext#cacheName()
     * @see GridJobContext#affinityKey()
     */
    public GridFuture<?> affinityRunAsync(
        String cacheName,
        @Nullable Object affKey,
        @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Executes given closure on the nodes where data for provided affinity keys are located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key). Note that implementation of multiple executions of the same closure will
     * be wrapped as a single task that splits into multiple {@code job}s that will be mapped to nodes
     * with provided affinity keys.
     * <p>
     * Unlike its sibling method {@link #affinityRun(String, Collection, Runnable, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations
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
     * @see #affinityRun(String, Object, Runnable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     * @see GridJobContext#cacheName()
     * @see GridJobContext#affinityKey()
     */
    public GridFuture<?> affinityRunAsync(
        String cacheName,
        @Nullable Collection<?> affKeys,
        @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

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
     * @see #affinityRunAsync(String, Object, Runnable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     * @see GridJobContext#cacheName()
     * @see GridJobContext#affinityKey()
     */
    public <R> R affinityCall(
        String cacheName,
        @Nullable Object affKey,
        @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

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
     * @see #affinityRunAsync(String, Object, Runnable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     * @see GridJobContext#cacheName()
     * @see GridJobContext#affinityKey()
     */
    public <R> Collection<R> affinityCall(
        String cacheName,
        @Nullable Collection<?> affKeys,
        @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Executes given closure on the node where data for provided affinity key is located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key).
     * <p>
     * Unlike its sibling method {@link #affinityCall(String, Object, Callable, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations
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
     * @see #affinityRunAsync(String, Object, Runnable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     * @see GridJobContext#cacheName()
     * @see GridJobContext#affinityKey()
     */
    public <R> GridFuture<R> affinityCallAsync(
        String cacheName,
        @Nullable Object affKey,
        @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Executes given closure on the nodes where data for provided affinity keys are located. This
     * is known as affinity co-location between compute grid (a closure) and in-memory data grid
     * (value with affinity key). Note that implementation of multiple executions of the same closure will
     * be wrapped as a single task that splits into multiple {@code job}s that will be mapped to nodes
     * with provided affinity keys.
     * <p>
     * Unlike its sibling method {@link #affinityCall(String, Object, Callable, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations
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
     * @see #affinityRunAsync(String, Object, Runnable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     * @see GridJobContext#cacheName()
     * @see GridJobContext#affinityKey()
     */
    public <R> GridFuture<Collection<R>> affinityCallAsync(
        String cacheName, @Nullable Collection<?> affKeys,
        @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Gets a metrics snapshot for this projection.
     *
     * @return Grid project metrics snapshot.
     * @throws GridException If projection is empty.
     * @see GridNode#metrics()
     */
    public GridProjectionMetrics projectionMetrics() throws GridException;

    /**
     * Gets collections of neighbors from this projection. Neighbors are the groups of nodes from the
     * same physical computer (host).
     * <p>
     * Detection of the same physical computer (host) is based on comparing set of network interface MACs.
     * If two nodes have the same set of MACs, GridGain considers these nodes running on the same
     * physical computer. Note that this same logic is used in license management.
     * <p>
     * Knowing your neighbors can be very important when performing a dynamic split since nodes on the
     * same computer will often bypass network when communicating with each other leading to much better
     * performance for certain use cases. Conversely, one would like to avoid loading the nodes
     * from the same physical computer with tasks as these nodes share CPU and memory between them resulting
     * in reduced performance comparing to a no-neighboring split.
     *
     * @return Collection of projections where each projection represents all nodes (in this projection)
     *      from a single physical computer. Result collection can be empty if this projection is empty.
     * @see GridRichNode#neighbors()
     * @see GridRichNode#neighborsAndMe()
     */
    public Collection<GridProjection> neighborhood();

    /**
     * Gets the youngest node in this topology. The youngest node is a node from this topology
     * that joined last.
     *
     * @return Youngest node in this topology. This method returns {@code null} if projection is
     *      empty.
     * @see #youngestx()
     */
    @Nullable public GridRichNode youngest();

    /**
     * Gets the oldest node in this topology. The oldest node is a node from this projection
     * that joined topology first.
     *
     * @return Oldest node in this topology. This method returns {@code null} if
     *      projection is empty.
     * @see #oldestx()
     */
    @Nullable public GridRichNode oldest();

    /**
     * Gets the randomly selected node from this projection.
     *
     * @return Randomly selected node in this topology. This method returns {@code null} if
     *      projection is empty.
     * @see #randomx()
     */
    @Nullable public GridRichNode random();

    /**
     * Gets the youngest node in this topology. The youngest node is a node from this topology
     * that joined last.
     *
     * @return Youngest node in this topology.
     * @throws GridEmptyProjectionException Thrown in case when projection is empty.
     * @see #youngest()
     */
    public GridRichNode youngestx() throws GridEmptyProjectionException;

    /**
     * Gets the oldest node in this topology. The oldest node is a node from this projection
     * that joined topology first.
     *
     * @return Oldest node in this topology.
     * @throws GridEmptyProjectionException Thrown in case when projection is empty.
     * @see #oldest()
     */
    public GridRichNode oldestx() throws GridEmptyProjectionException;

    /**
     * Gets the randomly selected node from this projection.
     *
     * @return Randomly selected node in this topology.
     * @throws GridEmptyProjectionException Thrown in case when projection is empty.
     * @see #random()
     */
    public GridRichNode randomx() throws GridEmptyProjectionException;

    /**
     * Gets number of unique hosts for nodes in this projection.
     * <p>
     * Detection of the same physical computer (host) is based on comparing set of network interface MACs.
     * If two nodes have the same set of MACs, GridGain considers these nodes running on the same
     * physical computer. Note that this same logic is used in license management.
     *
     * @return Number of unique hosts (always >= 1).
     */
    public int hosts();

    /**
     * Gets total number of CPUs for the nodes in this projection. Note that if two or more nodes
     * started on the same physical host - they will all share the CPUs on that host (and this method
     * will correctly account for that).
     *
     * @return Total number of CPUs (always >= 1).
     */
    public int cpus();

    /**
     * Tells whether or not this projection is dynamic.
     * <p>
     * Dynamic projection is based on predicate and in any particular moment of time
     * can consist of a different set of nodes. Static project does not change and always
     * consist of the same set of nodes (excluding the node that have left the topology
     * since the creation of the static projection).
     *
     * @return Whether or not projection is dynamic.
     */
    public boolean dynamic();

    /**
     * Gets number of nodes currently in this projection and satisfying optional set of predicates.
     * Note that if projection is dynamic the size can vary from call to call.
     *
     * @param p Optional set of predicates. If none provided - all nodes in the projection
     *      will count.
     * @return Number of nodes currently in this projection.
     */
    public int size(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Tests whether or not this projection has any remote nodes. Note that if this projection
     * is dynamic the result of this method can vary from call to call.
     *
     * @return {@code True} if this projection does not have any remote nodes in it at the moment
     *      of call, {@code false} otherwise.
     */
    public boolean hasRemoteNodes();

    /**
     * Tests whether or not this projection has local node in it. Note that if this projection
     * is dynamic the result of this method can vary from call to call.
     *
     * @return {@code True} if this projection does not have local node in it at the moment
     *      of call, {@code false} otherwise.
     */
    public boolean hasLocalNode();

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <T> Type of the return value.
     * @return Distributed version of the given closure.
     */
    public <T> GridOutClosure<GridFuture<T>> gridify(GridClosureCallMode mode, Callable<T> c,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param r Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @return Distributed version of the given closure.
     */
    public GridOutClosure<GridFuture<?>> gridify(GridClosureCallMode mode, Runnable r,
        @Nullable GridPredicate<? super GridRichNode>... p);

     /**
      * Curries given closure into distribution version of it. When resulting closure is
      * called it will return future without blocking and execute given closure asynchronously
      * on this projection using closure call mode.
      * <p>
      * This method effectively allows to convert "local" closure into a distributed one
      * that will take the same parameters (if any), execute "somewhere" on this projection,
      * and produce the same result but via future.
      *
      * @param mode Closure call mode with to curry given closure.
      * @param c Closure to convert.
      * @param p Optional set of filtering predicates. If none provided - all nodes from this
      *      projection will be candidates for load balancing.
      * @param <E> Type of the free variable.
      * @param <T> Type of the return value.
      * @return Distributed version of the given closure.
     */
    public <E, T> GridClosure<E, GridFuture<T>> gridify(GridClosureCallMode mode, GridClosure<E, T> c,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <E1> Type of the first free variable.
     * @param <E2> Type of the second free variable.
     * @param <T> Type of the return value.
     * @return Distributed version of the given closure.
     */
    public <E1, E2, T> GridClosure2<E1, E2, GridFuture<T>> gridify(GridClosureCallMode mode, GridClosure2<E1, E2, T> c,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <E1> Type of the first free variable.
     * @param <E2> Type of the second free variable.
     * @param <E3> Type of the third free variable.
     * @param <T> Type of the return value.
     * @return Distributed version of the given closure.
     */
    public <E1, E2, E3, T> GridClosure3<E1, E2, E3, GridFuture<T>> gridify(GridClosureCallMode mode,
        GridClosure3<E1, E2, E3, T> c, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <E> Type of the free variable.
     * @return Distributed version of the given closure.
     */
    public <E> GridClosure<E, GridFuture<?>> gridify(GridClosureCallMode mode, GridInClosure<E> c,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <E1> Type of the first free variable.
     * @param <E2> Type of the second free variable.
     * @return Distributed version of the given closure.
     */
    public <E1, E2> GridClosure2<E1, E2, GridFuture<?>> gridify(GridClosureCallMode mode, GridInClosure2<E1, E2> c,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <E1> Type of the first free variable.
     * @param <E2> Type of the second free variable.
     * @param <E3> Type of the third free variable.
     * @return Distributed version of the given closure.
     */
    public <E1, E2, E3> GridClosure3<E1, E2, E3, GridFuture<?>> gridify(GridClosureCallMode mode,
        GridInClosure3<E1, E2, E3> c, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given predicate into distribution version of it. When resulting closure
     * is called it will return future without blocking and execute given predicate
     * asynchronously on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" predicate into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Predicate to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @return Distributed version of the given predicate.
     */
    public GridOutClosure<GridFuture<Boolean>> gridify(GridClosureCallMode mode, GridAbsPredicate c,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given predicate into distribution version of it. When resulting closure
     * is called it will return future without blocking and execute given predicate
     * asynchronously on this projection using closure call mode set.
     * <p>
     * This method effectively allows to convert "local" predicate into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Predicate to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <E> Type of the free variable.
     * @return Distributed version of the given predicate.
     */
    public <E> GridClosure<E, GridFuture<Boolean>> gridify(GridClosureCallMode mode, GridPredicate<E> c,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given predicate into distribution version of it. When resulting closure
     * is called it will return future without blocking and execute given predicate
     * asynchronously on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" predicate into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Predicate to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <E1> Type of the first free variable.
     * @param <E2> Type of the second free variable.
     * @return Distributed version of the given predicate.
     */
    public <E1, E2> GridClosure2<E1, E2, GridFuture<Boolean>> gridify(GridClosureCallMode mode,
        GridPredicate2<E1, E2> c, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Curries given predicate into distribution version of it. When resulting closure
     * is called it will return future without blocking and execute given predicate
     * asynchronously on this projection using closure call mode.
     * <p>
     * This method effectively allows to convert "local" predicate into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Predicate to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @param <E1> Type of the first free variable.
     * @param <E2> Type of the second free variable.
     * @param <E3> Type of the third free variable.
     * @return Distributed version of the given predicate.
     */
    public <E1, E2, E3> GridClosure3<E1, E2, E3, GridFuture<Boolean>> gridify(GridClosureCallMode mode,
        GridPredicate3<E1, E2, E3> c, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Gets predicate that defines a subset of nodes for this projection at the time of the call.
     * Note that if projection is based on dynamically changing set of nodes - the predicate
     * returning from this method will change accordingly from call to call.
     *
     * @return Predicate that defines a subset of nodes for this projection.
     */
    public GridPredicate<GridRichNode> predicate();

    /**
     * Merges this projection with the optional set of passed in projections.
     *
     * @param prjs Optional set of projections to merge with. If non provided - this
     *      projection is returned.
     * @return New merged projection or this project if non merging projections were passed in.
     */
    public GridProjection merge(@Nullable GridProjection... prjs);

    /**
     * Companion to {@link #execute(Class, Object, GridPredicate[])} this method
     * executes given task synchronously. This method will block until execution
     * is complete, timeout expires or exception is thrown.
     * <p>
     * <b>Note:</b> this method will limit set of nodes to only those that are in
     * this projection at the time of the call. If this projection is changing its
     * node set dynamically, the set of nodes available for the task execution will
     * also change dynamically from call to call.
     * <p>
     * When using this method task will be deployed automatically, so no explicit
     * deployment step is required.
     *
     * @param task Instance of task to execute. If task class has {@link GridTaskName}
     *      annotation, then task is deployed under a name specified within annotation.
     *      Otherwise, full class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0} the system will wait indefinitely for execution completion.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)}
     *      method.
     * @param p Optional set of filtering predicates. If none provided - all nodes in
     *      this projection will be used for task topology.
     * @return Task result.
     * @throws GridTaskTimeoutException If task execution has timed out. Note that
     *      physically task may still be executing, as there is no practical way to stop
     *      it (however, every job within task will receive interrupt apply).
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridException If task execution resulted in exception.
     * @see #execute(Class, Object, GridPredicate[])
     * @see #execute(Class, Object, long, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> R executeSync(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Companion to {@link #execute(Class, Object, GridPredicate[])} this method executes given
     * task synchronously. This method will block until execution is complete, timeout expires or
     * exception is thrown.
     * <p>
     * <b>Note:</b> this method will limit set of nodes to only those that are in this projection at the time of
     * the call. If this projection is changing its node set dynamically, the set of nodes available for the
     * task execution will also change dynamically from call to call.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     *
     * @param taskCls Class of the task to execute. If class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0} the system will wait indefinitely for execution completion.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @return Task result.
     * @throws GridTaskTimeoutException If task execution has timed out. Note that physically
     *      task may still be executing, as there is no practical way to stop it (however,
     *      every job within task will receive interrupt apply).
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridException If task execution resulted in exception.
     * @see #execute(Class, Object, GridPredicate[])
     * @see #execute(Class, Object, long, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> R executeSync(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Companion to {@link #execute(String, Object, GridPredicate[])} this method executes given task synchronously.
     * This method will block until execution is complete, timeout expires or exception is thrown.
     * <p>
     * <b>Note:</b> this method will limit set of nodes to only those that are in this projection at the time of
     * the call. If this projection is changing its node set dynamically, the set of nodes available for the
     * task execution will also change dynamically from call to call.
     * <p>
     * If task for given name has not been deployed yet, then {@code taskName} will be
     * used as task class name to auto-deploy the task (see Grid#deployTask() method
     * for deployment algorithm).
     *
     * @param taskName Name of the task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0} the system will wait indefinitely for execution completion.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @return Task result.
     * @throws GridTaskTimeoutException If task execution has timed out. Note that physically
     *      task may still be executing, as there is no practical way to stop it (however,
     *      every job within task will receive interrupt apply).
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridException If task execution resulted in exception.
     * @see #execute(String, Object, GridPredicate[])
     * @see #execute(String, Object, long, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> R executeSync(String taskName, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Executes a task on the grid. For information on how task gets split into remote
     * jobs and how results are reduced back into one see {@link GridTask} documentation.
     * <p>
     * This method assumes indefinite wait for task completion. To provide a timeout, use
     * {@link #execute(String, Object, long, GridPredicate[])} method. It is always recommended
     * to specify explicit task timeout.
     * <p>
     * If task for given name has not been deployed yet, then {@code taskName} will be
     * used as task class name to auto-deploy the task (see Grid#deployTask() method
     * for deployment algorithm).
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param taskName Name of the task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @return Task future.
     * @see GridTask for information about task execution.
     * @see #executeSync(String, Object, long, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes a task on the grid. For information on how task gets split into remote
     * jobs and how results are reduced back into one see {@link GridTask} documentation.
     * <p>
     * If task for given name has not been deployed yet, then {@code taskName} will be
     * used as task class name to auto-deploy the task (see Grid#deployTask() method
     * for deployment algorithm).
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param taskName Name of the task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0} the system will wait indefinitely for execution completion.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @return Task future.
     * @see GridTask for information about task execution.
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Asynchronously executes a task on the grid. For information on how task gets
     * split into remote jobs and how results are reduced back into one see {@link GridTask}
     * documentation. Once task execution is complete, {@link GridTaskListener#onFinished(GridTaskFuture)}
     * gets called. In that case {@link GridTaskFuture#isDone()} will always return {@code true}.
     * <p>
     * This method assumes indefinite wait for task completion. To provide a timeout, use
     * {@link #execute(String, Object, long, GridTaskListener, GridPredicate[])} method. It is always
     * recommended to specify explicit task timeout.
     * <p>
     * If task for given name has not been deployed yet, then {@code taskName} will be
     * used as task class name to auto-deploy the task(see Grid#deployTask() method
     * for deployment algorithm).
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param taskName Name of the task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param lsnr Optional grid task result listener that will be called once the execution is completed
     *      (successfully or not).
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    @SuppressWarnings("deprecation")
    public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, @Nullable GridTaskListener lsnr,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Asynchronously executes a task on the grid. For information on how task gets
     * split into remote jobs and how results are reduced back into one see {@link GridTask}
     * documentation. Once task execution is complete, {@link GridTaskListener#onFinished(GridTaskFuture)}
     * gets called. In that case {@link GridTaskFuture#isDone()} will always return {@code true}.
     * <p>
     * If task for given name has not been deployed yet, then {@code taskName} will be
     * used as task class name to auto-deploy the task(see Grid#deployTask() method
     * for deployment algorithm).
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param taskName Name of the task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param lsnr Optional grid task result listener that will be called once the execution is completed
     *      (successfully or not).
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0}, then the system will wait indefinitely for execution completion.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    @SuppressWarnings("deprecation")
    public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes a task on the grid. For information on how task gets split into remote
     * jobs and how results are reduced back into one see {@link GridTask} documentation.
     * <p>
     * This method assumes indefinite wait for task completion. To provide a timeout, use
     * {@link #execute(String, Object, long, GridPredicate[])} method. It is always recommended
     * to specify explicit task timeout.
     * <p>
     * This method is extremely useful when task class is already loaded, for example,
     * in J2EE application server environment. Since application servers already support
     * deployment and hot-redeployment, it is convenient to deploy all task related classes
     * via standard J2EE deployment and then use task classes directly.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param taskCls Class of the task to execute. If class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes a task on the grid. For information on how task gets split into remote
     * jobs and how results are reduced back into one see {@link GridTask} documentation.
     * <p>
     * This method is extremely useful when task class is already loaded, for example,
     * in J2EE application server environment. Since application servers already support
     * deployment and hot-redeployment, it is convenient to deploy all task related classes
     * via standard J2EE deployment and then use task classes directly.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param taskCls Class of the task to execute. If class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0} the system will wait indefinitely for execution completion.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Asynchronously executes a task on the grid. For information on how task gets
     * split into remote jobs and how results are reduced back into one see {@link GridTask}
     * documentation. Once task execution is complete, {@link GridTaskListener#onFinished(GridTaskFuture)}
     * gets called. In that case {@link GridTaskFuture#isDone()} will always return {@code true}.
     * <p>
     * This method assumes indefinite wait for task completion. To provide a timeout, use
     * {@link #execute(String, Object, long, GridTaskListener, GridPredicate[])} method. It is always
     * recommended to specify explicit task timeout.
     * <p>
     * This method is extremely useful when task class is already loaded, for example,
     * in J2EE application server environment. Since application servers already support
     * deployment and hot-redeployment, it is convenient to deploy all task related classes
     * via standard J2EE deployment and then use task classes directly.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param taskCls Class of the task to execute. If class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param lsnr Optional grid task result listener that will be called once the execution is completed
     *      (successfully or not).
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     */
    @SuppressWarnings("deprecation")
    public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Asynchronously executes a task on the grid. For information on how task gets
     * split into remote jobs and how results are reduced back into one see {@link GridTask}
     * documentation. Once task execution is complete, {@link GridTaskListener#onFinished(GridTaskFuture)}
     * gets called. In that case {@link GridTaskFuture#isDone()} will always return {@code true}.
     * <p>
     * This method is extremely useful when task class is already loaded, for example,
     * in J2EE application server environment. Since application servers already support
     * deployment and hot-redeployment, it is convenient to deploy all task related classes
     * via standard J2EE deployment and then use task classes directly.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param taskCls Class of the task to execute. If class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param lsnr Optional grid task result listener that will be called once the execution is completed
     *      (successfully or not).
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0}, then the system will wait indefinitely for execution completion.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     */
    @SuppressWarnings("deprecation")
    public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes a task on the grid. For information on how task gets split into remote
     * jobs and how results are reduced back into one see {@link GridTask} documentation.
     * <p>
     * This method assumes indefinite wait for task completion. To provide a timeout, use
     * {@link #execute(String, Object, long, GridPredicate[])} method. It is always recommended to specify
     * explicit task timeout.
     * <p>
     * This method is extremely useful when task class is already loaded, for example,
     * in J2EE application server environment. Since application servers already support
     * deployment and hot-redeployment, it is convenient to deploy all task related classes
     * via standard J2EE deployment and then use task classes directly.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param task Instance of task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @return Task future.
     * @see GridTask for information about task execution.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes a task on the grid. For information on how task gets split into remote
     * jobs and how results are reduced back into one see {@link GridTask} documentation.
     * <p>
     * This method is extremely useful when task class is already loaded, for example,
     * in J2EE application server environment. Since application servers already support
     * deployment and hot-redeployment, it is convenient to deploy all task related classes
     * via standard J2EE deployment and then use task classes directly.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param task Instance of task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0} the system will wait indefinitely for execution completion.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Asynchronously executes a task on the grid. For information on how task gets
     * split into remote jobs and how results are reduced back into one see {@link GridTask}
     * documentation. Once task execution is complete, {@link GridTaskListener#onFinished(GridTaskFuture)}
     * gets called. In that case {@link GridTaskFuture#isDone()} will always return {@code true}.
     * <p>
     * This method assumes indefinite wait for task completion. To provide a timeout, use
     * {@link #execute(String, Object, long, GridTaskListener, GridPredicate[])} method. It is always
     * recommended to specify explicit task timeout.
     * <p>
     * This method is extremely useful when task class is already loaded, for example,
     * in J2EE application server environment. Since application servers already support
     * deployment and hot-redeployment, it is convenient to deploy all task related classes
     * via standard J2EE deployment and then use task classes directly.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param task Instance of task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param lsnr Optional grid task result listener that will be called once the execution is completed
     *      (successfully or not).
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     */
    @SuppressWarnings("deprecation")
    public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, @Nullable GridTaskListener lsnr,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Asynchronously executes a task on the grid. For information on how task gets
     * split into remote jobs and how results are reduced back into one see {@link GridTask}
     * documentation. Once task execution is complete, {@link GridTaskListener#onFinished(GridTaskFuture)}
     * gets called. In that case {@link GridTaskFuture#isDone()} will always return {@code true}.
     * <p>
     * This method is extremely useful when task class is already loaded, for example,
     * in J2EE application server environment. Since application servers already support
     * deployment and hot-redeployment, it is convenient to deploy all task related classes
     * via standard J2EE deployment and then use task classes directly.
     * <p>
     * When using this method task will be deployed automatically, so no explicit deployment
     * step is required.
     * <p>
     * Note that if projection is empty after applying filtering predicates, the result
     * future will finish with exception. In case of dynamic projection this method
     * will take a snapshot of all nodes in the projection, apply all filtering predicates,
     * if any, and if the resulting set of nodes is empty the returned future will
     * finish with exception.
     *
     * @param task Instance of task to execute. If task class has {@link GridTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task's name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @param lsnr Optional grid task result listener that will be called once the execution is completed
     *      (successfully or not).
     * @param timeout Optional timeout for this task execution in milliseconds.
     *      If {@code 0}, then the system will wait indefinitely for execution completion.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for task topology.
     * @param <T> Type of the task's argument.
     * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
     * @return Task future.
     * @see GridTask for information about task execution.
     */
    @SuppressWarnings("deprecation")
    public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given jobs on this projection with custom mapping and reducing logic.
     * <p>
     * This method will block until its execution is complete or an exception is thrown.
     * All default SPI implementations configured for this grid instance will be
     * used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message and calculates its length
     * by splitting it by spaces, calculating the length of each word on individual (remote) grid node
     * and then summing (reducing) results from all nodes to produce the final length of the input string
     * using function APIs, typedefs, and execution closures on the grid:
     * <pre name="code" class="java">
     * public static int length(final String msg) throws GridException {
     *     return G.grid().call(SPREAD, F.yield(msg.split(" "), F.cInvoke("length")), F.sumIntReducer());
     * }
     * </pre>
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param mapper Mapping closure that maps given jobs to the grid nodes. Note that each job will be
     *      mapped only once. If {@code null} - this method is no-op.
     * @param jobs Closures to map to grid nodes and execute on them.  If {@code null} or empty -
     *      this method is no-op.
     * @param rdc Reducing closure that reduces results from multiple closure into one final value.
     *       If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for topology.
     * @param <R1> Return type of the closures.
     * @param <R2> Return type of the final reduced value.
     * @return Reduced value from executing closures on this projection. if this method is no-op,
     *      {@code null} is returned.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see #withResultClosure(GridClosure2X)
     */
    public <R1, R2, T extends Callable<R1>> R2 mapreduce(@Nullable GridMapper<T, GridRichNode> mapper,
        @Nullable Collection<T> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Asynchronously executes given jobs on this projection with custom mapping and reducing logic.
     * <p>
     * Note that unlike its sibling method {@link #mapreduce(GridMapper, Collection, GridReducer, GridPredicate[])}
     * this method does not block and returns immediately with the future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * If you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param mapper Mapping closure that maps given jobs to the grid nodes. Note that each job will be
     *      mapped only once. If {@code null} - this method is no-op.
     * @param jobs Closures to map to grid nodes and execute on them.
     *       If {@code null} or empty - this method is no-op.
     * @param rdc Reducing closure that reduces results from multiple closure into one final value.
     *       If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be used for topology.
     * @param <R1> Return type of the closures.
     * @param <R2> Return type of the final reduced value.
     * @return Reduced value future from executing closures on this projection.  if this method is
     *      no-op, future with {@code null} value is returned.
     * @see #withResultClosure(GridClosure2X)
     */
    public <R1, R2, T extends Callable<R1>> GridFuture<R2> mapreduceAsync(@Nullable GridMapper<T, GridRichNode> mapper,
        @Nullable Collection<T> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Gets collection of grid nodes for given node IDs out of this projection.
     * Note that nodes that not in this projection at the moment of call will be excluded.
     * <p>
     * Nodes are returned in the same order as passed in IDs.
     *
     * @param ids Collection of node IDs. If none provides - empty collection will be returned.
     * @return Collection of grid nodes for given node IDs. Result collection can be
     *      smaller than the collection of IDs or even be empty depending on whether or not
     *      a node with given ID is still in the topology.
     */
    public Collection<GridRichNode> nodes(@Nullable Collection<UUID> ids);

    /**
     * Gets read-only collections of nodes in this projection that evaluate to {@code true} for all
     * given predicates.
     *
     * @param p Optional set of predicates. If none provided - all nodes will be returned.
     * @return All nodes in this projection that evaluate to {@code true} for provided predicates.
     *      Node that this collection can be empty when either given predicates filter out all
     *      nodes or method is called on daemon node and it is the only node in the topology.
     *      <b>Remember:</b> this method doesn't return daemon nodes.
     * @see #daemonNodes(GridPredicate[])
     */
    public Collection<GridRichNode> nodes(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Creates monadic projection with a given set of nodes out of this projection.
     * Note that nodes not in this projection at the moment of call will be excluded.
     *
     * @param nodes Collection of nodes to create a projection from.
     * @return Monadic projection with given nodes.
     */
    public GridProjection projectionForNodes(@Nullable Collection<? extends GridNode> nodes);

    /**
     * Creates monadic projection with a given set of nodes out of this projection.
     * Note that nodes not in this projection at the moment of call will excluded.
     *
     * @param nodes Collection of nodes to create a projection from.
     * @return Monadic projection with given nodes.
     */
    public GridProjection projectionForNodes(@Nullable GridRichNode... nodes);

    /**
     * Creates monadic projection with a given set of node IDs ouf of this projection.
     * Note that nodes not in this projection at the moment of call will excluded.
     * <p>
     * Note that name with prefix {@code 0} selected to avoid Java naming conflict.
     *
     * @param ids Collection of node IDs defining collection of nodes to create projection with.
     * @return Monadic projection made out of nodes with given IDs.
     */
    public GridProjection projectionForNodeIds(@Nullable UUID... ids);

    /**
     * Creates monadic projection with a given set of node IDs out of this projection.
     * Note that nodes not in this projection at the moment of call will excluded.
     * <p>
     * Note that name with prefix {@code 0} selected to avoid Java naming conflict.
     *
     * @param ids Collection of node IDs defining collection of nodes to create projection with.
     * @return Monadic projection made out of nodes with given IDs.
     */
    public GridProjection projectionForNodeIds(@Nullable Collection<UUID> ids);

    /**
     * Creates monadic projection with the nodes from this projection that also satisfy given
     * set of predicates.
     * <p>
     * Note that name with prefix {@code 1} selected to avoid Java naming conflict.
     *
     * @param p Collection of predicates that all should evaluate to {@code true} for a node
     *      to be included in the final projection.
     * @return Monadic projection.
     * @see PN
     */
    public GridProjection projectionForPredicate(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Creates monadic projection with the nodes from this projection that have given node
     * attribute with optional value. If value is {@code null} than simple attribute presence
     * (with any value) will be used for inclusion of the node.
     *
     * @param n Name of the attribute.
     * @param v Optional attribute value to match.
     * @return Monadic projection.
     */
    public GridProjection projectionForAttribute(String n, @Nullable String v);

    /**
     * Creates monadic projection with the nodes from this projection that have configured
     * caches with given names.
     *
     * @param cacheName Cache name.
     * @param cacheNames Cache names.
     * @return Monadic projection.
     */
    public GridProjection projectionForCaches(@Nullable String cacheName, @Nullable String... cacheNames);

    /**
     * Splits this projection into two: first will have nodes that evaluate to {@code true} for all
     * given predicates, second will have the remaining nodes. Note that if no predicates provided the first
     * projection in returned pair will be this projection and the second object in the pair will be {@code null}.
     *
     * @param p Optional set of splitting predicates.
     * @return Pair of two monads split from the this one.
     */
    public GridPair<GridProjection> split(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Creates cross product of this projection and a set of nodes. Resulting projection will only have
     * nodes from this projection that also present in given set of nodes.
     *
     * @param nodes Set of nodes to cross by. If none provided - this projection is returned.
     * @return Cross product projection.
     */
    public GridProjection cross(@Nullable Collection<? extends GridNode> nodes);

    /**
     * Creates cross product of this projection and a set of nodes. Resulting projection will only have
     * nodes from this projection that also present in given set of nodes.
     * <p>
     * Note that name with prefix {@code 1} selected to avoid Java naming conflict.
     *
     * @param nodes Set of nodes to cross by. If none provided - this projection is returned.
     * @return Cross product projection.
     */
    public GridProjection cross0(@Nullable GridRichNode... nodes);

    /**
     * Creates cross product of this projection and given projections. Resulting projection will only have
     * nodes from this projection that also present in all given projections.
     *
     * @param prjs Other projections. If none provided - this projection is returned.
     * @return Cross product projection.
     */
    public GridProjection cross(@Nullable GridProjection... prjs);

    /**
     * This method calculates hash value of the given set of nodes (a topology).
     * Topology hash can be used in applications with optimistic locking scenario
     * that relying on unchanged topology during a long operation.
     * <p>
     * Note that since GridGain topology architecture is peer-to-peer (without centralized
     * coordination) there is still a small window in which different nodes would have
     * different version for the same topology. Therefore, this version cannot be used
     * in strict ACID context. Values returned by this method are not guaranteed to be
     * sequential. Standard implementation uses CRC32 hash method.
     *
     * @param p Collection of predicates that all should evaluate to {@code true} for node
     *      to be included in the final calculation.
     * @return 8-byte topology hash value.
     */
    public long topologyHash(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Gets read-only collections of nodes from this projection excluding local node, if any.
     *
     * @param p Predicates to filter remote nodes. If none provided - all remote nodes
     *      will be returned.
     * @return Collections of nodes from this projection excluding local node, if any.
     */
    public Collection<GridRichNode> remoteNodes(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Gets monadic projection consisting from the nodes in this projection excluding the local node, if any.
     *
     * @param p Predicates to filter remote nodes. If none provided - all remote nodes
     *      will be used.
     * @return Monadic projection consisting from the nodes in this projection excluding the local node, if any.
     */
    public GridProjection remoteProjection(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Gets grid instance associated with this projection. Grid instance contains additional methods
     * for working with grid as well as acts as a global projection (i.e. projection that is defined on all
     * grid nodes in the topology).
     *
     * @return Grid instance associated with this projection.
     */
    public Grid grid();

    /**
     * Gets parent projection or {@code null} if this project is an instance of {@link Grid} interface, i.e.
     * root projection.
     *
     * @return Parent projection of {@code null}.
     */
    @Nullable public GridProjection parent();

    /**
     * Sets task name for the next executed task on this projection in the <b>current thread</b>.
     * When task starts execution name is reset, so one name is used only once.
     * <p>
     * You may use this method to set task name when you cannot use
     * {@link GridTaskName} annotation.
     * <p>
     * Here is an example.
     * <pre name="code" class="java">
     * G.grid().withName("MyTask").call(
     *     BROADCAST,
     *     new CAX() {
     *         &#64;Override public void applyx() throws GridException {
     *             System.out.println("Hello!");
     *         }
     *     }
     * );
     * </pre>
     *
     * @param taskName Task name.
     * @return Grid projection ({@code this}).
     */
    public GridProjection withName(@Nullable String taskName);

    /**
     * Sets custom ad-hoc implementation for {@link GridTask#result(GridJobResult, List)} method for the
     * next executed closure on this projection in the <b>current thread</b>.
     * When closure starts execution the ad-hoc implementation set here is reset - so it is valid only
     * for one execution from the current thread.
     * <p>
     * Note that this method makes sense <b>only for closure executions</b>, i.e. the case where GridGain
     * automatically converts closure to a grid task. If a user provided task is executed this ad-hoc
     * closure is ignored.
     * <p>
     * This particular useful when you need to disable, for example, failover on specific closure
     * execution like in the following example (note that <code>X.NO_FAILOVER</code> provides
     * built-in closure that effectively disables failover logic):
     * <pre name="code" class="java">
     * G.grid().withResultClosure(X.NO_FAILOVER).call(
     *     BROADCAST,
     *     new CAX() {
     *         &#64;Override public void applyx() throws GridException {
     *             System.out.println("Hello!");
     *         }
     *     }
     * );
     * </pre>
     *
     * @param res Ad-hoc implementation for {@link GridTask#result(GridJobResult, List)} method.
     * @return Grid projection ({@code this}).
     * @see X#NO_FAILOVER
     */
    public GridProjection withResultClosure(@Nullable GridClosure2X<GridJobResult, List<GridJobResult>,
        GridJobResultPolicy> res);

    /**
     * Sets failover SPI for the next executed task on this projection in the <b>current thread</b>.
     * When task starts execution the failover SPI set here is reset - so it is valid only
     * for one execution from the current thread.
     * <p>
     * You may use this method to set specific failover SPI when you cannot use
     * {@link GridTaskSpis} annotation.
     * <p>
     * Here is an example.
     * <pre name="code" class="java">
     * G.grid().withFailoverSpi("MyFailoverSpi").call(
     *     BROADCAST,
     *     new CAX() {
     *         &#64;Override public void applyx() throws GridException {
     *             System.out.println("Hello!");
     *         }
     *     }
     * );
     * </pre>
     *
     * @param spiName Failover SPI name to use.
     * @return Grid projection ({@code this}).
     * @see GridTaskSpis
     */
    public GridProjection withFailoverSpi(@Nullable String spiName);

    /**
     * Sets checkpoint SPI for the next executed task on this projection in the <b>current thread</b>.
     * When task starts execution the checkpoint SPI set here is reset - so it is valid only
     * for one execution from the current thread.
     * <p>
     * You may use this method to set specific checkpoint SPI when you cannot use
     * {@link GridTaskSpis} annotation.
     * <p>
     * Here is an example.
     * <pre name="code" class="java">
     * G.grid().withCheckpointSpi("MyCheckpointSpi").call(
     *     BROADCAST,
     *     new CAX() {
     *         &#64;Override public void applyx() throws GridException {
     *             System.out.println("Hello!");
     *         }
     *     }
     * );
     * </pre>
     *
     * @param spiName Checkpoint SPI name to use.
     * @return Grid projection ({@code this}).
     * @see GridTaskSpis
     */
    public GridProjection withCheckpointSpi(@Nullable String spiName);

    /**
     * Sets load balancing SPI for the next executed task on this projection in the <b>current thread</b>.
     * When task starts execution the load balancing SPI set here is reset - so it is valid only
     * for one execution from the current thread.
     * <p>
     * You may use this method to set specific load balancing SPI when you cannot use
     * {@link GridTaskSpis} annotation.
     * <p>
     * Here is an example.
     * <pre name="code" class="java">
     * G.grid().withLoadBalancingSpi("MyLoadBalancingSpi").call(
     *     BROADCAST,
     *     new CAX() {
     *         &#64;Override public void applyx() throws GridException {
     *             System.out.println("Hello!");
     *         }
     *     }
     * );
     * </pre>
     *
     * @param spiName Load balancing SPI name to use.
     * @return Grid projection ({@code this}).
     * @see GridTaskSpis
     */
    public GridProjection withLoadBalancingSpi(@Nullable String spiName);

    /**
     * Sets topology SPI for the next executed task on this projection in the <b>current thread</b>.
     * When task starts execution the topology SPI set here is reset - so it is valid only
     * for one execution from the current thread.
     * <p>
     * You may use this method to set specific topology SPI when you cannot use
     * {@link GridTaskSpis} annotation.
     * <p>
     * Here is an example.
     * <pre name="code" class="java">
     * G.grid().withTopologySpi("MyTopologySpi").call(
     *     BROADCAST,
     *     new CAX() {
     *         &#64;Override public void applyx() throws GridException {
     *             System.out.println("Hello!");
     *         }
     *     }
     * );
     * </pre>
     *
     * @param spiName Checkpoint SPI name to use.
     * @return Grid projection ({@code this}).
     * @see GridTaskSpis
     */
    public GridProjection withTopologySpi(@Nullable String spiName);

    /**
     * Executes collections of closures using given mapper on this projection.
     * <p>
     * This method will block until its execution is complete or an exception is thrown.
     * All default SPI implementations configured for this grid instance will be
     * used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message and calculates its length
     * by splitting it by spaces, calculating the length of each word on individual (remote) grid node
     * and then summing (reducing) results from all nodes to produce the final length of the input string
     * using function APIs, typedefs, and execution closures on the grid:
     * <pre name="code" class="java">
     * public static int length(final String msg) throws GridException {
     *     return G.grid().call(SPREAD, F.yield(msg.split(" "), F.cInvoke("length")), F.sumIntReducer());
     * }
     * </pre>
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param mapper Mapping closure that maps given jobs to the grid nodes. Note that each job will be
     *      mapped only once. If {@code null} - this method is no-op.
     * @param jobs Closures to map to grid nodes and execute on them.
     *       If {@code null} or empty - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public void run(@Nullable GridMapper<Runnable, GridRichNode> mapper, @Nullable Collection<? extends Runnable> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Asynchronously executes collections of closures using given mapper on this projection.
     * <p>
     * Unlike its sibling method {@link #run(GridClosureCallMode, Runnable, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations configured for this grid
     * instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message and calculates its length
     * by splitting it by spaces, calculating the length of each word on individual (remote) grid node
     * and then summing (reducing) results from all nodes to produce the final length of the input string
     * using function APIs, typedefs, and execution closures on the grid:
     * <pre name="code" class="java">
     * public static int length(final String msg) throws GridException {
     *     return G.grid().call(SPREAD, F.yield(msg.split(" "), F.cInvoke("length")), F.sumIntReducer());
     * }
     * </pre>
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param mapper Mapping closure that maps given jobs to the grid nodes. Note that each job will be
     *      mapped only once. If {@code null} - this method is no-op.
     * @param jobs Closures to map to grid nodes and execute on them.
     *       If {@code null} or empty - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used for topology.
     * @return Non-cancellable future of this execution.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public GridFuture<?> runAsync(@Nullable GridMapper<Runnable, GridRichNode> mapper,
        @Nullable Collection<? extends Runnable> jobs, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given closure on this projection.
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message, splits it into individual
     * words and prints each word on an individual grid node using typedefs, functional APIs and closure
     * execution on the grid:
     * <pre name="code" class="java">
     * public static void sayIt(String phrase) throws GridException {
     *     G.grid().call(SPREAD, F.yield(phrase.split(" "), F.&lt;String&gt;printf("%s")));
     * }
     * </pre>
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
     * @param mode Mode of the distribution for the closure.
     * @param job Job closure to execute.  If {@code null} - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used in topology.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see PN
     * @see #runAsync(GridClosureCallMode, Runnable, GridPredicate[])
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public void run(GridClosureCallMode mode, @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Asynchronously executes given closure on this projection.
     * <p>
     * Unlike its sibling method {@link #run(GridClosureCallMode, Runnable, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations
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
     * @param mode Mode of the distribution for the closure.
     * @param job Job closure to execute. If {@code null} - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used in topology.
     * @return Non-cancellable future of this execution.
     * @see PN
     * @see #run(GridClosureCallMode, Runnable, GridPredicate[])
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given closures on this projection.
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message, splits it into individual
     * words and prints each word on an individual grid node using typedefs, functional APIs and closure
     * execution on the grid:
     * <pre name="code" class="java">
     * public static void sayIt(String phrase) throws GridException {
     *     G.grid().call(SPREAD, F.yield(phrase.split(" "), F.&lt;String&gt;printf("%s")));
     * }
     * </pre>
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
     * @param mode Mode of the distribution for the closure.
     * @param jobs Job closures to execute. If {@code null} or empty - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used in topology.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see PN
     * @see #runAsync(GridClosureCallMode, Collection, GridPredicate[])
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public void run(GridClosureCallMode mode, @Nullable Collection<? extends Runnable> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Asynchronously executes given closures on this projection.
     * <p>
     * Unlike its sibling method {@link #run(GridClosureCallMode, Collection, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations
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
     * @param mode Mode of the distribution for the closure.
     * @param jobs Job closures to execute. If {@code null} or empty - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used in topology.
     * @return Non-cancellable future of this execution.
     * @see PN
     * @see #run(GridClosureCallMode, Collection, GridPredicate[])
     */
    public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Collection<? extends Runnable> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given closure on this projection.
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision
     * resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message, splits it into individual
     * words and prints each word on an individual grid node using typedefs, functional APIs and closure
     * execution on the grid:
     * <pre name="code" class="java">
     * public static void sayIt(String phrase) throws GridException {
     *     G.grid().call(SPREAD, F.yield(phrase.split(" "), F.&lt;String&gt;printf("%s")));
     * }
     * </pre>
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
     * @param mode Mode of the distribution for the closures.
     * @param job Closure to invoke. If {@code null} - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used.
     * @param <R> Type of the closure return value.
     * @return Closure result. Note that in case when there are more than one result (when call mode is
     *      not {@link GridClosureCallMode#UNICAST}) - all results will be received but only first one
     *      will be returned (and all other will be discarded). Such non-unicast executions make sense
     *      primarily for side-effects only closures (i.e. closures that have no return value).
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see PN
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R> R call(GridClosureCallMode mode, @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Asynchronously executes given closure on this projection.
     * <p>
     * Unlike its sibling method {@link #call(GridClosureCallMode, Callable, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations
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
     * @param mode  Mode of the distribution for the closures.
     * @param job Closure to invoke. If {@code null} - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used.
     * @param <R> Type of the closure return value.
     * @return Non-cancellable closure result future. Note that in case when there are more than one result
     *      (when call mode is not {@link GridClosureCallMode#UNICAST}) - all results will be received but
     *      only first one will be returned (and all other will be discarded). Such non-unicast executions
     *      make sense primarily for side-effects only closures (i.e. closures that have no return value).
     * @see PN
     * @see #call(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R> GridFuture<R> callAsync(GridClosureCallMode mode, @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given closures on this projection.
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision
     * resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message, splits it into individual
     * words and prints each word on an individual grid node using typedefs, functional APIs and closure
     * execution on the grid:
     * <pre name="code" class="java">
     * public static void sayIt(String phrase) throws GridException {
     *     G.grid().call(SPREAD, F.yield(phrase.split(" "), F.&lt;String&gt;printf("%s")));
     * }
     * </pre>
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
     * @param mode Mode of the distribution for the closures.
     * @param jobs Closures to invoke. If {@code null} or empty - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used.
     * @return Collection of closure results. Order is undefined.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see PN
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R> Collection<R> call(GridClosureCallMode mode, @Nullable Collection<? extends Callable<R>> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Asynchronously executes given closures on this projection.
     * <p>
     * Unlike its sibling method {@link #call(GridClosureCallMode, Collection, GridPredicate[])} this method does
     * not block and returns immediately with future. All default SPI implementations
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
     * @param mode Mode of the distribution for the closures.
     * @param jobs Closures to invoke. If {@code null} or empty - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used.
     * @param <R> Type of the closure return value.
     * @return Future collection of closure results. Order is undefined.
     * @see PN
     * @see #call(GridClosureCallMode, Collection, GridPredicate[])
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Executes given jobs on this projection.
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision
     * resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message and calculates its length
     * by splitting it by spaces, calculating the length of each word on individual (remote) grid node
     * and then summing (reducing) results from all nodes to produce the final length of the input string
     * using function APIs, typedefs, and execution closures on the grid:
     * <pre name="code" class="java">
     * public static int length(final String msg) throws GridException {
     *     return G.grid().call(SPREAD, F.yield(msg.split(" "), F.cInvoke("length")), F.sumIntReducer());
     * }
     * </pre>
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
     * @param mode Mode of the distribution for the closure.
     * @param jobs Closures to executes. If {@code null} or empty - this method is no-op.
     * @param rdc Result reducing closure. If {@code null} - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used.
     * @param <R1> Closure result type.
     * @param <R2> Type of the reduced value.
     * @return Value produced by reducing closure. if this method is no-op, {@code null} is returned.
     * @throws GridException Thrown in case of any error.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see PN
     * @see #reduceAsync(GridClosureCallMode, Collection, GridReducer, GridPredicate[])
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2> R2 reduce(GridClosureCallMode mode, @Nullable Collection<? extends Callable<R1>> jobs,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Asynchronously executes given jobs on this projection.
     * <p>
     * Unlike its sibling method {@link #reduce(GridClosureCallMode, Collection, GridReducer, GridPredicate[])}
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
     * @param mode Mode of the distribution for the closure.
     * @param jobs Closures to executes. If {@code null} or empty - this method is no-op.
     * @param rdc Result reducing closure. If {@code null} - this method is no-op.
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used.
     * @param <R1> Closure result type.
     * @param <R2> Type of the reduced value.
     * @return Future value produced by reducing closure.  if this method is no-op, future with {@code null}
     *      value is returned.
     * @see PN
     * @see #reduce(GridClosureCallMode, Collection, GridReducer, GridPredicate[])
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends Callable<R1>> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Sends given message to the nodes in this projection.
     *
     * @param msg Message to send. If {@code null} - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used.
     * @throws GridException If failed to send a message to any of the nodes.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     */
    public void send(@Nullable Object msg, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Sends given messages to the nodes in this projection.
     *
     * @param msgs Messages to send. Order of the sending is undefined. If the method produces
     *      the exception none or some messages could have been sent already.
     *      If {@code null} or empty - this method is no-op.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If none provided - all nodes in this projection will be used.
     * @throws GridException If failed to send a message to any of the nodes.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     */
    public void send(@Nullable Collection<?> msgs, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException;

    /**
     * Gets a node for given ID from this optionally filtered projection.
     *
     * @param nid Node ID.
     * @param p Optional set of filtering predicates. If non provided - all nodes in this
     *      projection will be included.
     * @return Node with given ID from this projection or {@code null} if such node does not exist in this
     *      projection.
     */
    @Nullable public GridRichNode node(UUID nid, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Gets collection of daemon nodes in this projection.
     * <p>
     * Daemon nodes are the usual grid nodes that participate in topology but not
     * visible on the main APIs, i.e. they are not part of any projections. The only
     * way to see daemon nodes is to use this method.
     * <p>
     * Daemon nodes are used primarily for management and monitoring functionality that
     * is build on GridGain and needs to participate in the topology but also needs to be
     * excluded from "normal" topology so that it won't participate in task execution
     * or data grid storage.
     *
     * @param p Optional set of predicates. If none provided - all daemon nodes will be returned.
     * @return Collection of daemon nodes, possible empty, but never {@code null}.
     */
    public Collection<GridRichNode> daemonNodes(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Gets collection of nodes for given node ID8. ID8 is not strictly unique node ID since
     * it returns first 8 characters of full node ID (which is UUID). ID8 is used for GUI
     * purposes and monitoring. Note that since ID8 is not strictly unique, this method may
     * return collection with more than one node matching given ID8.
     *
     * @param id8 node ID8.
     * @return Collection of nodes matching this ID8. Empty collection is returned when no
     *      nodes match given ID8.
     */
    public Collection<GridRichNode> nodeId8(String id8);

    /**
     * Tests whether this optionally filtered projection has any nodes in it.
     *
     * @param p Optional set of filtering predicates. If non provided - all nodes in this
     *      projection will be included.
     * @return {@code true} if at the time of calling this projection has at least
     *      one node - {@code false} otherwise.
     */
    public boolean isEmptyFor(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Tests whether this projection has any nodes in it.
     *
     * @return {@code true} if at the time of calling this projection has at least
     *      one node - {@code false} otherwise.
     */
    public boolean isEmpty();

    /**
     * Tests whether or not this optionally filtered projection contains given node.
     *
     * @param node Node to check.
     * @param p Optional set of filtering predicates. If non provided - all nodes in this
     *      projection will be included.
     * @return {@code true} if this node is in this projection, {@code false} otherwise.
     */
    public boolean contains(GridNode node, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Tests whether or not this optionally filtered projection contains a node with given node ID.
     * Note that previously stored ID of the node does not necessarily point to a still valid
     * node as that node may have left topology by now.
     *
     * @param nid Node ID to check.
     * @param p Optional set of filtering predicates. If non provided - all nodes in this
     *      projection will be included.
     * @return {@code true} if the node with given node ID is in this projection, {@code false} otherwise.
     */
    public boolean contains(UUID nid, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Queries nodes in this projection for events using passed in predicate filter for event selection.
     * This operation is distributed and hence can fail on communication layer and generally can
     * take much longer than local event notifications. Note that this method will block until
     * all results are received and method is complete.
     *
     * @param pe Predicate filter used to query events on remote nodes.
     * @param pn Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used.
     * @param timeout Maximum time to wait for result, {@code 0} to wait forever.
     * @return Collection of grid events returned from specified nodes.
     * @throws GridException If query failed to execute.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see #remoteEventsAsync(GridPredicate, long, GridPredicate[])
     * @see PE
     * @see PN
     */
    public List<GridEvent> remoteEvents(GridPredicate<? super GridEvent> pe, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... pn) throws GridException;

    /**
     * Asynchronously queries nodes in this projection for events using passed in predicate filter for event
     * selection. This operation is distributed and hence can fail on communication layer and generally can
     * take much longer than local event notifications. Note that this method will not block and will return
     * immediately with future.
     *
     * @param pe Predicate filter used to query events on remote nodes.
     * @param pn Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided - all nodes in this projection will be used.
     * @param timeout Maximum time to wait for result, {@code 0} to wait forever.
     * @return Collection of grid events returned from specified nodes.
     * @see #remoteEvents(GridPredicate, long, GridPredicate[])
     * @see PE
     * @see PN
     */
    public GridFuture<List<GridEvent>> remoteEventsAsync(GridPredicate<? super GridEvent> pe, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... pn);

    /**
     * Convenient utility listening method for messages from the nodes in this projection.
     * This method provides a convenient idiom of protocol-based message exchange with
     * automatic listener management.
     * <p>
     * When this method is called it will register message listener for the messages <tt>only from nodes
     * in this projection</tt> and return immediately without blocking. On the background, for each received
     * messages from these nodes it will call passed in predicates. If all predicates return {@code true} -
     * it will continue listen for the new messages. If at least one predicate returns {@code false} -
     * it will unregister the listener and stop receiving messages. Note that checking predicates will
     * be short-circuit if a predicate evaluates to {@code false}.
     * <p>
     * Note that all predicates will be called in synchronized context so that only one thread can
     * access given predicate at a time.
     *
     * @param p Collection of predicates that is called on each received message. If all predicates
     *      return {@code true} - the implementation will continue listen for the new messages. If any
     *      predicate returns {@code false} - the implementation will unregister the listener and stop
     *      receiving messages.
     *      <p>
     *      If none provided - this method is no-op.
     * @param <T> Type of the message.
     * @see GridListenActor
     * @see #remoteListenAsync(Collection, GridPredicate2[])
     * @see #remoteListenAsync(GridPredicate, GridPredicate2[])
     */
    public <T> void listen(@Nullable GridPredicate2<UUID, ? super T>... p);

    /**
     * Registers given message listeners on <b>all nodes defined by this projection</b> to listen for
     * messages sent <b>from the given {@code node}</b>. Messages can be sent using one of the following
     * methods:
     * <ul>
     *     <li>{@link #send(Object, GridPredicate[])}</li>
     *     <li>{@link #send(Collection, GridPredicate[])}</li>
     * </ul>
     * Essentially, this method allows to "wire up" sender and receiver(s) of the messages in a
     * completely distributed manner. Note that this method will take a current snapshot of nodes in
     * this projection which is an important consideration for dynamic projection that can have a
     * constantly changing set of nodes.
     *
     * @param node Node to listen for message from. If {@code null} this method is no-op.
     * @param p Collection of predicates that are called on each received message. If all predicates
     *      return {@code true} - the implementation will continue listen for the new messages. If any
     *      predicate returns {@code false} - the implementation will unregister the listener and stop
     *      receiving messages.
     *      <p>
     *      If none provided - this method is no-op.
     * @param <T> Type of the message.
     * @return Future for this distributed operation.
     * @see GridListenActor
     * @see #listen(GridPredicate2[])
     * @see #remoteListenAsync(GridPredicate, GridPredicate2[])
     * @see #send(Object, GridPredicate[])
     * @see #send(Collection, GridPredicate[])
     */
    public <T> GridFuture<?> remoteListenAsync(@Nullable GridNode node,
        @Nullable GridPredicate2<UUID, ? super T>... p);

    /**
     * Registers given message listeners on <b>all nodes defined by this projection</b> to listen for
     * messages sent <b>from the given {@code nodes}</b>. Messages can be sent using one of the following
     * methods:
     * <ul>
     *     <li>{@link #send(Object, GridPredicate[])}</li>
     *     <li>{@link #send(Collection, GridPredicate[])}</li>
     * </ul>
     * Essentially, this method allows to "wire up" senders and receiver(s) of the messages in a
     * completely distributed manner. Note that this method will take a current snapshot of nodes in
     * this projection which is an important consideration for dynamic projection that can have a
     * constantly changing set of nodes.
     *
     * @param nodes Nodes to listen for message from. If {@code null} or empty
     *      this method is no-op.
     * @param p Collection of predicates that is called on each received message. If all predicates
     *      return {@code true} - the implementation will continue listen for the new messages. If any
     *      predicate returns {@code false} - the implementation will unregister the listener and stop
     *      receiving messages.
     *      <p>
     *      If none provided - this method is no-op.
     * @param <T> Type of the message.
     * @return Future for this distributed operation.
     * @see GridListenActor
     * @see #listen(GridPredicate2[])
     * @see #remoteListenAsync(GridPredicate, GridPredicate2[])
     */
    public <T> GridFuture<?> remoteListenAsync(@Nullable Collection<? extends GridNode> nodes,
        @Nullable GridPredicate2<UUID, ? super T>... p);

    /**
     * Registers given message listeners on <b>all nodes defined by this projection</b> to listen for
     * messages sent <b>from the nodes</b> defined via predicate. Messages can be sent using one of the
     * following methods:
     * <ul>
     *     <li>{@link #send(Object, GridPredicate[])}</li>
     *     <li>{@link #send(Collection, GridPredicate[])}</li>
     * </ul>
     * Essentially, this method allows to "wire up" sender(s) and receiver(s) of the messages in a
     * completely distributed manner. Note that this method will take a current snapshot of nodes in
     * this projection which is an important consideration for dynamic projection that can have a
     * constantly changing set of nodes.
     *
     * @param pn Predicate to define nodes on each node from this projection to listen for messages
     *      from. If not provided - this method is no-op.
     * @param p Collection of predicates that is called on each received message. If all predicates
     *      return {@code true} - the implementation will continue listen for the new messages. If any
     *      predicate returns {@code false} - the implementation will unregister the listener and stop
     *      receiving messages.
     *      <p>
     *      If none provided - this method is no-op.
     * @param <T> Type of the message.
     * @return Future for this distributed operation.
     * @see GridListenActor
     * @see #listen(GridPredicate2[])
     * @see #remoteListenAsync(Collection, GridPredicate2[])
     */
    public <T> GridFuture<?> remoteListenAsync(@Nullable GridPredicate<? super GridRichNode> pn,
        @Nullable GridPredicate2<UUID, ? super T>... p);

    /**
     * Creates new {@link ExecutorService} which will execute all submitted
     * {@link Callable} and {@link Runnable} tasks on this projection. This essentially
     * creates a <b><i>Distributed Thread Pool</i</b> that can be used as a drop-in
     * replacement for local thread pools to gain easy distributed processing
     * capabilities.
     * <p>
     * User may run {@link Callable} and {@link Runnable} tasks
     * just like normally with {@link ExecutorService java.util.ExecutorService}.
     * <p>
     * The typical Java example could be:
     * <pre name="code" class="java">
     * ...
     * ExecutorService exec = grid.executor();
     *
     * Future&lt;String&gt; fut = exec.submit(new MyCallable());
     * ...
     * String res = fut.get();
     * ...
     * </pre>
     *
     * @param p Optional set of predicates. All predicates must evaluate to {@code true} for a node to be
     *      included. If none provided or {@code null} - all nodes in this projection will be used.
     * @return {@code ExecutorService} which delegates all calls to grid.
     */
    public ExecutorService executor(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs given collection of jobs producing result on this projection using
     * given mapper to map jobs to nodes. See the description of {@link GridMapper}
     * for mapper details.
     * <p>
     * Note that unlike its sibling {@link #callAsync(GridMapper, Collection, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mapper Mapper used to map jobs to nodes. If {@code null} - this method is no-op.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R> Type of job result.
     * @return Collection of job results.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R> Collection<R> call(@Nullable GridMapper<Callable<R>, GridRichNode> mapper,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException;

    /**
     * Runs given collection of jobs producing result on this projection using
     * given mapper to map jobs to nodes. See the description of {@link GridMapper}
     * for mapper details.
     * <p>
     * Note that unlike its sibling {@link #call(GridMapper, Collection, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mapper Mapper used to map jobs to nodes. If {@code null} - this method is no-op.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R> Type of job result.
     * @return Future of job results collection.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R> GridFuture<Collection<R>> callAsync(@Nullable GridMapper<Callable<R>, GridRichNode> mapper,
        @Nullable Collection<? extends Callable<R>> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs given collection of jobs taking argument and producing result on this
     * projection with given collection of arguments using given distribution mode.
     * <p>
     * Note that unlike its sibling
     * {@link #callAsync(GridClosureCallMode, Collection, Collection, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param args Jobs' arguments (closure free variables).
     * @param <T> Type of job argument.
     * @param <R> Type of job result.
     * @return Collection of job results.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T, R> Collection<R> call(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs given collection of jobs taking argument and producing result on this
     * projection with given collection of arguments using given distribution mode.
     * <p>
     * Note that unlike its sibling
     * {@link #call(GridClosureCallMode, Collection, Collection, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param args Jobs' arguments (closure free variables).
     * @param <T> Type of job argument.
     * @param <R> Type of job result.
     * @return Future of job results collection.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job taking argument and producing result on this projection with given
     * collection of arguments using given distribution mode. The job is sequentially
     * executed on every single argument from the collection so that number of actual
     * executions for any distribution mode except {@link GridClosureCallMode#BROADCAST}
     * will be equal to size of collection of arguments.
     * <p>
     * Note that unlike its sibling
     * {@link #callAsync(GridClosureCallMode, GridClosure, Collection, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param args Job arguments (closure free variables).
     * @param <T> Type of job argument.
     * @param <R> Type of job result.
     * @return Collection of job results.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T, R> Collection<R> call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs job taking argument and producing result on this projection with given
     * collection of arguments using given distribution mode. The job is sequentially
     * executed on every single argument from the collection so that number of actual
     * executions for any distribution mode except {@link GridClosureCallMode#BROADCAST}
     * will be equal to size of collection of arguments.
     * <p>
     * Note that unlike its sibling
     * {@link #call(GridClosureCallMode, GridClosure, Collection, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param args Job arguments (closure free variables).
     * @param <T> Type of job argument.
     * @param <R> Type of job result.
     * @return Future of job results collection.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R> job, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job taking argument and producing result on this projection with given
     * producer of arguments using given distribution mode. The job is sequentially
     * executed on every single argument produced by the producer so that number of actual
     * executions for any distribution mode except {@link GridClosureCallMode#BROADCAST}
     * will be equal to number of produced arguments specified by {@code cnt}.
     * <p>
     * Note that unlike its sibling
     * {@link #callAsync(GridClosureCallMode, GridClosure, GridOutClosure, int, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param pdc Producer of job arguments.
     * @param cnt Number of arguments to produce.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @param <R> Type of job result.
     * @return Collection of job results.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T, R> Collection<R> call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException;

    /**
     * Runs job taking argument and producing result on this projection with given
     * producer of arguments using given distribution mode. The job is sequentially
     * executed on every single argument produced by the producer so that number of actual
     * executions for any distribution mode except {@link GridClosureCallMode#BROADCAST}
     * will be equal to number of produced arguments specified by {@code cnt}.
     * <p>
     * Note that unlike its sibling
     * {@link #call(GridClosureCallMode, GridClosure, GridOutClosure, int, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param pdc Producer of job arguments. If {@code null} - this method is no-op.
     * @param cnt Number of arguments to produce.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @param <R> Type of job result.
     * @return Future of job results collection.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs given collection of jobs taking argument on this projection with given
     * collection of arguments using given distribution mode.
     * <p>
     * Note that unlike its sibling
     * {@link #runAsync(GridClosureCallMode, Collection, Collection, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param args Job arguments.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @throws GridException Thrown in case of any failure.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *  thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T> void run(GridClosureCallMode mode, @Nullable Collection<? extends GridInClosure<? super T>> jobs,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException;

    /**
     * Runs given collection of jobs taking argument on this projection with given
     * collection of arguments using given distribution mode.
     * <p>
     * Note that unlike its sibling
     * {@link #run(GridClosureCallMode, Collection, Collection, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param args Job arguments.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @return Future for jobs' execution.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T> GridFuture<?> runAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridInClosure<? super T>> jobs,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job taking argument on this projection with given collection of arguments
     * using given distribution mode. The job is sequentially executed on every single
     * argument from the collection so that number of actual executions for any
     * distribution mode except {@link GridClosureCallMode#BROADCAST} will be equal to
     * size of collection of arguments.
     * <p>
     * Note that unlike its sibling
     * {@link #runAsync(GridClosureCallMode, GridInClosure, Collection, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param args Job arguments.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException;

    /**
     * Runs job taking argument on this projection with given collection of arguments
     * using given distribution mode. The job is sequentially executed on every single
     * argument from the collection so that number of actual executions for any
     * distribution mode except {@link GridClosureCallMode#BROADCAST} will be equal to
     * size of collection of arguments.
     * <p>
     * Note that unlike its sibling
     * {@link #run(GridClosureCallMode, GridInClosure, Collection, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param args Job arguments.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @return Future for job execution.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job taking argument on this projection with given producer of arguments
     * using given distribution mode. The job is sequentially executed on every single
     * argument produced by the producer so that number of actual executions for any
     * distribution mode except {@link GridClosureCallMode#BROADCAST} will be equal to
     * number of produced arguments specified by {@code cnt}.
     * <p>
     * Note that unlike its sibling
     * {@link #runAsync(GridClosureCallMode, GridInClosure, GridOutClosure, int, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param pdc Producer of job arguments. If {@code null} - this method is no-op.
     * @param cnt Number of arguments to produce.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @throws GridException Thrown in case of any failure.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException;

    /**
     * Runs job taking argument on this projection with given producer of arguments
     * using given distribution mode. The job is sequentially executed on every single
     * argument produced by the producer so that number of actual executions for any
     * distribution mode except {@link GridClosureCallMode#BROADCAST} will be equal to
     * number of produced arguments specified by {@code cnt}.
     * <p>
     * Note that unlike its sibling
     * {@link #run(GridClosureCallMode, GridInClosure, GridOutClosure, int, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param pdc Producer of job arguments. If {@code null} - this method is no-op.
     * @param cnt Number of arguments to produce.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @return Future for job execution.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs given collection of jobs taking argument and producing result on this
     * projection with given collection of arguments using given distribution mode
     * and then reduces job results to a single execution result using provided reducer.
     * See {@link GridReducer} for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #reduceAsync(GridClosureCallMode, Collection, Collection, GridReducer, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param args Job arguments.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Result reduced from job results with given reducer. if this method is no-op,
     *      {@code null} is returned.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> R2 reduce(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs given collection of jobs taking argument and producing result on this
     * projection with given collection of arguments using given distribution mode
     * and then reduces job results to a single execution result using provided reducer.
     * See {@link GridReducer} for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #reduce(GridClosureCallMode, Collection, Collection, GridReducer, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param args Job arguments.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Future of reduced result. if this method is no-op, future with {@code null} value is returned.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job taking argument and producing result on this projection with given
     * collection of arguments using given distribution mode. The job is sequentially
     * executed on every single argument from the collection so that number of actual
     * executions for any distribution mode except {@link GridClosureCallMode#BROADCAST}
     * will be equal to size of collection of arguments. Then method reduces these job
     * results to a single execution result using provided reducer. See {@link GridReducer}
     * for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #reduceAsync(GridClosureCallMode, GridClosure, Collection, GridReducer, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param args Job arguments.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Result reduced from job results with given reducer. if this method is no-op,
     *      {@code null} is returned.
     * @throws GridException Thrown in case of any failure.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> R2 reduce(GridClosureCallMode mode, @Nullable GridClosure<? super T, R1> job,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs job taking argument and producing result on this projection with given
     * collection of arguments using given distribution mode. The job is sequentially
     * executed on every single argument from the collection so that number of actual
     * executions for any distribution mode except {@link GridClosureCallMode#BROADCAST}
     * will be equal to size of collection of arguments. Then method reduces these job
     * results to a single execution result using provided reducer. See {@link GridReducer}
     * for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #reduce(GridClosureCallMode, GridClosure, Collection, GridReducer, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param args Job arguments.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Future of reduced result. if this method is no-op, future with {@code null} value is returned.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode, @Nullable GridClosure<? super T, R1> job,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job taking argument and producing result on this projection with given
     * producer of arguments using given distribution mode. The job is sequentially
     * executed on every single argument produced by the producer so that number of actual
     * executions for any distribution mode except {@link GridClosureCallMode#BROADCAST}
     * will be equal to number of produced arguments specified by {@code cnt}. Then method
     * reduces these job results to a single execution result using provided reducer. See
     * {@link GridReducer} for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #reduceAsync(GridClosureCallMode, GridClosure, GridOutClosure, int, GridReducer, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param pdc Producer of job arguments. If {@code null} - this method is no-op.
     * @param cnt Number of arguments to produce.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Result reduced from job results with given reducer. if this method is no-op,
     *      {@code null} is returned.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> R2 reduce(GridClosureCallMode mode, @Nullable GridClosure<? super T, R1> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs job taking argument and producing result on this projection with given
     * producer of arguments using given distribution mode. The job is sequentially
     * executed on every single argument produced by the producer so that number of actual
     * executions for any distribution mode except {@link GridClosureCallMode#BROADCAST}
     * will be equal to number of produced arguments specified by {@code cnt}. Then method
     * reduces these job results to a single execution result using provided reducer. See
     * {@link GridReducer} for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #reduce(GridClosureCallMode, GridClosure, GridOutClosure, int, GridReducer, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param pdc Producer of job arguments. If {@code null} - this method is no-op.
     * @param cnt Number of arguments to produce.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Future of reduced result. if this method is no-op, future with {@code null} value is returned.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode, @Nullable GridClosure<? super T, R1> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs given collection of jobs taking argument and producing result on this
     * projection with given collection of arguments using given mapper to map jobs
     * to nodes and then reduces job results to a single execution result using
     * provided reducer. See {@link GridMapper} for mapper details and {@link GridReducer}
     * for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #mapreduceAsync(GridMapper, Collection, Collection, GridReducer, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mapper Mapper used to map jobs to nodes.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param args Job arguments.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Result reduced from job results with given reducer. if this method is no-op,
     *      {@code null} is returned.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs given collection of jobs taking argument and producing result on this
     * projection with given collection of arguments using given mapper to map jobs
     * to nodes and then reduces job results to a single execution result using
     * provided reducer. See {@link GridMapper} for mapper details and {@link GridReducer}
     * for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #mapreduce(GridMapper, Collection, Collection, GridReducer, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mapper Mapper used to map jobs to nodes.
     * @param jobs Jobs to run. If {@code null} or empty - this method is no-op.
     * @param args Job arguments.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Future of reduced result. if this method is no-op, future with {@code null} value
     *      is returned.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> GridFuture<R2> mapreduceAsync(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job taking argument and producing result on this projection with given
     * collection of arguments using given mapper to map job executions to nodes.
     * The job is sequentially executed on every single argument from the collection
     * so that number of actual executions for any distribution mode except
     * {@link GridClosureCallMode#BROADCAST} will be equal to size of collection of
     * arguments. Then method reduces these job results to a single execution result
     * using provided reducer. See {@link GridMapper} for mapper details and
     * {@link GridReducer} for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #mapreduceAsync(GridMapper, GridClosure, Collection, GridReducer, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mapper Mapper used to map jobs to nodes.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param args Job arguments.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Result reduced from job results with given reducer. if this method is no-op,
     *      {@code null} is returned.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs job taking argument and producing result on this projection with given
     * collection of arguments using given mapper to map job executions to nodes.
     * The job is sequentially executed on every single argument from the collection
     * so that number of actual executions for any distribution mode except
     * {@link GridClosureCallMode#BROADCAST} will be equal to size of collection of
     * arguments. Then method reduces these job results to a single execution result
     * using provided reducer. See {@link GridMapper} for mapper details and
     * {@link GridReducer} for reducer details.
     * <p>
     *
     * Note that unlike its sibling
     * {@link #mapreduce(GridMapper, GridClosure, Collection, GridReducer, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mapper Mapper used to map jobs to nodes.
     * @param job  If {@code null} - this method is no-op.
     * @param args Job arguments.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Future of reduced result. if this method is no-op, future with {@code null} value is returned.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> GridFuture<R2> mapreduceAsync(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job taking argument and producing result on this projection with given
     * producer of arguments using given mapper to map job executions to nodes. The job
     * is sequentially executed on every single argument produced by the producer so that
     * number of actual executions for any distribution mode except
     * {@link GridClosureCallMode#BROADCAST} will be equal to number of produced arguments
     * specified by {@code cnt}. Then method reduces these job results to a single execution
     * result using provided reducer. See {@link GridMapper} for mapper details and
     * {@link GridReducer} for reducer details.
     * <p>
     * Note that unlike its sibling
     * {@link #mapreduceAsync(GridMapper, GridClosure, GridOutClosure, int, GridReducer, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mapper Mapper used to map jobs to nodes.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param pdc Producer of job arguments. If {@code null} - this method is no-op.
     * @param cnt Number of arguments to produce.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Result reduced from job results with given reducer. if this method is no-op,
     *      {@code null} is returned.
     * @throws GridException Thrown in case of any failure.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs job taking argument and producing result on this projection with given
     * producer of arguments using given mapper to map job executions to nodes. The job
     * is sequentially executed on every single argument produced by the producer so that
     * number of actual executions for any distribution mode except
     * {@link GridClosureCallMode#BROADCAST} will be equal to number of produced arguments
     * specified by {@code cnt}. Then method reduces these job results to a single execution
     * result using provided reducer. See {@link GridMapper} for mapper details and
     * {@link GridReducer} for reducer details.
     *
     * Note that unlike its sibling
     * {@link #mapreduce(GridMapper, GridClosure, GridOutClosure, int, GridReducer, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mapper Mapper used to map jobs to nodes.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param pdc Producer of job arguments. If {@code null} - this method is no-op.
     * @param cnt Number of arguments to produce.
     * @param rdc Job result reducer. If {@code null} - this method is no-op.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R1> Type of job result.
     * @param <R2> Type of reduced result.
     * @param <T> Type of job argument.
     * @return Future of reduced result. if this method is no-op, future with {@code null} value is returned.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R1, R2, T> GridFuture<R2> mapreduceAsync(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job that doesn't produce any result with given argument on this projection
     * using given distribution mode. Note that unlike its sibling
     * {@link #callAsync(GridClosureCallMode, GridClosure, Object, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
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
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs job that doesn't produce any result with given argument on this projection
     * using given distribution mode. Note that unlike its sibling
     * {@link #run(GridClosureCallMode, GridInClosure, Object, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param arg Job argument.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <T> Type of job argument.
     * @return Future for job execution.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable T arg, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Runs job producing result with given argument on this projection using given
     * distribution mode. Note that unlike its sibling
     * {@link #callAsync(GridClosureCallMode, GridClosure, Object, GridPredicate[])}
     * this method will block until execution is complete, timeout expires, execution
     * is cancelled or exception is thrown.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param arg Job argument.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R> Type of job result.
     * @param <T> Type of job argument.
     * @return Closure result. Note that in case when there are more than one result (when call mode is
     *      not {@link GridClosureCallMode#UNICAST}) - all results will be received but only first one
     *      will be returned (and all other will be discarded). Such non-unicast executions make sense
     *      primarily for side-effects only closures (i.e. closures that have no return value).
     * @throws GridException Thrown in case of any failure.
     * @throws GridInterruptedException Subclass of {@link GridException}
     *      thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException}
     *      thrown if computation was cancelled.
     * @throws GridEmptyProjectionException Thrown in case when this projection is empty.
     *      Note that in case of dynamic projection this method will take a snapshot of all the
     *      nodes at the time of this call, apply all filtering predicates, if any, and if the
     *      resulting collection of nodes is empty - the exception will be thrown.
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R, T> R call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Runs job producing result with given argument on this projection using given
     * distribution mode. Note that unlike its sibling
     * {@link #call(GridClosureCallMode, GridClosure, Object, GridPredicate[])}
     * this method doesn't block and immediately returns with future of execution.
     *
     * @param mode Distribution mode.
     * @param job Job to run. If {@code null} - this method is no-op.
     * @param arg Job argument.
     * @param p Optional set of predicates filtering execution topology. If not
     *      provided - all nodes in this projection will be included.
     * @param <R> Type of job result.
     * @param <T> Type of job argument.
     * @return Non-cancellable closure result future. Note that in case when there are more than one result
     *      (when call mode is not {@link GridClosureCallMode#UNICAST}) - all results will be received but
     *      only first one will be returned (and all other will be discarded). Such non-unicast executions
     *      make sense primarily for side-effects only closures (i.e. closures that have no return value).
     * @see #callAsync(GridClosureCallMode, Callable, GridPredicate[])
     * @see #withCheckpointSpi(String)
     * @see #withFailoverSpi(String)
     * @see #withName(String)
     * @see #withResultClosure(GridClosure2X)
     * @see #withTopologySpi(String)
     */
    public <R, T> GridFuture<R> callAsync(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable T arg, @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * This method provides ability to detect which cache keys are mapped to which nodes
     * on default unnamed cache instance. Use it to determine which nodes are storing which
     * keys prior to sending jobs that access these keys.
     * <p>
     * This method works as following:
     * <ul>
     * <li>For local caches it returns only local node mapped to all keys.</li>
     * <li>
     *      For fully replicated caches {@link GridCacheAffinity} is
     *      used to determine which keys are mapped to which nodes.
     * </li>
     * <li>For partitioned caches, the returned map represents node-to-key affinity.</li>
     * </ul>
     *
     * @param keys Cache keys to map to nodes.
     * @return Map of nodes to cache keys.
     * @throws GridException If failed to map cache keys.
     */
    @GridEnterpriseFeature("Data affinity outside of GridCache is enterprise-only feature.")
    public <K> Map<GridRichNode, Collection<K>> mapKeysToNodes(@Nullable Collection<? extends K> keys)
        throws GridException;

    /**
     * This method provides ability to detect which cache keys are mapped to which nodes
     * on cache instance with given name. Use it to determine which nodes are storing which
     * keys prior to sending jobs that access these keys.
     * <p>
     * This method works as following:
     * <ul>
     * <li>For local caches it returns only local node mapped to all keys.</li>
     * <li>
     *      For fully replicated caches, {@link GridCacheAffinity} is
     *      used to determine which keys are mapped to which groups of nodes.
     * </li>
     * <li>For partitioned caches, the returned map represents node-to-key affinity.</li>
     * </ul>
     *
     * @param cacheName Cache name, if {@code null}, then default cache instance is used.
     * @param keys Cache keys to map to nodes.
     * @return Map of node IDs to cache keys.
     * @throws GridException If failed to map cache keys.
     */
    @GridEnterpriseFeature("Data affinity outside of GridCache is enterprise-only feature.")
    public <K> Map<GridRichNode, Collection<K>> mapKeysToNodes(@Nullable String cacheName,
        @Nullable Collection<? extends K> keys) throws GridException;

    /**
     * This method provides ability to detect which keys are mapped to which nodes on
     * default unnamed cache instance. Use it to determine which nodes are storing which
     * keys prior to sending jobs that access these keys.
     * <p>
     * This method works as following:
     * <ul>
     * <li>For local caches it returns only local node ID.</li>
     * <li>
     *      For fully replicated caches first node ID returned by {@link GridCacheAffinity}
     *      is returned.
     * </li>
     * <li>For partitioned caches, the returned node ID is the primary node for the key.</li>
     * </ul>
     *
     * @param key Cache key to map to a node.
     * @return Primary node for the key or {@code null} if cache with default name
     *      is not present in the grid.
     * @throws GridException If failed to map key.
     */
    @GridEnterpriseFeature("Data affinity outside of GridCache is enterprise-only feature.")
    @Nullable public <K> GridRichNode mapKeyToNode(K key) throws GridException;

    /**
     * This method provides ability to detect which cache keys are mapped to which nodes
     * on cache instance with given name. Use it to determine which nodes are storing which
     * keys prior to sending jobs that access these keys.
     * <p>
     * This method works as following:
     * <ul>
     * <li>For local caches it returns only local node ID.</li>
     * <li>
     *      For fully replicated caches first node ID returned by {@link GridCacheAffinity}
     *      is returned.
     * </li>
     * <li>For partitioned caches, the returned node ID is the primary node for the key.</li>
     * </ul>
     *
     * @param cacheName Cache name, if {@code null}, then default cache instance is used.
     * @param key Cache key to map to a node.
     * @return Primary node for the key or {@code null} if cache with given name
     *      is not present in the grid.
     * @throws GridException If failed to map key.
     */
    @GridEnterpriseFeature("Data affinity outside of GridCache is enterprise-only feature.")
    @Nullable public <K> GridRichNode mapKeyToNode(@Nullable String cacheName, K key) throws GridException;

    /**
     * Starts one or more nodes on remote host(s) defined by file with host specifications
     * (one specification per line).
     * <p>
     * Parameters for each host are defined in specification which has the following format:
     * <tt>&lt;username&gt;:&lt;password&gt;@&lt;hostname&gt;:&lt;port&gt;#&lt;nodes&gt;</tt>
     * (only hostname is required).
     * <p>
     * If username or password are not provided in specification, default values from
     * {@code dfltUname} and {@code dfltPasswd} parameters are used.
     * <p>
     * Default port is {@code 22}.
     * <p>
     * <tt>&lt;nodes&gt;</tt> defines number of nodes that should run on the host. E.g., if
     * {@code 5} nodes are expected and {@code 3} are already running, only {@code 2} new
     * nodes will be started.
     * <p>
     * One specification can define several hosts if their IPs are sequential.
     * E.g., {@code 10.0.0.1~5} defines range of five IP addresses. Other parameters
     * (username, password, etc.) are applied to all hosts equally.
     * <p>
     * Returned result contains tuples. Each corresponds to one node start attempt and contains
     * hostname, success flag and error message if attempt was not successful. Note that successful
     * attempt doesn't mean that node was actually started and joined topology. For large topologies
     * (> 100s nodes) it can take over 10 minutes for all nodes to start. See individual node logs
     * for details.
     * <p>
     * Note that SSH remote execution requires that all environment properties be set globally
     * on the remote node. Standard GridGain ggstart.{sh|bat} script needs both GRIDGAIN_HOME
     * (cam also be passed in {@code ggHome} parameter) and JAVA_HOME environment variables
     * set globally for SSH-based execution to work.
     * <p>
     * On Linux - you can use '/etc/environment' file to set global
     * environment variables at the login time.
     * Mac OSX currently doesn't support automatic setting of global
     * variable and you need to provide custom start script in this case.
     * On Windows use standard way to set environment properties.
     *
     * @param file File with host specifications.
     * @param dfltUname Default username (used if specification doesn't contain username).
     * @param dfltPasswd Default password (used if specification doesn't contain password).
     * @param key Private key file. Define it if server requires key authentication.
     * @param nodes Expected number of nodes on the host. If some nodes are started
     *      already, then only remaining nodes will be started. If current count of
     *      nodes is equal to this number, and {@code restart} flag is {@code false},
     *      then nothing will happen.
     * @param ggHome Path to GridGain installation folder. If {@code null}, global
     *      GRIDGAIN_HOME environment variable must be set on remote hosts.
     * @param cfg Path to configuration file (optional).
     * @param script Path to start script (optional).
     * @param log Path to log file (optional).
     * @param restart Whether to stop existing nodes. If {@code true}, all existing
     *      nodes on the host will be stopped before starting new ones. If {@code false},
     *      nodes will be started only if there are less nodes on the host than expected.
     * @return Collection of tuples, each contains host name, result (success of failure)
     *      and error message (if any).
     * @throws GridException In case of error.
     */
    public Collection<GridTuple3<String, Boolean, String>> startNodes(
        File file,
        @Nullable String dfltUname,
        @Nullable String dfltPasswd,
        @Nullable File key,
        int nodes,
        @Nullable String ggHome,
        @Nullable String cfg,
        @Nullable String script,
        @Nullable String log,
        boolean restart
    ) throws GridException;

    /**
     * Starts one or more nodes on remote host(s) defined by collection of host specifications.
     * <p>
     * Parameters for each host are defined in specification which has the following format:
     * <tt>&lt;username&gt;:&lt;password&gt;@&lt;hostname&gt;:&lt;port&gt;#&lt;nodes&gt;</tt>
     * (only hostname is required).
     * <p>
     * If username or password are not provided in specification, default values from
     * {@code dfltUname} and {@code dfltPasswd} parameters are used.
     * <p>
     * Default port is {@code 22}.
     * <p>
     * <tt>&lt;nodes&gt;</tt> defines number of nodes that should run on the host. E.g., if
     * {@code 5} nodes are expected and {@code 3} are already running, only {@code 2} new
     * nodes will be started.
     * <p>
     * One specification can define several hosts if their IPs are sequential.
     * E.g., {@code 10.0.0.1~5} defines range of five IP addresses. Other parameters
     * (username, password, etc.) are applied to all hosts equally.
     * <p>
     * Returned result contains tuples. Each corresponds to one node start attempt and contains
     * hostname, success flag and error message if attempt was not successful. Note that successful
     * attempt doesn't mean that node was actually started and joined topology. For large topologies
     * (> 100s nodes) it can take over 10 minutes for all nodes to start. See individual node logs
     * for details.
     * <p>
     * Note that SSH remote execution requires that all environment properties be set globally
     * on the remote node. Standard GridGain ggstart.{sh|bat} script needs both GRIDGAIN_HOME
     * (cam also be passed in {@code ggHome} parameter) and JAVA_HOME environment variables
     * set globally for SSH-based execution to work.
     * <p>
     * On Linux - you can use '/etc/environment' file to set global
     * environment variables at the login time.
     * Mac OSX currently doesn't support automatic setting of global
     * variable and you need to provide custom start script in this case.
     * On Windows use standard way to set environment properties.
     *
     * @param hostSpecs Collection of host specifications.
     * @param dfltUname Default username (used if specification doesn't contain username).
     * @param dfltPasswd Default password (used if specification doesn't contain password).
     * @param key Private key file. Define it if server requires key authentication.
     * @param nodes Expected number of nodes on the host. If some nodes are started
     *      already, then only remaining nodes will be started. If current count of
     *      nodes is equal to this number, and {@code restart} flag is {@code false},
     *      then nothing will happen.
     * @param ggHome Path to GridGain installation folder. If {@code null}, global
     *      GRIDGAIN_HOME environment variable must be set on remote hosts.
     * @param cfg Path to configuration file (optional).
     * @param script Path to start script (optional).
     * @param log Path to log file (optional).
     * @param restart Whether to stop existing nodes. If {@code true}, all existing
     *      nodes on the host will be stopped before starting new ones. If {@code false},
     *      nodes will be started only if there are less nodes on the host than expected.
     * @return Collection of tuples, each contains host name, result (success of failure)
     *      and error message (if any).
     * @throws GridException In case of error.
     */
    public Collection<GridTuple3<String, Boolean, String>> startNodes(
        Collection<String> hostSpecs,
        @Nullable String dfltUname,
        @Nullable String dfltPasswd,
        @Nullable File key,
        int nodes,
        @Nullable String ggHome,
        @Nullable String cfg,
        @Nullable String script,
        @Nullable String log,
        boolean restart
    ) throws GridException;

    /**
     * Stops nodes satisfying optional set of predicates.
     *
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be stopped.
     * @throws GridException In case of error.
     */
    public void stopNodes(@Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Stops nodes defined by provided IDs.
     *
     * @param id ID defining node to stop.
     * @param ids IDs defining nodes to stop.
     * @throws GridException In case of error.
     */
    public void stopNodes(UUID id, @Nullable UUID... ids) throws GridException;

    /**
     * Stops nodes defined by provided IDs.
     *
     * @param ids IDs defining nodes to stop.
     * @throws GridException In case of error.
     */
    public void stopNodes(Collection<UUID> ids) throws GridException;

    /**
     * Restarts nodes satisfying optional set of predicates.
     *
     * @param p Optional set of filtering predicates. If none provided - all nodes in this projection
     *      will be restarted.
     * @throws GridException In case of error.
     */
    public void restartNodes(@Nullable GridPredicate<? super GridRichNode>... p) throws GridException;

    /**
     * Restarts nodes defined by provided IDs.
     *
     * @param id ID defining node to restart.
     * @param ids IDs defining nodes to restart.
     * @throws GridException In case of error.
     */
    public void restartNodes(UUID id, @Nullable UUID... ids) throws GridException;

    /**
     * Restarts nodes defined by provided IDs.
     *
     * @param ids IDs defining nodes to restart.
     * @throws GridException In case of error.
     */
    public void restartNodes(Collection<UUID> ids) throws GridException;
}

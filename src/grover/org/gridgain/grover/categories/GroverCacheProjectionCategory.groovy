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

import org.gridgain.grid.*
import org.gridgain.grid.cache.*
import org.gridgain.grid.cache.query.*
import static org.gridgain.grid.cache.query.GridCacheQueryType.*
import org.gridgain.grid.lang.*
import org.gridgain.grover.lang.*

/**
 * Extensions for {@code GridCacheProjection}.
 * <p>
 * To access methods from the category target class has
 * to be annotated: {@code @Use(GroverCacheProjectionCategory)}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
class GroverCacheProjectionCategory {
    /**
     * Convert {@link java.util.Map.Entry} to {@link GroverTuple}.
     *
     * @param e Entry.
     * @return Tuple.
     */
    private static <K, V> GroverTuple<K, V> toTuple(Map.Entry<K, V> e) {
        [e.key, e.value]
    }

    /**
     * Converts tuples reducer to Grid Reducer that takes map entries.
     *
     * @param rdc GroverTuple reducer.
     * @return Entry reducer.
     */
    private static <K, V, R> GridReducer<Map.Entry<K, V>, R> toEntryReducer(
        GroverReducer<GroverTuple<K, V>, R> rdc) {
        new EntryReducer<K, V, R>(rdc)
    }

    /**
     * Creates and executes ad-hoc {@code SCAN} query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See {@code GridCacheQuery} for more details.
     * @param vp Value filter. See {@code GridCacheQuery} for more details..
     * @return Collection of cache key-value pairs.
     */
    static <K, V> Collection<GroverTuple<K, V>> scan(GridCacheProjection<K, V> cp,
        GridProjection grid, Class<? extends V> cls, GridPredicate<K> kp, GridPredicate<V> vp) {
        assert grid != null
        assert cls != null
        assert kp != null
        assert vp != null

        def q = cp.createQuery(SCAN, cls, null)

        q.remoteKeyFilter { Object[] arr -> kp }
        q.remoteValueFilter { Object[] arr -> vp }

        q.execute(grid).get().collect { e -> toTuple(e) }
    }

    /**
     * Creates and executes ad-hoc {@code SCAN} query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See {@code GridCacheQuery} for more details.
     * @param vp Value filter. See {@code GridCacheQuery} for more details..
     * @return Collection of cache key-value pairs.
     */
    static <K, V> Collection<GroverTuple<K, V>> scan(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, GridPredicate<K> kp, GridPredicate<V> vp) {
        assert cp != null
        assert cls != null
        assert kp != null
        assert vp != null

        scan(cp, cp.cache().gridProjection().grid(), cls, kp, vp)
    }

    /**
     * Creates and executes ad-hoc {@code SQL} query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See {@code GridCacheQuery} for more details.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    static <K, V> Collection<GroverTuple<K, V>> sql(GridCacheProjection<K, V> cp,
        GridProjection grid, Class<? extends V> cls, String clause, Object... args) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert args != null

        GridCacheQuery<K, V> q

        if (args.length > 0)
            q = cp.createQuery(SQL, cls, clause).queryArguments(args)
        else
            q = cp.createQuery(SQL, cls, clause)

        q.execute(grid).get().collect { e -> toTuple(e) }
    }

    /**
     * Creates and executes ad-hoc {@code SQL} query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See {@code GridCacheQuery} for more details.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    static <K, V> Collection<GroverTuple<K, V>> sql(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, Object... args) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert args != null

        sql(cp, cp.cache().gridProjection().grid(), cls, clause, args)
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See {@code GridCacheQuery} for more details.
     * @return Collection of cache key-value pairs.
     */
    static <K, V> Collection<GroverTuple<K, V>> lucene(GridCacheProjection<K, V> cp,
        GridProjection grid, Class<? extends V> cls, String clause) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null

        cp.createQuery(LUCENE, cls, clause).execute(grid).get().collect { e -> toTuple(e) }
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See {@code GridCacheQuery} for more details.
     * @return Collection of cache key-value pairs.
     */
    static <K, V> Collection<GroverTuple<K, V>> lucene(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause) {
        assert cp != null
        assert cls != null
        assert clause != null

        lucene(cp, cp.cache().gridProjection().grid(), cls, clause)
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See {@code GridCacheQuery} for more details.
     * @return Collection of cache key-value pairs.
     */
    static <K, V> Collection<GroverTuple<K, V>> h2Text(GridCacheProjection<K, V> cp,
        GridProjection grid, Class<? extends V> cls, String clause) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null

        cp.createQuery(H2TEXT, cls, clause).execute(grid).get().collect { e -> toTuple(e) }
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See {@code GridCacheQuery} for more details.
     * @return Collection of cache key-value pairs.
     */
    static <K, V> Collection<GroverTuple<K, V>> h2Text(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause) {
        assert cp != null
        assert cls != null
        assert clause != null

        h2Text(cp, cp.cache().gridProjection().grid(), cls, clause)
    }

    /**
     * Creates and executes ad-hoc {@code SCAN} transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See {@code GridCacheQuery} for more details.
     * @param vp Value filter. See {@code GridCacheQuery} for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    static <K, V, T> Collection<GroverTuple<K, T>> scanTransform(GridCacheProjection<K, V> cp,
        GridProjection grid, Class<? extends V> cls, GridPredicate<K> kp, GridPredicate<V> vp,
        GridClosure<V, T> trans) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert kp != null
        assert vp != null
        assert trans != null

        def q = cp.<T>createTransformQuery(SCAN, cls, null)

        q.remoteKeyFilter { Object[] arr -> kp }
        q.remoteValueFilter { Object[] arr -> vp }
        q.remoteTransformer { Object[] arr -> trans }

        q.execute(grid).get().collect { e -> toTuple(e) }
    }

    /**
     * Creates and executes ad-hoc {@code SCAN} transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See {@code GridCacheQuery} for more details.
     * @param vp Value filter. See {@code GridCacheQuery} for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    static <K, V, T> Collection<GroverTuple<K, T>> scanTransform(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, GridPredicate<K> kp, GridPredicate<V> vp, GridClosure<V, T> trans) {
        assert cp != null
        assert cls != null
        assert kp != null
        assert vp != null
        assert trans != null

        scanTransform(cp, cp.cache().gridProjection().grid(), cls, kp, vp, trans)
    }

    /**
     * Creates and executes ad-hoc {@code SQL} transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See {@code GridCacheQuery} for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    static <K, V, T> Collection<GroverTuple<K, T>> sqlTransform(GridCacheProjection<K, V> cp,
        GridProjection grid, Class<? extends V> cls, String clause, GridClosure<V, T> trans,
        Object... args) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert trans != null
        assert args != null

        GridCacheTransformQuery<K, V, T> q

        if (args.length > 0)
            q = cp.<T>createTransformQuery(SQL, cls, clause).queryArguments(args)
        else
            q = cp.<T>createTransformQuery(SQL, cls, clause)

        q.remoteTransformer { Object[] arr -> trans }

        q.execute(grid).get().collect { e -> toTuple(e) }
    }

    /**
     * Creates and executes ad-hoc {@code SQL} transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See {@code GridCacheQuery} for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    static <K, V, T> Collection<GroverTuple<K, T>> sqlTransform(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GridClosure<V, T> trans, Object... args) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert trans != null
        assert args != null

        sqlTransform(cp, cp.cache().gridProjection().grid(), cls, clause, trans, args)
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See {@code GridCacheQuery} for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    static <K, V, T> Collection<GroverTuple<K, T>> luceneTransform(GridCacheProjection<K, V> cp,
        GridProjection grid, Class<? extends V> cls, String clause, GridClosure<V, T> trans) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert trans != null

        def q = cp.<T>createTransformQuery(LUCENE, cls, clause)

        q.remoteTransformer { Object[] arr -> trans }

        q.execute(grid).get().collect { e -> toTuple(e) }
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See {@code GridCacheQuery} for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    static <K, V, T> Collection<GroverTuple<K, T>> luceneTransform(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GridClosure<V, T> trans) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert trans != null

        luceneTransform(cp, cp.cache().gridProjection().grid(), cls, clause, trans)
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See {@code GridCacheQuery} for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    static <K, V, T> Collection<GroverTuple<K, T>> h2TextTransform(GridCacheProjection<K, V> cp,
        GridProjection grid, Class<? extends V> cls, String clause, GridClosure<V, T> trans) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert trans != null

        def q = cp.<T>createTransformQuery(H2TEXT, cls, clause)

        q.remoteTransformer { Object[] arr -> trans }

        q.execute(grid).get().collect { e -> toTuple(e) }
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See {@code GridCacheQuery} for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    static <K, V, T> Collection<GroverTuple<K, T>> h2TextTransform(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GridClosure<V, T> trans) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert trans != null

        h2TextTransform(cp, cp.cache().gridProjection().grid(), cls, clause, trans)
    }

    /**
     * Creates and executes ad-hoc {@code SCAN} reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See {@code GridCacheQuery} for more details.
     * @param vp Value filter. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    static <K, V, R1, R2> R2 scanReduce(GridCacheProjection<K, V> cp, GridProjection grid,
        Class<? extends V> cls, GridPredicate<K> kp, GridPredicate<V> vp,
        GroverReducer<GroverTuple<K, V>, R1> rmtRdc, GroverReducer<R1, R2> locRdc) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert kp != null
        assert vp != null
        assert rmtRdc != null
        assert locRdc != null

        def q = cp.<R1, R2>createReduceQuery(SCAN, cls, null)

        q.remoteKeyFilter { Object[] arr -> kp }
        q.remoteValueFilter { Object[] arr -> vp }
        q.remoteReducer { Object[] arr -> toEntryReducer(rmtRdc) }
        q.localReducer { Object[] arr -> locRdc }

        q.reduce(grid).get()
    }

    /**
     * Creates and executes ad-hoc {@code SCAN} reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See {@code GridCacheQuery} for more details.
     * @param vp Value filter. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    static <K, V, R1, R2> R2 scanReduce(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, GridPredicate<K> kp, GridPredicate<V> vp,
        GroverReducer<GroverTuple<K, V>, R1> rmtRdc, GroverReducer<R1, R2> locRdc) {
        assert cp != null
        assert cls != null
        assert kp != null
        assert vp != null
        assert rmtRdc != null
        assert locRdc != null

        scanReduce(cp, cp.cache().gridProjection().grid(), cls, kp, vp, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc {@code SQL} reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @param args Optional list of query arguments.
     * @return Reduced value.
     */
    static <K, V, R1, R2> R2 sqlReduce(GridCacheProjection<K, V> cp, GridProjection grid,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R1> rmtRdc,
        GroverReducer<R1, R2> locRdc, Object... args) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null
        assert locRdc != null
        assert args != null

        GridCacheReduceQuery<K, V, R1, R2> q

        if (args.length > 0)
            q = cp.<R1, R2>createReduceQuery(SQL, cls, clause).queryArguments(args)
        else
            q = cp.<R1, R2>createReduceQuery(SQL, cls, clause)

        q.remoteReducer { Object[] arr -> toEntryReducer(rmtRdc) }
        q.localReducer { Object[] arr -> locRdc }

        q.reduce(grid).get()
    }

    /**
     * Creates and executes ad-hoc {@code SQL} reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @param args Optional list of query arguments.
     * @return Reduced value.
     */
    static <K, V, R1, R2> R2 sqlReduce(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R1> rmtRdc,
        GroverReducer<R1, R2> locRdc, Object... args) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null
        assert locRdc != null
        assert args != null

        sqlReduce(cp, cp.cache().gridProjection().grid(), cls, clause, rmtRdc, locRdc, args)
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    static <K, V, R1, R2> R2 luceneReduce(GridCacheProjection<K, V> cp, GridProjection grid,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R1> rmtRdc,
        GroverReducer<R1, R2> locRdc) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null
        assert locRdc != null

        def q = cp.<R1, R2>createReduceQuery(LUCENE, cls, clause)

        q.remoteReducer { Object[] arr -> toEntryReducer(rmtRdc) }
        q.localReducer { Object[] arr -> locRdc }

        q.reduce(grid).get()
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    static <K, V, R1, R2> R2 luceneReduce(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R1> rmtRdc,
        GroverReducer<R1, R2> locRdc) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null
        assert locRdc != null

        luceneReduce(cp, cp.cache().gridProjection().grid(), cls, clause, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    static <K, V, R1, R2> R2 h2TextReduce(GridCacheProjection<K, V> cp, GridProjection grid,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R1> rmtRdc,
        GroverReducer<R1, R2> locRdc) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null
        assert locRdc != null

        def q = cp.<R1, R2>createReduceQuery(H2TEXT, cls, clause)

        q.remoteReducer { Object[] arr -> toEntryReducer(rmtRdc) }
        q.localReducer { Object[] arr -> locRdc }

        q.reduce(grid).get()
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    static <K, V, R1, R2> R2 h2TextReduce(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R1> rmtRdc,
        GroverReducer<R1, R2> locRdc) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null
        assert locRdc != null

        h2TextReduce(cp, cp.cache().gridProjection().grid(), cls, clause, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc {@code SCAN} reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See {@code GridCacheQuery} for more details.
     * @param vp Value filter. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    static <K, V, R> Collection<R> scanReduce(GridCacheProjection<K, V> cp, GridProjection grid,
        Class<? extends V> cls, GridPredicate<K> kp, GridPredicate<V> vp,
        GroverReducer<GroverTuple<K, V>, R> rmtRdc) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert kp != null
        assert vp != null
        assert rmtRdc != null

        def q = cp.<R, R>createReduceQuery(SCAN, cls, null)

        q.remoteKeyFilter { Object[] arr -> kp }
        q.remoteValueFilter { Object[] arr -> vp }
        q.remoteReducer { Object[] arr -> toEntryReducer(rmtRdc) }

        q.reduceRemote(grid).get()
    }

    /**
     * Creates and executes ad-hoc {@code SCAN} reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See {@code GridCacheQuery} for more details.
     * @param vp Value filter. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    static <K, V, R> Collection<R> scanReduce(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, GridPredicate<K> kp, GridPredicate<V> vp,
        GroverReducer<GroverTuple<K, V>, R> rmtRdc) {
        assert cp != null
        assert cls != null
        assert kp != null
        assert vp != null
        assert rmtRdc != null

        scanReduce(cp, cp.cache().gridProjection().grid(), cls, kp, vp, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc {@code SQL} reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param args Optional list of query arguments.
     * @return Collection of reduced values.
     */
    static <K, V, R> Collection<R> sqlRemoteReduce(GridCacheProjection<K, V> cp, GridProjection grid,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R> rmtRdc,
        Object... args) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null
        assert args != null

        GridCacheReduceQuery<K, V, R, R> q

        if (args.length > 0)
            q = cp.<R, R>createReduceQuery(SQL, cls, clause).queryArguments(args)
        else
            q = cp.<R, R>createReduceQuery(SQL, cls, clause)

        q.remoteReducer { Object[] arr -> toEntryReducer(rmtRdc) }

        q.reduceRemote(grid).get()
    }

    /**
     * Creates and executes ad-hoc {@code SQL} reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param args Optional list of query arguments.
     * @return Collection of reduced values.
     */
    static <K, V, R> Collection<R> sqlRemoteReduce(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R> rmtRdc,
        Object... args) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null
        assert args != null

        sqlRemoteReduce(cp, cp.cache().gridProjection().grid(), cls, clause, rmtRdc, args)
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    static <K, V, R> Collection<R> luceneReduce(GridCacheProjection<K, V> cp, GridProjection grid,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R> rmtRdc) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null

        def q = cp.<R, R>createReduceQuery(LUCENE, cls, clause)

        q.remoteReducer { Object[] arr -> toEntryReducer(rmtRdc) }

        q.reduceRemote(grid).get()
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    static <K, V, R> Collection<R> luceneReduce(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R> rmtRdc) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null

        luceneReduce(cp, cp.cache().gridProjection().grid(), cls, clause, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    static <K, V, R> Collection<R> h2TextReduce(GridCacheProjection<K, V> cp, GridProjection grid,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R> rmtRdc) {
        assert cp != null
        assert grid != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null

        def q = cp.<R, R>createReduceQuery(H2TEXT, cls, clause)

        q.remoteReducer { Object[] arr -> toEntryReducer(rmtRdc) }

        q.reduceRemote(grid).get()
    }

    /**
     * Creates and executes ad-hoc {@code LUCENE} reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC {@code PreparedStatement}.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of {@code V}
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See {@code GridCacheQuery} for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    static <K, V, R> Collection<R> h2TextReduce(GridCacheProjection<K, V> cp,
        Class<? extends V> cls, String clause, GroverReducer<GroverTuple<K, V>, R> rmtRdc) {
        assert cp != null
        assert cls != null
        assert clause != null
        assert rmtRdc != null

        h2TextReduce(cp, cp.cache().gridProjection().grid(), cls, clause, rmtRdc)
    }

    /**
     * Entry reducer.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    private static class EntryReducer<K, V, R> extends GridReducer<Map.Entry<K, V>, R> {
        private final List<GroverTuple<K, V>> buf = new ArrayList<GroverTuple<K, V>>()

        private final GroverReducer<GroverTuple<K, V>, R> rdc

        EntryReducer(GroverReducer<GroverTuple<K, V>, R> rdc) {
            this.rdc = rdc
        }

        @Override boolean collect(Map.Entry<K, V> e) {
            buf.add(toTuple(e))

            true
        }

        @Override R apply() {
            rdc.reduce(buf)
        }
    }
}

package org.gridgain.scalar.pimps

// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */

import org.gridgain.scalar._
import scalar._
import org.gridgain.grid.cache._
import query.GridCacheQueryType._
import org.jetbrains.annotations.Nullable
import org.gridgain.grid._
import org.gridgain.grid.lang._
import collection._
import JavaConversions._
import scalaz._

/**
 * Companion object.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
object ScalarCacheProjectionPimp {
    /**
     * Creates new Scalar cache projection pimp with given Java-side implementation.
     *
     * @param value Java-side implementation.
     */
    def apply[K, V](impl: GridCacheProjection[K, V]) = {
        if (impl == null)
            throw new NullPointerException("impl")

        val pimp = new ScalarCacheProjectionPimp[K, V]

        pimp.impl = impl

        pimp
    }
}

/**
 * ==Overview==
 * Defines Scalar "pimp" for `GridCacheProjection` on Java side.
 *
 * Essentially this class extends Java `GridCacheProjection` interface with Scala specific
 * API adapters using primarily implicit conversions defined in `ScalarConversions` object. What
 * it means is that you can use functions defined in this class on object
 * of Java `GridCacheProjection` type. Scala will automatically (implicitly) convert it into
 * Scalar's pimp and replace the original call with a call on that pimp.
 *
 * Note that Scalar provide extensive library of implicit conversion between Java and
 * Scala GridGain counterparts in `ScalarConversions` object
 *
 * ==Suffix '$' In Names==
 * Symbol `$` is used in names when they conflict with the names in the base Java class
 * that Scala pimp is shadowing or with Java package name that your Scala code is importing.
 * Instead of giving two different names to the same function we've decided to simply mark
 * Scala's side method with `$` suffix.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
class ScalarCacheProjectionPimp[@specialized K, @specialized V] extends PimpedType[GridCacheProjection[K, V]]
    with Iterable[GridCacheEntry[K, V]] {
    /** */
    lazy val value: GridCacheProjection[K, V] = impl

    /** */
    protected var impl: GridCacheProjection[K, V] = _

    /** Type alias. */
    protected type EntryPred = (_ >: GridCacheEntry[K, V]) => Boolean

    /** Type alias */
    protected type KeyPred = K => Boolean

    /** Type alias */
    protected type ValuePred = V => Boolean

    /** Type alias. */
    protected type KvPred = (K, V) => Boolean

    /**
     * Gets iterator for cache entries.
     */
    def iterator =
        toScalaSeq(value.iterator).iterator

    /**
     * Unwraps sequence of functions to sequence of GridGain predicates.
     */
    private def unwrap(@Nullable p: Seq[EntryPred]): Seq[GridPredicate[_ >: GridCacheEntry[K, V]]] =
        if (p == null)
            null
        else
            p map ((f: EntryPred) => toPredicate(f))

    /**
     * Converts reduce function to Grid Reducer that takes map entries.
     *
     * @param rdc Reduce function.
     * @return Entry reducer.
     */
    private def toEntryReducer[R](rdc: Iterable[(K, V)] => R): GridReducer[java.util.Map.Entry[K, V], R] = {
        new GridReducer[java.util.Map.Entry[K, V], R] {
            private var seq = Seq.empty[(K, V)]

            def collect(e: java.util.Map.Entry[K, V]): Boolean = {
                seq +:= (e.getKey, e.getValue)

                true
            }

            def apply(): R = {
                rdc(seq)
            }
        }
    }

    /**
     * Retrieves value mapped to the specified key from cache. The return value of {@code null}
     * means entry did not pass the provided filter or cache has no mapping for the key.
     *
     * @param k Key to retrieve the value for.
     * @return Value for the given key.
     */
    def apply(k: K): V =
        value.get(k)

    /**
     * Retrieves value mapped to the specified key from cache as an option. The return value
     * of `null` means entry did not pass the provided filter or cache has no mapping for the key.
     *
     * @param k Key to retrieve the value for.
     * @param p Filter to check prior to getting the value. Note that filter check
     *      together with getting the value is an atomic operation.
     * @return Value for the given key.
     * @see `org.gridgain.grid.cache.GridCacheProjection.get(...)`
     */
    def opt(k: K, p: EntryPred*): Option[V] =
        Option(value.get(k, unwrap(p): _*))

    /**
     * Gets cache projection based on given key-value predicate. Whenever makes sense,
     * this predicate will be used to pre-filter cache operations. If
     * operation passed pre-filtering, this filter will be passed through
     * to cache operations as well.
     *
     * @param p Key-value predicate for this projection. If `null`, then the
     *      same projection is returned.
     * @return Projection for given key-value predicate.
     * @see `org.gridgain.grid.cache.GridCacheProjection.projection(...)`
     */
    def viewByKv(@Nullable p: ((K, V) => Boolean)*): GridCacheProjection[K, V] =
        if (p == null || p.length == 0)
            value
        else
            value.projection(p.map((f: ((K, V) => Boolean)) => toPredicate2(f)): _*)

    /**
     * Gets cache projection based on given entry filter. This filter will be simply passed through
     * to all cache operations on this projection. Unlike `viewByKv` function, this filter
     * will '''not''' be used for pre-filtering.
     *
     * @param p Filter to be passed through to all cache operations. If `null`, then the
     *      same projection is returned.  If cache operation receives its own filter, then filters
     *      will be `anded`.
     * @return Projection based on given filter.
     * @see `org.gridgain.grid.cache.GridCacheProjection.projection(...)`
     */
    def viewByEntry(@Nullable p: EntryPred*): GridCacheProjection[K, V] =
        if (p == null || p.length == 0)
            value
        else
            value.projection(unwrap(p): _*)

    /**
     * Gets cache projection only for given key and value type. Only `non-null` key-value
     * pairs that have matching key and value pairs will be used in this projection.
     *
     * ===Cache Flags===
     * The resulting projection will have flag `GridCacheFlag#STRICT` set on it.
     *
     * @param k Key type.
     * @param v Value type.
     * @return Cache projection for given key and value types.
     * @see `org.gridgain.grid.cache.GridCacheProjection.projection(...)`
     */
    def viewByType[A, B](k: Class[A], v: Class[B]):
        GridCacheProjection[A, B] = {
        assert(k != null && v != null)

        value.projection(toJavaType(k), toJavaType(v));
    }

    /**
     * Converts given type of corresponding Java type, if Scala does
     * auto-conversion for given type. Only primitive types and Strings
     * are supported.
     *
     * @param c Type to convert.
     */
    private def toJavaType(c: Class[_]) = {
        assert(c != null)

        if (c == classOf[Symbol])
            throw new GridException("Cache type projeciton on 'scala.Symbol' are not supported.")

        if (c == classOf[Int])
            classOf[java.lang.Integer]
        else if (c == classOf[Boolean])
            classOf[java.lang.Boolean]
        else if (c == classOf[String])
            classOf[java.lang.String]
        else if (c == classOf[Char])
            classOf[java.lang.Character]
        else if (c == classOf[Long])
            classOf[java.lang.Long]
        else if (c == classOf[Double])
            classOf[java.lang.Double]
        else if (c == classOf[Float])
            classOf[java.lang.Float]
        else if (c == classOf[Short])
            classOf[java.lang.Short]
        else if (c == classOf[Byte])
            classOf[java.lang.Byte]
        else
            c
    }

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * If write-through is enabled, the stored value will be persisted to `GridCacheStore`
     * via `GridCacheStore#put(String, GridCacheTx, Object, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kv Key-Value pair to store in cache.
     * @param p Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return `True` if value was stored in cache, `false` otherwise.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putx(...)`
     */
    def putx$(kv: (K, V), @Nullable p: EntryPred*): Boolean =
        value.putx(kv._1, kv._2, unwrap(p): _*)

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * If write-through is enabled, the stored value will be persisted to `GridCacheStore`
     * via `GridCacheStore#put(String, GridCacheTx, Object, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kv Key-Value pair to store in cache.
     * @param p Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return Previous value associated with specified key, or `null`
     *      if entry did not pass the filter, or if there was no mapping for the key in swap
     *      or in persistent storage.
     * @see `org.gridgain.grid.cache.GridCacheProjection#put(...)`
     */
    def put$(kv: (K, V), @Nullable p: EntryPred*): V =
        value.put(kv._1, kv._2, unwrap(p): _*)

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * If write-through is enabled, the stored value will be persisted to `GridCacheStore`
     * via `GridCacheStore#put(String, GridCacheTx, Object, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kv Key-Value pair to store in cache.
     * @param p Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return Previous value associated with specified key as an option.
     * @see `org.gridgain.grid.cache.GridCacheProjection#put(...)`
     */
    def putOpt$(kv: (K, V), @Nullable p: EntryPred*): Option[V] =
        Option(value.put(kv._1, kv._2, unwrap(p): _*))

    /**
     * Operator alias for the same function `putx$`.
     *
     * @param kv Key-Value pair to store in cache.
     * @param p Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return `True` if value was stored in cache, `false` otherwise.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putx(...)`
     */
    def +=(kv: (K, V), @Nullable p: EntryPred*): Boolean =
        putx$(kv, p: _*)

    /**
     * Stores given key-value pairs in cache.
     *
     * If write-through is enabled, the stored values will be persisted to `GridCacheStore`
     * via `GridCacheStore#putAll(String, GridCacheTx, Map)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kv1 Key-value pair to store in cache.
     * @param kv2 Key-value pair to store in cache.
     * @param kvs Optional key-value pairs to store in cache.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putAll(...)`
     */
    def putAll$(kv1: (K, V), kv2: (K, V), @Nullable kvs: (K, V)*) {
        var m = mutable.Map.empty[K, V]

        m += (kv1, kv2)

        if (kvs != null)
            kvs foreach (m += _)

        value.putAll(m)
    }

    /**
     * Stores given key-value pairs from the sequence in cache.
     *
     * If write-through is enabled, the stored values will be persisted to `GridCacheStore`
     * via `GridCacheStore#putAll(String, GridCacheTx, Map)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kvs Key-value pairs to store in cache. If `null` this function is no-op.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putAll(...)`
     */
    def putAll$(@Nullable kvs: Seq[(K, V)]) {
        if (kvs != null)
            value.putAll(mutable.Map(kvs: _*))
    }

    /**
     * Removes given key mappings from cache.
     *
     * If write-through is enabled, the values will be removed from `GridCacheStore`
     * via `GridCacheStore#removeAll(String, GridCacheTx, Collection)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param ks Sequence of additional keys to remove. If `null` - this function is no-op.
     * @see `org.gridgain.grid.cache.GridCacheProjection#removeAll(...)`
     */
    def removeAll$(@Nullable ks: Seq[K]) {
        if (ks != null)
            value.removeAll(ks)
    }

    /**
     * Operator alias for the same function `putAll$`.
     *
     * @param kv1 Key-value pair to store in cache.
     * @param kv2 Key-value pair to store in cache.
     * @param kvs Optional key-value pairs to store in cache.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putAll(...)`
     */
    def +=(kv1: (K, V), kv2: (K, V), @Nullable kvs: (K, V)*) {
        putAll$(kv1, kv2, kvs: _*)
    }

    /**
     * Removes given key mapping from cache. If cache previously contained value for the given key,
     * then this value is returned. Otherwise, in case of `GridCacheMode#REPLICATED` caches,
     * the value will be loaded from swap and, if it's not there, and read-through is allowed,
     * from the underlying `GridCacheStore` storage. In case of `GridCacheMode#PARTITIONED`
     * caches, the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, `GridCacheStore#load(String, GridCacheTx, Object)` method will be used.
     *
     * If the returned value is not needed, method `removex$(...)` should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     *
     * If write-through is enabled, the value will be removed from 'GridCacheStore'
     * via `GridCacheStore#remove(String, GridCacheTx, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param k Key whose mapping is to be removed from cache.
     * @param p Optional filters to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Previous value associated with specified key, or `null`
     *      if there was no value for this key.
     * @see `org.gridgain.grid.cache.GridCacheProjection#remove(...)`
     */
    def remove$(k: K, @Nullable p: EntryPred*): V =
        value.remove(k, unwrap(p): _*)

    /**
     * Removes given key mapping from cache. If cache previously contained value for the given key,
     * then this value is returned. Otherwise, in case of `GridCacheMode#REPLICATED` caches,
     * the value will be loaded from swap and, if it's not there, and read-through is allowed,
     * from the underlying `GridCacheStore` storage. In case of `GridCacheMode#PARTITIONED`
     * caches, the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, `GridCacheStore#load(String, GridCacheTx, Object)` method will be used.
     *
     * If the returned value is not needed, method `removex$(...)` should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     *
     * If write-through is enabled, the value will be removed from 'GridCacheStore'
     * via `GridCacheStore#remove(String, GridCacheTx, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param k Key whose mapping is to be removed from cache.
     * @param p Optional filters to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Previous value associated with specified key as an option.
     * @see `org.gridgain.grid.cache.GridCacheProjection#remove(...)`
     */
    def removeOpt$(k: K, @Nullable p: EntryPred*): Option[V] =
        Option(value.remove(k, unwrap(p): _*))

    /**
     * Operator alias for the same function `remove$`.
     *
     * @param k Key whose mapping is to be removed from cache.
     * @param p Optional filters to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Previous value associated with specified key, or `null`
     *      if there was no value for this key.
     * @see `org.gridgain.grid.cache.GridCacheProjection#remove(...)`
     */
    def -=(k: K, @Nullable p: EntryPred*): V =
        remove$(k, p: _*)

    /**
     * Removes given key mappings from cache.
     *
     * If write-through is enabled, the values will be removed from `GridCacheStore`
     * via `GridCacheStore#removeAll(String, GridCacheTx, Collection)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param k1 1st key to remove.
     * @param k2 2nd key to remove.
     * @param ks Optional sequence of additional keys to remove.
     * @see `org.gridgain.grid.cache.GridCacheProjection#removeAll(...)`
     */
    def removeAll$(k1: K, k2: K, @Nullable ks: K*) {
        val s = new mutable.ArraySeq[K](2 + (if (ks == null) 0 else ks.length))

        k1 +: s
        k2 +: s

        if (ks != null)
            ks foreach (_ +: s)

        value.removeAll(s)
    }

    /**
     * Operator alias for the same function `remove$`.
     *
     * @param k1 1st key to remove.
     * @param k2 2nd key to remove.
     * @param ks Optional sequence of additional keys to remove.
     * @see `org.gridgain.grid.cache.GridCacheProjection#removeAll(...)`
     */
    def -=(k1: K, k2: K, @Nullable ks: K*) {
        removeAll$(k1, k2, ks: _*)
    }

    /**
     * Creates and executes ad-hoc `SCAN` query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details..
     * @return Collection of cache key-value pairs.
     */
    def scan(grid: GridProjection, cls: Class[_ <: V], kp: KeyPred, vp: ValuePred): Iterable[(K, V)] = {
        assert(grid != null)
        assert(cls != null)
        assert(kp != null)
        assert(vp != null)

        val q = value.createQuery(SCAN, cls, null)

        q.remoteKeyFilter((a: Array[AnyRef]) => toPredicate(kp))
        q.remoteValueFilter((a: Array[AnyRef]) => toPredicate(vp))

        q.execute(grid).get.map(e => (e.getKey, e.getValue))
    }

    /**
     * Creates and executes ad-hoc `SCAN` query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details..
     * @return Collection of cache key-value pairs.
     */
    def scan(grid: GridProjection, kp: KeyPred, vp: ValuePred)
        (implicit m: Manifest[V]): Iterable[(K, V)] = {
        assert(grid != null)
        assert(kp != null)
        assert(vp != null)

        scan(grid, m.erasure.asInstanceOf[Class[V]], kp, vp)
    }

    /**
     * Creates and executes ad-hoc `SCAN` query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details..
     * @return Collection of cache key-value pairs.
     */
    def scan(cls: Class[_ <: V], kp: KeyPred, vp: ValuePred): Iterable[(K, V)] = {
        assert(cls != null)
        assert(kp != null)
        assert(vp != null)

        scan(value.cache.gridProjection.grid, cls, kp, vp)
    }

    /**
     * Creates and executes ad-hoc `SCAN` query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details..
     * @return Collection of cache key-value pairs.
     */
    def scan(kp: KeyPred, vp: ValuePred)(implicit m: Manifest[V]): Iterable[(K, V)] = {
        assert(kp != null)
        assert(vp != null)

        scan(m.erasure.asInstanceOf[Class[V]], kp, vp)
    }

    /**
     * Creates and executes ad-hoc `SQL` query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    def sql(grid: GridProjection, cls: Class[_ <: V], clause: String, args: Any*): Iterable[(K, V)] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(args != null)

        val q =
            if (!args.isEmpty)
                value.createQuery(SQL, cls, clause).queryArguments(args.toArray)
            else
                value.createQuery(SQL, cls, clause)

        q.execute(grid).get.map(e => (e.getKey, e.getValue))
    }

    /**
     * Creates and executes ad-hoc `SQL` query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    def sql(grid: GridProjection, clause: String, args: Any*)
        (implicit m: Manifest[V]): Iterable[(K, V)] = {
        assert(grid != null)
        assert(clause != null)
        assert(args != null)

        sql(grid, m.erasure.asInstanceOf[Class[V]], clause, args: _*)
    }

    /**
     * Creates and executes ad-hoc `SQL` query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    def sql(cls: Class[_ <: V], clause: String, args: Any*): Iterable[(K, V)] = {
        assert(cls != null)
        assert(clause != null)

        sql(value.cache.gridProjection.grid, cls, clause, args: _*)
    }

    /**
     * Creates and executes ad-hoc `SQL` query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    def sql(clause: String, args: Any*)(implicit m: Manifest[V]): Iterable[(K, V)] = {
        assert(clause != null)

        sql(m.erasure.asInstanceOf[Class[V]], clause, args: _*)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @return Collection of cache key-value pairs.
     */
    def lucene(grid: GridProjection, cls: Class[_ <: V], clause: String): Iterable[(K, V)] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)

        value.createQuery(LUCENE, cls, clause).execute(grid).get.map(e => (e.getKey, e.getValue))
    }

    /**
     * Creates and executes ad-hoc `LUCENE` query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @return Collection of cache key-value pairs.
     */
    def lucene(grid: GridProjection, clause: String)(implicit m: Manifest[V]): Iterable[(K, V)] = {
        assert(grid != null)
        assert(clause != null)

        lucene(grid, m.erasure.asInstanceOf[Class[V]], clause)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @return Collection of cache key-value pairs.
     */
    def lucene(cls: Class[_ <: V], clause: String): Iterable[(K, V)] = {
        assert(cls != null)
        assert(clause != null)

        lucene(value.cache.gridProjection.grid, cls, clause)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @return Collection of cache key-value pairs.
     */
    def lucene(clause: String)(implicit m: Manifest[V]): Iterable[(K, V)] = {
        assert(clause != null)

        lucene(m.erasure.asInstanceOf[Class[V]], clause)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @return Collection of cache key-value pairs.
     */
    def h2Text(grid: GridProjection, cls: Class[_ <: V], clause: String): Iterable[(K, V)] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)

        value.createQuery(H2TEXT, cls, clause).execute(grid).get.map(e => (e.getKey, e.getValue))
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @return Collection of cache key-value pairs.
     */
    def h2Text(grid: GridProjection, clause: String)(implicit m: Manifest[V]): Iterable[(K, V)] = {
        assert(grid != null)
        assert(clause != null)

        h2Text(grid, m.erasure.asInstanceOf[Class[V]], clause)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @return Collection of cache key-value pairs.
     */
    def h2Text(cls: Class[_ <: V], clause: String): Iterable[(K, V)] = {
        assert(cls != null)
        assert(clause != null)

        h2Text(value.cache.gridProjection.grid, cls, clause)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @return Collection of cache key-value pairs.
     */
    def h2Text(clause: String)(implicit m: Manifest[V]): Iterable[(K, V)] = {
        assert(clause != null)

        h2Text(m.erasure.asInstanceOf[Class[V]], clause)
    }

    /**
     * Creates and executes ad-hoc `SCAN` transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def scanTransform[T](grid: GridProjection, cls: Class[_ <: V], kp: KeyPred, vp: ValuePred,
        trans: V => T): Iterable[(K, T)] = {
        assert(grid != null)
        assert(cls != null)
        assert(kp != null)
        assert(vp != null)
        assert(trans != null)

        val q = value.createTransformQuery[T](SCAN, cls, null)

        q.remoteKeyFilter((a: Array[AnyRef]) => toPredicate(kp))
        q.remoteValueFilter((a: Array[AnyRef]) => toPredicate(vp))
        q.remoteTransformer((a: Array[AnyRef]) => toClosure(trans))

        q.execute(grid).get.map(e => (e.getKey, e.getValue))
    }

    /**
     * Creates and executes ad-hoc `SCAN` transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def scanTransform[T](grid: GridProjection, kp: KeyPred, vp: ValuePred,
        trans: V => T)(implicit m: Manifest[V]): Iterable[(K, T)] = {
        assert(grid != null)
        assert(kp != null)
        assert(vp != null)
        assert(trans != null)

        scanTransform(grid, m.erasure.asInstanceOf[Class[V]], kp, vp, trans)
    }

    /**
     * Creates and executes ad-hoc `SCAN` transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def scanTransform[T](cls: Class[_ <: V], kp: KeyPred, vp: ValuePred,
        trans: V => T): Iterable[(K, T)] = {
        assert(cls != null)
        assert(kp != null)
        assert(vp != null)
        assert(trans != null)

        scanTransform(value.cache.gridProjection.grid, cls, kp, vp, trans)
    }

    /**
     * Creates and executes ad-hoc `SCAN` transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def scanTransform[T](kp: KeyPred, vp: ValuePred, trans: V => T)
        (implicit m: Manifest[V]): Iterable[(K, T)] = {
        assert(kp != null)
        assert(vp != null)
        assert(trans != null)

        scanTransform(m.erasure.asInstanceOf[Class[V]], kp, vp, trans)
    }

    /**
     * Creates and executes ad-hoc `SQL` transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    def sqlTransform[T](grid: GridProjection, cls: Class[_ <: V], clause: String,
        trans: V => T, args: Any*): Iterable[(K, T)] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(trans != null)
        assert(args != null)

        val q =
            if (!args.isEmpty)
                value.createTransformQuery[T](SQL, cls, clause).queryArguments(args.toArray)
            else
                value.createTransformQuery[T](SQL, cls, clause)

        q.remoteTransformer((a: Array[AnyRef]) => toClosure(trans))

        q.execute(grid).get.map(e => (e.getKey, e.getValue))
    }

    /**
     * Creates and executes ad-hoc `SQL` transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    def sqlTransform[T](grid: GridProjection, clause: String, trans: V => T, args: Any*)
        (implicit m: Manifest[V]): Iterable[(K, T)] = {
        assert(grid != null)
        assert(clause != null)
        assert(trans != null)
        assert(args != null)

        sqlTransform(grid, m.erasure.asInstanceOf[Class[V]], clause, trans, args: _*)
    }

    /**
     * Creates and executes ad-hoc `SQL` transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    def sqlTransform[T](cls: Class[_ <: V], clause: String, trans: V => T,
        args: Any*): Iterable[(K, T)] = {
        assert(cls != null)
        assert(clause != null)
        assert(trans != null)
        assert(args != null)

        sqlTransform(value.cache.gridProjection.grid, cls, clause, trans, args: _*)
    }

    /**
     * Creates and executes ad-hoc `SQL` transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @param args Optional list of query arguments.
     * @return Collection of cache key-value pairs.
     */
    def sqlTransform[T](clause: String, trans: V => T, args: Any*)
        (implicit m: Manifest[V]): Iterable[(K, T)] = {
        assert(clause != null)
        assert(trans != null)
        assert(args != null)

        sqlTransform(m.erasure.asInstanceOf[Class[V]], clause, trans, args: _*)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def luceneTransform[T](grid: GridProjection, cls: Class[_ <: V], clause: String,
        trans: V => T): Iterable[(K, T)] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(trans != null)

        val q = value.createTransformQuery[T](LUCENE, cls, clause)

        q.remoteTransformer((a: Array[AnyRef]) => toClosure(trans))

        q.execute(grid).get.map(e => (e.getKey, e.getValue))
    }

    /**
     * Creates and executes ad-hoc `LUCENE` transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def luceneTransform[T](grid: GridProjection, clause: String, trans: V => T)
        (implicit m: Manifest[V]): Iterable[(K, T)] = {
        assert(grid != null)
        assert(clause != null)
        assert(trans != null)

        luceneTransform(grid, m.erasure.asInstanceOf[Class[V]], clause, trans)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def luceneTransform[T](cls: Class[_ <: V], clause: String, trans: V => T): Iterable[(K, T)] = {
        assert(cls != null)
        assert(clause != null)
        assert(trans != null)

        luceneTransform(value.cache.gridProjection.grid, cls, clause, trans)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def luceneTransform[T](clause: String, trans: V => T)
        (implicit m: Manifest[V]): Iterable[(K, T)] = {
        assert(clause != null)
        assert(trans != null)

        luceneTransform(m.erasure.asInstanceOf[Class[V]], clause, trans)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def h2TextTransform[T](grid: GridProjection, cls: Class[_ <: V], clause: String,
        trans: V => T): Iterable[(K, T)] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(trans != null)

        val q = value.createTransformQuery[T](H2TEXT, cls, clause)

        q.remoteTransformer((a: Array[AnyRef]) => toClosure(trans))

        q.execute(grid).get.map(e => (e.getKey, e.getValue))
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` transform query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def h2TextTransform[T](grid: GridProjection, clause: String, trans: V => T)
        (implicit m: Manifest[V]): Iterable[(K, T)] = {
        assert(grid != null)
        assert(clause != null)
        assert(trans != null)

        h2TextTransform(grid, m.erasure.asInstanceOf[Class[V]], clause, trans)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def h2TextTransform[T](cls: Class[_ <: V], clause: String, trans: V => T): Iterable[(K, T)] = {
        assert(cls != null)
        assert(clause != null)
        assert(trans != null)

        h2TextTransform(value.cache.gridProjection.grid, cls, clause, trans)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` transform query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param trans Transform function that will be applied to each returned value.
     * @return Collection of cache key-value pairs.
     */
    def h2TextTransform[T](clause: String, trans: V => T)
        (implicit m: Manifest[V]): Iterable[(K, T)] = {
        assert(clause != null)
        assert(trans != null)

        h2TextTransform(m.erasure.asInstanceOf[Class[V]], clause, trans)
    }

    /**
     * Creates and executes ad-hoc `SCAN` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def scanReduce[R1, R2](grid: GridProjection, cls: Class[_ <: V], kp: KeyPred,
        vp: ValuePred, rmtRdc: Iterable[(K, V)] => R1, locRdc: Iterable[R1] => R2): R2 = {
        assert(grid != null)
        assert(cls != null)
        assert(kp != null)
        assert(vp != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        val q = value.createReduceQuery[R1, R2](SCAN, cls, null)

        q.remoteKeyFilter((a: Array[AnyRef]) => toPredicate(kp))
        q.remoteValueFilter((a: Array[AnyRef]) => toPredicate(vp))
        q.remoteReducer((a: Array[AnyRef]) => toEntryReducer(rmtRdc))
        q.localReducer((a: Array[AnyRef]) => toReducer(locRdc))

        q.reduce(grid).get
    }

    /**
     * Creates and executes ad-hoc `SCAN` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def scanReduce[R1, R2](grid: GridProjection, kp: KeyPred, vp: ValuePred,
        rmtRdc: Iterable[(K, V)] => R1, locRdc: Iterable[R1] => R2)(implicit m: Manifest[V]): R2 = {
        assert(grid != null)
        assert(kp != null)
        assert(vp != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        scanReduce(grid, m.erasure.asInstanceOf[Class[V]], kp, vp, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `SCAN` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def scanReduce[R1, R2](cls: Class[_ <: V], kp: KeyPred, vp: ValuePred,
        rmtRdc: Iterable[(K, V)] => R1, locRdc: Iterable[R1] => R2): R2 = {
        assert(cls != null)
        assert(kp != null)
        assert(vp != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        scanReduce(value.cache.gridProjection.grid, cls, kp, vp, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `SCAN` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def scanReduce[R1, R2](kp: KeyPred, vp: ValuePred, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2)(implicit m: Manifest[V]): R2 = {
        assert(kp != null)
        assert(vp != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        scanReduce(m.erasure.asInstanceOf[Class[V]], kp, vp, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `SQL` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @param args Optional list of query arguments.
     * @return Reduced value.
     */
    def sqlReduce[R1, R2](grid: GridProjection, cls: Class[_ <: V], clause: String,
        rmtRdc: Iterable[(K, V)] => R1, locRdc: Iterable[R1] => R2, args: Any*): R2 = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)
        assert(args != null)

        val q =
            if (!args.isEmpty)
                value.createReduceQuery[R1, R2](SQL, cls, clause).queryArguments(args.toArray)
            else
                value.createReduceQuery[R1, R2](SQL, cls, clause)

        q.remoteReducer((a: Array[AnyRef]) => toEntryReducer(rmtRdc))
        q.localReducer((a: Array[AnyRef]) => toReducer(locRdc))

        q.reduce(grid).get
    }

    /**
     * Creates and executes ad-hoc `SQL` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @param args Optional list of query arguments.
     * @return Reduced value.
     */
    def sqlReduce[R1, R2](grid: GridProjection, clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2, args: Any*)(implicit m: Manifest[V]): R2 = {
        assert(grid != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)
        assert(args != null)

        sqlReduce(grid, m.erasure.asInstanceOf[Class[V]], clause, rmtRdc, locRdc, args: _*)
    }

    /**
     * Creates and executes ad-hoc `SQL` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @param args Optional list of query arguments.
     * @return Reduced value.
     */
    def sqlReduce[R1, R2](cls: Class[_ <: V], clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2, args: Any*): R2 = {
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)
        assert(args != null)

        sqlReduce(value.cache.gridProjection.grid, cls, clause, rmtRdc, locRdc, args: _*)
    }

    /**
     * Creates and executes ad-hoc `SQL` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @param args Optional list of query arguments.
     * @return Reduced value.
     */
    def sqlReduce[R1, R2](clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2, args: Any*)(implicit m: Manifest[V]): R2 = {
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)
        assert(args != null)

        sqlReduce(m.erasure.asInstanceOf[Class[V]], clause, rmtRdc, locRdc, args: _*)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def luceneReduce[R1, R2](grid: GridProjection, cls: Class[_ <: V], clause: String,
        rmtRdc: Iterable[(K, V)] => R1, locRdc: Iterable[R1] => R2): R2 = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        val q = value.createReduceQuery[R1, R2](LUCENE, cls, clause)

        q.remoteReducer((a: Array[AnyRef]) => toEntryReducer(rmtRdc))
        q.localReducer((a: Array[AnyRef]) => toReducer(locRdc))

        q.reduce(grid).get
    }

    /**
     * Creates and executes ad-hoc `LUCENE` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def luceneReduce[R1, R2](grid: GridProjection, clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2)(implicit m: Manifest[V]): R2 = {
        assert(grid != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        luceneReduce(grid, m.erasure.asInstanceOf[Class[V]], clause, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def luceneReduce[R1, R2](cls: Class[_ <: V], clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2): R2 = {
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        luceneReduce(value.cache.gridProjection.grid, cls, clause, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def luceneReduce[R1, R2](clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2)(implicit m: Manifest[V]): R2 = {
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        luceneReduce(m.erasure.asInstanceOf[Class[V]], clause, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def h2TextReduce[R1, R2](grid: GridProjection, cls: Class[_ <: V], clause: String,
        rmtRdc: Iterable[(K, V)] => R1, locRdc: Iterable[R1] => R2): R2 = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        val q = value.createReduceQuery[R1, R2](H2TEXT, cls, clause)

        q.remoteReducer((a: Array[AnyRef]) => toEntryReducer(rmtRdc))
        q.localReducer((a: Array[AnyRef]) => toReducer(locRdc))

        q.reduce(grid).get
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def h2TextReduce[R1, R2](grid: GridProjection, clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2)(implicit m: Manifest[V]): R2 = {
        assert(grid != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        h2TextReduce(grid, m.erasure.asInstanceOf[Class[V]], clause, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def h2TextReduce[R1, R2](cls: Class[_ <: V], clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2): R2 = {
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        h2TextReduce(value.cache.gridProjection.grid, cls, clause, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Reduced value.
     */
    def h2TextReduce[R1, R2](clause: String, rmtRdc: Iterable[(K, V)] => R1,
        locRdc: Iterable[R1] => R2)(implicit m: Manifest[V]): R2 = {
        assert(clause != null)
        assert(rmtRdc != null)
        assert(locRdc != null)

        h2TextReduce(m.erasure.asInstanceOf[Class[V]], clause, rmtRdc, locRdc)
    }

    /**
     * Creates and executes ad-hoc `SCAN` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def scanReduce[R](grid: GridProjection, cls: Class[_ <: V], kp: KeyPred,
        vp: ValuePred, rmtRdc: Iterable[(K, V)] => R): Iterable[R] = {
        assert(grid != null)
        assert(cls != null)
        assert(kp != null)
        assert(vp != null)
        assert(rmtRdc != null)

        val q = value.createReduceQuery[R, R](SCAN, cls, null)

        q.remoteKeyFilter((a: Array[AnyRef]) => toPredicate(kp))
        q.remoteValueFilter((a: Array[AnyRef]) => toPredicate(vp))
        q.remoteReducer((a: Array[AnyRef]) => toEntryReducer(rmtRdc))

        q.reduceRemote(grid).get
    }

    /**
     * Creates and executes ad-hoc `SCAN` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def scanReduce[R](grid: GridProjection, kp: KeyPred, vp: ValuePred,
        rmtRdc: Iterable[(K, V)] => R)(implicit m: Manifest[V]): Iterable[R] = {
        assert(grid != null)
        assert(kp != null)
        assert(vp != null)
        assert(rmtRdc != null)

        scanReduce(grid, m.erasure.asInstanceOf[Class[V]], kp, vp, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc `SCAN` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def scanReduce[R](cls: Class[_ <: V], kp: KeyPred, vp: ValuePred,
        rmtRdc: Iterable[(K, V)] => R): Iterable[R] = {
        assert(cls != null)
        assert(kp != null)
        assert(vp != null)
        assert(rmtRdc != null)

        scanReduce(value.cache.gridProjection.grid, cls, kp, vp, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc `SCAN` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param kp Key filter. See `GridCacheQuery` for more details.
     * @param vp Value filter. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param locRdc Reduce function that will be called on local node.
     * @return Collection of reduced values.
     */
    def scanReduce[R](kp: KeyPred, vp: ValuePred, rmtRdc: Iterable[(K, V)] => R)
        (implicit m: Manifest[V]): Iterable[R] = {
        assert(kp != null)
        assert(vp != null)
        assert(rmtRdc != null)

        scanReduce(m.erasure.asInstanceOf[Class[V]], kp, vp, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc `SQL` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param args Optional list of query arguments.
     * @return Collection of reduced values.
     */
    def sqlReduce[R](grid: GridProjection, cls: Class[_ <: V], clause: String,
        rmtRdc: Iterable[(K, V)] => R, args: Any*): Iterable[R] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(args != null)

        val q =
            if (!args.isEmpty)
                value.createReduceQuery[R, R](SQL, cls, clause).queryArguments(args.toArray)
            else
                value.createReduceQuery[R, R](SQL, cls, clause)

        q.remoteReducer((a: Array[AnyRef]) => toEntryReducer(rmtRdc))

        q.reduceRemote(grid).get
    }

    /**
     * Creates and executes ad-hoc `SQL` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param args Optional list of query arguments.
     * @return Collection of reduced values.
     */
    def sqlReduce[R](grid: GridProjection, clause: String, rmtRdc: Iterable[(K, V)] => R,
        args: Any*)(implicit m: Manifest[V]): Iterable[R] = {
        assert(grid != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(args != null)

        sqlReduce(grid, m.erasure.asInstanceOf[Class[V]], clause, rmtRdc, args: _*)
    }

    /**
     * Creates and executes ad-hoc `SQL` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param args Optional list of query arguments.
     * @return Collection of reduced values.
     */
    def sqlReduce[R](cls: Class[_ <: V], clause: String, rmtRdc: Iterable[(K, V)] => R,
        args: Any*): Iterable[R] = {
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)
        assert(args != null)

        sqlReduce(value.cache.gridProjection.grid, cls, clause, rmtRdc, args: _*)
    }

    /**
     * Creates and executes ad-hoc `SQL` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query SQL clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @param args Optional list of query arguments.
     * @return Collection of reduced values.
     */
    def sqlReduce[R](clause: String, rmtRdc: Iterable[(K, V)] => R,
        args: Any*)(implicit m: Manifest[V]): Iterable[R] = {
        assert(clause != null)
        assert(rmtRdc != null)
        assert(args != null)

        sqlReduce(m.erasure.asInstanceOf[Class[V]], clause, rmtRdc, args: _*)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def luceneReduce[R](grid: GridProjection, cls: Class[_ <: V], clause: String,
        rmtRdc: Iterable[(K, V)] => R): Iterable[R] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)

        val q = value.createReduceQuery[R, R](LUCENE, cls, clause)

        q.remoteReducer((a: Array[AnyRef]) => toEntryReducer(rmtRdc))

        q.reduceRemote(grid).get
    }

    /**
     * Creates and executes ad-hoc `LUCENE` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def luceneReduce[R](grid: GridProjection, clause: String, rmtRdc: Iterable[(K, V)] => R)
        (implicit m: Manifest[V]): Iterable[R] = {
        assert(grid != null)
        assert(clause != null)
        assert(rmtRdc != null)

        luceneReduce(grid, m.erasure.asInstanceOf[Class[V]], clause, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def luceneReduce[R](cls: Class[_ <: V], clause: String, rmtRdc: Iterable[(K, V)] => R): Iterable[R] = {
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)

        luceneReduce(value.cache.gridProjection.grid, cls, clause, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc `LUCENE` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param clause Query Lucene clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def luceneReduce[R](clause: String, rmtRdc: Iterable[(K, V)] => R)
        (implicit m: Manifest[V]): Iterable[R] = {
        assert(clause != null)
        assert(rmtRdc != null)

        luceneReduce(m.erasure.asInstanceOf[Class[V]], clause, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def h2TextReduce[R](grid: GridProjection, cls: Class[_ <: V], clause: String,
        rmtRdc: Iterable[(K, V)] => R): Iterable[R] = {
        assert(grid != null)
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)

        val q = value.createReduceQuery[R, R](H2TEXT, cls, clause)

        q.remoteReducer((a: Array[AnyRef]) => toEntryReducer(rmtRdc))

        q.reduceRemote(grid).get
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` reduce query on given projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param grid Grid projection on which this query will be executed.
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def h2TextReduce[R](grid: GridProjection, clause: String, rmtRdc: Iterable[(K, V)] => R)
        (implicit m: Manifest[V]): Iterable[R] = {
        assert(grid != null)
        assert(clause != null)
        assert(rmtRdc != null)

        h2TextReduce(grid, m.erasure.asInstanceOf[Class[V]], clause, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def h2TextReduce[R](cls: Class[_ <: V], clause: String,
        rmtRdc: Iterable[(K, V)] => R): Iterable[R] = {
        assert(cls != null)
        assert(clause != null)
        assert(rmtRdc != null)

        h2TextReduce(value.cache.gridProjection.grid, cls, clause, rmtRdc)
    }

    /**
     * Creates and executes ad-hoc `H2TEXT` reduce query on global projection returning its result.
     *
     * Note that if query is executed more than once (potentially with different
     * arguments) it is more performant to create query via standard mechanism
     * and execute it multiple times with different arguments. The analogy is
     * similar to JDBC `PreparedStatement`.
     *
     * Note that query value class will be taken implicitly as exact type `V` of this
     * cache projection.
     *
     * @param cls Query values class. Since cache can, in general, contain values of any subtype of `V`
     *     query needs to know the exact type it should operate on.
     * @param clause Query H2 text clause. See `GridCacheQuery` for more details.
     * @param rmtRdc Reduce function that will be called on each remote node.
     * @return Collection of reduced values.
     */
    def h2TextReduce[R](clause: String, rmtRdc: Iterable[(K, V)] => R)
        (implicit m: Manifest[V]): Iterable[R] = {
        assert(clause != null)
        assert(rmtRdc != null)

        h2TextReduce(m.erasure.asInstanceOf[Class[V]], clause, rmtRdc)
    }
}

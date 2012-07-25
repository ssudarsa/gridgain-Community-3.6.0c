// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.GridException;
import org.gridgain.grid.cache.affinity.GridCacheAffinityMapped;
import org.gridgain.grid.lang.GridPredicate;
import org.gridgain.grid.lang.GridTuple2;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reflection field and method cache for classes.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@SuppressWarnings("TransientFieldNotInitialized")
public class GridReflectionCache implements Externalizable {
    /** Compares fields by name. */
    private static final Comparator<Field> FIELD_NAME_COMPARATOR = new Comparator<Field>() {
        @Override public int compare(Field f1, Field f2) {
            return f1.getName().compareTo(f2.getName());
        }
    };

    /** Compares methods by name. */
    private static final Comparator<Method> METHOD_NAME_COMPARATOR = new Comparator<Method>() {
        @Override public int compare(Method m1, Method m2) {
            return m1.getName().compareTo(m2.getName());
        }
    };

    /** Weak fields cache. If class is GC'ed, then it will be removed from this cache. */
    private transient ConcurrentMap<String, GridTuple2<List<Field>, Class<?>>> fields =
            new ConcurrentHashMap<String, GridTuple2<List<Field>, Class<?>>>();

    /** Weak methods cache. If class is GC'ed, then it will be removed from this cache. */
    private transient ConcurrentMap<String, GridTuple2<List<Method>, Class<?>>> mtds =
            new ConcurrentHashMap<String, GridTuple2<List<Method>, Class<?>>>();

    /** Field predicate. */
    private GridPredicate<Field> fp;

    /** Method predicate. */
    private GridPredicate<Method> mp;

    /**
     * Reflection cache without any method or field predicates.
     */
    public GridReflectionCache() {
        // No-op.
    }

    /**
     * Reflection cache with specified field and method predicates.
     * @param fp Field predicate.
     * @param mp Method predicate.
     */
    public GridReflectionCache(@Nullable GridPredicate<Field> fp, @Nullable GridPredicate<Method> mp) {
        this.fp = fp;
        this.mp = mp;
    }

    /**
     * Gets field value for object.
     *
     * @param o Key to get affinity key for.
     * @return Value of the field for given object or {@code null} if field was not found.
     * @throws GridException If failed.
     */
    @Nullable public Object firstFieldValue(Object o) throws GridException {
        assert o != null;

        Field f = firstField(o.getClass());

        if (f != null) {
            try {
                return f.get(o);
            }
            catch (IllegalAccessException e) {
                throw new GridException("Failed to access field for object [field=" + f + ", obj=" + o + ']', e);
            }
        }

        return null;
    }

    /**
     * Gets method return value for object.
     *
     * @param o Key to get affinity key for.
     * @return Method return value for given object or {@code null} if method was not found.
     * @throws GridException If failed.
     */
    @Nullable public Object firstMethodValue(Object o) throws GridException {
        assert o != null;

        Method m = firstMethod(o.getClass());

        if (m != null) {
            try {
                return m.invoke(o);
            }
            catch (IllegalAccessException e) {
                throw new GridException("Failed to invoke method for object [mtd=" + m + ", obj=" + o + ']', e);
            }
            catch (InvocationTargetException e) {
                throw new GridException("Failed to invoke method for object [mtd=" + m + ", obj=" + o + ']', e);
            }
        }

        return null;
    }

    /**
     * Gets first field in the class list of fields.
     *
     * @param cls Class.
     * @return First field.
     */
    @Nullable public Field firstField(Class<?> cls) {
        assert cls != null;

        List<Field> l = fields(cls);

        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Gets first method in the class list of methods.
     *
     * @param cls Class.
     * @return First method.
     */
    @Nullable public Method firstMethod(Class<?> cls) {
        assert cls != null;

        List<Method> l = methods(cls);

        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Gets fields annotated with {@link org.gridgain.grid.cache.affinity.GridCacheAffinityMapped} annotation.
     *
     * @param cls Class.
     * @return Annotated field.
     */
    public List<Field> fields(Class<?> cls) {
        assert cls != null;

        GridTuple2<List<Field>, Class<?>> t = fields.get(cls.getName());

        if (t == null || !cls.equals(t.get2())) {
            ArrayList<Field> all = new ArrayList<Field>();

            for (Class<?> c = cls; c != null && !c.equals(Object.class); c = c.getSuperclass()) {
                List<Field> l = new ArrayList<Field>();

                for (Field f : c.getDeclaredFields()) {
                    if (fp == null || fp.apply(f)) {
                        f.setAccessible(true);

                        l.add(f);
                    }
                }

                Collections.sort(l, FIELD_NAME_COMPARATOR);

                all.addAll(l);
            }

            all.trimToSize();

            fields.putIfAbsent(cls.getName(), t = new GridTuple2<List<Field>, Class<?>>(all, cls));
        }

        return t.get1();
    }


    /**
     * Gets method annotated with {@link GridCacheAffinityMapped} annotation.
     *
     * @param cls Class.
     * @return Annotated method.
     */
    public List<Method> methods(Class<?> cls) {
        assert cls != null;

        GridTuple2<List<Method>, Class<?>> t = mtds.get(cls.getName());

        if (t == null || !cls.equals(t.get2())) {
            ArrayList<Method> all = new ArrayList<Method>();

            for (Class<?> c = cls; c != null && !c.equals(Object.class); c = c.getSuperclass()) {
                List<Method> l = new ArrayList<Method>();

                for (Method m : c.getDeclaredMethods()) {
                    if (mp == null || mp.apply(m)) {
                        m.setAccessible(true);

                        l.add(m);
                    }
                }

                Collections.sort(l, METHOD_NAME_COMPARATOR);

                all.addAll(l);
            }

            all.trimToSize();

            mtds.putIfAbsent(cls.getName(), t = new GridTuple2<List<Method>, Class<?>>(all, cls));
        }

        return t.get1();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(fp);
        out.writeObject(mp);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Start fresh.
        fields = new ConcurrentHashMap<String, GridTuple2<List<Field>, Class<?>>>();
        mtds = new ConcurrentHashMap<String, GridTuple2<List<Method>, Class<?>>>();

        fp = (GridPredicate<Field>)in.readObject();
        mp = (GridPredicate<Method>)in.readObject();
    }
}

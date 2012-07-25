// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Represents single class deployment.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridDeployment extends GridMetadataAwareAdapter implements GridDeploymentInfo {
    /** Timestamp. */
    private final long timestamp = System.currentTimeMillis();

    /** Deployment mode. */
    private final GridDeploymentMode depMode;

    /** Class loader. */
    private final ClassLoader clsLdr;

    /** Class loader ID. */
    private final GridUuid clsLdrId;

    /** User version. */
    private final String userVer;

    /** Sequence number. */
    private final long seqNum;

    /** Flag indicating local (non-p2p) deployment. */
    private final boolean local;

    /** */
    private final AtomicReference<Class<?>> sampleCls = new AtomicReference<Class<?>>();

    /** */
    private final String sampleClsName;

    /** {@code True} if undeployed. */
    private volatile boolean undeployed;

    /** {@code True} if undeploy was scheduled. */
    private volatile boolean pendingUndeploy;

    /** Current usage count. */
    private int usage;

    /** Mutex. */
    private final Object mux = new Object();

    /** Class annotations. */
    @GridToStringExclude
    private final ConcurrentMap<Class<?>,
        ConcurrentMap<Class<? extends Annotation>, AtomicReference<Annotation>>> anns =
        new ConcurrentHashMap<Class<?>, ConcurrentMap<Class<? extends Annotation>, AtomicReference<Annotation>>>(1);

    /** Classes. */
    @GridToStringExclude
    private final ConcurrentMap<String, Class<?>> clss = new ConcurrentHashMap<String, Class<?>>();

    /** Field cache. */
    @GridToStringExclude
    private final ConcurrentMap<Class<?>, ConcurrentMap<Class<? extends Annotation>, Collection<Field>>> fieldCache =
        new ConcurrentHashMap<Class<?>, ConcurrentMap<Class<? extends Annotation>, Collection<Field>>>();

    /** Method cache. */
    @GridToStringExclude
    private final ConcurrentMap<Class<?>, ConcurrentMap<Class<? extends Annotation>, Collection<Method>>> mtdCache =
        new ConcurrentHashMap<Class<?>, ConcurrentMap<Class<? extends Annotation>, Collection<Method>>>();

    /**
     * @param depMode Deployment mode.
     * @param clsLdr Class loader.
     * @param clsLdrId Class loader ID.
     * @param seqNum Sequence number.
     * @param userVer User version.
     * @param sampleClsName Sample class name.
     * @param local {@code True} if local deployment.
     */
    GridDeployment(GridDeploymentMode depMode, ClassLoader clsLdr, GridUuid clsLdrId, long seqNum, String userVer,
        String sampleClsName, boolean local) {
        assert depMode != null;
        assert clsLdr != null;
        assert clsLdrId != null;
        assert userVer != null;
        assert sampleClsName != null;

        this.clsLdr = clsLdr;
        this.clsLdrId = clsLdrId;
        this.seqNum = seqNum;
        this.userVer = userVer;
        this.depMode = depMode;
        this.sampleClsName = sampleClsName;
        this.local = local;
    }

    /**
     * Gets timestamp.
     *
     * @return Timestamp.
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Gets property sampleCls.
     *
     * @return Property sampleCls.
     */
    public Class<?> sampleClass() {
        return sampleCls.get();
    }

    /**
     * @return Sample class name.
     */
    public String sampleClassName() {
        return sampleClsName;
    }

    /**
     * Gets property depMode.
     *
     * @return Property depMode.
     */
    @Override public GridDeploymentMode deployMode() {
        return depMode;
    }

    /**
     * Gets property seqNum.
     *
     * @return Property seqNum.
     */
    public long sequenceNumber() {
        return seqNum;
    }

    /**
     * @return Class loader.
     */
    public ClassLoader classLoader() {
        return clsLdr;
    }

    /**
     * Gets property clsLdrId.
     *
     * @return Property clsLdrId.
     */
    @Override public GridUuid classLoaderId() {
        return clsLdrId;
    }

    /**
     * Gets property userVer.
     *
     * @return Property userVer.
     */
    @Override public String userVersion() {
        return userVer;
    }

    /**
     * @param name Either class name or alias.
     * @return {@code True} if name is equal to either class name or alias.
     */
    public boolean hasName(String name) {
        assert name != null;

        return clss.containsKey(name);
    }

    /**
     * Gets property local.
     *
     * @return Property local.
     */
    public boolean isLocal() {
        return local;
    }

    /**
     * Gets property undeployed.
     *
     * @return Property undeployed.
     */
    public boolean isUndeployed() {
        synchronized (mux) {
            return undeployed;
        }
    }

    /**
     * Sets property undeployed.
     */
    public void undeploy() {
        synchronized (mux) {
            undeployed = true;
        }
    }

    /**
     * Gets property pendingUndeploy.
     *
     * @return Property pendingUndeploy.
     */
    public boolean isPendingUndeploy() {
        synchronized (mux) {
            return pendingUndeploy;
        }
    }

    /**
     * Invoked whenever this deployment is scheduled to be undeployed.
     * Used for handling obsolete or phantom requests.
     */
    public void onUndeployScheduled() {
        synchronized (mux) {
            pendingUndeploy = true;
        }
    }

    /**
     * Increments usage count for deployment. If deployment is undeployed,
     * then usage count is not incremented.
     *
     * @return {@code True} if deployment is still active.
     */
    public boolean acquire() {
        synchronized (mux) {
            if (undeployed && usage == 0) {
                return false;
            }

            usage++;
        }

        return true;
    }

    /**
     * Checks if deployment is obsolete, i.e. is not used and has been undeployed.
     *
     * @return {@code True} if deployment is obsolete.
     */
    public boolean isObsolete() {
        synchronized (mux) {
            return undeployed && usage == 0;
        }
    }

    /**
     * @return Node participant map.
     */
    @Nullable @Override public Map<UUID, GridTuple2<GridUuid, Long>> participants() {
        if (clsLdr instanceof GridDeploymentClassLoader) {
            return ((GridDeploymentInfo)clsLdr).participants();
        }

        return null;
    }

    /**
     * Decrements usage count.
     */
    public void release() {
        synchronized (mux) {
            assert usage > 0;

            usage--;
        }
    }

    /**
     * @return Usage count.
     */
    public int getUsage() {
        synchronized (mux) {
            return usage;
        }
    }

    /**
     * Deployment callback.
     *
     * @param cls Deployed class.
     */
    public void onDeployed(Class<?> cls) {
        // No-op.
    }

    /**
     * @param cls Class to get annotation for.
     * @param annCls Annotation class.
     * @return Annotation value.
     * @param <T> Annotation class.
     */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    public <T extends Annotation> T annotation(Class<?> cls, Class<T> annCls) {
        ConcurrentMap<Class<? extends Annotation>, AtomicReference<Annotation>> clsAnns = anns.get(cls);

        if (clsAnns == null) {
            ConcurrentMap<Class<? extends Annotation>, AtomicReference<Annotation>> old = anns.putIfAbsent(cls,
                clsAnns = new ConcurrentHashMap<Class<? extends Annotation>, AtomicReference<Annotation>>());

            if (old != null) {
                clsAnns = old;
            }
        }

        AtomicReference<T> ann = (AtomicReference<T>)clsAnns.get(annCls);

        if (ann == null) {
            ann = new AtomicReference<T>(U.getAnnotation(cls, annCls));

            clsAnns.putIfAbsent(annCls, (AtomicReference<Annotation>)ann);
        }

        return ann.get();
    }

    /**
     * @param clsName Class name to check.
     * @return Class for given name if it was previously deployed.
     */
    public Class<?> existingDeployedClass(String clsName) {
        return clss.get(clsName);
    }

    /**
     * Gets class for a name.
     *
     * @param clsName Class name.
     * @param alias Optional array of aliases.
     * @return Class for given name.
     */
    @SuppressWarnings({"StringEquality", "UnusedCatchParameter"})
    @Nullable public Class<?> deployedClass(String clsName, String... alias) {
        Class<?> cls = clss.get(clsName);

        if (cls == null) {
            try {
                cls = Class.forName(clsName, true, clsLdr);

                Class<?> cur = clss.putIfAbsent(clsName, cls);

                if (cur == null) {
                    for (String a : alias) {
                        clss.putIfAbsent(a, cls);
                    }

                    onDeployed(cls);
                }
            }
            catch (ClassNotFoundException ignored) {
                // Check aliases.
                for (String a : alias) {
                    cls = clss.get(a);

                    if (cls != null) {
                        return cls;
                    }
                    else if (!a.equals(clsName)) {
                        try {
                            cls = Class.forName(a, true, clsLdr);
                        }
                        catch (ClassNotFoundException ignored0) {
                            continue;
                        }

                        Class<?> cur = clss.putIfAbsent(a, cls);

                        if (cur == null) {
                            for (String a1 : alias) {
                                // The original alias has already been put into the map,
                                // so we don't try to put it again here.
                                if (a1 != a) {
                                    clss.putIfAbsent(a1, cls);
                                }
                            }

                            onDeployed(cls);
                        }

                        return cls;
                    }
                }
            }
        }

        return cls;
    }

    /**
     * Adds deployed class together with aliases.
     *
     * @param cls Deployed class.
     * @param aliases Class aliases.
     * @return Class for given class name.
     */
    @Nullable public Class<?> addDeployedClass(Class<?> cls, String... aliases) {
        if (cls != null) {
            sampleCls.compareAndSet(null, cls);

            Class<?> cur = clss.putIfAbsent(cls.getName(), cls);

            if (cur == null) {
                onDeployed(cls);
            }

            for (String alias : aliases) {
                if (alias != null) {
                    clss.putIfAbsent(alias, cls);
                }
            }
        }

        return cls;
    }

    /**
     * @return Deployed classes.
     */
    public Collection<Class<?>> deployedClasses() {
        return Collections.unmodifiableCollection(clss.values());
    }

    /**
     * @return Deployed class map, keyed by class name or alias.
     */
    public Map<String, Class<?>> deployedClassMap() {
        return Collections.unmodifiableMap(clss);
    }

    /**
     * Gets value of annotated field or method.
     *
     * @param target Object to find a value in.
     * @param annCls Annotation class.
     * @return Value of annotated field or method.
     * @throws GridException If failed to find.
     */
    @Nullable public Object annotatedValue(Object target, Class<? extends Annotation> annCls) throws GridException {
        return annotatedValue(target, annCls, null, false).get1();
    }

    /**
     * @param target Object to find a value in.
     * @param annCls Annotation class.
     * @param visited Set of visited objects to avoid cycling.
     * @param annFound Flag indicating if value has already been found.
     * @return Value of annotated field or method.
     * @throws GridException If failed to find.
     */
    private GridTuple2<Object, Boolean> annotatedValue(Object target, Class<? extends Annotation> annCls,
        @Nullable Set<Object> visited, boolean annFound) throws GridException {
        assert target != null;

        // To avoid infinite recursion.
        if (visited != null && visited.contains(target))
            return F.t(null, annFound);

        Object val = null;

        for (Class<?> cls = target.getClass(); !cls.equals(Object.class); cls = cls.getSuperclass()) {
            // Fields.
            for (Field f : fieldsWithAnnotation(cls, annCls)) {
                f.setAccessible(true);

                Object fieldVal;

                try {
                    fieldVal = f.get(target);
                }
                catch (IllegalAccessException e) {
                    throw new GridException("Failed to get annotated field value [cls=" + cls.getName() +
                        ", ann=" + annCls.getSimpleName(), e);
                }

                if (needsRecursion(f)) {
                    if (fieldVal != null) {
                        if (visited == null)
                            visited = new GridLeanSet<Object>();

                        visited.add(target);

                        // Recursion.
                        GridTuple2<Object, Boolean> tup = annotatedValue(fieldVal, annCls, visited, annFound);

                        if (!annFound && tup.get2())
                            // Update value only if annotation was found in recursive call.
                            val = tup.get1();

                        annFound = tup.get2();
                    }
                }
                else {
                    if (annFound)
                        throw new GridException("Multiple annotations have been found [cls=" + cls.getName() +
                            ", ann=" + annCls.getSimpleName() + "]");

                    val = fieldVal;

                    annFound = true;
                }
            }

            // Methods.
            for (Method m : methodsWithAnnotation(cls, annCls)) {
                if (annFound)
                    throw new GridException("Multiple annotations have been found [cls=" + cls.getName() +
                        ", ann=" + annCls.getSimpleName() + "]");

                m.setAccessible(true);

                try {
                    val = m.invoke(target);
                }
                catch (Exception e) {
                    throw new GridException("Failed to get annotated method value [cls=" + cls.getName() +
                        ", ann=" + annCls.getSimpleName(), e);
                }

                annFound = true;
            }
        }

        return F.t(val, annFound);
    }

    /**
     * Gets all entries from the specified class or its super-classes that have
     * been annotated with annotation provided.
     *
     * @param cls Class in which search for methods.
     * @param annCls Annotation.
     * @return Set of entries with given annotations.
     */
    private Iterable<Field> fieldsWithAnnotation(Class<?> cls, Class<? extends Annotation> annCls) {
        assert cls != null;
        assert annCls != null;

        Collection<Field> fields = fieldsFromCache(cls, annCls);

        if (fields == null) {
            fields = new ArrayList<Field>();

            for (Field field : cls.getDeclaredFields()) {
                Annotation ann = field.getAnnotation(annCls);

                if (ann != null || needsRecursion(field))
                    fields.add(field);
            }

            cacheFields(cls, annCls, fields);
        }

        return fields;
    }

    /**
     * Gets set of methods with given annotation.
     *
     * @param cls Class in which search for methods.
     * @param annCls Annotation.
     * @return Set of methods with given annotations.
     */
    private Iterable<Method> methodsWithAnnotation(Class<?> cls, Class<? extends Annotation> annCls) {
        assert cls != null;
        assert annCls != null;

        Collection<Method> mtds = methodsFromCache(cls, annCls);

        if (mtds == null) {
            mtds = new ArrayList<Method>();

            for (Method mtd : cls.getDeclaredMethods()) {
                Annotation ann = mtd.getAnnotation(annCls);

                if (ann != null)
                    mtds.add(mtd);
            }

            cacheMethods(cls, annCls, mtds);
        }

        return mtds;
    }

    /**
     * @param f Field.
     * @return {@code true} if recursive inspection is required.
     */
    private boolean needsRecursion(Field f) {
        assert f != null;

        // Need to inspect anonymous classes, callable and runnable instances.
        return f.getName().startsWith("this$") || f.getName().startsWith("val$") ||
            Callable.class.isAssignableFrom(f.getType()) || Runnable.class.isAssignableFrom(f.getType()) ||
            GridLambda.class.isAssignableFrom(f.getType());
    }

    /**
     * Gets all fields for a given class with given annotation from cache.
     *
     * @param cls Class to get fields from.
     * @param annCls Annotation class for fields.
     * @return List of fields with given annotation, possibly {@code null}.
     */
    @Nullable private Collection<Field> fieldsFromCache(Class<?> cls, Class<? extends Annotation> annCls) {
        assert cls != null;
        assert annCls != null;

        Map<Class<? extends Annotation>, Collection<Field>> annCache = fieldCache.get(cls);

        return annCache != null ? annCache.get(annCls) : null;
    }

    /**
     * Caches list of fields with given annotation from given class.
     *
     * @param cls Class the fields belong to.
     * @param annCls Annotation class for the fields.
     * @param fields Fields to cache.
     */
    private void cacheFields(Class<?> cls, Class<? extends Annotation> annCls, Collection<Field> fields) {
        assert cls != null;
        assert annCls != null;
        assert fields != null;

        Map<Class<? extends Annotation>, Collection<Field>> annFields = F.addIfAbsent(fieldCache,
            cls, F.<Class<? extends Annotation>, Collection<Field>>newCMap());

        assert annFields != null;

        annFields.put(annCls, fields);
    }

    /**
     * Gets all methods for a given class with given annotation from cache.
     *
     * @param cls Class to get methods from.
     * @param annCls Annotation class for fields.
     * @return List of methods with given annotation, possibly {@code null}.
     */
    @Nullable private Collection<Method> methodsFromCache(Class<?> cls, Class<? extends Annotation> annCls) {
        assert cls != null;
        assert annCls != null;

        Map<Class<? extends Annotation>, Collection<Method>> annCache = mtdCache.get(cls);

        return annCache != null ? annCache.get(annCls) : null;
    }

    /**
     * Caches list of methods with given annotation from given class.
     *
     * @param cls Class the fields belong to.
     * @param annCls Annotation class for the fields.
     * @param mtds Methods to cache.
     */
    private void cacheMethods(Class<?> cls, Class<? extends Annotation> annCls,
        Collection<Method> mtds) {
        assert cls != null;
        assert annCls != null;
        assert mtds != null;

        Map<Class<? extends Annotation>, Collection<Method>> annMtds = F.addIfAbsent(mtdCache,
            cls, F.<Class<? extends Annotation>, Collection<Method>>newCMap());

        assert annMtds != null;

        annMtds.put(annCls, mtds);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeployment.class, this);
    }
}

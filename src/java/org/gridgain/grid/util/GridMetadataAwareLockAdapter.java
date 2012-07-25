// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * Convenient adapter for {@link GridMetadataAware}.
 * <h2 class="header">Thread Safety</h2>
 * This class provides necessary synchronization for thread-safe access.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@SuppressWarnings( {"SynchronizeOnNonFinalField"})
public class GridMetadataAwareLockAdapter extends ReentrantLock implements GridMetadataAware, Cloneable {
    /** Attributes. */
    @GridToStringInclude
    private GridLeanMap<String, Object> data;

    /**
     * Default constructor.
     */
    public GridMetadataAwareLockAdapter() {
        // No-op.
    }

    /**
     * Creates adapter with predefined data.
     *
     * @param data Data to copy.
     */
    public GridMetadataAwareLockAdapter(Map<String, Object> data) {
        if (data != null && !data.isEmpty())
            this.data = new GridLeanMap<String, Object>(data);
    }

    /**
     * Ensures that internal data storage is created.
     *
     * @param size Amount of data to ensure.
     * @return {@code true} if data storage was created.
     */
    private boolean ensureData(int size) {
        if (data == null) {
            data = new GridLeanMap<String, Object>(size);

            return true;
        }
        else
            return false;
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        A.notNull(from, "from");

        lock();

        try {
            Map m = from.allMeta();

            ensureData(m.size());

            data.putAll(from.allMeta());
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        A.notNull(data, "data");

        lock();

        try {
            ensureData(data.size());

            this.data.putAll(data);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override @Nullable public <V> V addMeta(String name, V val) {
        A.notNull(name, "name", val, "val");

        lock();

        try {
            ensureData(1);

            return (V)data.put(name, val);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override @Nullable public <V> V meta(String name) {
        A.notNull(name, "name");

        lock();

        try {
            return data == null ? null : (V)data.get(name);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable
    @Override public <V> V removeMeta(String name) {
        A.notNull(name, "name");

        lock();

        try {
            if (data == null)
                return null;

            V old = (V)data.remove(name);

            if (data.isEmpty())
                data = null;

            return old;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> boolean removeMeta(String name, V val) {
        A.notNull(name, "name", val, "val");

        lock();

        try {
            if (data == null)
                return false;

            V old = (V)data.get(name);

            if (old != null && old.equals(val)) {
                data.remove(name);

                return true;
            }

            return false;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked", "RedundantCast"})
    @Override public <V> Map<String, V> allMeta() {
        lock();

        try {
            if (data == null)
                return Collections.emptyMap();

            if (data.size() <= 5)
                // This is a singleton unmodifiable map.
                return (Map<String, V>)data;

            // Return a copy.
            return new HashMap<String, V>((Map<String, V>) data);
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return meta(name) != null;
    }

    /** {@inheritDoc} */
    @Override public <V> boolean hasMeta(String name, V val) {
        A.notNull(name, "name");

        Object v = meta(name);

        return v != null && v.equals(val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override @Nullable public <V> V putMetaIfAbsent(String name, V val) {
        A.notNull(name, "name", val, "val");

        lock();

        try {
            V v = (V) meta(name);

            if (v == null)
                return addMeta(name, val);

            return v;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "ClassReferencesSubclass"})
    @Override @Nullable public <V> V putMetaIfAbsent(String name, Callable<V> c) {
        A.notNull(name, "name", c, "c");

        lock();

        try {
            V v = (V) meta(name);

            if (v == null)
                try {
                    return addMeta(name, c.call());
                }
                catch (Exception e) {
                    throw F.wrap(e);
                }

            return v;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> V addMetaIfAbsent(String name, V val) {
        A.notNull(name, "name", val, "val");

        lock();

        try {
            V v = (V) meta(name);

            if (v == null)
                addMeta(name, v = val);

            return v;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable @Override public <V> V addMetaIfAbsent(String name, @Nullable Callable<V> c) {
        A.notNull(name, "name", c, "c");

        lock();

        try {
            V v = (V) meta(name);

            if (v == null && c != null)
                try {
                    addMeta(name, v = c.call());
                }
                catch (Exception e) {
                    throw F.wrap(e);
                }

            return v;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Override public <V> boolean replaceMeta(String name, V curVal, V newVal) {
        A.notNull(name, "name", newVal, "newVal", curVal, "curVal");

        lock();

        try {
            if (hasMeta(name)) {
                V val = this.<V>meta(name);

                if (val != null && val.equals(curVal)) {
                    addMeta(name, newVal);

                    return true;
                }
            }

            return false;
        }
        finally {
            unlock();
        }
    }

    /**
     * Convenience way for super-classes which implement {@link Externalizable} to
     * serialize metadata. Super-classes must call this method explicitly from
     * within {@link Externalizable#writeExternal(ObjectOutput)} methods implementation.
     *
     * @param out Output to write to.
     * @throws IOException If I/O error occurred.
     */
    @SuppressWarnings({"TooBroadScope"})
    protected void writeExternalMeta(ObjectOutput out) throws IOException {
        Map<String, Object> cp;

        lock();

        // Avoid code warning (suppressing is bad here, because we need this warning for other places).
        try {
            cp = new GridLeanMap<String, Object>(data);
        }
        finally {
            unlock();
        }

        out.writeObject(cp);
    }

    /**
     * Convenience way for super-classes which implement {@link Externalizable} to
     * serialize metadata. Super-classes must call this method explicitly from
     * within {@link Externalizable#readExternal(ObjectInput)} methods implementation.
     *
     * @param in Input to read from.
     * @throws IOException If I/O error occurred.
     * @throws ClassNotFoundException If some class could not be found.
     */
    @SuppressWarnings({"unchecked"})
    protected void readExternalMeta(ObjectInput in) throws IOException, ClassNotFoundException {
        GridLeanMap<String, Object> cp = (GridLeanMap<String, Object>)in.readObject();

        lock();

        try {
            data = cp;
        }
        finally {
            unlock();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException", "OverriddenMethodCallDuringObjectConstruction"})
    @Override public Object clone() {
        try {
            GridMetadataAwareLockAdapter clone = (GridMetadataAwareLockAdapter)super.clone();

            clone.data = null;

            clone.copyMeta(this);

            return clone;
        }
        catch (CloneNotSupportedException ignore) {
            throw new InternalError();
        }
    }
}

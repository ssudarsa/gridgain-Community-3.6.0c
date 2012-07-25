// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.GridException;
import org.gridgain.grid.cache.GridCacheConfiguration;
import org.gridgain.grid.cache.affinity.GridCacheAffinityKey;
import org.gridgain.grid.cache.affinity.GridCacheAffinityMapped;
import org.gridgain.grid.cache.affinity.GridCacheAffinityMapper;
import org.gridgain.grid.logger.GridLogger;
import org.gridgain.grid.resources.GridLoggerResource;
import org.gridgain.grid.typedef.F;
import org.gridgain.grid.typedef.P1;
import org.gridgain.grid.typedef.internal.U;
import org.gridgain.grid.util.GridArgumentCheck;
import org.gridgain.grid.util.GridReflectionCache;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Default key affinity mapper. If key class has annotation {@link GridCacheAffinityMapped},
 * then the value of annotated method or field will be used to get affinity value instead
 * of the key itself. If there is no annotation, then the key is used as is.
 * <p>
 * Convenience affinity key adapter, {@link GridCacheAffinityKey} can be used in
 * conjunction with this mapper to automatically provide custom affinity keys for cache keys.
 * <p>
 * If non-default affinity mapper is used, is should be provided via
 * {@link GridCacheConfiguration#getAffinityMapper()} configuration property.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheDefaultAffinityMapper<K> implements GridCacheAffinityMapper<K> {
    /** Reflection cache. */
    private GridReflectionCache reflectCache = new GridReflectionCache(
        new P1<Field>() {
            @Override public boolean apply(Field f) {
                // Account for anonymous inner classes.
                return f.getAnnotation(GridCacheAffinityMapped.class) != null;
            }
        },
        new P1<Method>() {
            @Override public boolean apply(Method m) {
                // Account for anonymous inner classes.
                Annotation ann = m.getAnnotation(GridCacheAffinityMapped.class);

                if (ann != null) {
                    if (!F.isEmpty(m.getParameterTypes()))
                        throw new IllegalStateException("Method annotated with @GridCacheAffinityKey annotation " +
                                "cannot have parameters: " + m);

                    return true;
                }

                return false;
            }
        }
    );

    /** Logger. */
    @GridLoggerResource
    private transient GridLogger log;

    /**
     * If key class has annotation {@link GridCacheAffinityMapped},
     * then the value of annotated method or field will be used to get affinity value instead
     * of the key itself. If there is no annotation, then the key is returned as is.
     *
     * @param key Key to get affinity key for.
     * @return Affinity key for given key.
     */
    @Override public Object affinityKey(K key) {
        GridArgumentCheck.notNull(key, "key");

        try {
            Object o = reflectCache.firstFieldValue(key);

            if (o != null)
                return o;
        }
        catch (GridException e) {
            U.error(log, "Failed to access affinity field for key [field=" + reflectCache.firstField(key.getClass()) +
                ", key=" + key + ']', e);
        }

        try {
            Object o = reflectCache.firstMethodValue(key);

            if (o != null)
                return o;
        }
        catch (GridException e) {
            U.error(log, "Failed to invoke affinity method for key [mtd=" + reflectCache.firstMethod(key.getClass()) +
                ", key=" + key + ']', e);
        }

        return key;
    }

    /** {@inheritDoc} */
    @Override public void reset() {
        // No-op.
    }
}

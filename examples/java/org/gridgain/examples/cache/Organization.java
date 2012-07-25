// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache;

import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Organization record used for query examples.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class Organization implements Serializable {
    /** Organization ID (create unique SQL-based index for this field). */
    @GridCacheQuerySqlField(unique = true)
    private UUID id;

    /** Organization name (create non-unique SQL-based index for this field. */
    @GridCacheQuerySqlField
    private String name;

    /**
     * Constructs organization with generated ID.
     */
    public Organization() {
        id = UUID.randomUUID();
    }

    /**
     * Constructs organization with given ID.
     *
     * @param id Organization ID.
     */
    public Organization(UUID id) {
        this.id = id;
    }

    /**
     * Create organization.
     *
     * @param name Organization name.
     */
    public Organization(String name) {
        id = UUID.randomUUID();

        this.name = name;
    }

    /**
     * @return Organization id.
     */
    public UUID getId() {
        return id;
    }

    /**
     * @param id Organization id.
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * @return Organization name.
     */
    public String getName() {
        return name;
    }

    /**
     * @param name Organization name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return this == o || (o instanceof Organization) && id.equals(((Organization)o).id);

    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(Organization.class, this);
    }
}

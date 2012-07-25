// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache;

import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Person record used for query examples.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class Person implements Serializable {
    /** Person ID (create unique SQL index for this field). */
    @GridCacheQuerySqlField
    private UUID id;

    /** Organization ID (create non-unique SQL index for this field). */
    @GridCacheQuerySqlField
    private UUID orgId;

    /** First name (not-indexed). */
    private String firstName;

    /** Last name (not indexed). */
    private String lastName;

    /** Resume text (create LUCENE-based TEXT index for this field). */
    @GridCacheQueryLuceneField
    private String resume;

    /** Salary (create non-unique SQL index for this field). */
    @GridCacheQuerySqlField
    private double salary;

    /** Custom cache key to guarantee that person is always collocated with its organization. */
    private transient GridCacheAffinityKey<UUID> key;

    /**
     * Constructs person with generated ID.
     */
    public Person() {
        id = UUID.randomUUID();
    }

    /**
     * Constructs person with given ID.
     *
     * @param id Person ID.
     */
    public Person(UUID id) {
        this.id = id;
    }

    /**
     * Constructs person record that is not linked to any organization.
     *
     * @param firstName First name.
     * @param lastName Last name.
     * @param salary Salary.
     * @param resume Resume text.
     */
    public Person(String firstName, String lastName, double salary, String resume) {
        // Generate unique ID for this person.
        id = UUID.randomUUID();

        this.firstName = firstName;
        this.lastName = lastName;
        this.resume = resume;
        this.salary = salary;
    }

    /**
     * Constructs person record.
     *
     * @param org Organization.
     * @param firstName First name.
     * @param lastName Last name.
     * @param salary Salary.
     * @param resume Resume text.
     */
    public Person(Organization org, String firstName, String lastName, double salary, String resume) {
        // Generate unique ID for this person.
        id = UUID.randomUUID();

        orgId = org.getId();

        this.firstName = firstName;
        this.lastName = lastName;
        this.resume = resume;
        this.salary = salary;
    }

    /**
     * Gets cache affinity key. Since in some examples person needs to be collocated with organization, we create
     * custom affinity key to guarantee this collocation.
     *
     * @return Custom affinity key to guarantee that person is always collocated with organization.
     */
    public GridCacheAffinityKey<UUID> key() {
        if (key == null)
            key = new GridCacheAffinityKey<UUID>(id, orgId);

        return key;
    }

    /**
     * @return Person id.
     */
    public UUID getId() {
        return id;
    }

    /**
     * @param id Person id.
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * @return Organization id.
     */
    public UUID getOrganizationId() {
        return orgId;
    }

    /**
     * @param orgId Organization id.
     */
    public void setOrganizationId(UUID orgId) {
        this.orgId = orgId;
    }

    /**
     * @return Person first name.
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * @param firstName Person first name.
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * @return Person last name.
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * @param lastName Person last name.
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * @return Resume.
     */
    public String getResume() {
        return resume;
    }

    /**
     * @param resume Resume.
     */
    public void setResume(String resume) {
        this.resume = resume;
    }

    /**
     * @return Salary.
     */
    public double getSalary() {
        return salary;
    }

    /**
     * @param salary Salary.
     */
    public void setSalary(double salary) {
        this.salary = salary;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return this == o || (o instanceof Person) && id.equals(((Person)o).id);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(Person.class, this);
    }
}

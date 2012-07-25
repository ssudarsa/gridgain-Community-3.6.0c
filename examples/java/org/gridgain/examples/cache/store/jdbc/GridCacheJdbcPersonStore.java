// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.cache.store.jdbc;

import org.gridgain.examples.cache.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.sql.*;
import java.util.*;

/**
 * Example of {@link GridCacheStore} implementation that uses JDBC
 * transaction with cache transactions and maps {@link UUID} to {@link Person}.
 *
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridCacheJdbcPersonStore extends GridCacheStoreAdapter<UUID, Person> {
    /** Transaction metadata attribute name. */
    private static final String ATTR_NAME = "SIMPLE_STORE_CONNECTION";

    /**
     * Constructor.
     *
     * @throws GridException If failed.
     */
    public GridCacheJdbcPersonStore() throws GridException {
        prepareDb();
    }

    /**
     * Prepares database for example execution. This method will create a
     * table called "PERSONS" so it can be used by store implementation.
     *
     * @throws GridException If failed.
     */
    private void prepareDb() throws GridException {
        Connection conn = null;

        Statement st = null;

        try {
            conn = openConnection(false);

            st = conn.createStatement();

            st.execute("create table if not exists PERSONS (id varchar(36) unique, firstName varchar(255), " +
                "lastName varchar(255), resume varchar(255))");

            conn.commit();
        }
        catch (SQLException e) {
            throw new GridException("Failed to create database table.", e);
        }
        finally {
            end(null, conn, st);
        }
    }

    /** {@inheritDoc} */
    @Override public void txEnd(@Nullable String cacheName, GridCacheTx tx, boolean commit) throws GridException {
        Connection conn = tx.removeMeta(ATTR_NAME);

        if (conn != null) {
            try {
                if (commit)
                    conn.commit();
                else
                    conn.rollback();

                X.println("Transaction ended [xid=" + tx.xid() + ", commit=" + commit + ']');
            }
            catch (SQLException e) {
                throw new GridException("Failed to end transaction [xid=" + tx.xid() + ", commit=" + commit + ']', e);
            }
            finally {
                // Return connection back to the pool.
                U.closeQuiet(conn);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public Person load(@Nullable String cacheName, @Nullable GridCacheTx tx, UUID key)
        throws GridException {
        X.println("Store load [key=" + key + ", tx=" + tx + ']');

        Connection conn = null;

        PreparedStatement st = null;

        try {
            conn = connection(tx);

            st = conn.prepareStatement("select * from PERSONS where id=?");

            st.setString(1, key.toString());

            ResultSet rs = st.executeQuery();

            if (rs.next())
                return person(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
        }
        catch (SQLException e) {
            throw new GridException("Failed to load object: " + key, e);
        }
        finally {
            end(tx, conn, st);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public void put(@Nullable String cacheName, @Nullable GridCacheTx tx, UUID key, Person val)
        throws GridException {
        X.println("Store put [key=" + key + ", val=" + val + ", tx=" + tx + ']');

        PreparedStatement st = null;

        Connection conn = null;

        try {
            conn = connection(tx);

            st = conn.prepareStatement("update PERSONS set firstName=?, lastName=?, resume=? where id=?");

            st.setString(1, val.getFirstName());
            st.setString(2, val.getLastName());
            st.setString(3, val.getResume());
            st.setString(4, val.getId().toString());

            if (st.executeUpdate() == 0) {
                st.close();

                st = conn.prepareStatement(
                    "insert into PERSONS (id, firstName, lastName, resume) values(?, ?, ?, ?)");

                st.setString(1, val.getId().toString());
                st.setString(2, val.getFirstName());
                st.setString(3, val.getLastName());
                st.setString(4, val.getResume());

                st.executeUpdate();
            }
        }
        catch (SQLException e) {
            throw new GridException("Failed to put object [key=" + key + ", val=" + val + ']', e);
        }
        finally {
            end(tx, conn, st);
        }
    }

    /** {@inheritDoc} */
    @Override public void remove(@Nullable String cacheName, @Nullable GridCacheTx tx, UUID key) throws GridException {
        X.println("Store remove [key=" + key + ", tx=" + tx + ']');

        PreparedStatement st = null;

        Connection conn = null;

        try {
            conn = connection(tx);

            st = conn.prepareStatement("delete from PERSONS where id=?");

            st.setString(1, key.toString());

            st.executeUpdate();
        }
        catch (SQLException e) {
            throw new GridException("Failed to remove object: " + key, e);
        }
        finally {
            end(tx, conn, st);
        }
    }

    /**
     * @param tx Cache transaction.
     * @return Connection.
     * @throws SQLException In case of error.
     */
    private Connection connection(@Nullable GridCacheTx tx) throws SQLException  {
        if (tx != null) {
            Connection conn = tx.meta(ATTR_NAME);

            if (conn == null) {
                conn = openConnection(false);

                // Store connection in transaction metadata, so it can be accessed
                // for other operations on the same transaction.
                tx.addMeta(ATTR_NAME, conn);
            }

            return conn;
        }
        // Transaction can be null in case of simple load or put operation.
        else
            return openConnection(true);
    }

    /**
     * Closes allocated resources depending on transaction status.
     *
     * @param tx Active transaction, if any.
     * @param conn Allocated connection.
     * @param st Created statement,
     */
    private void end(@Nullable GridCacheTx tx, Connection conn, Statement st) {
        U.closeQuiet(st);

        if (tx == null)
            // Close connection right away if there is no transaction.
            U.closeQuiet(conn);
    }

    /**
     * Gets connection from a pool.
     *
     * @param autocommit {@code true} If connection should use autocommit mode.
     * @return Pooled connection.
     * @throws SQLException In case of error.
     */
    private Connection openConnection(boolean autocommit) throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:example;DB_CLOSE_DELAY=-1");

        conn.setAutoCommit(autocommit);

        return conn;
    }

    /**
     * Builds person object out of provided values.
     *
     * @param id ID.
     * @param firstName First name.
     * @param lastName Last name.
     * @param resume Resume.
     * @return Person.
     */
    private Person person(String id, String firstName, String lastName, String resume) {
        Person person = new Person(UUID.fromString(id));

        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setResume(resume);

        return person;
    }
}

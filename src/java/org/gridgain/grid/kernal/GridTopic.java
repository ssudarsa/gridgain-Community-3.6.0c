// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Communication topic.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public enum GridTopic {
    /** */
    TOPIC_JOB,

    /** */
    TOPIC_JOB_SIBLINGS,

    /** */
    TOPIC_TASK,

    /** */
    TOPIC_CHECKPOINT,

    /** */
    TOPIC_CANCEL,

    /** */
    TOPIC_CLASSLOAD,

    /** */
    TOPIC_EVENT,

    /** Cache topic. */
    TOPIC_CACHE,

    /** */
    TOPIC_COMM_USER,

    /** */
    TOPIC_COMM_SYNC,

    /** */
    TOPIC_REST;

    /** Enum values. */
    private static final GridTopic[] VALS = values();

    /**
     * Efficiently gets enumerated value from its ordinal.
     *
     * @param ord Ordinal value.
     * @return Enumerated value.
     */
    @Nullable public static GridTopic fromOrdinal(int ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }

    /**
     * This method uses cached instances of {@link StringBuilder} to avoid
     * constant resizing and object creation.
     *
     * @param suffixes Suffixes to append to topic name.
     * @return Grid message topic with specified suffixes.
     */
    public String name(String... suffixes) {
        SB sb = GridStringBuilderFactory.acquire();

        try {
            sb.a(name());

            for (String suffix : suffixes)
                sb.a('-').a(suffix);

            return sb.toString();
        }
        finally {
            GridStringBuilderFactory.release(sb);
        }
    }

    /**
     * This method uses cached instances of {@link StringBuilder} to avoid
     * constant resizing and object creation.
     *
     * @param ids Topic IDs.
     * @return Grid message topic with specified IDs.
     */
    public String name(UUID... ids) {
        SB sb = GridStringBuilderFactory.acquire();

        try {
            sb.a(name());

            for (UUID id : ids) {
                sb.a('-');

                append(sb, id);
            }

            return sb.toString();
        }
        finally {
            GridStringBuilderFactory.release(sb);
        }
    }

    /**
     * This method uses cached instances of {@link StringBuilder} to avoid
     * constant resizing and object creation.
     *
     * @param id1 ID1
     * @param id2 ID2
     * @return Grid message topic with specified IDs.
     */
    public String name(GridUuid id1, UUID id2) {
        SB sb = GridStringBuilderFactory.acquire();

        try {
            sb.a(name());

            sb.a('-');

            append(sb, id1);

            sb.a('-');

            append(sb, id2);

            return sb.toString();
        }
        finally {
            GridStringBuilderFactory.release(sb);
        }
    }

    /**
     * This method uses cached instances of {@link StringBuilder} to avoid
     * constant resizing and object creation.
     *
     * @param ids Topic IDs.
     * @return Grid message topic with specified IDs.
     */
    public String name(GridUuid... ids) {
        SB sb = GridStringBuilderFactory.acquire();

        try {
            sb.a(name());

            for (GridUuid id : ids) {
                sb.a('-');

                append(sb, id);
            }

            return sb.toString();
        }
        finally {
            GridStringBuilderFactory.release(sb);
        }
    }

    /**
     * @param sb String builder.
     * @param id ID.
     */
    private void append(SB sb, UUID id) {
        sb.a(id.getLeastSignificantBits()).a('-').a(id.getMostSignificantBits());
    }

    /**
     * @param sb String builder.
     * @param id ID.
     */
    private void append(SB sb, GridUuid id) {
        sb.a(id.globalId().getLeastSignificantBits()).a('-').a(id.globalId().getMostSignificantBits()).
            a('-').a(id.localId());
    }
}

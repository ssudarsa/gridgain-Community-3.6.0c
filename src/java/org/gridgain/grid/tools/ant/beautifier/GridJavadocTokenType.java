// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.tools.ant.beautifier;

/**
 * Lexical token type.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
enum GridJavadocTokenType {
    /** HTML instruction.  */
    TOKEN_INSTR,

    /** HTML comment. */
    TOKEN_COMM,

    /** HTML open tag. */
    TOKEN_OPEN_TAG,

    /** HTML close tag. */
    TOKEN_CLOSE_TAG,

    /** HTML text. */
    TOKEN_TEXT
}

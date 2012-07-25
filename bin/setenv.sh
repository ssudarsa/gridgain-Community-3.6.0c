#!/bin/bash
#
# Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html
#  _________        _____ __________________        _____
#  __  ____/___________(_)______  /__  ____/______ ____(_)_______
#  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
#  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
#  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
#
# Version: 3.6.0c.13012012
#

#
# Exports GRIDGAIN_LIBS variable containing classpath for GridGain.
# Expects GRIDGAIN_HOME to be set.
# Can be used like:
#       . "${GRIDGAIN_HOME}"/bin/setenv.sh
# in other scripts to set classpath using exported GRIDGAIN_LIBS variable.
#

#
# Check GRIDGAIN_HOME.
#
if [ "${GRIDGAIN_HOME}" = "" ]; then
    echo $0", ERROR: GRIDGAIN_HOME environment variable is not found."
    echo "Please create GRIDGAIN_HOME variable pointing to location of"
    echo "GridGain installation folder."

    exit 1
fi

# USER_LIBS variable can optionally contain user's JARs/libs.
# USER_LIBS=

#
# OS specific support.
#
SEP=":";

case "`uname`" in
    CYGWIN*)
        SEP=";";
        ;;
esac

# The following libraries are required for GridGain.
GRIDGAIN_LIBS="${USER_LIBS}${SEP}${GRIDGAIN_HOME}/config/userversion${SEP}${GRIDGAIN_HOME}/libs/*"

# Comment these jars if you do not wish to use Hyperic SIGAR licensed under GPL
# Note that starting with GridGain 3.0 - Community Edition is licensed under GPLv3.
GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${GRIDGAIN_HOME}"/libs/sigar.jar

# Uncomment if using JBoss.
# JBOSS_HOME must point to JBoss installation folder.
# JBOSS_HOME=

# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/lib/jboss-common.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/lib/jboss-jmx.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/lib/jboss-system.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/server/all/lib/jbossha.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/server/all/lib/jboss-j2ee.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/server/all/lib/jboss.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/server/all/lib/jboss-transaction.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/server/all/lib/jmx-adaptor-plugin.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/server/all/lib/jnpserver.jar

# If using JBoss AOP following libraries need to be downloaded separately
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/lib/jboss-aop-jdk50.jar
# GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${JBOSS_HOME}"/lib/jboss-aspect-library-jdk50.jar

for jar in `find ${GRIDGAIN_HOME}/libs/ext -depth -name '*.jar'`
do
    GRIDGAIN_LIBS="${GRIDGAIN_LIBS}${SEP}${jar}"
done

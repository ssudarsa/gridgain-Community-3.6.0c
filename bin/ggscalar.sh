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
# Starts Scala REPL with GridGain on the classpath.
# Note that 'scala' must be on PATH.
#

#
# Check Scala
#
if [ ! `which scala` ]; then
    echo $0", ERROR: 'scala' must be on PATH."

    exit 1
fi

#
# Set property JAR name during the Ant build.
#
ANT_AUGMENTED_GGJAR=gridgain-3.6.0c.jar

osname=`uname`

#
# Set GRIDGAIN_HOME, if needed.
#
if [ "${GRIDGAIN_HOME}" = "" ]; then
    echo $0", WARN: GRIDGAIN_HOME environment variable is not found."

    case $osname in
        Darwin*)
            export GRIDGAIN_HOME=$(dirname $(dirname $(cd ${0%/*} && echo $PWD/${0##*/})))
            ;;
        *)
            export GRIDGAIN_HOME="$(dirname $(readlink -f $0))"/..
            ;;
    esac
fi

#
# Check GRIDGAIN_HOME
#
if [ ! -d "${GRIDGAIN_HOME}/config" ]; then
    echo $0", ERROR: GRIDGAIN_HOME environment variable is not found or is not valid."
    echo $0", ERROR: GRIDGAIN_HOME variable must point to GridGain installation folder."

    exit 1
fi

#
# Set GRIDGAIN_LIBS.
#
. "${GRIDGAIN_HOME}"/bin/setenv.sh

#
# OS specific support.
#
SEPARATOR=":";

case $osname in
    CYGWIN*)
        SEPARATOR=";";
        ;;
esac

CP="${GRIDGAIN_LIBS}${SEPARATOR}${GRIDGAIN_HOME}/${ANT_AUGMENTED_GGJAR}"

QUIET="-DGRIDGAIN_QUIET=true"

while [ $# -gt 0 ]
do
    case "$1" in
        -v) QUIET="-DGRIDGAIN_QUIET=false";;
    esac
    shift
done

#
# Set Java options.
#
JAVA_OPTS=-Xss2m

#
# Start REPL.
#
env JAVA_OPTS=${JAVA_OPTS} scala -Yrepl-sync ${QUIET}  -DGRIDGAIN_SCRIPT -DGRIDGAIN_HOME="${GRIDGAIN_HOME}" -DGRIDGAIN_PROG_NAME="$0" -cp "${CP}" -i ${GRIDGAIN_HOME}/bin/scalar.scala

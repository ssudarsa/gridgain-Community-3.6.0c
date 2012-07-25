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
# Grid command line loader.
#

#
# Check JAVA_HOME.
#
if [ "$JAVA_HOME" = "" ]; then
    echo $0", ERROR: JAVA_HOME environment variable is not found."
    echo $0", ERROR: Please create JAVA_HOME variable pointing to location of JDK 1.6 or JDK 1.7."
    echo $0", ERROR: You can also download latest JDK at: http://java.sun.com/getjava"

    exit 1
fi

JAVA=${JAVA_HOME}/bin/java

#
# Check JDK.
#
if [ ! -e "$JAVA" ]; then
    echo $0", ERROR: The JAVA is not found in $JAVA_HOME."
    echo $0", ERROR: Please modify your script so that JAVA_HOME would point"
    echo $0", ERROR: to valid location of Java installation."

    exit 1
fi

JAVA_VER=`$JAVA -version 2>&1 | egrep "1\.[67]\."`

if [ "$JAVA_VER" == "" ]; then
    echo $0", ERROR: The version of JAVA installed in $JAVA_HOME is incorrect."
    echo $0", ERROR: Please install JDK 1.6 or 1.7."
    echo $0", ERROR: You can also download latest JDK at: http://java.sun.com/getjava"

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

CONFIG="${GRIDGAIN_HOME}/config/default-spring.xml"
QUIET="-DGRIDGAIN_QUIET=true"
RESTART_SUCCESS_FILE="${GRIDGAIN_HOME}/work/gridgain_success_$RANDOM"
RESTART_SUCCESS_OPT="-DGRIDGAIN_SUCCESS_FILE=${RESTART_SUCCESS_FILE}"

while [ $# -gt 0 ]
do
    case "$1" in
        -i) INTERACTIVE="1";;
        -v) QUIET="-DGRIDGAIN_QUIET=false";;
        *) CONFIG="$1";;
    esac
    shift
done

#
# Find available port for JMX
#
JMX_PORT=`$JAVA -cp ${GRIDGAIN_HOME}/${ANT_AUGMENTED_GGJAR} org.gridgain.grid.tools.portscanner.GridPortScanner`

# This variable defines necessary parameters for JMX
# monitoring and management.
# ADD YOUR ADDITIONAL PARAMETERS/OPTIONS HERE
#
JMX_MON=-Dcom.sun.management.jmxremote

#
# This enables remote unsecure access to JConsole or VisualVM.
# ADD YOUR ADDITIONAL PARAMETERS/OPTIONS HERE
#
JMX_MON="${JMX_MON} -Dcom.sun.management.jmxremote.port=${JMX_PORT} -Dcom.sun.management.jmxremote.authenticate=false \
-Dcom.sun.management.jmxremote.ssl=false"

#
# JVM options. See http://java.sun.com/javase/technologies/hotspot/vmoptions.jsp
# for more details. Note that default settings use ** PARALLEL GC**.
#
# NOTE
# ====
# ASSERTIONS ARE DISABLED BY DEFAULT SINCE VERSION 3.5.
# IF YOU WANT TO ENABLE THEM - ADD '-ea' TO JVM_OPTS VARIABLE
#
# ADD YOUR ADDITIONAL PARAMETERS/OPTIONS HERE
#
JVM_OPTS="-Xms512m -Xmx512m -XX:MaxPermSize=384m -XX:+UseParNewGC -XX:MaxNewSize=128m -XX:NewSize=32m \
-XX:SurvivorRatio=128 -XX:MaxTenuringThreshold=0 -XX:+UseTLAB -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled"

# Uncomment to set preference for IPv4 stack.
# JVM_OPTS="${JVM_OPTS} -Djava.net.preferIPv4Stack=true"

# Remote debugging (JPDA).
# Uncomment and change if remote debugging is required.
# JVM_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=n ${JVM_OPTS}"

ERRORCODE="-1"

while [ "${ERRORCODE}" -ne "130" ]
do
    if [ -z "${INTERACTIVE}" ] ; then
        "$JAVA" ${JVM_OPTS} ${QUIET} ${RESTART_SUCCESS_OPT} ${JMX_MON}  \
        -DGRIDGAIN_SCRIPT -DGRIDGAIN_HOME="${GRIDGAIN_HOME}" -DGRIDGAIN_PROG_NAME="$0" -cp "${CP}" \
        org.gridgain.grid.loaders.cmdline.GridCommandLineLoader "${CONFIG}"
    else
        "$JAVA" ${JVM_OPTS} ${QUIET} ${RESTART_SUCCESS_OPT} ${JMX_MON}  \
        -DGRIDGAIN_SCRIPT -DGRIDGAIN_HOME="${GRIDGAIN_HOME}" -DGRIDGAIN_PROG_NAME="$0" -cp "${CP}" \
        org.gridgain.grid.loaders.cmdline.GridCommandLineLoader
    fi

    ERRORCODE="$?"

    if [ ! -f "${RESTART_SUCCESS_FILE}" ] ; then
       break
    else
        rm -f "${RESTART_SUCCESS_FILE}"
    fi
done

if [ -f "${RESTART_SUCCESS_FILE}" ] ; then
    rm -f "${RESTART_SUCCESS_FILE}"
fi

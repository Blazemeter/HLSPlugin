#!/bin/bash
# For Travis
# runs the plugin with the given JMeter version (through taurus)
# environment variables: JMETER_VERSION, JVM_VERSION (optional, by default is 8)
set -e

JMETER_VERSION=${JMETER_VERSION:-$1}
BZ_TOKEN=${BZ_TOKEN:-$2}

JMETER_PATH=${project.basedir}/.jmeter/$JMETER_VERSION
DEFAULT_JVM_VERSION=8
JVM_VERSION=${JVM_VERSION:-$DEFAULT_JVM_VERSION}

ERROR=0

if [ -z "$BZ_TOKEN" ]
then
 [ "$JVM_VERSION" != "$DEFAULT_JVM_VERSION" ] && update-java-alternatives --set java-1.${JVM_VERSION}.0-openjdk-amd64
 bzt testJMeter.yaml -o modules.jmeter.version=$JMETER_VERSION -o modules.jmeter.path=$JMETER_PATH || ERROR=$?
 [ "$JVM_VERSION" != "$DEFAULT_JVM_VERSION" ] && update-java-alternatives --set java-1.${DEFAULT_JVM_VERSION}.0-openjdk-amd64
else
 bzt testJMeter.yaml -o modules.cloud.token=$BZ_TOKEN || ERROR=$?
fi

exit $ERROR

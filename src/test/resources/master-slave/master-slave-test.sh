#!/usr/bin/env bash

echo "Initializing wiremock service"
#wiremock needs to be executed from inside, because is using mappings from current path
cd /wiremock
java -jar *.jar --port=8080 &
echo "Starting slave"
sh $1/jmeter-server > /slave_output.txt &
touch /master_logs.txt
touch /result.jtl
tail -f /slave_output.txt /master_logs.txt /result.jtl

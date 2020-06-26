#!/usr/bin/env bash

echo "Initializing wiremock service"
java -jar /wiremock/*.jar --port=8080 &
echo "Starting slave"
sh $1/jmeter-server > /slave_output.txt &
echo "Starting master and triggering test"
sleep 10
touch /master_logs.txt
touch /result.jtl
sh $1/jmeter -n -r -t /test.jmx -l /result.jtl -j /master_logs.txt &
tail -f /slave_output.txt /master_logs.txt /result.jtl

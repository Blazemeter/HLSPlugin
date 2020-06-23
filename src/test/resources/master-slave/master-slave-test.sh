#!/usr/bin/env bash

echo "LOG: Starting slave"
$JMETER_PATH/jmeter-server > /slave_output.txt &
echo "LOG: Starting master and triggering test"
cd $JMETER_PATH
$JMETER_PATH/jmeter -n -t /test.jmx -r > /master_output.txt & 
tail -f slave_output.txt master_output.txt

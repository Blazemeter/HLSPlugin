#!/usr/bin/env bash

echo "Starting slave"
sh $1/jmeter-server > /slave_output &
touch /master_logs /result
tail -f /slave_output /master_logs /result

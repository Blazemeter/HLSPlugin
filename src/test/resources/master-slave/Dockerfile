FROM java:8

# Updating system & installing net-tools to have ifconfig command avaiable
# Due to issue non-zero code 100 (returned by apt-get update -y), this was the approach found to install net-tools.
RUN apt-get update -y || apt-get install -y net-tools

ENV JMETER_VERSION 5.1.1

RUN mkdir /jmeter \
    && cd /jmeter/ \
    && wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$JMETER_VERSION.tgz \
    && tar -xvzf apache-jmeter-$JMETER_VERSION.tgz \
    && rm apache-jmeter-$JMETER_VERSION.tgz \
    && chmod +x /jmeter/apache-jmeter-$JMETER_VERSION/bin/jmeter-server \
    && chmod +x /jmeter/apache-jmeter-$JMETER_VERSION/bin/jmeter
    
ENV JMETER_HOME /jmeter/apache-jmeter-$JMETER_VERSION/bin

# Disable keystore file on jmeter.properties
RUN sed -i '334s/.*/server.rmi.ssl.disable=true/' $JMETER_HOME/jmeter.properties

COPY test.jmx /test.jmx
COPY master-slave-test.sh /master-slave-test.sh
COPY hlsparserj.jar jmeter/apache-jmeter-$JMETER_VERSION/lib/
COPY jmeter-bzm-hls.jar jmeter/apache-jmeter-$JMETER_VERSION/lib/ext/

RUN chmod +x /master-slave-test.sh
# Setting master alias on jmeter.properties as remote host
RUN sed -i '259s/.*/remote_hosts=master/'  $JMETER_HOME/jmeter.properties
CMD /master-slave-test.sh $JMETER_HOME



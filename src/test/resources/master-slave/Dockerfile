FROM java:8

# Updating system & installing net-tools to have ifconfig command avaiable
# Due to issue non-zero code 100 (returned by apt-get update -y), this was the approach found to install net-tools.
RUN apt-get update -y || apt-get install -y net-tools
    
RUN mkdir /wiremock \
    && cd /wiremock \
    && wget https://repo1.maven.org/maven2/com/github/tomakehurst/wiremock-standalone/2.26.3/wiremock-standalone-2.26.3.jar \
    && mkdir mappings

RUN mkdir /jmeter \
    && cd /jmeter/ \
    && wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.1.1.tgz \
    && tar -xvzf apache-jmeter-5.1.1.tgz \
    && rm apache-jmeter-5.1.1.tgz
    
ENV JMETER_HOME /jmeter/apache-jmeter-5.1.1/bin
COPY rmi_keystore.jks $JMETER_HOME/rmi_keystore.jks
COPY jmeter-bzm-hls.jar jmeter/apache-jmeter-5.1.1/lib/ext/
COPY hlsparserj.jar jmeter/apache-jmeter-5.1.1/lib/
COPY test.jmx /test.jmx
COPY master-slave-test.sh /master-slave-test.sh
COPY mapping.json /wiremock/mappings/

# Setting current ip address on jmeter.properties
RUN sed -i '259s/.*/remote_hosts='$(ifconfig | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*' | grep -v '127.0.0.1')',/' $JMETER_HOME/jmeter.properties

# Configuring keystore file on jmeter.properties
RUN sed -i '313s/.*/server.rmi.ssl.keystore.type=JKS/' $JMETER_HOME/jmeter.properties \
    && sed -i '316s/.*/server.rmi.ssl.keystore.file=\/jmeter\/apache-jmeter-5.1.1\/bin\/rmi_keystore.jks/' $JMETER_HOME/jmeter.properties \
    && sed -i '313s/.*/server.rmi.ssl.keystore.password=changeit/' $JMETER_HOME/jmeter.properties \
    && sed -i '313s/.*/server.rmi.ssl.keystore.alias=rmi/' $JMETER_HOME/jmeter.properties

RUN chmod +x $JMETER_HOME/jmeter-server \
  && chmod +x $JMETER_HOME/jmeter \
  && chmod +x /master-slave-test.sh
  
CMD /master-slave-test.sh $JMETER_HOME



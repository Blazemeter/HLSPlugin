FROM java:8
 
RUN mkdir /jmeter \
    && cd /jmeter/ \
    && wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.1.1.tgz \
    && tar -xvzf apache-jmeter-5.1.1.tgz \
    && rm apache-jmeter-5.1.1.tgz \
    && cd ..

ENV JMETER_HOME /jmeter/apache-jmeter-5.1.1/bin

COPY rmi_keystore.jks $JMETER_HOME/rmi_keystore.jks
# Getting ip address
RUN export IP_ADDRESS=$(ifconfig | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*' | grep -v '127.0.0.1')
# Setting ip address on jmeter.properties
RUN sed -i '259s/.*/remote_hosts=$IP_ADDRESS/' $JMETER_HOME/jmeter.properties

# Configuring keystore file on jmeter.properties
RUN sed -i '313s/.*/server.rmi.ssl.keystore.type=JKS/' $JMETER_HOME/jmeter.properties \
    && sed -i '316s/.*/server.rmi.ssl.keystore.file=rmi_keystore.jks/' $JMETER_HOME/jmeter.properties \
    && sed -i '313s/.*/server.rmi.ssl.keystore.password=changeit/' $JMETER_HOME/jmeter.properties \
    && sed -i '313s/.*/server.rmi.ssl.keystore.alias=rmi/' $JMETER_HOME/jmeter.properties
     
# RUN mv jmeter-bzm-hls.jar jmeter/apache-jmeter-5.1.1/lib/ext/
# && mv hlsparserj.jar /jmeter/apache-jmeter-5.1.1/lib/
CMD sh $JMETER_HOME/jmeter.sh



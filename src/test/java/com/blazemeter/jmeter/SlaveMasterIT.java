package com.blazemeter.jmeter;

import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class SlaveMasterIT {

  private static final Logger LOG = LoggerFactory.getLogger(SlaveMasterIT.class);

  public GenericContainer container = null;


  @Before
  public void setup() {
    container = new GenericContainer(new ImageFromDockerfile()
        //adding files to testcontainer context
        .withFileFromClasspath("rmi_keystore.jks", "master-slave/rmi_keystore.jks")
        .withFileFromClasspath("master-slave-test.sh", "master-slave/master-slave-test.sh")
        .withFileFromClasspath("Dockerfile", "master-slave/Dockerfile")
        .withFileFromClasspath("test.jmx", "jmeter/HLSSamplerSlaveRemoteTest.jmx")
        .withFileFromFile("jmeter-bzm-hls.jar",
            new File("target/jmeter-test/lib/jmeter-bzm-hls.jar"))
        .withFileFromFile("hlsparserj.jar",
            new File("target/jmeter-test/lib/hlsparserj.jar"))
        .withDockerfilePath("Dockerfile")
    );
  }

  @Test
  public void shouldWhen() throws Exception {
    // make sure Docker is running on your machine.
    container.start();
    // getting outputs from the container
    container.copyFileFromContainer("/slave_output.txt",
        "src/test/resources/master-slave/slave_output.txt");
    container.copyFileFromContainer("/master_output.txt",
        "src/test/resources/master-slave/master_output.txt");
    container.copyFileFromContainer("/docker_output.txt",
        "src/test/resources/master-slave//docker_output.txt");


  }
}

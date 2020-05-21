package com.blazemeter.jmeter;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class SlaveMasterIT {

  @ClassRule
  public static GenericContainer container =
      new GenericContainer(new ImageFromDockerfile()
          //adding files to testcontainer context
          .withFileFromClasspath("rmi_keystore.jks", "master-slave/rmi_keystore.jks")
          .withFileFromClasspath("Dockerfile", "master-slave/Dockerfile")
          .withFileFromClasspath("test.jmx", "jmeter/HLSSamplerSlaveRemoteTest.jmx")
          .withDockerfilePath("Dockerfile")
      );

  @BeforeClass
  public static void beforeClass() {
    // Device running this test must have Docker running.
  }

  @Test
  public void shouldWhen() throws Exception {
    container.start();
  }
}

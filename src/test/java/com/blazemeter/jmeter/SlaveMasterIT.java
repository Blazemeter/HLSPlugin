package com.blazemeter.jmeter;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.LogUtils;

public class SlaveMasterIT {

  private static final Logger LOG = LoggerFactory.getLogger(SlaveMasterIT.class);

  @Test
  public void shouldRunHLSMasterSlaveTestWhenStartContainer() {
    // make sure Docker is running on your machine.
    GenericContainer container = new GenericContainer(
        new ImageFromDockerfile()
            //adding files to testcontainer context
            .withFileFromClasspath("rmi_keystore.jks", "master-slave/rmi_keystore.jks")
            .withFileFromClasspath("master-slave-test.sh", "master-slave/master-slave-test.sh")
            .withFileFromClasspath("Dockerfile", "master-slave/Dockerfile")
            .withFileFromClasspath("test.jmx", "jmeter/HLSSamplerSlaveRemoteTest.jmx")
            .withFileFromClasspath("mapping.json", "master-slave/mappings/mapping.json")
            .withFileFromFile("jmeter-bzm-hls.jar",
                new File("target/jmeter-test/lib/jmeter-bzm-hls.jar"))
            .withFileFromFile("hlsparserj.jar",
                new File("target/jmeter-test/lib/hlsparserj.jar"))
            .withDockerfilePath("Dockerfile"))
        .withLogConsumer(new Slf4jLogConsumer(LOG));
    container.waitingFor(new LogWaiterCondition().withRegex(".*Err:\\s+(\\d*).*").withTimeout(60));
    container.start();

  }

  private static class LogWaiterCondition extends AbstractWaitStrategy {

    private String regEx;
    private int timeout;

    @Override
    protected void waitUntilReady() {
      WaitingConsumer waitingConsumer = new WaitingConsumer();
      LogUtils.followOutput(DockerClientFactory.instance().client(),
          waitStrategyTarget.getContainerId(), waitingConsumer);

      Predicate<OutputFrame> waitPredicate = outputFrame ->
          outputFrame.getUtf8String().matches("(?s)" + regEx);

      try {
        waitingConsumer.waitUntil(waitPredicate, timeout, TimeUnit.SECONDS, 1);
      } catch (TimeoutException e) {
        throw new ContainerLaunchException(
            "Timed out waiting for log output matching '" + regEx + "'");
      }
    }

    private LogWaiterCondition withTimeout(int secondsTimeout) {
      this.timeout = secondsTimeout;
      return this;
    }

    private LogWaiterCondition withRegex(String regex) {
      this.regEx = regex;
      return this;
    }
  }
}

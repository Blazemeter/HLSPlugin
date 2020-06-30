package com.blazemeter.jmeter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.junit.Rule;
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

public class MasterSlave {

  private static final Logger LOG = LoggerFactory.getLogger(MasterSlave.class);
  private static final long TIMEOUT_MILLI = 120000;
  
  @Rule
  public GenericContainer container = new GenericContainer(
      new ImageFromDockerfile()
          //adding files to testcontainer context
          .withFileFromClasspath("rmi_keystore.jks", "master-slave/rmi_keystore.jks")
          .withFileFromClasspath("master-slave-test.sh", "master-slave/master-slave-test.sh")
          .withFileFromClasspath("Dockerfile", "master-slave/Dockerfile")
          .withFileFromClasspath("test.jmx", "master-slave/HLSSamplerSlaveRemoteTest.jmx")
          .withFileFromClasspath("mapping.json", "master-slave/mapping.json")
          .withFileFromFile("jmeter-bzm-hls.jar",
              new File("target/jmeter-test/lib/jmeter-bzm-hls.jar"))
          .withFileFromFile("hlsparserj.jar",
              new File("target/jmeter-test/lib/hlsparserj.jar"))
          .withDockerfilePath("Dockerfile"))
      .withLogConsumer(new Slf4jLogConsumer(LOG))
      .waitingFor(new LogWaiterCondition().withRegex(".*Created\\sremote\\sobject.*")
          .withTimeout(TIMEOUT_MILLI));

  @Test(timeout = TIMEOUT_MILLI * 2)
  public void shouldRunHLSMasterSlaveTestWhenStartContainer()
      throws IOException, InterruptedException {
    container.start();
    container.execInContainer("sh", "/jmeter/apache-jmeter-5.1.1/bin/jmeter", "-n", "-r", "-t",
        "/test.jmx", "-l", "/result.jtl", "-j", "/result.jtl");
    container.waitingFor(
        new LogWaiterCondition().withRegex("Finished\\sremote\\shost").withTimeout(TIMEOUT_MILLI));
  }

  private static class LogWaiterCondition extends AbstractWaitStrategy {

    private String regEx;
    private long timeout;

    @Override
    protected void waitUntilReady() {
      WaitingConsumer waitingConsumer = new WaitingConsumer();
      LogUtils.followOutput(DockerClientFactory.instance().client(),
          waitStrategyTarget.getContainerId(), waitingConsumer);

      Predicate<OutputFrame> waitPredicate = outputFrame ->
          outputFrame.getUtf8String().matches("(?s)" + regEx);

      try {
        waitingConsumer.waitUntil(waitPredicate, timeout, TimeUnit.MILLISECONDS, 1);
      } catch (TimeoutException e) {
        throw new ContainerLaunchException(
            "Timed out waiting for log output matching '" + regEx + "'");
      }
    }

    private LogWaiterCondition withTimeout(long milliTimeout) {
      this.timeout = milliTimeout;
      return this;
    }

    private LogWaiterCondition withRegex(String regex) {
      this.regEx = regex;
      return this;
    }
  }
}

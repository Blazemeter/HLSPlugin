package com.blazemeter.jmeter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

@Category(MasterSlaveIT.class)
public class MasterSlaveIT {

  private static final Logger LOG = LoggerFactory.getLogger(MasterSlaveIT.class);
  private static final long TIMEOUT_MILLI = 120000;
  private static final String JMETER_HOME_PATH = "/jmeter/apache-jmeter-5.1.1/bin";
  private static final Network network = Network.newNetwork();

  public GenericContainer<?> wiremockContainer;
  public GenericContainer<?> container;

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(wiremockContainer = buildWiremockContainerFromDockerfile())
      .around(container = getJavaContainerFromDockerfile());

  @Test(timeout = TIMEOUT_MILLI * 2)
  public void shouldRunHLSMasterSlaveTestWhenStartContainer()
      throws IOException, InterruptedException {
    ExecResult execResult = container.execInContainer("sh", JMETER_HOME_PATH + "/jmeter",
        "-n", "-r", "-t", "/test.jmx", "-l", "/result", "-j", "/master_logs");

    assertThat(execResult.getStdout()).contains("... end of run");
  }

  private GenericContainer<?> buildWiremockContainerFromDockerfile() {
    return new GenericContainer<>(
        new ImageFromDockerfile()
            //adding files to test-container context
            .withFileFromClasspath("mapping.json", "master-slave/mapping.json")
            .withFileFromClasspath("Dockerfile", "master-slave/WiremockDockerfile")
            .withDockerfilePath("Dockerfile"))
        .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("WIREMOCK"))
        .withExposedPorts(8080)
        .withNetwork(network)
        .withNetworkAliases("wiremock")
        // wait for wiremock to be running
        .waitingFor(new LogMessageWaitStrategy()
            .withRegEx(".*(/\\$\\$      /\\$\\$ /\\$\\$                 "
                + "    /\\$\\$      /\\$\\$                     /\\$\\$     ).*")
            .withStartupTimeout(Duration.ofMillis(TIMEOUT_MILLI)));
  }

  private GenericContainer<?> getJavaContainerFromDockerfile() {
    return new GenericContainer<>(
        new ImageFromDockerfile()
            //adding files to test-container context
            .withFileFromClasspath("master-slave-test.sh", "master-slave/master-slave-test.sh")
            .withFileFromClasspath("Dockerfile", "master-slave/Dockerfile")
            .withFileFromClasspath("test.jmx", "master-slave/HLSSamplerSlaveRemoteTest.jmx")
            .withFileFromFile("jmeter-bzm-hls.jar",
                new File("target/jmeter-test/lib/jmeter-bzm-hls.jar"))
            .withFileFromFile("hlsparserj.jar",
                new File("target/jmeter-test/lib/hlsparserj.jar"))
            .withDockerfilePath("Dockerfile"))
        .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("MAIN"))
        .withNetwork(network)
        .withNetworkAliases("master")
        .waitingFor(new LogMessageWaitStrategy().withRegEx(".*Created\\sremote\\sobject.*")
            .withStartupTimeout(Duration.ofMillis(TIMEOUT_MILLI)));

  }

}

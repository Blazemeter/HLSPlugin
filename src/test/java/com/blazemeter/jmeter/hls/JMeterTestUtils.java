package com.blazemeter.jmeter.hls;

import kg.apc.emulators.TestJMeterUtils;

public class JMeterTestUtils {

  private static boolean jmeterEnvInitialized = false;

  private JMeterTestUtils() {
  }

  public static void setupJmeterEnv() {
    if (!jmeterEnvInitialized) {
      jmeterEnvInitialized = true;
      TestJMeterUtils.createJmeterEnv();
    }
  }

}

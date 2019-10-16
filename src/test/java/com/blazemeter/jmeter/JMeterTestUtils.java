package com.blazemeter.jmeter;

import kg.apc.emulators.EmulatorJmeterEngine;
import kg.apc.emulators.EmulatorThreadMonitor;
import kg.apc.emulators.TestJMeterUtils;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.JMeterThreadMonitor;
import org.apache.jmeter.threads.ListenerNotifier;
import org.apache.jorphan.collections.HashTree;

public class JMeterTestUtils {

  private static boolean jmeterEnvInitialized = false;

  private JMeterTestUtils() {
  }

  public static void setupJmeterEnv() {
    if (!jmeterEnvInitialized) {
      jmeterEnvInitialized = true;
      TestJMeterUtils.createJmeterEnv();
      setupThreadWithNotifier();
    }
  }

  private static void setupThreadWithNotifier() {
    StandardJMeterEngine engine = new EmulatorJmeterEngine();
    JMeterThreadMonitor monitor = new EmulatorThreadMonitor();
    JMeterContextService.getContext().setEngine(engine);
    HashTree hashtree = new HashTree();
    hashtree.add(new LoopController());
    JMeterThread thread = new JMeterThread(hashtree, monitor, new ListenerNotifier());
    thread.setThreadName("test thread");
    JMeterContextService.getContext().setThread(thread);
  }

}

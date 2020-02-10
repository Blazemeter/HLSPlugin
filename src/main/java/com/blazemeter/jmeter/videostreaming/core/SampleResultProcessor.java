package com.blazemeter.jmeter.videostreaming.core;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.apache.jmeter.assertions.Assertion;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.processor.PostProcessor;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBeanHelper;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.SamplePackage;
import org.apache.jorphan.util.JMeterError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampleResultProcessor implements BiConsumer<String, SampleResult> {

  private static final Logger LOG = LoggerFactory.getLogger(SampleResultProcessor.class);
  private static final Set<String> SAMPLE_TYPE_NAMES = VideoStreamingSampler.getSampleTypesSet();

  private final TestElement testElement;

  public SampleResultProcessor(TestElement testElement) {
    this.testElement = testElement;
  }

  @Override
  public void accept(String name, SampleResult result) {
    result.setSampleLabel(testElement.getName() + " - " + name);
    JMeterContext threadContext = testElement.getThreadContext();
    updateSampleResultThreadsInfo(result, threadContext);
    threadContext.setPreviousResult(result);
    SamplePackage pack = (SamplePackage) threadContext.getVariables()
        .getObject(JMeterThread.PACKAGE_OBJECT);
    runPostProcessors(result, pack.getPostProcessors());
    checkAssertions(result, pack.getAssertions());
    threadContext.getVariables()
        .put(JMeterThread.LAST_SAMPLE_OK, Boolean.toString(result.isSuccessful()));
    notifySampleListeners(result, pack.getSampleListeners());
  }

  private void updateSampleResultThreadsInfo(SampleResult result, JMeterContext threadContext) {
    int totalActiveThreads = JMeterContextService.getNumberOfThreads();
    String threadName = threadContext.getThread().getThreadName();
    int activeThreadsInGroup = threadContext.getThreadGroup().getNumberOfThreads();
    result.setAllThreads(totalActiveThreads);
    result.setThreadName(threadName);
    result.setGroupThreads(activeThreadsInGroup);
    SampleResult[] subResults = result.getSubResults();
    if (subResults != null) {
      for (SampleResult subResult : subResults) {
        subResult.setGroupThreads(activeThreadsInGroup);
        subResult.setAllThreads(totalActiveThreads);
        subResult.setThreadName(threadName);
      }
    }
  }

  private void runPostProcessors(SampleResult result, List<PostProcessor> extractors) {
    for (PostProcessor ex : extractors) {
      TestBeanHelper.prepare((TestElement) ex);
      if (doesTestElementApplyToSampleResult(result, (TestElement) ex)) {
        ex.process();
      }
    }
  }

  private void checkAssertions(SampleResult result, List<Assertion> assertions) {
    for (Assertion assertion : assertions) {
      TestElement testElem = (TestElement) assertion;
      TestBeanHelper.prepare(testElem);
      if (doesTestElementApplyToSampleResult(result, testElem)) {
        AssertionResult assertionResult;
        try {
          assertionResult = assertion.getResult(result);
        } catch (AssertionError e) {
          LOG.debug("Error processing Assertion.", e);
          assertionResult = new AssertionResult(
              "Assertion failed! See log file (debug level, only).");
          assertionResult.setFailure(true);
          assertionResult.setFailureMessage(e.toString());
        } catch (JMeterError e) {
          LOG.error("Error processing Assertion.", e);
          assertionResult = new AssertionResult("Assertion failed! See log file.");
          assertionResult.setError(true);
          assertionResult.setFailureMessage(e.toString());
        } catch (Exception e) {
          LOG.error("Exception processing Assertion.", e);
          assertionResult = new AssertionResult("Assertion failed! See log file.");
          assertionResult.setError(true);
          assertionResult.setFailureMessage(e.toString());
        }
        result.setSuccessful(
            result.isSuccessful() && !(assertionResult.isError() || assertionResult.isFailure()));
        result.addAssertionResult(assertionResult);
      }
    }
  }

  private boolean doesTestElementApplyToSampleResult(SampleResult result, TestElement elem) {
    String elemLabelType = extractLabelType(elem.getName());
    String sampleLabelType = extractLabelType(result.getSampleLabel());
    return sampleLabelType.equals(elemLabelType) || !SAMPLE_TYPE_NAMES.contains(elemLabelType);
  }

  private String extractLabelType(String label) {
    int typeSeparatorIndex = label.lastIndexOf('-');
    return typeSeparatorIndex >= 0 ? label.substring(typeSeparatorIndex + 1).trim().toLowerCase()
        : "";
  }

  private void notifySampleListeners(SampleResult sampleResult,
      List<SampleListener> sampleListeners) {
    JMeterContext threadContext = testElement.getThreadContext();
    SampleEvent event = new SampleEvent(sampleResult, testElement.getThreadName(),
        threadContext.getVariables(),
        false);
    threadContext.getThread().getNotifier().notifyListeners(event, sampleListeners);
  }

}

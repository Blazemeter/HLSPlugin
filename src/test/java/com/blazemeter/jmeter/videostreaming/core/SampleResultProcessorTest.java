package com.blazemeter.jmeter.videostreaming.core;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.blazemeter.jmeter.JMeterTestUtils;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import org.apache.jmeter.assertions.ResponseAssertion;
import org.apache.jmeter.extractor.RegexExtractor;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.threads.SamplePackage;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SampleResultProcessorTest {

  private static final String MEDIA_SEGMENT_SAMPLE_TYPE = "media segment";
  private static final String SAMPLER_NAME = "HLS";
  private static final String TEST_URL = "http://test.com";
  private static final String EXTRACTOR_VAR_NAME = "myVar";

  private SampleResultProcessor processor;

  /*
   we use static context and listener since we need to initialize context only once to avoid issue
   of files initialization in mac complaining about existing folder and since we are using thread
   context, and the thread in beforeClass is different than one used on in threads, we need to reset
   it to avoid NullPointerException issues when getting thread context info.
   */
  private static JMeterContext context;

  @Mock
  private Consumer<SampleResult> sampleResultListener;

  @Mock
  private TestElement testElement;

  @BeforeClass
  public static void setupClass() {
    JMeterTestUtils.setupJmeterEnv();
    context = JMeterContextService.getContext();
  }

  @Before
  public void setup() {
    JMeterContextService.replaceContext(context);
    JMeterContextService.getContext().setVariables(new JMeterVariables());
    setupSampleListener();
    getSamplePackage().getAssertions().clear();
    getSamplePackage().getPostProcessors().clear();
    when(testElement.getName()).thenReturn(SAMPLER_NAME);
    when(testElement.getThreadContext()).thenReturn(context);
    processor = new SampleResultProcessor(testElement);
  }

  private void setupSampleListener() {
    SampleListener sampleListener = mock(SampleListener.class, withSettings().extraInterfaces(
        TestElement.class));
    doAnswer(a -> {
      sampleResultListener.accept(a.getArgument(0, SampleEvent.class).getResult());
      return null;
    }).
        when(sampleListener).sampleOccurred(any());
    // we use arrayList for assertions and post processors to be able to add elements
    SamplePackage pack = new SamplePackage(Collections.emptyList(),
        Collections.singletonList(sampleListener), Collections.emptyList(), new ArrayList<>(),
        new ArrayList<>(), Collections.emptyList(), Collections.emptyList());
    JMeterContextService.getContext().getVariables().putObject(JMeterThread.PACKAGE_OBJECT, pack);
  }

  @Test
  public void shouldSetSampleResultNameWhenProcessSampleResult() {
    SampleResult sampleResult = new SampleResult();
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, sampleResult);
    assertThat(sampleResult.getSampleLabel())
        .isEqualTo(SAMPLER_NAME + " - " + MEDIA_SEGMENT_SAMPLE_TYPE);
  }

  @Test
  public void shouldNotifySampleListenerWhenProcessSampleResult() {
    SampleResult sampleResult = new SampleResult();
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, sampleResult);
    verify(sampleResultListener).accept(sampleResult);
  }

  @Test
  public void shouldKeepSuccessResultWhenAssertionWithSameLabelTypeAndPasses() throws Exception {
    addAssertionWithLabelTypeAndUrl(MEDIA_SEGMENT_SAMPLE_TYPE, TEST_URL);
    SampleResult result = buildSampleResultWithUrl(TEST_URL);
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, result);
    assertThat(result.isSuccessful()).isTrue();
  }

  private SampleResult buildSampleResultWithUrl(String url) throws MalformedURLException {
    SampleResult result = new SampleResult();
    result.setURL(new URL(url));
    result.setSuccessful(true);
    return result;
  }

  private void addAssertionWithLabelTypeAndUrl(String labelType, String urlSubstring) {
    ResponseAssertion assertion = new ResponseAssertion();
    assertion.setName("test - " + labelType);
    assertion.setTestFieldURL();
    assertion.setToEqualsType();
    assertion.addTestString(urlSubstring);
    getSamplePackage().addAssertion(assertion);
  }

  private SamplePackage getSamplePackage() {
    return (SamplePackage) JMeterContextService.getContext().getVariables()
        .getObject(JMeterThread.PACKAGE_OBJECT);
  }

  @Test
  public void shouldSetFailResultWhenAssertionWithSameLabelTypeAndFails() throws Exception {
    buildFailedAssertionWithLabelType(MEDIA_SEGMENT_SAMPLE_TYPE);
    SampleResult result = buildSampleResultWithUrl(TEST_URL);
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, result);
    assertThat(result.isSuccessful()).isFalse();
  }

  private void buildFailedAssertionWithLabelType(String labelType) {
    addAssertionWithLabelTypeAndUrl(labelType, "fail");
  }

  @Test
  public void shouldSetFailResultsWhenAssertionWithNoLabelTypeAndFails() throws Exception {
    buildFailedAssertionWithLabelType("");
    SampleResult result = buildSampleResultWithUrl(TEST_URL);
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, result);
    assertThat(result.isSuccessful()).isFalse();
  }

  @Test
  public void shouldKeepSuccessResultWhenAssertionWithDifferentLabelTypeAndFails() throws Exception {
    buildFailedAssertionWithLabelType("audio playlist");
    SampleResult result = buildSampleResultWithUrl(TEST_URL);
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, result);
    assertThat(result.isSuccessful()).isTrue();
  }

  @Test
  public void shouldGetExtractedVariableWhenExtractorWithSameLabelType()
      throws Exception {
    addExtractorWithLabelType(MEDIA_SEGMENT_SAMPLE_TYPE);
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, buildSampleResultWithUrl(TEST_URL));
    assertThat(getExtractedVariable()).isEqualTo(TEST_URL);
  }

  private void addExtractorWithLabelType(String labelType) {
    RegexExtractor extractor = new RegexExtractor();
    extractor.setName("-" + labelType);
    extractor.setRefName(EXTRACTOR_VAR_NAME);
    extractor.setRegex(".*");
    extractor.setMatchNumber(1);
    extractor.setTemplate("$0$");
    extractor.setUseField(RegexExtractor.USE_URL);
    getSamplePackage().addPostProcessor(extractor);
  }

  private String getExtractedVariable() {
    return JMeterContextService.getContext().getVariables().get(EXTRACTOR_VAR_NAME);
  }

  @Test
  public void shouldGetNullVariableWhenExtractorWithDifferentLabelType()
      throws Exception {
    addExtractorWithLabelType("audio playlist");
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, buildSampleResultWithUrl(TEST_URL));
    assertThat(getExtractedVariable()).isNull();
  }

  @Test
  public void shouldGetExtractedVariableWhenExtractorWithNoLabelType()
      throws Exception {
    addExtractorWithLabelType("");
    processor.accept(MEDIA_SEGMENT_SAMPLE_TYPE, buildSampleResultWithUrl(TEST_URL));
    assertThat(getExtractedVariable()).isEqualTo(TEST_URL);
  }

}

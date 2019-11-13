package com.blazemeter.jmeter.videostreaming;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public abstract class VideoStreamingSamplerTest {

  protected static final String SEGMENT_CONTENT_TYPE = "video/MP2T";
  protected static final String BASE_URI = "http://test";
  protected static final String SAMPLER_NAME = "HLS";
  protected static final String VIDEO_TYPE_NAME = "video";
  protected static final String AUDIO_TYPE_NAME = "audio";
  protected static final String SUBTITLES_TYPE_NAME = "subtitles";

  protected com.blazemeter.jmeter.hls.logic.HlsSampler baseSampler;
  protected SegmentResultFallbackUriSamplerMock uriSampler = new SegmentResultFallbackUriSamplerMock();
  @Mock
  protected SampleResultProcessor sampleResultProcessor;
  protected TimeMachine timeMachine = new TimeMachine() {

    private Instant now = Instant.now();

    @Override
    public synchronized void awaitMillis(long millis) {
      now = now.plusMillis(millis);
    }

    @Override
    public synchronized Instant now() {
      return now;
    }

    @Override
    public void interrupt() {
    }

  };
  @Mock
  protected VideoStreamingHttpClient httpClient;

  protected static class SegmentResultFallbackUriSamplerMock implements
      Function<URI, HTTPSampleResult> {

    @SuppressWarnings("unchecked")
    private Function<URI, HTTPSampleResult> mock = (Function<URI, HTTPSampleResult>) Mockito
        .mock(Function.class);

    @Override
    public HTTPSampleResult apply(URI uri) {
      HTTPSampleResult result = mock.apply(uri);
      return result != null ? result : buildSegmentResult(uri);
    }

    private HTTPSampleResult buildSegmentResult(URI uri) {
      return buildSampleResult(uri, SEGMENT_CONTENT_TYPE, "");
    }

    public void setupUriSampleResults(URI uri, HTTPSampleResult result,
        HTTPSampleResult... results) {
      when(mock.apply(uri)).thenReturn(result, results);
    }

  }

  protected static HTTPSampleResult buildBaseSegmentSampleResult(String type, int sequenceNumber) {
    return buildSampleResult(buildSegmentUri(type, sequenceNumber), SEGMENT_CONTENT_TYPE, "");
  }

  protected static URI buildSegmentUri(String type, int sequenceNumber) {
    return URI.create(String.format("%s/%s/%03d.ts", BASE_URI, type, sequenceNumber));
  }

  protected static HTTPSampleResult buildSampleResult(URI uri, String contentType,
      String responseBody) {
    HTTPSampleResult ret = buildBaseSampleResult(uri);
    ret.setSuccessful(true);
    ret.setResponseCodeOK();
    ret.setResponseMessageOK();
    ret.setResponseHeaders("Content-Type: " + contentType + "\n");
    ret.setResponseData(responseBody, Charsets.UTF_8.name());
    return ret;
  }

  protected static HTTPSampleResult buildBaseSampleResult(URI uri) {
    try {
      HTTPSampleResult ret = new HTTPSampleResult();
      ret.setHTTPMethod("GET");
      ret.setURL(uri.toURL());
      ret.setRequestHeaders("TestHeader: TestVal");
      return ret;
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Before
  public void setUp() {
    buildSampler(uriSampler);
  }

  protected void buildSampler(Function<URI, HTTPSampleResult> uriSampler) {
    doAnswer(a -> uriSampler.apply(a.getArgument(0, URI.class))).
        when(httpClient).downloadUri(any());
    baseSampler = new com.blazemeter.jmeter.hls.logic.HlsSampler();
    baseSampler.setName(SAMPLER_NAME);
    buildSampleImpl();
  }

  protected abstract void buildSampleImpl();

  protected String getResource(String resourceName) throws IOException {
    return Resources.toString(Resources.getResource(getClass(), resourceName), Charsets.UTF_8);
  }

  protected HTTPSampleResult buildBaseSampleResult(String name, URI uri, String body) {
    HTTPSampleResult ret = buildSampleResult(uri, "application/x-mpegURL", body);
    ret.setSampleLabel(name);
    return ret;
  }

  protected void setPlaySeconds(double playSeconds) {
    baseSampler.setPlayVideoDuration(true);
    baseSampler.setPlaySeconds(String.valueOf((int) playSeconds));
  }

  protected String buildSegmentName(String segmentType) {
    return "HLS - " + segmentType + " segment";
  }

  protected HTTPSampleResult addDurationHeader(HTTPSampleResult result, double duration) {
    result.setResponseHeaders(result.getResponseHeaders() + "X-MEDIA-SEGMENT-DURATION: " + duration
        + "\n");
    return result;
  }

  protected void verifySampleResults(SampleResult... results) {
    ArgumentCaptor<String> sampleNamesCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<SampleResult> sampleResultCaptor = ArgumentCaptor.forClass(SampleResult.class);
    verify(sampleResultProcessor, atLeastOnce())
        .accept(sampleNamesCaptor.capture(), sampleResultCaptor.capture());

    Iterator<String> sampleNameIt = sampleNamesCaptor.getAllValues().iterator();
    for (SampleResult sampleResult : sampleResultCaptor.getAllValues()) {
      sampleResult.setSampleLabel(SAMPLER_NAME + " - " + sampleNameIt.next());
    }
    assertThat(ComparableSampleResult.listFrom(sampleResultCaptor.getAllValues()))
        .isEqualTo(ComparableSampleResult.listFrom(Arrays.asList(results)));
  }

}

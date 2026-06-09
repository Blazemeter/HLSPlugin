package com.blazemeter.jmeter.videostreaming.core;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.blazemeter.jmeter.JMeterTestUtils;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import java.net.URI;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class VideoStreamingHttpClientTest {

  private VideoStreamingHttpClient httpClient;

  @BeforeClass
  public static void setupClass() {
    JMeterTestUtils.setupJmeterEnv();
  }

  @Before
  public void setup() {
    httpClient = new VideoStreamingHttpClient(new HlsSampler());
  }

  /*
   Reproduces the production scenario where a dynamic master URL variable resolves to a value
   without a scheme (e.g. the JSON extractor default "C_hlsManifestPath_Not_Found"). Such a value
   is a valid relative URI, so URI.create succeeds but URI.toURL() throws "URI is not absolute".
   The client must not propagate that exception; it must return a failed sample result.
   */
  @Test
  public void shouldReturnFailedResultWhenUriIsNotAbsolute() {
    HTTPSampleResult result = httpClient.downloadUri(URI.create("C_hlsManifestPath_Not_Found"));
    assertFailedResult(result);
  }

  @Test
  public void shouldReturnFailedResultWhenUriIsRelativePath() {
    HTTPSampleResult result = httpClient.downloadUri(URI.create("/relative/master.m3u8"));
    assertFailedResult(result);
  }

  @Test
  public void shouldReturnFailedResultWhenUriIsNull() {
    HTTPSampleResult result = httpClient.downloadUri(null);
    assertFailedResult(result);
  }

  private void assertFailedResult(HTTPSampleResult result) {
    assertThat(result).isNotNull();
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getResponseCode()).contains(IllegalArgumentException.class.getName());
  }

}

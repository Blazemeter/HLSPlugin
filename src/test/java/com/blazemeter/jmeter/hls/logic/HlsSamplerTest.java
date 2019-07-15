package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.jmeter.samplers.SampleResult;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HlsSamplerTest {

  private static final String HEADERS = "headerKey1 : header11 header12 header13\n"
      + "headerKey2 : header21 header22 header23\n"
      + "headerKey3 : header31\n";
  private static final String PLAYLIST_PATH = "/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d";
  private static final String BASE_URL = "http://www.mock.com";
  private static final String VIDEO_URL = BASE_URL + "/path";

  private HlsSampler sampler;

  @Mock
  private Parser parserMock;

  @Before
  public void setup() throws IOException {
    buildSampler();
    setupParser();
  }

  private void buildSampler() {
    sampler = new HlsSampler();
    sampler.setURLData(VIDEO_URL);
    sampler.setResData("640x360");
    sampler.setNetworkData("1395723");
    sampler.setBandwidthType(BandwidthOption.CUSTOM);
    sampler.setResolutionType(ResolutionOption.CUSTOM);
    sampler.setProtocol("http");
    sampler.setPlaySecondsData("20");
    sampler.setVideoDuration(true);
    sampler.setParser(parserMock);
    sampler.setName("Test");
  }

  private void setupParser() throws IOException {
    Map<String, List<String>> headers = this.buildHeaders();
    setupMasterListParser(headers);
    setupPlayListParser(headers);
    setupFragmentParser(1, headers);
    setupFragmentParser(2, headers);
    setupFragmentParser(3, headers);
  }

  private Map<String, List<String>> buildHeaders() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("headerKey1", Arrays.asList("header11", "header12", "header13"));
    headers.put("headerKey2", Arrays.asList("header21", "header22", "header23"));
    headers.put("headerKey3", Collections.singletonList("header31"));
    return headers;
  }

  private void setupMasterListParser(Map<String, List<String>> headers) throws IOException {
    String payload1 = "#EXTM3U\n"
        + "#EXT-X-VERSION:4\n"
        + "#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=1395723,PROGRAM-ID=1,CODECS=\"avc1.42c01e,mp4a.40.2\",RESOLUTION=640x360,SUBTITLES=\"subs\"\n"
        + "/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n"
        + "#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=170129,PROGRAM-ID=1,CODECS=\"avc1.42c00c,mp4a.40.2\",RESOLUTION=320x180,SUBTITLES=\"subs\"\n"
        + "/videos/DianaLaufenberg_2010X/video/64k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n"
        + "#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=425858,PROGRAM-ID=1,CODECS=\"avc1.42c015,mp4a.40.2\",RESOLUTION=512x288,SUBTITLES=\"subs\"\n"
        + "/videos/DianaLaufenberg_2010X/video/180k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n"
        + "#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=718158,PROGRAM-ID=1,CODECS=\"avc1.42c015,mp4a.40.2\",RESOLUTION=512x288,SUBTITLES=\"subs\"\n"
        + "/videos/DianaLaufenberg_2010X/video/320k.m3u8?preroll=Thousands&uniqueId=4df94b1d";
    setupUrlParser(VIDEO_URL, headers, payload1);
    when(parserMock.extractMediaUrl(any(String.class), any(String.class),
        any(Integer.class), any(BandwidthOption.class),
        any(ResolutionOption.class)))
        .thenReturn(PLAYLIST_PATH);
  }

  private void setupUrlParser(String url, Map<String, List<String>> headers, String payload)
      throws IOException {
    when(parserMock.getBaseUrl(eq(new URL(url)), any(SampleResult.class), anyBoolean()))
        .thenReturn(buildDataRequest(url, headers, payload));
  }

  private DataRequest buildDataRequest(String url, Map<String, List<String>> headers,
      String payload) {
    DataRequest respond = new DataRequest();
    respond.setRequestHeaders("GET  " + url + "\n");
    respond.setHeaders(headers);
    respond.setResponse(payload);
    respond.setResponseCode("200");
    respond.setResponseMessage("OK");
    respond.setContentType("application/json;charset=UTF-8");
    respond.setSuccess(true);
    respond.setSentBytes(payload.length());
    respond.setContentEncoding("UTF-8");
    return respond;
  }

  private void setupPlayListParser(Map<String, List<String>> headers) throws IOException {
    String payload2 = "#EXTM3U\n"
        + "#EXT-X-TARGETDURATION:10\n"
        + "#EXT-X-VERSION:4\n"
        + "#EXT-X-MEDIA-SEQUENCE:0\n"
        + "#EXT-X-PLAYLIST-TYPE:VOD\n"
        + "#EXTINF:5.0000,\n"
        + "#EXT-X-BYTERANGE:440672@0\n"
        + "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n"
        + "#EXTINF:5.0000,\n"
        + "#EXT-X-BYTERANGE:94000@440672\n"
        + "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n"
        + "#EXTINF:1.9583,\n"
        + "#EXT-X-BYTERANGE:22748@534672\n"
        + "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n"
        + "#EXT-X-DISCONTINUITY";
    setupUrlParser(BASE_URL + PLAYLIST_PATH, headers, payload2);
    //We are building the list each time due to the hls sampler modifying the returned list.
    when(parserMock.extractVideoUrl(any()))
        .thenAnswer(i -> buildFragments());
    when(parserMock.isLive(any(String.class)))
        .thenReturn(false);
  }

  private List<DataFragment> buildFragments() {
    return IntStream.rangeClosed(1, 3)
        .mapToObj(i -> new DataFragment("10", buildSegmentUrl(i)))
        .collect(Collectors.toList());
  }

  private String buildSegmentUrl(int segmentNumber) {
    return "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_" + segmentNumber + ".ts";
  }

  private void setupFragmentParser(int fragmentNumber,
      Map<String, List<String>> headers) throws IOException {
    String segmentUrl = buildSegmentUrl(fragmentNumber);
    setupUrlParser(segmentUrl, headers, "chunk");
  }

  @Test
  public void testSample() {
    SampleResult result = sampler.sample(null);
    SampleResult expected = buildExpectedSampleResult();
    assertSampleResult(expected, result);
  }

  private SampleResult buildExpectedSampleResult() {
    SampleResult subResult = buildSampleResult(BASE_URL + PLAYLIST_PATH,
        "600k.m3u8?preroll=Thousands&uniqueId=4df94b1d");
    subResult.addRawSubResult(buildSegmentSampleResult(1));
    subResult.addRawSubResult(buildSegmentSampleResult(2));
    subResult.addRawSubResult(buildSegmentSampleResult(3));

    subResult.setResponseHeaders(HEADERS);

    SampleResult expected = buildSampleResult(VIDEO_URL, "Test");
    expected.setResponseHeaders(HEADERS);
    expected.addRawSubResult(subResult);
    return expected;
  }

  private SampleResult buildSampleResult(String url, String label) {
    SampleResult sResult = new SampleResult();
    sResult.setRequestHeaders("GET  " + url + "\n\n\n\n\n");
    sResult.setSuccessful(true);
    sResult.setResponseMessage("OK");
    sResult.setSampleLabel(label);
    sResult.setResponseHeaders(
        "URL: " + url + "\n" + HEADERS);
    sResult.setResponseData(String.valueOf(StandardCharsets.UTF_8), StandardCharsets.UTF_8.name());
    sResult.setResponseCode("200");
    sResult.setContentType("application/json;charset=UTF-8");
    sResult.setDataEncoding("UTF-8");
    return sResult;
  }

  private SampleResult buildSegmentSampleResult(int segmentNumber) {
    return buildSampleResult(buildSegmentUrl(segmentNumber),
        "Thousands-320k_" + segmentNumber + ".ts");
  }

  private void assertSampleResult(SampleResult expected, SampleResult actual) {
    assertThat(sampleResultToJson(actual).toString())
        .isEqualTo(sampleResultToJson(expected).toString());
  }

  @SuppressWarnings("unchecked")
  private JSONObject sampleResultToJson(SampleResult sample) {
    JSONObject json = new JSONObject();
    json.put("requestHeaders", sample.getRequestHeaders());
    json.put("responseMessage", sample.getResponseMessage());
    json.put("label", sample.getSampleLabel());
    json.put("responseHeaders", sample.getResponseHeaders());
    json.put("responseCode", sample.getResponseCode());
    json.put("contentType", sample.getContentType());
    json.put("dataEncoding", sample.getDataEncodingNoDefault());
    json.put("children", Arrays.stream(sample.getSubResults()).map(this::sampleResultToJson)
        .collect(Collectors.toList()));
    return json;
  }

  @Test
  public void testResumeVideoDownloadBetweenIterationsOn() {
    sampler.setPlaySecondsData("9");
    sampler.setResumeVideoStatus(true);
    sampler.sample(null);
    SampleResult result = sampler.sample(null);

    assertEquals("Thousands-320k_2.ts", getFirstSegmentLabel(result));
  }

  @Test
  public void testResumeVideoDownloadBetweenIterationsOff() {
    sampler.setPlaySecondsData("9");
    sampler.setResumeVideoStatus(false);
    sampler.sample(null);
    SampleResult result = sampler.sample(null);

    assertEquals("Thousands-320k_1.ts", getFirstSegmentLabel(result));
  }

  private String getFirstSegmentLabel(SampleResult result) {
    SampleResult[] subresults = result.getSubResults();
    SampleResult[] subSubresults = subresults[0].getSubResults();

    return subSubresults[0].getSampleLabel();
  }
}
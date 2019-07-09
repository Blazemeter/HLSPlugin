package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.jmeter.samplers.SampleResult;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class HlsSamplerTest {

  private static final String HEADERS = "headerKey1 : header11 header12 header13\n"
      + "headerKey2 : header21 header22 header23\n"
      + "headerKey3 : header31\n";
  private static final String PLAYLIST_PATH = "/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d";
  private static final String VIDEO_URL = "http://www.mock.com/path";

  private static final String PLAYLIST_URL = "https://www.mock.com/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d";

  private HlsSampler sampler;
  private Parser parserMock;

  @Before
  public void setup() {
    parserMock = Mockito.mock(Parser.class);

    sampler = new HlsSampler();
    sampler.setURLData(VIDEO_URL);
    sampler.setResData("640x360");
    sampler.setNetworkData("1395723");
    sampler.setBandwidthType(BandwidthOption.CUSTOM);
    sampler.setResolutionType(ResolutionOption.CUSTOM);
    sampler.setProtocol("https");
    sampler.setPlaySecondsData("20");
    sampler.setVideoDuration(true);
    sampler.setParser(parserMock);
    sampler.setName("Test");
    sampler.setResumeVideoStatus(true);
  }

  @Test
  public void testSample() throws Exception {
    Map<String, List<String>> headers = this.buildHeaders();
    setupMasterListParser(headers);
    setupPlayListParser(headers);
    setupFragmentParser(1, headers);
    setupFragmentParser(2, headers);
    setupFragmentParser(3, headers);
    SampleResult result = sampler.sample(null);
    SampleResult expected = buildExpectedSampleResult();
    this.assertSampleResult(expected, result);
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
    setupUrlParser(PLAYLIST_URL, headers, payload2);
    when(parserMock.extractVideoUrl(any()))
        .thenReturn(buildFragments());
    when(parserMock.isLive(any(String.class)))
        .thenReturn(false);
  }

  private void setupFragmentParser(int fragmentNumber,
      Map<String, List<String>> headers) throws IOException {
    String segmentUrl = this.buildSegmentUrl(fragmentNumber);
    setupUrlParser(segmentUrl, headers, "chunck");
  }

  //In this method we didn't use Arrays.asList since the test modifies
  // the list and list provided by the method is immutable.
  private List<DataFragment> buildFragments() {
    List<DataFragment> fragments = new ArrayList<>();
    fragments.add(new DataFragment("10",
        this.buildSegmentUrl(1)));
    fragments.add(new DataFragment("10",
        this.buildSegmentUrl(2)));
    fragments.add(new DataFragment("10",
        this.buildSegmentUrl(3)));

    return fragments;
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

  private SampleResult buildExpectedSampleResult() {

    SampleResult subResult = this
        .buildSampleResult(VIDEO_URL + PLAYLIST_PATH,
            "600k.m3u8?preroll=Thousands&uniqueId=4df94b1d");
    subResult.addRawSubResult(this.buildSegmentSampleResult(1));
    subResult.addRawSubResult(this.buildSegmentSampleResult(2));
    subResult.addRawSubResult(this.buildSegmentSampleResult(3));

    subResult.setResponseHeaders(HEADERS);

    SampleResult expected = this.buildSampleResult(VIDEO_URL, "Test");
    expected.setResponseHeaders(HEADERS);
    expected.addRawSubResult(subResult);
    return expected;
  }

  private SampleResult buildSegmentSampleResult(int segmentNumber) {
    return this.buildSampleResult(
        this.buildSegmentUrl(segmentNumber),
        "Thousands-320k_" + segmentNumber + ".ts");
  }

  private String buildSegmentUrl(int segmentNumber) {
    return "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_" + segmentNumber + ".ts";
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

  private void assertSampleResult(SampleResult expected, SampleResult actual) {
    assertThat(sampleResultToJson(actual).toString())
        .isEqualTo(sampleResultToJson(expected).toString());
  }

}

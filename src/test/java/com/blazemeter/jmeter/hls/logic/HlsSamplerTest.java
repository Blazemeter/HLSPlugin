package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Java6Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

  private HlsSampler sampler;
  private Parser parserMock;
  static final String HEADERS = "headerKey1 : header11 header12 header13\nheaderKey2 : header21 header22 header23\nheaderKey3 : header31\n";
  static final String PLAYLIST_PATH = "/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d";
  static final String VIDEO_URL = "http://www.mock.com/PLAYLIST_PATH";

  @Before
  public void setup() {
    parserMock = Mockito.mock(Parser.class);
    sampler = new HlsSampler();
    sampler.setURLData("http://www.mock.com/PLAYLIST_PATH");
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

    List<DataFragment> fragments = this.buildFragments();

    String payload1 = "#EXTM3U\n#EXT-X-VERSION:4\n#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=1395723,PROGRAM-ID=1,CODECS=\"avc1.42c01e,mp4a.40.2\",RESOLUTION=640x360,SUBTITLES=\"subs\"\n/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=170129,PROGRAM-ID=1,CODECS=\"avc1.42c00c,mp4a.40.2\",RESOLUTION=320x180,SUBTITLES=\"subs\"\n/videos/DianaLaufenberg_2010X/video/64k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=425858,PROGRAM-ID=1,CODECS=\"avc1.42c015,mp4a.40.2\",RESOLUTION=512x288,SUBTITLES=\"subs\"\n/videos/DianaLaufenberg_2010X/video/180k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=718158,PROGRAM-ID=1,CODECS=\"avc1.42c015,mp4a.40.2\",RESOLUTION=512x288,SUBTITLES=\"subs\"\n/videos/DianaLaufenberg_2010X/video/320k.m3u8?preroll=Thousands&uniqueId=4df94b1d";
    String payload2 = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXT-X-VERSION:4\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:5.0000,\n#EXT-X-BYTERANGE:440672@0\nhttps://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n#EXTINF:5.0000,\n#EXT-X-BYTERANGE:94000@440672\nhttps://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n#EXTINF:1.9583,\n#EXT-X-BYTERANGE:22748@534672\nhttps://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n#EXT-X-DISCONTINUITY";

    Map<String, List<String>> headers = this.buildHeaders();

    DataRequest respond1 = buildDataRequest("http://www.mock.com/PLAYLIST_PATH", headers, payload1);
    DataRequest respond2 = buildDataRequest(VIDEO_URL + PLAYLIST_PATH, headers, payload2);
    DataRequest respond3 = buildDataRequest(
        this.buildSegmentUrl(1), headers, "chunck");
    DataRequest respond4 = buildDataRequest(
        this.buildSegmentUrl(2), headers, "chunck");
    DataRequest respond5 = buildDataRequest(
        this.buildSegmentUrl(3), headers, "chunk");

    Mockito.when(parserMock
        .getBaseUrl(Mockito.any(URL.class), Mockito.any(SampleResult.class), Mockito.anyBoolean()))
        .thenReturn(respond1)
        .thenReturn(respond2)
        .thenReturn(respond3)
        .thenReturn(respond4)
        .thenReturn(respond5);

    Mockito.when(parserMock.extractMediaUrl(Mockito.any(String.class), Mockito.any(String.class),
        Mockito.any(Integer.class), Mockito.any(BandwidthOption.class),
        Mockito.any(ResolutionOption.class)))
        .thenReturn(PLAYLIST_PATH);
    Mockito.when(parserMock.extractVideoUrl(Mockito.any()))
        .thenReturn(fragments);
    Mockito.when(parserMock.isLive(Mockito.any(String.class)))
        .thenReturn(false);

    SampleResult result = sampler.sample(null);
    SampleResult expected = buildExpectedSampleResult();

    this.assertSampleResult(expected, result);
  }

  private Map<String, List<String>> buildHeaders() {
    Map<String, List<String>> headers = new HashMap<>();
    List<String> header1 = Arrays.asList("header11", "header12", "header13");
    List<String> header2 = Arrays.asList("header21", "header22", "header23");
    List<String> header3 = Arrays.asList("header31");

    headers.put("headerKey1", header1);
    headers.put("headerKey2", header2);
    headers.put("headerKey3", header3);

    return headers;
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
    SampleResult subResult = this.buildSampleResult(
        this.buildSegmentUrl(segmentNumber),
        "Thousands-320k_" + segmentNumber + ".ts");

    return subResult;
  }

  private String buildSegmentUrl(int segmentNumber) {
    String url =
        "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_" + segmentNumber + ".ts";
    return url;
  }

  private SampleResult buildSampleResult(String url, String label) {
    SampleResult sResult = new SampleResult();
    sResult.setRequestHeaders("GET  " + url + "\n\n\n\n\n");
    sResult.setSuccessful(true);
    sResult.setResponseMessage("OK");
    sResult.setSampleLabel(label);
    sResult.setResponseHeaders(
        "URL: " + url + "\n" + HEADERS);
    sResult.setResponseData(String.valueOf(StandardCharsets.UTF_8));
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

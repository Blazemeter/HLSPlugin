package com.blazemeter.jmeter.hls.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.jmeter.samplers.SampleResult;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HlsSamplerTest {

  private HlsSampler sampler;
  private Parser parserMock;

  @Before
  public void setup() {
    parserMock = Mockito.mock(Parser.class);
    sampler = new HlsSampler();
    sampler.setURLData("http://www.mock.com/path");
    sampler.setResData("640x360");
    sampler.setNetworkData("1395723");
    sampler.setBandwidthType(BandwidthOption.CUSTOM);
    sampler.setResolutionType(ResolutionOption.CUSTOM);
    sampler.setProtocol("https");
    sampler.setPlaySecondsData("20");
    sampler.setVideoDuration(true);
    sampler.setParser(parserMock);
    sampler.setName("Test");
  }

  @Test
  public void testSample() throws Exception {

    List<DataFragment> fragments = this.buildFragments();

    String payload1 = "#EXTM3U\n#EXT-X-VERSION:4\n#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=1395723,PROGRAM-ID=1,CODECS=\"avc1.42c01e,mp4a.40.2\",RESOLUTION=640x360,SUBTITLES=\"subs\"\n/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=170129,PROGRAM-ID=1,CODECS=\"avc1.42c00c,mp4a.40.2\",RESOLUTION=320x180,SUBTITLES=\"subs\"\n/videos/DianaLaufenberg_2010X/video/64k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=425858,PROGRAM-ID=1,CODECS=\"avc1.42c015,mp4a.40.2\",RESOLUTION=512x288,SUBTITLES=\"subs\"\n/videos/DianaLaufenberg_2010X/video/180k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n#EXT-X-STREAM-INF:AUDIO=\"600k\",BANDWIDTH=718158,PROGRAM-ID=1,CODECS=\"avc1.42c015,mp4a.40.2\",RESOLUTION=512x288,SUBTITLES=\"subs\"\n/videos/DianaLaufenberg_2010X/video/320k.m3u8?preroll=Thousands&uniqueId=4df94b1d";
    String payload2 = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXT-X-VERSION:4\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:5.0000,\n#EXT-X-BYTERANGE:440672@0\nhttps://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n#EXTINF:5.0000,\n#EXT-X-BYTERANGE:94000@440672\nhttps://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n#EXTINF:1.9583,\n#EXT-X-BYTERANGE:22748@534672\nhttps://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k.ts\n#EXT-X-DISCONTINUITY";
    Map<String, List<String>> headers = new HashMap<>();
    List<String> header1 = buildHeader("header11", "header12", "header13");
    List<String> header2 = buildHeader("header21", "header22", "header23");
    List<String> header3 = buildHeader("header31", "", "");

    headers.put("headerKey1", header1);
    headers.put("headerKey2", header2);
    headers.put("headerKey3", header3);

    DataRequest respond1 = buildDataRequest(headers, payload1, "GET  http://www.mock.com/path\n");
    DataRequest respond2 = buildDataRequest(headers, payload2,
        "GET  http://www.mock.com/path/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n");
    DataRequest respond3 = buildDataRequest(headers, "chunck",
        "GET  https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_1.ts\n");
    DataRequest respond4 = buildDataRequest(headers, "chunck",
        "GET  https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_2.ts\n");
    DataRequest respond5 = buildDataRequest(headers, "chunk",
        "GET  https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_3.ts\n");

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
        .thenReturn(
            "/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d");
    Mockito.when(parserMock.extractVideoUrl(Mockito.any()))
        .thenReturn(fragments);
    Mockito.when(parserMock.isLive(Mockito.any(String.class)))
        .thenReturn(false);

    SampleResult result = sampler.sample(null);
    SampleResult expected = buildExpectedSampleResult();

    assertThat(sampleResultToJson(result).toString()).isEqualTo(sampleResultToJson(expected).toString());
  }

  private List<DataFragment> buildFragments() {
    DataFragment f1 = new DataFragment("10",
        "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_1.ts");
    DataFragment f2 = new DataFragment("10",
        "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_2.ts");
    DataFragment f3 = new DataFragment("10",
        "https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_3.ts");
    List<DataFragment> fragments = new ArrayList<>();
    fragments.add(f1);
    fragments.add(f2);
    fragments.add(f3);

    return fragments;
  }

  private List<String> buildHeader(String val1, String val2, String val3) {
    List<String> header = new ArrayList<>();

    header.add(val1);
    if (!val1.isEmpty()) {
      header.add(val2);
    }
    if (!val3.isEmpty()) {
      header.add(val3);
    }
    return header;
  }

  private DataRequest buildDataRequest(Map<String, List<String>> headers, String payload,
      String request) {

    DataRequest respond = new DataRequest();
    respond.setRequestHeaders(request);
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
    json.put("children", Arrays.stream(sample.getSubResults()).map(this::sampleResultToJson).collect(Collectors.toList()));
    return json;
  }

  private SampleResult buildExpectedSampleResult() {
    sampler = new HlsSampler();


    SampleResult subResult = sampler.sample(null);
    SampleResult subResult1 = sampler.sample(null);
    SampleResult subResult2 = sampler.sample(null);
    SampleResult subResult3 = sampler.sample(null);

    subResult1.setRequestHeaders(
        "GET  https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_1.ts\n\n\n\n\n");
    subResult1.setSuccessful(true);
    subResult1.setResponseMessage("OK");
    subResult1.setSampleLabel("Thousands-320k_1.ts");
    subResult1.setResponseHeaders(
        "URL: https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_1.ts\nheaderKey1 : header11 header12 header13\nheaderKey2 : header21 header22 header23\nheaderKey3 : header31 \n");
    subResult1.setResponseData(String.valueOf(StandardCharsets.UTF_8));
    subResult1.setResponseCode("200");
    subResult1.setContentType("application/json;charset=UTF-8");
    subResult1.setDataEncoding("UTF-8");

    subResult2.setRequestHeaders(
        "GET  https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_2.ts\n\n\n\n\n");
    subResult2.setSuccessful(true);
    subResult2.setResponseMessage("OK");
    subResult2.setSampleLabel("Thousands-320k_2.ts");
    subResult2.setResponseHeaders(
        "URL: https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_2.ts\nheaderKey1 : header11 header12 header13\nheaderKey2 : header21 header22 header23\nheaderKey3 : header31 \n");
    subResult2.setResponseData(String.valueOf(StandardCharsets.UTF_8));
    subResult2.setResponseCode("200");
    subResult2.setContentType("application/json;charset=UTF-8");
    subResult2.setDataEncoding("UTF-8");

    subResult3.setRequestHeaders(
        "GET  https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_3.ts\n\n\n\n\n");
    subResult3.setSuccessful(true);
    subResult3.setResponseMessage("OK");
    subResult3.setSampleLabel("Thousands-320k_3.ts");
    subResult3.setResponseHeaders(
        "URL: https://pb.tedcdn.com/bumpers/hls/video/in/Thousands-320k_3.ts\nheaderKey1 : header11 header12 header13\nheaderKey2 : header21 header22 header23\nheaderKey3 : header31 \n");
    subResult3.setResponseData(String.valueOf(StandardCharsets.UTF_8));
    subResult3.setResponseCode("200");
    subResult3.setContentType("application/json;charset=UTF-8");
    subResult3.setDataEncoding("UTF-8");

    subResult.setRequestHeaders(
        "GET  http://www.mock.com/path/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d\n\n\n\n\n");
    subResult.setSuccessful(true);
    subResult.setResponseMessage("OK");
    subResult.setSampleLabel("600k.m3u8?preroll=Thousands&uniqueId=4df94b1d");
    subResult.setResponseHeaders(
        "headerKey1 : header11 header12 header13\nheaderKey2 : header21 header22 header23\nheaderKey3 : header31 \n");
    subResult.setResponseData(String.valueOf(StandardCharsets.UTF_8));
    subResult.setResponseCode("200");
    subResult.setContentType("application/json;charset=UTF-8");
    subResult.setDataEncoding("UTF-8");
    subResult.addRawSubResult(subResult1);
    subResult.addRawSubResult(subResult2);
    subResult.addRawSubResult(subResult3);

    SampleResult expected = sampler.sample(null);
    expected.setRequestHeaders("GET  http://www.mock.com/path\n\n\n\n\n");
    expected.setSuccessful(true);
    expected.setResponseMessage("OK");
    expected.setSampleLabel("Test");
    expected.setResponseHeaders(
        "headerKey1 : header11 header12 header13\nheaderKey2 : header21 header22 header23\nheaderKey3 : header31 \n");
    expected.setResponseData(String.valueOf(StandardCharsets.UTF_8));
    expected.setResponseCode("200");
    expected.setContentType("application/json;charset=UTF-8");
    expected.setDataEncoding("UTF-8");
    expected.addRawSubResult(subResult);
    return expected;
  }

}

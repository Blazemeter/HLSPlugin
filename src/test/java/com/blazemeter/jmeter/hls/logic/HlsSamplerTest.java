package com.blazemeter.jmeter.hls.logic;

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
    String jsonToTest = this.buildSampleResultJson(result);
    String jsonOk = "{\"Test\":\"{\\\"requestHeaders\\\":\\\"GET  http:\\\\\\/\\\\\\/www.mock.com\\\\\\/path\\\\n\\\\n\\\\n\\\\n\\\\n\\\",\\\"responseHeaders\\\":\\\"headerKey1 : header11 header12 header13\\\\nheaderKey2 : header21 header22 header23\\\\nheaderKey3 : header31\\\\n\\\",\\\"dataEncoding\\\":\\\"UTF-8\\\",\\\"label\\\":\\\"Test\\\",\\\"responseMessage\\\":\\\"OK\\\",\\\"contentType\\\":\\\"application\\\\\\/json;charset=UTF-8\\\",\\\"responseCode\\\":\\\"200\\\"}\"}";
    assertEquals(jsonOk, jsonToTest);

    SampleResult[] subresults = result.getSubResults();
    String jsonToTest2 = this.buildSampleSubResultJson(subresults, 0, payload2);
    String jsonOk2 = "{\"Test\":\"{\\\"requestHeaders\\\":\\\"GET  http:\\\\\\/\\\\\\/www.mock.com\\\\\\/path\\\\\\/videos\\\\\\/DianaLaufenberg_2010X\\\\\\/video\\\\\\/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d\\\\n\\\\n\\\\n\\\\n\\\\n\\\",\\\"responseHeaders\\\":\\\"headerKey1 : header11 header12 header13\\\\nheaderKey2 : header21 header22 header23\\\\nheaderKey3 : header31\\\\n\\\",\\\"success\\\":true,\\\"bytes\\\":\\\"#EXTM3U\\\\n#EXT-X-TARGETDURATION:10\\\\n#EXT-X-VERSION:4\\\\n#EXT-X-MEDIA-SEQUENCE:0\\\\n#EXT-X-PLAYLIST-TYPE:VOD\\\\n#EXTINF:5.0000,\\\\n#EXT-X-BYTERANGE:440672@0\\\\nhttps:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k.ts\\\\n#EXTINF:5.0000,\\\\n#EXT-X-BYTERANGE:94000@440672\\\\nhttps:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k.ts\\\\n#EXTINF:1.9583,\\\\n#EXT-X-BYTERANGE:22748@534672\\\\nhttps:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k.ts\\\\n#EXT-X-DISCONTINUITY\\\",\\\"dataEncoding\\\":\\\"UTF-8\\\",\\\"label\\\":\\\"600k.m3u8?preroll=Thousands&uniqueId=4df94b1d\\\",\\\"responseMessage\\\":\\\"OK\\\",\\\"contentType\\\":\\\"application\\\\\\/json;charset=UTF-8\\\",\\\"responseCode\\\":\\\"200\\\"}\"}";
    assertEquals(jsonOk2, jsonToTest2);

    SampleResult[] subsubresults = subresults[0].getSubResults();

    String jsonToTest3 = this.buildSampleSubSubResultJson(subsubresults, 0);
    String jsonOk3 = "{\"Test\":\"{\\\"requestHeaders\\\":\\\"GET  https:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k_1.ts\\\\n\\\\n\\\\n\\\\n\\\\n\\\",\\\"responseHeaders\\\":\\\"URL: https:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k_1.ts\\\\nheaderKey1 : header11 header12 header13\\\\nheaderKey2 : header21 header22 header23\\\\nheaderKey3 : header31\\\\n\\\",\\\"success\\\":true,\\\"dataEncoding\\\":\\\"UTF-8\\\",\\\"label\\\":\\\"Thousands-320k_1.ts\\\",\\\"responseMessage\\\":\\\"OK\\\",\\\"contentType\\\":\\\"application\\\\\\/json;charset=UTF-8\\\",\\\"responseCode\\\":\\\"200\\\"}\"}";
    assertEquals(jsonOk3, jsonToTest3);

    String jsonToTest4 = this.buildSampleSubSubResultJson(subsubresults, 1);
    String jsonOk4 = "{\"Test\":\"{\\\"requestHeaders\\\":\\\"GET  https:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k_2.ts\\\\n\\\\n\\\\n\\\\n\\\\n\\\",\\\"responseHeaders\\\":\\\"URL: https:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k_2.ts\\\\nheaderKey1 : header11 header12 header13\\\\nheaderKey2 : header21 header22 header23\\\\nheaderKey3 : header31\\\\n\\\",\\\"success\\\":true,\\\"dataEncoding\\\":\\\"UTF-8\\\",\\\"label\\\":\\\"Thousands-320k_2.ts\\\",\\\"responseMessage\\\":\\\"OK\\\",\\\"contentType\\\":\\\"application\\\\\\/json;charset=UTF-8\\\",\\\"responseCode\\\":\\\"200\\\"}\"}";
    assertEquals(jsonOk4, jsonToTest4);

    String jsonToTest5 = this.buildSampleSubSubResultJson(subsubresults, 2);
    String jsonOk5 = "{\"Test\":\"{\\\"requestHeaders\\\":\\\"GET  https:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k_3.ts\\\\n\\\\n\\\\n\\\\n\\\\n\\\",\\\"responseHeaders\\\":\\\"URL: https:\\\\\\/\\\\\\/pb.tedcdn.com\\\\\\/bumpers\\\\\\/hls\\\\\\/video\\\\\\/in\\\\\\/Thousands-320k_3.ts\\\\nheaderKey1 : header11 header12 header13\\\\nheaderKey2 : header21 header22 header23\\\\nheaderKey3 : header31\\\\n\\\",\\\"success\\\":true,\\\"dataEncoding\\\":\\\"UTF-8\\\",\\\"label\\\":\\\"Thousands-320k_3.ts\\\",\\\"responseMessage\\\":\\\"OK\\\",\\\"contentType\\\":\\\"application\\\\\\/json;charset=UTF-8\\\",\\\"responseCode\\\":\\\"200\\\"}\"}";
    assertEquals(jsonOk5, jsonToTest5);
  }

  private List<String> buildHeader(String val1, String val2, String val3) {
    List<String> header = new ArrayList<>();

    header.add(val1);
    if (val2 != "") {
      header.add(val2);
    }
    if (val3 != "") {
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

  private String buildSampleResultJson(SampleResult result) throws Exception {
    JSONObject json = new JSONObject();
    JSONObject parent = new JSONObject();
    json.put("requestHeaders", result.getRequestHeaders());
    json.put("responseMessage", result.getResponseMessage());
    json.put("label", result.getSampleLabel());
    json.put("responseHeaders", result.getResponseHeaders());
    json.put("responseCode", result.getResponseCode());
    json.put("contentType", result.getContentType());
    json.put("dataEncoding", result.getDataEncodingNoDefault());
    parent.put("Test", json.toString());
    return parent.toString();
  }

  private String buildSampleSubResultJson(SampleResult[] subresults, int pos, String payload)
      throws Exception {
    JSONObject json = new JSONObject();
    JSONObject parent = new JSONObject();
    json.put("requestHeaders", subresults[pos].getRequestHeaders());
    json.put("success", subresults[pos].isSuccessful());
    json.put("responseMessage", subresults[pos].getResponseMessage());
    json.put("label", subresults[pos].getSampleLabel());
    json.put("responseHeaders", subresults[pos].getResponseHeaders());
    json.put("bytes", new String(payload.getBytes()));
    json.put("responseCode", subresults[pos].getResponseCode());
    json.put("contentType", subresults[pos].getContentType());
    json.put("dataEncoding", subresults[pos].getDataEncodingNoDefault());
    parent.put("Test", json.toString());
    return parent.toString();
  }

  private String buildSampleSubSubResultJson(SampleResult[] subsubresults, int pos)
      throws Exception {
    JSONObject json = new JSONObject();
    JSONObject parent = new JSONObject();
    json.put("requestHeaders", subsubresults[pos].getRequestHeaders());
    json.put("success", subsubresults[pos].isSuccessful());
    json.put("responseMessage", subsubresults[pos].getResponseMessage());
    json.put("label", subsubresults[pos].getSampleLabel());
    json.put("responseHeaders", subsubresults[pos].getResponseHeaders());
    json.put("responseCode", subsubresults[pos].getResponseCode());
    json.put("contentType", subsubresults[pos].getContentType());
    json.put("dataEncoding", subsubresults[pos].getDataEncodingNoDefault());
    parent.put("Test", json.toString());
    return parent.toString();
  }

}

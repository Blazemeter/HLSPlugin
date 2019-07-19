package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Java6Assertions.assertThat;
<<<<<<< HEAD
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
=======
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
>>>>>>> 3ec46b1b426f7e5f26029749593ab9160e5cfbe4
import static org.mockito.Mockito.when;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
<<<<<<< HEAD
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
=======
import java.net.MalformedURLException;
import java.net.URI;
>>>>>>> 3ec46b1b426f7e5f26029749593ab9160e5cfbe4
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
<<<<<<< HEAD
=======
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
>>>>>>> 3ec46b1b426f7e5f26029749593ab9160e5cfbe4
import org.apache.jmeter.samplers.SampleResult;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
<<<<<<< HEAD
import org.mockito.Mockito;
=======
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
>>>>>>> 3ec46b1b426f7e5f26029749593ab9160e5cfbe4

public class HlsSamplerTest {

<<<<<<< HEAD
  private static final String HEADERS = "headerKey1 : header11 header12 header13\n"
      + "headerKey2 : header21 header22 header23\n"
      + "headerKey3 : header31\n";
  private static final String PLAYLIST_PATH = "/videos/DianaLaufenberg_2010X/video/600k.m3u8?preroll=Thousands&uniqueId=4df94b1d";
  private static final String VIDEO_URL = "http://www.mock.com/path";

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
    setupUrlParser(VIDEO_URL + PLAYLIST_PATH, headers, payload2);
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
=======
  private static final URI MASTER_URI = URI.create("http://test/master.m3u8");
  private static final String PLAYLIST_CONTENT_TYPE = "application/x-mpegURL";
  private static final String SEGMENT_CONTENT_TYPE = "video/MP2T";
  private static final String SAMPLER_NAME = "HLS";
  private static final String MASTER_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - master playlist";
  private static final String MEDIA_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - media playlist";
  private static final int SEGMENT_DURATION_SECONDS = 5;
  private static final String SIMPLE_MEDIA_PLAYLIST_NAME = "simpleMediaPlaylist.m3u8";
  private static final String VOD_MEDIA_PLAYLIST_NAME = "vodMediaPlaylist.m3u8";
  private static final String EVENT_MEDIA_PLAYLIST_PART_1_NAME = "eventMediaPlaylist-Part1.m3u8";
  private static final String EVENT_MEDIA_PLAYLIST_PART_2_NAME = "eventMediaPlaylist-Part2.m3u8";
  private static final URI MEDIA_PLAYLIST_URI = URI.create("http://example.com/audio-only.m3u8");
  private static final String MASTER_PLAYLIST_NAME = "masterPlaylist.m3u8";

  private HlsSampler sampler;
  private SegmentResultFallbackUriSamplerMock uriSampler = new SegmentResultFallbackUriSamplerMock();
  @Mock
  private Consumer<SampleResult> sampleResultNotifier;
  @Captor
  private ArgumentCaptor<SampleResult> sampleResultCaptor;

  @Before
  public void setUp() {
    sampler = new HlsSampler(uriSampler, sampleResultNotifier);
    sampler.setName(SAMPLER_NAME);
    sampler.setMasterUrl(MASTER_URI.toString());
  }

  private class SegmentResultFallbackUriSamplerMock implements Function<URI, SampleResult> {

    @SuppressWarnings("unchecked")
    private Function<URI, SampleResult> mock = (Function<URI, SampleResult>) Mockito
        .mock(Function.class);

    @Override
    public SampleResult apply(URI uri) {
      SampleResult result = mock.apply(uri);
      return result != null ? result : buildSegmentResult(uri);
    }

    private SampleResult buildSegmentResult(URI uri) {
      String uriStr = uri.toString();
      String segmentExtension = ".ts";
      int sequenceNumber = uriStr.endsWith(segmentExtension) ? Integer
          .parseInt(uriStr.substring(uriStr.length() - segmentExtension.length() - 1,
              uriStr.length() - segmentExtension.length())) - 1 : 0;
      return buildSegmentSampleResult(sequenceNumber);
    }

  }

  private SampleResult buildSegmentSampleResult(int sequenceNumber) {
    return buildSampleResult(buildSegmentSampleName(sequenceNumber),
        buildSegmentUri(sequenceNumber), SEGMENT_CONTENT_TYPE, "");
  }

  private String buildSegmentSampleName(int sequenceNumber) {
    return SAMPLER_NAME + " - segment " + sequenceNumber;
  }

  private URI buildSegmentUri(int sequenceNumber) {
    return URI.create("http://media.example.com/00" + (sequenceNumber + 1) + ".ts");
  }

  private SampleResult buildSampleResult(String name, URI uri, String contentType,
      String responseBody) {
    HTTPSampleResult ret = buildBaseSampleResult(name, uri);
    ret.setSuccessful(true);
    ret.setResponseCodeOK();
    ret.setResponseMessageOK();
    ret.setResponseHeaders("Content-Type: " + contentType);
    ret.setResponseData(responseBody, Charsets.UTF_8.name());
    return ret;
  }

  private HTTPSampleResult buildBaseSampleResult(String name, URI uri) {
    try {
      HTTPSampleResult ret = new HTTPSampleResult();
      ret.setSampleLabel(name);
      ret.setHTTPMethod("GET");
      ret.setURL(uri.toURL());
      ret.setRequestHeaders("TestHeader: TestVal");
      return ret;
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Test
  public void shouldDownloadSegmentWhenUriIsFromMediaPlaylist() throws Exception {
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(SEGMENT_DURATION_SECONDS);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0)));
  }

  private static String getPlaylist(String playlistFileName) throws IOException {
    return Resources
        .toString(Resources.getResource(HlsSamplerTest.class, playlistFileName),
            Charsets.UTF_8);
  }
>>>>>>> 3ec46b1b426f7e5f26029749593ab9160e5cfbe4

  private void setupUriSamplerPlaylist(URI uri, String... playlists) {
    SampleResult[] rest = new SampleResult[playlists.length - 1];
    for (int i = 1; i < playlists.length; i++) {
      rest[i - 1] = buildPlaylistSampleResult(SAMPLER_NAME, uri, playlists[i]);
    }
    when(uriSampler.mock.apply(uri))
        .thenReturn(buildPlaylistSampleResult(SAMPLER_NAME, uri, playlists[0]), rest);
  }

<<<<<<< HEAD
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

=======
  private SampleResult buildPlaylistSampleResult(String name, URI uri, String body) {
    return buildSampleResult(name, uri, PLAYLIST_CONTENT_TYPE, body);
  }

  private void setPlaySeconds(int playSeconds) {
    sampler.setPlayVideoDuration(true);
    sampler.setPlaySeconds(String.valueOf(playSeconds));
  }

  private void verifyNotifiedSampleResults(List<SampleResult> results) {
    verify(sampleResultNotifier, atLeastOnce()).accept(sampleResultCaptor.capture());
    //we convert to json to easily compare and trace issues
    assertThat(toJson(sampleResultCaptor.getAllValues())).isEqualTo(toJson(results));
  }

  private List<String> toJson(List<SampleResult> results) {
    return results.stream()
        .map(this::sampleResultToJson)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private String sampleResultToJson(SampleResult sample) {
    JSONObject json = new JSONObject();
    json.put("label", sample.getSampleLabel());
    json.put("requestHeaders", sample.getRequestHeaders());
    json.put("sampleData", sample.getSamplerData());
    json.put("responseCode", sample.getResponseCode());
    json.put("responseMessage", sample.getResponseMessage());
    json.put("responseHeaders", sample.getResponseHeaders());
    json.put("responseData", sample.getResponseDataAsString());
    return json.toString();
  }

  @Test
  public void shouldDownloadSegmentWhenUriIsFromMasterPlaylist() throws Exception {
    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_NAME);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setPlaySeconds(SEGMENT_DURATION_SECONDS);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildSegmentSampleResult(0)));
  }

  @Test
  public void shouldDownloadAllSegmentsWhenSampleWholeVideoAndVOD() throws Exception {
    String mediaPlaylist = getPlaylist(VOD_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0),
        buildSegmentSampleResult(1),
        buildSegmentSampleResult(2)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilListEndWhenEventStreamAndPlayWholeVideo()
      throws Exception {
    String mediaPlaylist1 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    String mediaPlaylist2 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_2_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildSegmentSampleResult(0),
        buildSegmentSampleResult(1),
        buildSegmentSampleResult(2),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildSegmentSampleResult(3),
        buildSegmentSampleResult(4)));
  }

  @Test
  public void shouldResumeDownloadWhenMultipleSamplesAndResumeDownload() throws Exception {
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(SEGMENT_DURATION_SECONDS);
    sampler.setResumeVideoStatus(true);
    sampler.sample();
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0),
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(1)));
  }

  @Test
  public void shouldKeepDownloadingSameSegmentsWhenMultipleSamplesAndNoResumeDownload()
      throws Exception {
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(SEGMENT_DURATION_SECONDS);
    sampler.sample();
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0),
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0)));
  }

  @Test
  public void shouldDownloadSegmentsUntilPlayPeriodWhenVodWithPlayPeriod()
      throws Exception {
    String mediaPlaylist = getPlaylist(VOD_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(SEGMENT_DURATION_SECONDS * 2);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0),
        buildSegmentSampleResult(1)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilPlayPeriodWhenEventStreamWithPlayPeriod()
      throws Exception {
    String mediaPlaylist1 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    String mediaPlaylist2 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_2_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    setPlaySeconds(SEGMENT_DURATION_SECONDS * 4);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildSegmentSampleResult(0),
        buildSegmentSampleResult(1),
        buildSegmentSampleResult(2),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildSegmentSampleResult(3)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilPlayPeriodWhenLiveStreamWithPlayPeriod()
      throws Exception {
    String mediaPlaylist1 = getPlaylist("liveMediaPlaylist-Part1.m3u8");
    String mediaPlaylist2 = getPlaylist("liveMediaPlaylist-Part2.m3u8");
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    setPlaySeconds(SEGMENT_DURATION_SECONDS * 4);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildSegmentSampleResult(0),
        buildSegmentSampleResult(1),
        buildSegmentSampleResult(2),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildSegmentSampleResult(3)));
  }

  @Test
  public void shouldNotDownloadMediaPlaylistOrSegmentsWhenMasterPlaylistRequestFails() {
    setupUriSamplerErrorResult(MASTER_URI);
    sampler.sample();
    verifyNotifiedSampleResults(
        Collections.singletonList(buildErrorSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI)));
  }

  private void setupUriSamplerErrorResult(URI uri) {
    when(uriSampler.mock.apply(uri))
        .thenReturn(buildErrorSampleResult(SAMPLER_NAME, uri));
  }

  private SampleResult buildErrorSampleResult(String name, URI uri) {
    HTTPSampleResult ret = buildBaseSampleResult(name, uri);
    ret.setSuccessful(false);
    ret.setResponseCode("404");
    ret.setResponseData("Not Found", Charsets.UTF_8.name());
    return ret;
  }

  @Test
  public void shouldNotDownloadSegmentsWhenMediaPlaylistRequestFails() throws Exception {
    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, masterPlaylist);
    setupUriSamplerErrorResult(MEDIA_PLAYLIST_URI);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, masterPlaylist),
        buildErrorSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI)));
  }

  @Test
  public void shouldStopDownloadingSegmentsWhenMediaPlaylistReloadFails() throws Exception {
    String mediaPlaylist1 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    when(uriSampler.mock.apply(MASTER_URI))
        .thenReturn(buildPlaylistSampleResult(SAMPLER_NAME, MASTER_URI, mediaPlaylist1),
            buildErrorSampleResult(SAMPLER_NAME, MASTER_URI));
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildSegmentSampleResult(0),
        buildSegmentSampleResult(1),
        buildSegmentSampleResult(2),
        buildErrorSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsWhenOneSegmentRequestFails() throws Exception {
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    URI failingSegmentUri = buildSegmentUri(1);
    setupUriSamplerErrorResult(failingSegmentUri);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0),
        buildErrorSampleResult(buildSegmentSampleName(1), failingSegmentUri),
        buildSegmentSampleResult(2)));
  }

>>>>>>> 3ec46b1b426f7e5f26029749593ab9160e5cfbe4
}

package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HlsSamplerTest {

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

  private void setupUriSamplerPlaylist(URI uri, String... playlists) {
    SampleResult[] rest = new SampleResult[playlists.length - 1];
    for (int i = 1; i < playlists.length; i++) {
      rest[i - 1] = buildPlaylistSampleResult(SAMPLER_NAME, uri, playlists[i]);
    }
    when(uriSampler.mock.apply(uri))
        .thenReturn(buildPlaylistSampleResult(SAMPLER_NAME, uri, playlists[0]), rest);
  }

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

}

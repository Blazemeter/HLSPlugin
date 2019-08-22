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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
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
  private static final String AUDIO_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - audio playlist";
  private static final String SUBTITLE_PLAYLIST_SAMPLE_NAME =
      SAMPLER_NAME + " - subtitles playlist";
  private static final String SUBTITLE_SAMPLE_NAME = SAMPLER_NAME + " - subtitles";
  private static final String SEGMENT_SAMPLE_NAME = SAMPLER_NAME + " - media segment";
  private static final int SEGMENT_DURATION_SECONDS = 5;
  private static final String SIMPLE_MEDIA_PLAYLIST_NAME = "simpleMediaPlaylist.m3u8";
  private static final String VOD_MEDIA_PLAYLIST_NAME = "vodMediaPlaylist.m3u8";
  private static final String EVENT_MEDIA_PLAYLIST_PART_1_NAME = "eventMediaPlaylist-Part1.m3u8";
  private static final String EVENT_MEDIA_PLAYLIST_PART_2_NAME = "eventMediaPlaylist-Part2.m3u8";
  private static final URI MEDIA_PLAYLIST_URI = URI.create("http://example.com/audio-only.m3u8");
  private static final String MASTER_PLAYLIST_NAME = "masterPlaylist.m3u8";

  private static final String MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE = "masterPlaylistWithRenditions.m3u8";
  private static final String SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE = "defaultEnglishSubtitlePlaylist.m3u8";
  private static final String SUBTITLES_PLAYLIST_ENGLISH_PARSING_EXCEPTION_RESOURCE = "defaultEnglishSubtitlePlaylistParsingException.m3u8";
  private static final String SUBTITLE_PLAYLIST_FRENCH_RESOURCE = "frenchSubtitlePlaylist.m3u8";
  private static final String AUDIO_PLAYLIST_RESOURCE = "audioPlaylist.m3u8";
  private static final String AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE = "defaultEnglishAudioPlaylist.m3u8";

  private static final URI MASTER_PLAYLIST_WITH_RENDITIONS_URI = URI
      .create("http://example.com/masterPlaylistWithRenditions.m3u8");
  private static final URI SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI = URI
      .create("http://example.com/subtitles_en_default.m3u8");
  private static final URI FRENCH_SUBTITLES_PLAYLIST_URI = URI
      .create("http://example.com/subtitles_fr_no_default.m3u8");
  private static final URI AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI = URI
      .create("http://example.com/audio/audio_stereo_en_default.m3u8");

  private static final long TARGET_TIME_MILLIS = 10000;
  private static final long TIME_THRESHOLD_MILLIS = 5000;
  private static final long TEST_TIMEOUT = 10000;


  private HlsSampler sampler;
  private SegmentResultFallbackUriSamplerMock uriSampler = new SegmentResultFallbackUriSamplerMock();
  @Mock
  private Consumer<SampleResult> sampleResultNotifier;
  @Captor
  private ArgumentCaptor<SampleResult> sampleResultCaptor;

  private TimeMachine timeMachine = new TimeMachine() {

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

  @Before
  public void setUp() {
    buildSampler(uriSampler);
  }

  private void buildSampler(Function<URI, HTTPSampleResult> uriSampler) {
    sampler = new HlsSampler(uriSampler, sampleResultNotifier, timeMachine);
    sampler.setName(SAMPLER_NAME);
    sampler.setMasterUrl(MASTER_URI.toString());
  }

  private class SegmentResultFallbackUriSamplerMock implements Function<URI, HTTPSampleResult> {

    @SuppressWarnings("unchecked")
    private Function<URI, HTTPSampleResult> mock = (Function<URI, HTTPSampleResult>) Mockito
        .mock(Function.class);

    @Override
    public HTTPSampleResult apply(URI uri) {
      HTTPSampleResult result = mock.apply(uri);
      return result != null ? result : buildSegmentResult(uri);
    }

    private HTTPSampleResult buildSegmentResult(URI uri) {
      String uriStr = uri.toString();
      String segmentExtension = ".ts";
      int sequenceNumber = uriStr.endsWith(segmentExtension) ? Integer
          .parseInt(uriStr.substring(uriStr.length() - segmentExtension.length() - 1,
              uriStr.length() - segmentExtension.length())) - 1 : 0;
      return buildSegmentSampleResult(sequenceNumber);
    }
  }

  private HTTPSampleResult buildSegmentSampleResult(int sequenceNumber) {
    return buildSampleResult(SEGMENT_SAMPLE_NAME, buildSegmentUri(sequenceNumber),
        SEGMENT_CONTENT_TYPE, "");
  }

  private URI buildSegmentUri(int sequenceNumber) {
    return URI.create("http://media.example.com/00" + (sequenceNumber + 1) + ".ts");
  }

  private HTTPSampleResult buildSampleResult(String name, URI uri, String contentType,
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0)));
  }

  private static String getPlaylist(String playlistFileName) throws IOException {
    return Resources
        .toString(Resources.getResource(HlsSamplerTest.class, playlistFileName),
            Charsets.UTF_8);
  }

  private void setupUriSamplerPlaylist(URI uri, String... playlists) {
    HTTPSampleResult[] rest = new HTTPSampleResult[playlists.length - 1];
    for (int i = 1; i < playlists.length; i++) {
      rest[i - 1] = buildPlaylistSampleResult(SAMPLER_NAME, uri, playlists[i]);
    }
    //Todo: REMOVE Me
    HTTPSampleResult sampleResult = buildPlaylistSampleResult(SAMPLER_NAME, uri, playlists[0]);
    when(uriSampler.mock.apply(uri))
        .thenReturn(sampleResult, rest);
  }

  private HTTPSampleResult buildPlaylistSampleResult(String name, URI uri, String body) {
    return buildSampleResult(name, uri, PLAYLIST_CONTENT_TYPE, body);
  }

  private void setPlaySeconds(int playSeconds) {
    sampler.setPlayVideoDuration(true);
    sampler.setPlaySeconds(String.valueOf(playSeconds));
  }

  private void verifyNotifiedSampleResults(List<SampleResult> results) {
    verify(sampleResultNotifier, atLeastOnce()).accept(sampleResultCaptor.capture());
    //we convert to json to easily compare and trace issues

    List<String> result = toJson(sampleResultCaptor.getAllValues());
    List<String> expected = toJson(results);

    assertThat(result).isEqualTo(expected);
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
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

  private HTTPSampleResult buildErrorSampleResult(String name, URI uri) {
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
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
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0),
        buildErrorSampleResult(SEGMENT_SAMPLE_NAME, failingSegmentUri),
        buildSegmentSampleResult(2)));
  }

  @Test
  public void shouldWaitTargetTimeForPlaylistReloadWhenSegmentsDownloadFasterThanTargetTime()
      throws Exception {
    TimedUriSampler timedUriSampler = new TimedUriSampler(uriSampler, timeMachine, 0);
    buildSampler(timedUriSampler);
    setupUriSamplerPlaylist(MASTER_URI, getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME),
        getPlaylist(EVENT_MEDIA_PLAYLIST_PART_2_NAME));
    sampler.sample();
    List<Instant> timestamps = timedUriSampler.getUriSamplesTimeStamps(MASTER_URI);
    assertThat(timestamps.get(0).until(timestamps.get(1), ChronoUnit.MILLIS))
        .isBetween(TARGET_TIME_MILLIS, TARGET_TIME_MILLIS + TIME_THRESHOLD_MILLIS);
  }

  private static class TimedUriSampler implements Function<URI, HTTPSampleResult> {

    private final Function<URI, HTTPSampleResult> baseUriSampler;
    private final TimeMachine timeMachine;
    private final long downloadTimeMillis;
    private final Map<URI, List<Instant>> samplesTimestamps = new HashMap<>();

    private TimedUriSampler(Function<URI, HTTPSampleResult> baseUriSampler, TimeMachine timeMachine,
        long downloadTimeMillis) {
      this.baseUriSampler = baseUriSampler;
      this.timeMachine = timeMachine;
      this.downloadTimeMillis = downloadTimeMillis;
    }

    @Override
    public HTTPSampleResult apply(URI uri) {
      try {
        samplesTimestamps.computeIfAbsent(uri, k -> new ArrayList<>()).add(timeMachine.now());
        timeMachine.awaitMillis(downloadTimeMillis);
        return baseUriSampler.apply(uri);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    private List<Instant> getUriSamplesTimeStamps(URI uri) {
      return samplesTimestamps.get(uri);
    }
  }

  @Test
  public void shouldNotWaitWhenSegmentsDownloadSlowerThanTargetTime() throws Exception {
    int segmentsCount = 3;
    long requestDownloadTime = TARGET_TIME_MILLIS / (segmentsCount - 1);
    TimedUriSampler timedUriSampler = new TimedUriSampler(uriSampler, timeMachine,
        requestDownloadTime);
    buildSampler(timedUriSampler);
    setupUriSamplerPlaylist(MASTER_URI, getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME),
        getPlaylist(EVENT_MEDIA_PLAYLIST_PART_2_NAME));
    sampler.sample();
    List<Instant> timestamps = timedUriSampler.getUriSamplesTimeStamps(MASTER_URI);
    assertThat(timestamps.get(0).until(timestamps.get(1), ChronoUnit.MILLIS))
        .isBetween(requestDownloadTime * (segmentsCount + 1),
            requestDownloadTime * (segmentsCount + 1) + TIME_THRESHOLD_MILLIS);
  }

  @Test
  public void shouldWaitHalfTargetTimeForPlaylistReloadWhenReloadGetsSamePlaylistBody()
      throws Exception {
    TimedUriSampler timedUriSampler = new TimedUriSampler(uriSampler, timeMachine, 0);
    buildSampler(timedUriSampler);
    String mediaPlaylistPart1 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylistPart1, mediaPlaylistPart1,
        getPlaylist(EVENT_MEDIA_PLAYLIST_PART_2_NAME));
    sampler.sample();
    List<Instant> timestamps = timedUriSampler.getUriSamplesTimeStamps(MASTER_URI);
    assertThat(timestamps.get(1).until(timestamps.get(2), ChronoUnit.MILLIS))
        .isBetween(TARGET_TIME_MILLIS / 2, TARGET_TIME_MILLIS / 2 + TIME_THRESHOLD_MILLIS);
  }


  @Test(timeout = TEST_TIMEOUT)
  public void shouldOnlyDownloadMediaPlaylistWhenInterruptMediaPlaylistDownload() throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 1);
    buildSampler(uriSampler);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    runWithAsyncSample(() -> {
      uriSampler.interruptSamplerWhileDownloading(sampler);
      return null;
    });
    verifyNotifiedSampleResults(Collections.singletonList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist)));
  }

  private void runWithAsyncSample(Callable<Void> run) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Object> sampleResult = executor.submit(() -> sampler.sample());
      run.call();
      sampleResult.get();
    } finally {
      executor.shutdown();
    }
  }

  private class DownloadBlockingUriSampler implements Function<URI, HTTPSampleResult> {

    private final Function<URI, HTTPSampleResult> uriSampler;
    private final int blockingDownloadsCount;
    private final Semaphore startDownloadLock;
    private final Semaphore endDownloadLock;
    private int currentDownload;

    private DownloadBlockingUriSampler(Function<URI, HTTPSampleResult> uriSampler,
        int blockingDownloadsCount) {
      this.uriSampler = uriSampler;
      this.blockingDownloadsCount = blockingDownloadsCount;
      this.startDownloadLock = new Semaphore(0);
      this.endDownloadLock = new Semaphore(0);
    }

    @Override
    public HTTPSampleResult apply(URI uri) {
      startDownloadLock.release();
      if (currentDownload < blockingDownloadsCount) {
        currentDownload++;
        try {
          endDownloadLock.acquire();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      return uriSampler.apply(uri);
    }

    private void syncDownload() throws InterruptedException {
      awaitStartDownload();
      endDownload();
    }

    private void awaitStartDownload() throws InterruptedException {
      startDownloadLock.acquire();
    }

    private void endDownload() {
      endDownloadLock.release();
    }

    private void interruptSamplerWhileDownloading(HlsSampler sampler) throws InterruptedException {
      awaitStartDownload();
      sampler.interrupt();
      endDownload();
    }
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldDownloadPlaylistAndFirstSegmentWhenInterruptSegmentDownload() throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 2);
    buildSampler(uriSampler);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);

    runWithAsyncSample(() -> {
      uriSampler.syncDownload();
      uriSampler.interruptSamplerWhileDownloading(sampler);
      return null;
    });

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildSegmentSampleResult(0)));
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldDownloadOnlyMasterListWhenInterruptDuringMasterListDownload() throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 1);
    buildSampler(uriSampler);
    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, masterPlaylist);

    runWithAsyncSample(() -> {
      uriSampler.interruptSamplerWhileDownloading(sampler);
      return null;
    });

    verifyNotifiedSampleResults(Collections.singletonList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, masterPlaylist)));
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldNotReloadPlayListWhenInterruptDuringLastSegmentDownload() throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 4);
    buildSampler(uriSampler);
    String mediaPlaylistPart1 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylistPart1);

    runWithAsyncSample(() -> {
      for (int i = 0; i < 3; i++) {
        uriSampler.syncDownload();
      }
      uriSampler.interruptSamplerWhileDownloading(sampler);
      return null;
    });

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1),
        buildSegmentSampleResult(0),
        buildSegmentSampleResult(1),
        buildSegmentSampleResult(2)));
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldStopReloadingPlaylistWhenInterruptPlaylistReloadGettingSamePlaylist()
      throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 5);
    buildSampler(uriSampler);
    String mediaPlaylistPart1 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylistPart1, mediaPlaylistPart1);

    runWithAsyncSample(() -> {
      for (int i = 0; i < 4; i++) {
        uriSampler.syncDownload();
      }
      uriSampler.interruptSamplerWhileDownloading(sampler);
      return null;
    });

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1),
        buildSegmentSampleResult(0),
        buildSegmentSampleResult(1),
        buildSegmentSampleResult(2),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1)));
  }

  @Test
  public void shouldDownloadDefaultSubtitleWhenSelectorNotFound() throws IOException {
    setUpSamplerForRenditions("sp", "", 3);

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_RESOURCE);
    String defaultSubtitlePlaylist = getPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, defaultSubtitlePlaylist);

    sampler.sample();

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_SAMPLE_NAME, SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            defaultSubtitlePlaylist),
        buildSegmentSampleResultByType(0, "media", true),
        buildSegmentSampleResultByType(-1, "audio", true),
        buildSegmentSampleResultByType(0, "audio", true),
        buildSegmentSampleResultByType(1, "audio", true),
        buildSegmentSampleResultByType(0, "subtitles", true)));
  }

  private HTTPSampleResult buildSegmentSampleResultByType(int sequenceNumber, String type,
      boolean segment) {
    return buildSampleResult("HLS - " + type + (segment ? " segment" : ""),
        buildSegmentUri(sequenceNumber),
        SEGMENT_CONTENT_TYPE, "");
  }

  private void setUpSamplerForRenditions(String subtitleLanguage, String audioLanguage,
      int playSeconds) {
    sampler.setMasterUrl(MASTER_PLAYLIST_WITH_RENDITIONS_URI.toString());
    sampler.setSubtitleLanguage(subtitleLanguage);
    sampler.setAudioLanguage(audioLanguage);
    setPlaySeconds(playSeconds);
  }

  @Test
  public void shouldDownloadSubtitlesWhenSelector() throws Exception {
    setUpSamplerForRenditions("fr", "", 3);

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_RESOURCE);
    String subtitlePlaylist = getPlaylist(SUBTITLE_PLAYLIST_FRENCH_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(FRENCH_SUBTITLES_PLAYLIST_URI, subtitlePlaylist);

    sampler.sample();

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_SAMPLE_NAME, FRENCH_SUBTITLES_PLAYLIST_URI,
            subtitlePlaylist),
        buildSegmentSampleResultByType(0, "media", true),
        buildSegmentSampleResultByType(-1, "audio", true),
        buildSegmentSampleResultByType(0, "audio", true),
        buildSegmentSampleResultByType(1, "audio", true),
        buildSegmentSampleResultByType(0, "subtitles", true)));
  }

  @Test
  public void shouldDownloadDefaultAudioWhenSelectorNotFound() throws IOException {
    setUpSamplerForRenditions("", "sp", 3);

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlePlaylist = getPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, subtitlePlaylist);

    sampler.sample();

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_SAMPLE_NAME, SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            subtitlePlaylist),
        buildSegmentSampleResultByType(0, "media", true),
        buildSegmentSampleResultByType(0, "audio", true),
        buildSegmentSampleResultByType(0, "subtitles", true)));
  }

  @Test
  public void shouldKeepDownloadOtherSegmentsAndNotifyErrorWhenParsingPlaylistException()
      throws IOException {
    setUpSamplerForRenditions("", "sp", 3);

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlePlaylistWithParsingException = getPlaylist(
        SUBTITLES_PLAYLIST_ENGLISH_PARSING_EXCEPTION_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
        subtitlePlaylistWithParsingException);

    sampler.sample();

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_SAMPLE_NAME, SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            subtitlePlaylistWithParsingException),
        buildSegmentSampleResultByType(0, "media", true),
        buildSegmentSampleResultByType(0, "audio", true),
        buildSegmentSampleResultByType(0, "subtitles", true)));
  }

  public void shouldKeepDownloadOtherSegmentsAndNotifyErrorWhenDownloadingPlaylistException() {
  }

  public void shouldDownloadSubtitleWholeFileWhenSubtitleWithoutPlaylist() {
    setUpSamplerForRenditions("", "sp", 3);

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlePlaylist = getPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, subtitlePlaylist);

    sampler.sample();

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_SAMPLE_NAME, SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            subtitlePlaylist),
        buildSegmentSampleResultByType(0, "media", true),
        buildSegmentSampleResultByType(0, "audio", true),
        buildSegmentSampleResultByType(0, "subtitles", true)));
  }

}

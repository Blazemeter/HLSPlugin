package com.blazemeter.jmeter.videostreaming.hls;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.BandwidthSelector.CustomBandwidthSelector;
import com.blazemeter.jmeter.videostreaming.VideoStreamingSamplerTest;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import com.blazemeter.jmeter.videostreaming.core.exception.SamplerInterruptedException;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.junit.Test;


public class HlsSamplerTest extends VideoStreamingSamplerTest {

  private static final String SIMPLE_MEDIA_PLAYLIST_NAME = "simpleMediaPlaylist.m3u8";
  private static final URI MASTER_URI = URI.create(BASE_URI + "/master.m3u8");
  protected static final String MEDIA_TYPE_NAME = "media";
  protected static final double MEDIA_SEGMENT_DURATION = 5.0;
  private static final String MEDIA_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - media playlist";
  private static final String MASTER_PLAYLIST_NAME = "masterPlaylist.m3u8";
  private static final URI MEDIA_PLAYLIST_URI = URI.create(BASE_URI + "/media/audio-only.m3u8");
  private static final String MASTER_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - master playlist";
  private static final String VOD_MEDIA_PLAYLIST_NAME = "vodMediaPlaylist.m3u8";
  private static final String EVENT_MEDIA_PLAYLIST_PART_1_NAME = "eventMediaPlaylist-Part1.m3u8";
  private static final String EVENT_MEDIA_PLAYLIST_PART_2_NAME = "eventMediaPlaylist-Part2.m3u8";
  private static final String MEDIA_SEGMENT_SAMPLE_TYPE = "media segment";
  private static final String SEGMENT_SAMPLE_NAME =
      SAMPLER_NAME + " - " + MEDIA_SEGMENT_SAMPLE_TYPE;
  private static final long TARGET_TIME_MILLIS = 10000;
  private static final long TIME_THRESHOLD_MILLIS = 5000;
  private static final long TEST_TIMEOUT = 10000;
  private static final String MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE = "masterPlaylistWithRenditions.m3u8";
  private static final String AUDIO_PLAYLIST_RESOURCE = "audioPlaylist.m3u8";
  private static final String SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE = "defaultEnglishSubtitlePlaylist.m3u8";
  private static final URI MASTER_PLAYLIST_WITH_RENDITIONS_URI = URI
      .create(BASE_URI + "/masterPlaylistWithRenditions.m3u8");
  private static final URI AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI = URI
      .create(BASE_URI + "/audio/audio_stereo_en_default.m3u8");
  private static final URI SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI = URI
      .create(BASE_URI + "/subtitles/subtitles_en_default.m3u8");
  private static final String AUDIO_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - audio playlist";
  private static final String SUBTITLE_PLAYLIST_SAMPLE_NAME =
      SAMPLER_NAME + " - subtitles playlist";
  private static final double AUDIO_SEGMENT_DURATION = 2.0;
  private static final double SUBTITLES_SEGMENT_DURATION = 60.0;
  private static final String FRENCH_LANGUAGE_SELECTOR = "fr";
  private static final String SUBTITLE_PLAYLIST_FRENCH_RESOURCE = "frenchSubtitlePlaylist.m3u8";
  private static final URI FRENCH_SUBTITLES_PLAYLIST_URI = URI
      .create(BASE_URI + "/subtitles/subtitles_fr_no_default.m3u8");
  private static final String AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE = "defaultEnglishAudioPlaylist.m3u8";
  private static final String SUBTITLE_SAMPLE_NAME = SAMPLER_NAME + " - subtitles";

  private HlsSampler sampler;

  @Override
  protected void buildSampleImpl() {
    baseSampler.setMasterUrl(MASTER_URI.toString());
    sampler = new HlsSampler(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  @Test
  public void shouldDownloadSegmentWhenUriIsFromMediaPlaylist() throws Exception {
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1));
  }

  private void setupUriSamplerPlaylist(URI uri, String... playlists) {
    HTTPSampleResult[] rest = new HTTPSampleResult[playlists.length - 1];
    for (int i = 1; i < playlists.length; i++) {
      rest[i - 1] = buildBaseSampleResult(SAMPLER_NAME, uri, playlists[i]);
    }
    uriSampler
        .setupUriSampleResults(uri, buildBaseSampleResult(SAMPLER_NAME, uri, playlists[0]), rest);
  }

  private HTTPSampleResult buildMediaSegmentSampleResult(int sequenceNumber) {
    return buildExpectedSegmentSampleResult(sequenceNumber, MEDIA_TYPE_NAME,
        MEDIA_SEGMENT_DURATION);
  }

  private HTTPSampleResult buildExpectedSegmentSampleResult(int sequenceNumber, String segmentType,
      double segmentDuration) {
    HTTPSampleResult result = buildBaseSegmentSampleResult(segmentType, sequenceNumber);
    result.setSampleLabel(buildSegmentName(segmentType));
    return addDurationHeader(result, segmentDuration);
  }

  @Test
  public void shouldDownloadSegmentWhenUriIsFromMasterPlaylist() throws Exception {
    String masterPlaylist = getResource(MASTER_PLAYLIST_NAME);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, masterPlaylist),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1));
  }

  @Test
  public void shouldDownloadAllSegmentsWhenSampleWholeVideoAndVOD() throws Exception {
    String mediaPlaylist = getResource(VOD_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    sampler.sample();
    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilListEndWhenEventStreamAndPlayWholeVideo()
      throws Exception {
    String mediaPlaylist1 = getResource(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    String mediaPlaylist2 = getResource(EVENT_MEDIA_PLAYLIST_PART_2_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    sampler.sample();
    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber));
  }

  @Test
  public void shouldResumeDownloadWhenMultipleSamplesAndResumeDownload() throws Exception {
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    baseSampler.setResumeVideoStatus(true);
    sampler.sample();
    sampler.sample();
    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(sequenceNumber));
  }

  @Test
  public void shouldKeepDownloadingSameSegmentsWhenResetVideoStatus()
      throws Exception {
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    sampler.resetVideoStatus();
    sampler.sample();
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1));
  }

  @Test
  public void shouldDownloadSegmentsUntilPlayPeriodWhenVodWithPlayPeriod()
      throws Exception {
    String mediaPlaylist = getResource(VOD_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION * 2);
    sampler.sample();
    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilPlayPeriodWhenEventStreamWithPlayPeriod()
      throws Exception {
    String mediaPlaylist1 = getResource(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    String mediaPlaylist2 = getResource(EVENT_MEDIA_PLAYLIST_PART_2_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    setPlaySeconds(MEDIA_SEGMENT_DURATION * 4);
    sampler.sample();
    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildMediaSegmentSampleResult(sequenceNumber));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilPlayPeriodWhenLiveStreamWithPlayPeriod()
      throws Exception {
    String mediaPlaylist1 = getResource("liveMediaPlaylist-Part1.m3u8");
    String mediaPlaylist2 = getResource("liveMediaPlaylist-Part2.m3u8");
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    setPlaySeconds(MEDIA_SEGMENT_DURATION * 4);
    sampler.sample();
    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildMediaSegmentSampleResult(sequenceNumber));
  }

  @Test
  public void shouldNotDownloadMediaPlaylistOrSegmentsWhenMasterPlaylistRequestFails() {
    setupUriSamplerErrorResult(MASTER_URI);
    sampler.sample();
    verifySampleResults(buildErrorSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI));
  }

  private void setupUriSamplerErrorResult(URI uri) {
    uriSampler.setupUriSampleResults(uri, buildBaseErrorSampleResult(uri));
  }

  private HTTPSampleResult buildBaseErrorSampleResult(URI uri) {
    HTTPSampleResult ret = buildBaseSampleResult(uri);
    ret.setSuccessful(false);
    ret.setResponseCode("404");
    ret.setResponseData("Not Found", Charsets.UTF_8.name());
    return ret;
  }

  private HTTPSampleResult buildErrorSampleResult(String sampleName, URI uri) {
    HTTPSampleResult ret = buildBaseErrorSampleResult(uri);
    ret.setSampleLabel(sampleName);
    return ret;
  }

  @Test
  public void shouldNotDownloadSegmentsWhenMediaPlaylistRequestFails() throws Exception {
    String masterPlaylist = getResource(MASTER_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, masterPlaylist);
    setupUriSamplerErrorResult(MEDIA_PLAYLIST_URI);
    sampler.sample();
    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, masterPlaylist),
        buildErrorSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI));
  }

  @Test
  public void shouldStopDownloadingSegmentsWhenMediaPlaylistReloadFails() throws Exception {
    String mediaPlaylist1 = getResource(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    uriSampler.setupUriSampleResults(MASTER_URI,
        buildBaseSampleResult(SAMPLER_NAME, MASTER_URI, mediaPlaylist1),
        buildErrorSampleResult(SAMPLER_NAME, MASTER_URI));
    sampler.sample();
    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber),
        buildErrorSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI));
  }

  @Test
  public void shouldKeepDownloadingSegmentsWhenOneSegmentRequestFails() throws Exception {
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    URI failingSegmentUri = buildSegmentUri(MEDIA_TYPE_NAME, 2);
    setupUriSamplerErrorResult(failingSegmentUri);
    sampler.sample();
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1),
        buildErrorMediaSegmentSampleResult(failingSegmentUri),
        buildMediaSegmentSampleResult(3));
  }

  private HTTPSampleResult buildErrorMediaSegmentSampleResult(URI uri) {
    return addDurationHeader(buildErrorSampleResult(SEGMENT_SAMPLE_NAME, uri),
        MEDIA_SEGMENT_DURATION);
  }

  @Test
  public void shouldWaitTargetTimeForPlaylistReloadWhenSegmentsDownloadFasterThanTargetTime()
      throws Exception {
    TimedUriSampler timedUriSampler = new TimedUriSampler(uriSampler, timeMachine, 0);
    buildSampler(timedUriSampler);
    setupUriSamplerPlaylist(MASTER_URI, getResource(EVENT_MEDIA_PLAYLIST_PART_1_NAME),
        getResource(EVENT_MEDIA_PLAYLIST_PART_2_NAME));
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
    setupUriSamplerPlaylist(MASTER_URI, getResource(EVENT_MEDIA_PLAYLIST_PART_1_NAME),
        getResource(EVENT_MEDIA_PLAYLIST_PART_2_NAME));
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
    String mediaPlaylistPart1 = getResource(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylistPart1, mediaPlaylistPart1,
        getResource(EVENT_MEDIA_PLAYLIST_PART_2_NAME));
    sampler.sample();
    List<Instant> timestamps = timedUriSampler.getUriSamplesTimeStamps(MASTER_URI);
    assertThat(timestamps.get(1).until(timestamps.get(2), ChronoUnit.MILLIS))
        .isBetween(TARGET_TIME_MILLIS / 2, TARGET_TIME_MILLIS / 2 + TIME_THRESHOLD_MILLIS);
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldOnlyDownloadMediaPlaylistWhenInterruptMediaPlaylistDownload() throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 1);
    buildSampler(uriSampler);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    runWithAsyncSample(() -> {
      uriSampler.interruptSamplerWhileDownloading(httpClient);
      return null;
    });
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist));
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

  private static class DownloadBlockingUriSampler implements Function<URI, HTTPSampleResult> {

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

    private void interruptSamplerWhileDownloading(
        VideoStreamingHttpClient httpClient) throws InterruptedException {
      awaitStartDownload();
      doThrow(new SamplerInterruptedException()).when(httpClient).downloadUri(any());
      endDownload();
    }

  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldDownloadPlaylistAndFirstSegmentWhenInterruptSegmentDownload() throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 2);
    buildSampler(uriSampler);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);

    runWithAsyncSample(() -> {
      uriSampler.syncDownload();
      uriSampler.interruptSamplerWhileDownloading(httpClient);
      return null;
    });

    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1));
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldDownloadOnlyMasterListWhenInterruptDuringMasterListDownload() throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 1);
    buildSampler(uriSampler);
    String masterPlaylist = getResource(MASTER_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, masterPlaylist);

    runWithAsyncSample(() -> {
      uriSampler.interruptSamplerWhileDownloading(httpClient);
      return null;
    });

    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, masterPlaylist));
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldNotReloadPlayListWhenInterruptDuringLastSegmentDownload() throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 4);
    buildSampler(uriSampler);
    String mediaPlaylistPart1 = getResource(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylistPart1);

    runWithAsyncSample(() -> {
      for (int i = 0; i < 3; i++) {
        uriSampler.syncDownload();
      }
      uriSampler.interruptSamplerWhileDownloading(httpClient);
      return null;
    });

    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber));
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldStopReloadingPlaylistWhenInterruptPlaylistReloadGettingSamePlaylist()
      throws Exception {
    DownloadBlockingUriSampler uriSampler = new DownloadBlockingUriSampler(this.uriSampler, 5);
    buildSampler(uriSampler);
    String mediaPlaylistPart1 = getResource(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylistPart1, mediaPlaylistPart1);

    runWithAsyncSample(() -> {
      for (int i = 0; i < 4; i++) {
        uriSampler.syncDownload();
      }
      uriSampler.interruptSamplerWhileDownloading(httpClient);
      return null;
    });

    int sequenceNumber = 1;
    verifySampleResults(
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1));
  }

  @Test
  public void shouldDownloadDefaultSubtitleWhenSelectorNotFound() throws IOException {
    setUpSamplerForRenditions("sp", "");

    String masterPlaylist = getResource(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getResource(AUDIO_PLAYLIST_RESOURCE);
    String defaultSubtitlePlaylist = getResource(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, defaultSubtitlePlaylist);

    sampler.sample();

    int audioSegmentIndex = 1;
    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildBaseSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildBaseSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME,
            SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            defaultSubtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex),
        buildSubtitlesSegmentSampleResult());
  }

  private void setUpSamplerForRenditions(String subtitleLanguage, String audioLanguage) {
    baseSampler.setMasterUrl(MASTER_PLAYLIST_WITH_RENDITIONS_URI.toString());
    baseSampler.setSubtitleLanguage(subtitleLanguage);
    baseSampler.setAudioLanguage(audioLanguage);
    setPlaySeconds(3);
  }

  private HTTPSampleResult buildAudioSegmentSampleResult(int sequenceNumber) {
    return buildExpectedSegmentSampleResult(sequenceNumber, AUDIO_TYPE_NAME,
        AUDIO_SEGMENT_DURATION);
  }

  private HTTPSampleResult buildSubtitlesSegmentSampleResult() {
    return buildExpectedSegmentSampleResult(1, SUBTITLES_TYPE_NAME, SUBTITLES_SEGMENT_DURATION);
  }

  @Test
  public void shouldDownloadSubtitlesWhenSamplerWithSubtitleMatchingLanguageSelector()
      throws Exception {
    setUpSamplerForRenditions(FRENCH_LANGUAGE_SELECTOR, "");

    String masterPlaylist = getResource(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getResource(AUDIO_PLAYLIST_RESOURCE);
    String subtitlePlaylist = getResource(SUBTITLE_PLAYLIST_FRENCH_RESOURCE);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(FRENCH_SUBTITLES_PLAYLIST_URI, subtitlePlaylist);

    sampler.sample();

    int audioSegmentIndex = 1;
    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildBaseSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildBaseSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME, FRENCH_SUBTITLES_PLAYLIST_URI,
            subtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex),
        buildSubtitlesSegmentSampleResult());
  }

  @Test
  public void shouldDownloadDefaultAudioWhenSelectorNotFound() throws IOException {
    setUpSamplerForRenditions("", "sp");

    String masterPlaylist = getResource(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getResource(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlePlaylist = getResource(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, subtitlePlaylist);

    sampler.sample();

    int audioSegmentIndex = 1;
    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildBaseSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildBaseSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME,
            SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            subtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex),
        buildSubtitlesSegmentSampleResult());
  }

  @Test
  public void shouldKeepDownloadMediaAndAudioSegmentsWhenFailsSubtitleDownload()
      throws IOException {
    setUpSamplerForRenditions("", "sp");

    String masterPlaylist = getResource(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getResource(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlesPlaylist = getResource(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, subtitlesPlaylist);

    when(uriSampler.apply(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI))
        .thenReturn(buildBaseErrorSampleResult(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI));

    sampler.sample();

    int audioSegmentIndex = 1;
    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildBaseSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildErrorSampleResult(SUBTITLE_SAMPLE_NAME, SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex));
  }


  @Test
  public void shouldKeepDownloadMediaAndSubtitleSegmentsWhenFailsAudioDownload()
      throws IOException {
    setUpSamplerForRenditions("", "sp");

    String masterPlaylist = getResource(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getResource(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlePlaylistWithParsingException = getResource(
        SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
        subtitlePlaylistWithParsingException);

    when(uriSampler.apply(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI))
        .thenReturn(buildBaseErrorSampleResult(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI));

    sampler.sample();

    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildErrorSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI),
        buildBaseSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME,
            SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            subtitlePlaylistWithParsingException),
        buildMediaSegmentSampleResult(1),
        buildSubtitlesSegmentSampleResult());
  }

  @Test
  public void shouldDownloadSubtitleWholeFileWhenSubtitleWithoutPlaylist() throws IOException {
    setUpSamplerForRenditions("it", "");

    String masterPlaylist = getResource(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getResource(AUDIO_PLAYLIST_RESOURCE);
    String subtitlePlaylist = getResource("italianSubtitlePlaylist.ttml");
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    URI subtitlesUri = URI.create(BASE_URI + "/subtitles/subtitles_it_no_default.ttml");
    setupUriSamplerPlaylist(subtitlesUri, subtitlePlaylist);

    sampler.sample();

    int audioSegmentIndex = 1;
    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildBaseSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildBaseSampleResult(SUBTITLE_SAMPLE_NAME, subtitlesUri, subtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex));
  }

  @Test
  public void shouldKeepDownloadingSegmentsWhenOneSegmentDownloadFails() throws Exception {
    setUpSamplerForRenditions(FRENCH_LANGUAGE_SELECTOR, "");

    String masterPlaylist = getResource(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getResource(AUDIO_PLAYLIST_RESOURCE);
    String subtitlePlaylist = getResource(SUBTITLE_PLAYLIST_FRENCH_RESOURCE);
    String mediaPlaylist = getResource(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(FRENCH_SUBTITLES_PLAYLIST_URI, subtitlePlaylist);

    URI firstAudioSegmentUri = URI.create(BASE_URI + "/audio/001.ts");
    when(uriSampler.apply(firstAudioSegmentUri))
        .thenReturn(buildBaseErrorSampleResult(firstAudioSegmentUri));

    sampler.sample();

    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildBaseSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildBaseSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildBaseSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME, FRENCH_SUBTITLES_PLAYLIST_URI,
            subtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        addDurationHeader(
            buildErrorSampleResult(buildSegmentName(AUDIO_TYPE_NAME), firstAudioSegmentUri),
            AUDIO_SEGMENT_DURATION),
        buildAudioSegmentSampleResult(2),
        buildSubtitlesSegmentSampleResult());
  }

  @Test
  public void shouldGetNotMatchMediaPlaylistWhenNoMediaFound() throws IOException {
    setUpSamplerForRenditions(FRENCH_LANGUAGE_SELECTOR, "");
    BandwidthSelector bandwidthSelector = new CustomBandwidthSelector("1234567");
    baseSampler.setBandwidthSelector(bandwidthSelector);

    String masterPlaylist = getResource("masterPlaylistWithoutMedia.m3u8");
    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);

    sampler.sample();

    verifySampleResults(
        buildBaseSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildNotMatchingPlaylistResult());
  }

  private HTTPSampleResult buildNotMatchingPlaylistResult() {
    HTTPSampleResult result = VideoStreamingSampler.buildNotMatchingMediaPlaylistResult();
    result.setSampleLabel(MEDIA_PLAYLIST_SAMPLE_NAME);
    return result;
  }

}

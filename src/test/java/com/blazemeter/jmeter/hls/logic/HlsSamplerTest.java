package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.blazemeter.jmeter.hls.JMeterTestUtils;
import com.blazemeter.jmeter.hls.logic.BandwidthSelector.CustomBandwidthSelector;
import com.blazemeter.jmeter.hls.logic.HlsSampler.HlsHttpClient;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
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
import org.apache.jmeter.assertions.ResponseAssertion;
import org.apache.jmeter.extractor.RegexExtractor;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.threads.SamplePackage;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HlsSamplerTest {

  private static final String BASE_URI = "http://test";
  private static final URI MASTER_URI = URI.create(BASE_URI + "/master.m3u8");
  private static final String SEGMENT_CONTENT_TYPE = "video/MP2T";
  private static final String SAMPLER_NAME = "HLS";
  private static final String MASTER_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - master playlist";
  private static final String MEDIA_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - media playlist";
  private static final String AUDIO_PLAYLIST_SAMPLE_NAME = SAMPLER_NAME + " - audio playlist";
  private static final String SUBTITLE_PLAYLIST_SAMPLE_NAME =
      SAMPLER_NAME + " - subtitles playlist";
  private static final String SUBTITLE_SAMPLE_NAME = SAMPLER_NAME + " - subtitles";
  private static final String MEDIA_SEGMENT_SAMPLE_TYPE = "media segment";
  private static final String SEGMENT_SAMPLE_NAME =
      SAMPLER_NAME + " - " + MEDIA_SEGMENT_SAMPLE_TYPE;
  private static final String SIMPLE_MEDIA_PLAYLIST_NAME = "simpleMediaPlaylist.m3u8";
  private static final String VOD_MEDIA_PLAYLIST_NAME = "vodMediaPlaylist.m3u8";
  private static final String EVENT_MEDIA_PLAYLIST_PART_1_NAME = "eventMediaPlaylist-Part1.m3u8";
  private static final String EVENT_MEDIA_PLAYLIST_PART_2_NAME = "eventMediaPlaylist-Part2.m3u8";
  private static final URI MEDIA_PLAYLIST_URI = URI.create(BASE_URI + "/audio-only.m3u8");
  private static final String MASTER_PLAYLIST_NAME = "masterPlaylist.m3u8";

  private static final String MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE = "masterPlaylistWithRenditions.m3u8";
  private static final String SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE = "defaultEnglishSubtitlePlaylist.m3u8";
  private static final String SUBTITLE_PLAYLIST_FRENCH_RESOURCE = "frenchSubtitlePlaylist.m3u8";
  private static final String AUDIO_PLAYLIST_RESOURCE = "audioPlaylist.m3u8";
  private static final String AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE = "defaultEnglishAudioPlaylist.m3u8";

  private static final URI MASTER_PLAYLIST_WITH_RENDITIONS_URI = URI
      .create(BASE_URI + "/masterPlaylistWithRenditions.m3u8");
  private static final URI SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI = URI
      .create(BASE_URI + "/subtitles_en_default.m3u8");
  private static final URI FRENCH_SUBTITLES_PLAYLIST_URI = URI
      .create(BASE_URI + "/subtitles_fr_no_default.m3u8");
  private static final URI AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI = URI
      .create(BASE_URI + "/audio/audio_stereo_en_default.m3u8");

  private static final long TARGET_TIME_MILLIS = 10000;
  private static final long TIME_THRESHOLD_MILLIS = 5000;
  private static final long TEST_TIMEOUT = 10000;
  private static final String SUBTITLES_TYPE_NAME = "subtitles";
  private static final String AUDIO_TYPE_NAME = "audio";
  private static final String MEDIA_TYPE_NAME = "media";
  private static final String FRENCH_LANGUAGE_SELECTOR = "fr";
  private static final String EXTRACTOR_VAR_NAME = "myVar";
  public static final double MEDIA_SEGMENT_DURATION = 5.0;
  public static final double AUDIO_SEGMENT_DURATION = 2.0;
  public static final double SUBTITLES_SEGMENT_DURATION = 60.0;

  // we use static context and listener due to issue
  private static JMeterContext context;

  private HlsSampler sampler;
  private SegmentResultFallbackUriSamplerMock uriSampler = new SegmentResultFallbackUriSamplerMock();
  @Mock
  private Consumer<SampleResult> sampleResultListener;
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

  @BeforeClass
  public static void setupClass() {
    JMeterTestUtils.setupJmeterEnv();
    context = JMeterContextService.getContext();
  }

  @Before
  public void setUp() {
    buildSampler(uriSampler);
    JMeterContextService.replaceContext(context);
    JMeterContextService.getContext().setVariables(new JMeterVariables());
    setupSampleListener();
    getSamplePackage().getAssertions().clear();
  }

  private void buildSampler(Function<URI, HTTPSampleResult> uriSampler) {
    HlsHttpClient httpClient = Mockito.mock(HlsHttpClient.class);
    when(httpClient.sample(any(), any(), anyBoolean(), anyInt()))
        .thenAnswer(a -> uriSampler.apply(a.getArgument(0, URL.class).toURI()));
    sampler = new HlsSampler(httpClient, timeMachine);
    sampler.setName(SAMPLER_NAME);
    sampler.setMasterUrl(MASTER_URI.toString());
  }

  private void setupSampleListener() {
    SampleListener sampleListener = mock(SampleListener.class, withSettings().extraInterfaces(
        TestElement.class));
    doAnswer(a -> {
      sampleResultListener.accept(a.getArgument(0, SampleEvent.class).getResult());
      return null;
    }).
        when(sampleListener).sampleOccurred(any());
    // we use arrayList for assertions and post processors to be able to add elements
    SamplePackage pack = new SamplePackage(Collections.emptyList(),
        Collections.singletonList(sampleListener), Collections.emptyList(), new ArrayList<>(),
        new ArrayList<>(), Collections.emptyList(), Collections.emptyList());
    context.getVariables().putObject(JMeterThread.PACKAGE_OBJECT, pack);
  }

  private SamplePackage getSamplePackage() {
    return (SamplePackage) JMeterContextService.getContext().getVariables()
        .getObject(JMeterThread.PACKAGE_OBJECT);
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
      int extensionPos = uriStr.lastIndexOf('.');
      int sequenceNumber = Integer.parseInt(uriStr.substring(extensionPos - 1, extensionPos));
      return buildBaseSegmentSampleResult(sequenceNumber);
    }
  }

  private HTTPSampleResult buildBaseSegmentSampleResult(int sequenceNumber) {
    return buildSampleResult(buildSegmentUri(sequenceNumber), SEGMENT_CONTENT_TYPE, "");
  }

  private URI buildSegmentUri(int sequenceNumber) {
    return URI.create(BASE_URI + "/00" + sequenceNumber + ".ts");
  }

  private HTTPSampleResult buildSampleResult(URI uri, String contentType,
      String responseBody) {
    HTTPSampleResult ret = buildBaseSampleResult(uri);
    ret.setSuccessful(true);
    ret.setResponseCodeOK();
    ret.setResponseMessageOK();
    ret.setResponseHeaders("Content-Type: " + contentType + "\n");
    ret.setResponseData(responseBody, Charsets.UTF_8.name());
    return ret;
  }

  private HTTPSampleResult buildBaseSampleResult(URI uri) {
    try {
      HTTPSampleResult ret = new HTTPSampleResult();
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
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1)));
  }

  private HTTPSampleResult buildMediaSegmentSampleResult(int sequenceNumber) {
    return buildExpectedSegmentSampleResult(sequenceNumber, MEDIA_TYPE_NAME,
        MEDIA_SEGMENT_DURATION);
  }

  private HTTPSampleResult buildExpectedSegmentSampleResult(int sequenceNumber, String segmentType,
      double segmentDuration) {
    HTTPSampleResult result = buildBaseSegmentSampleResult(sequenceNumber);
    result.setSampleLabel("HLS - " + segmentType + " segment");
    addDurationHeader(result, segmentDuration);
    return result;
  }

  private void addDurationHeader(HTTPSampleResult result, double duration) {
    result.setResponseHeaders(result.getResponseHeaders() + "X-MEDIA-SEGMENT-DURATION: " + duration
        + "\n");
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
    when(uriSampler.mock.apply(uri))
        .thenReturn(buildPlaylistSampleResult(SAMPLER_NAME, uri, playlists[0]), rest);
  }

  private HTTPSampleResult buildPlaylistSampleResult(String name, URI uri, String body) {
    HTTPSampleResult ret = buildSampleResult(uri, "application/x-mpegURL", body);
    ret.setSampleLabel(name);
    return ret;
  }

  private void setPlaySeconds(double playSeconds) {
    sampler.setPlayVideoDuration(true);
    sampler.setPlaySeconds(String.valueOf((int) playSeconds));
  }

  private void verifyNotifiedSampleResults(List<SampleResult> results) {
    verify(sampleResultListener, atLeastOnce()).accept(sampleResultCaptor.capture());
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
    json.put("successful", sample.isSuccessful());
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
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_URI, masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1)));
  }

  @Test
  public void shouldDownloadAllSegmentsWhenSampleWholeVideoAndVOD() throws Exception {
    String mediaPlaylist = getPlaylist(VOD_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    sampler.sample();
    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilListEndWhenEventStreamAndPlayWholeVideo()
      throws Exception {
    String mediaPlaylist1 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    String mediaPlaylist2 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_2_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    sampler.sample();
    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber)));
  }

  @Test
  public void shouldResumeDownloadWhenMultipleSamplesAndResumeDownload() throws Exception {
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.setResumeVideoStatus(true);
    sampler.sample();
    sampler.sample();
    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(sequenceNumber)));
  }

  @Test
  public void shouldKeepDownloadingSameSegmentsWhenMultipleSamplesAndNoResumeDownload()
      throws Exception {
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1)));
  }

  @Test
  public void shouldDownloadSegmentsUntilPlayPeriodWhenVodWithPlayPeriod()
      throws Exception {
    String mediaPlaylist = getPlaylist(VOD_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION * 2);
    sampler.sample();
    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilPlayPeriodWhenEventStreamWithPlayPeriod()
      throws Exception {
    String mediaPlaylist1 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_1_NAME);
    String mediaPlaylist2 = getPlaylist(EVENT_MEDIA_PLAYLIST_PART_2_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    setPlaySeconds(MEDIA_SEGMENT_DURATION * 4);
    sampler.sample();
    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildMediaSegmentSampleResult(sequenceNumber)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsUntilPlayPeriodWhenLiveStreamWithPlayPeriod()
      throws Exception {
    String mediaPlaylist1 = getPlaylist("liveMediaPlaylist-Part1.m3u8");
    String mediaPlaylist2 = getPlaylist("liveMediaPlaylist-Part2.m3u8");
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist1, mediaPlaylist2);
    setPlaySeconds(MEDIA_SEGMENT_DURATION * 4);
    sampler.sample();
    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist2),
        buildMediaSegmentSampleResult(sequenceNumber)));
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
        .thenReturn(buildBaseErrorSampleResult(uri));
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
    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber),
        buildErrorSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsWhenOneSegmentRequestFails() throws Exception {
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    URI failingSegmentUri = buildSegmentUri(2);
    setupUriSamplerErrorResult(failingSegmentUri);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1),
        buildErrorMediaSegmentSampleResult(failingSegmentUri),
        buildMediaSegmentSampleResult(3)));
  }

  private HTTPSampleResult buildErrorMediaSegmentSampleResult(URI uri) {
    HTTPSampleResult ret = buildErrorSampleResult(SEGMENT_SAMPLE_NAME, uri);
    addDurationHeader(ret, MEDIA_SEGMENT_DURATION);
    return ret;
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
      Future<Object> sampleResult = executor.submit(() -> {
        JMeterContextService.replaceContext(context);
        return sampler.sample();
      });
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
        buildMediaSegmentSampleResult(1)));
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

    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber)));
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

    int sequenceNumber = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber++),
        buildMediaSegmentSampleResult(sequenceNumber),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylistPart1)));
  }

  @Test
  public void shouldDownloadDefaultSubtitleWhenSelectorNotFound() throws IOException {
    setUpSamplerForRenditions("sp", "");

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_RESOURCE);
    String defaultSubtitlePlaylist = getPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, defaultSubtitlePlaylist);

    sampler.sample();

    int audioSegmentIndex = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME,
            SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            defaultSubtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex),
        buildSubtitlesSegmentSampleResult()));
  }

  private void setUpSamplerForRenditions(String subtitleLanguage, String audioLanguage) {
    sampler.setMasterUrl(MASTER_PLAYLIST_WITH_RENDITIONS_URI.toString());
    sampler.setSubtitleLanguage(subtitleLanguage);
    sampler.setAudioLanguage(audioLanguage);
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

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_RESOURCE);
    String subtitlePlaylist = getPlaylist(SUBTITLE_PLAYLIST_FRENCH_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(FRENCH_SUBTITLES_PLAYLIST_URI, subtitlePlaylist);

    sampler.sample();

    int audioSegmentIndex = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME, FRENCH_SUBTITLES_PLAYLIST_URI,
            subtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex),
        buildSubtitlesSegmentSampleResult()));
  }

  @Test
  public void shouldDownloadDefaultAudioWhenSelectorNotFound() throws IOException {
    setUpSamplerForRenditions("", "sp");

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlePlaylist = getPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, subtitlePlaylist);

    sampler.sample();

    int audioSegmentIndex = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME,
            SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            subtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex),
        buildSubtitlesSegmentSampleResult()));
  }

  @Test
  public void shouldKeepDownloadMediaAndAudioSegmentsWhenFailsSubtitleDownload()
      throws IOException {
    setUpSamplerForRenditions("", "sp");

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlesPlaylist = getPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI, subtitlesPlaylist);

    when(uriSampler.apply(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI))
        .thenReturn(buildBaseErrorSampleResult(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI));

    sampler.sample();

    int audioSegmentIndex = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildErrorSampleResult(SUBTITLE_SAMPLE_NAME, SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex)));
  }


  @Test
  public void shouldKeepDownloadMediaAndSubtitleSegmentsWhenFailsAudioDownload()
      throws IOException {
    setUpSamplerForRenditions("", "sp");

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_ENGLISH_DEFAULT_RESOURCE);
    String subtitlePlaylistWithParsingException = getPlaylist(
        SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_RESOURCE);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
        subtitlePlaylistWithParsingException);

    when(uriSampler.apply(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI))
        .thenReturn(buildBaseErrorSampleResult(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI));

    sampler.sample();

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildErrorSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI),
        buildPlaylistSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME,
            SUBTITLES_PLAYLIST_DEFAULT_ENGLISH_URI,
            subtitlePlaylistWithParsingException),
        buildMediaSegmentSampleResult(1),
        buildSubtitlesSegmentSampleResult()));
  }

  @Test
  public void shouldDownloadSubtitleWholeFileWhenSubtitleWithoutPlaylist() throws IOException {
    setUpSamplerForRenditions("it", "");

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_RESOURCE);
    String subtitlePlaylist = getPlaylist("italianSubtitlePlaylist.ttml");
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    URI subtitlesUri = URI.create(BASE_URI + "/subtitles_it_no_default.ttml");
    setupUriSamplerPlaylist(subtitlesUri, subtitlePlaylist);

    sampler.sample();

    int audioSegmentIndex = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_SAMPLE_NAME, subtitlesUri, subtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex)));
  }

  @Test
  public void shouldKeepDownloadingSegmentsWhenOneSegmentDownloadFails() throws Exception {
    setUpSamplerForRenditions(FRENCH_LANGUAGE_SELECTOR, "");

    String masterPlaylist = getPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_RESOURCE);
    String audioPlaylist = getPlaylist(AUDIO_PLAYLIST_RESOURCE);
    String subtitlePlaylist = getPlaylist(SUBTITLE_PLAYLIST_FRENCH_RESOURCE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);

    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);
    setupUriSamplerPlaylist(MEDIA_PLAYLIST_URI, mediaPlaylist);
    setupUriSamplerPlaylist(AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI, audioPlaylist);
    setupUriSamplerPlaylist(FRENCH_SUBTITLES_PLAYLIST_URI, subtitlePlaylist);

    URI firstAudioSegmentUri = URI.create(BASE_URI + "/audio/audio_1.aac");
    when(uriSampler.apply(firstAudioSegmentUri))
        .thenReturn(buildBaseErrorSampleResult(firstAudioSegmentUri));

    sampler.sample();

    int audioSegmentIndex = 1;
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MEDIA_PLAYLIST_URI, mediaPlaylist),
        buildPlaylistSampleResult(AUDIO_PLAYLIST_SAMPLE_NAME, AUDIO_PLAYLIST_DEFAULT_ENGLISH_URI,
            audioPlaylist),
        buildPlaylistSampleResult(SUBTITLE_PLAYLIST_SAMPLE_NAME, FRENCH_SUBTITLES_PLAYLIST_URI,
            subtitlePlaylist),
        buildMediaSegmentSampleResult(1),
        buildAudioSegmentSampleResult(audioSegmentIndex++),
        buildAudioSegmentSampleResult(audioSegmentIndex),
        buildSubtitlesSegmentSampleResult()));
  }

  @Test
  public void shouldGetNotMatchMediaPlaylistWhenNoMediaFound() throws IOException {
    setUpSamplerForRenditions(FRENCH_LANGUAGE_SELECTOR, "");
    BandwidthSelector bandwidthSelector = new CustomBandwidthSelector(1234567L);
    sampler.setBandwidthSelector(bandwidthSelector);

    String masterPlaylist = getPlaylist("masterPlaylistWithoutMedia.m3u8");
    setupUriSamplerPlaylist(MASTER_PLAYLIST_WITH_RENDITIONS_URI, masterPlaylist);

    sampler.sample();

    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MASTER_PLAYLIST_SAMPLE_NAME, MASTER_PLAYLIST_WITH_RENDITIONS_URI,
            masterPlaylist),
        buildNotMatchingPlaylistResult()));
  }

  private HTTPSampleResult buildNotMatchingPlaylistResult() {
    HTTPSampleResult result = HlsSampler.buildNotMatchingMediaPlaylistResult();
    result.setSampleLabel(MEDIA_PLAYLIST_SAMPLE_NAME);
    return result;
  }

  @Test
  public void shouldGetSuccessResultWhenAssertionWithSameLabelTypeAndPasses() throws Exception {
    addAssertionWithLabelTypeAndTestString(MEDIA_SEGMENT_SAMPLE_TYPE, "");
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1)));
  }

  private void addAssertionWithLabelTypeAndTestString(String labelType, String testString) {
    ResponseAssertion assertion = new ResponseAssertion();
    assertion.setName("test - " + labelType);
    assertion.setTestFieldURL();
    assertion.setToContainsType();
    assertion.addTestString(testString);
    getSamplePackage().addAssertion(assertion);
  }

  @Test
  public void shouldGetFailResultWhenAssertionWithSameLabelTypeAndFails() throws Exception {
    buildFailedAssertionWithLabelType(MEDIA_SEGMENT_SAMPLE_TYPE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildFailedAssertionResult(buildMediaSegmentSampleResult(1))));
  }

  private void buildFailedAssertionWithLabelType(String labelType) {
    addAssertionWithLabelTypeAndTestString(labelType, "FAILURE");
  }

  private HTTPSampleResult buildFailedAssertionResult(HTTPSampleResult result) {
    result.setSuccessful(false);
    return result;
  }


  @Test
  public void shouldGetFailResultsWhenAssertionWithNoLabelTypeAndFails() throws Exception {
    buildFailedAssertionWithLabelType("");
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildFailedAssertionResult(
            buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist)),
        buildFailedAssertionResult(buildMediaSegmentSampleResult(1))));
  }

  @Test
  public void shouldGetSuccessResultWhenAssertionWithDifferentLabelTypeAndFails() throws Exception {
    buildFailedAssertionWithLabelType("audio playlist");
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    verifyNotifiedSampleResults(Arrays.asList(
        buildPlaylistSampleResult(MEDIA_PLAYLIST_SAMPLE_NAME, MASTER_URI, mediaPlaylist),
        buildMediaSegmentSampleResult(1)
    ));
  }

  @Test
  public void shouldGetExtractedVariableWhenExtractorWithSameLabelType()
      throws Exception {
    addExtractorWithLabelType(MEDIA_SEGMENT_SAMPLE_TYPE);
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    assertThat(getExtractedVariable()).isNotNull();
  }

  private String getExtractedVariable() {
    return JMeterContextService.getContext().getVariables().get(EXTRACTOR_VAR_NAME);
  }

  private void addExtractorWithLabelType(String labelType) {
    RegexExtractor extractor = new RegexExtractor();
    extractor.setName("-" + labelType);
    extractor.setRefName(EXTRACTOR_VAR_NAME);
    extractor.setMatchNumber(1);
    extractor.setRegex(".*");
    getSamplePackage().addPostProcessor(extractor);
  }

  @Test
  public void shouldGetNullVariableWhenExtractorWithDifferentLabelType()
      throws Exception {
    addExtractorWithLabelType("audio playlist");
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    assertThat(getExtractedVariable()).isNull();
  }

  @Test
  public void shouldGetExtractedVariableWhenExtractorWithNoLabelType()
      throws Exception {
    addExtractorWithLabelType("");
    String mediaPlaylist = getPlaylist(SIMPLE_MEDIA_PLAYLIST_NAME);
    setupUriSamplerPlaylist(MASTER_URI, mediaPlaylist);
    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();
    assertThat(getExtractedVariable()).isNotNull();
  }

}

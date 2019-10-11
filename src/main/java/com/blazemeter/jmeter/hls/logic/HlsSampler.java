package com.blazemeter.jmeter.hls.logic;

import com.google.common.annotations.VisibleForTesting;
import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.Segment;
import io.lindstrom.mpd.data.SegmentTemplate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jmeter.assertions.Assertion;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.processor.PostProcessor;
import org.apache.jmeter.protocol.http.control.CacheManager;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPHC4Impl;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBeanHelper;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.SamplePackage;
import org.apache.jorphan.util.JMeterError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HlsSampler extends HTTPSamplerBase implements Interruptible {

  private static final Logger LOG = LoggerFactory.getLogger(HlsSampler.class);

  private static final String MASTER_URL_PROPERTY_NAME = "HLS.URL_DATA";
  private static final String CUSTOM_RESOLUTION_PROPERTY_NAME = "HLS.RES_DATA";
  private static final String CUSTOM_BANDWIDTH_PROPERTY_NAME = "HLS.NET_DATA";
  private static final String PLAY_SECONDS_PROPERTY_NAME = "HLS.SECONDS_DATA";
  private static final String PLAY_VIDEO_DURATION_PROPERTY_NAME = "HLS.DURATION";
  private static final String AUDIO_LANGUAGE_PROPERTY_NAME = "HLS.AUDIO_LANGUAGE";
  private static final String SUBTITLE_LANGUAGE_PROPERTY_NAME = "HLS.SUBTITLE_LANGUAGE";
  private static final String BANDWIDTH_TYPE_PROPERTY_NAME = "HLS.BANDWIDTH_TYPE";
  private static final String RESOLUTION_TYPE_PROPERTY_NAME = "HLS.RESOLUTION_TYPE";
  private static final String RESUME_DOWNLOAD_PROPERTY_NAME = "HLS.RESUME_DOWNLOAD";

  private static final String HEADER_MANAGER = "HLSRequest.header_manager";
  private static final String COOKIE_MANAGER = "HLSRequest.cookie_manager";
  private static final String CACHE_MANAGER = "HLSRequest.cache_manager";
  private static final String MASTER_TYPE_NAME = "master";
  private static final String SUBTITLES_TYPE_NAME = "subtitles";
  private static final String MEDIA_TYPE_NAME = "media";
  private static final String AUDIO_TYPE_NAME = "audio";
  private static final String VIDEO_TYPE_NAME = "video";
  private static final Set<String> SAMPLE_TYPE_NAMES = buildSampleTypesSet();
  private static final String INITIALIZATION_SEGMENT = "Initialization";
  private static final String REPRESENTATIONID_FORMULA_REPLACE = "$RepresentationID$";
  private static final String BANDWIDTH_FORMULA_REPLACE = "$Bandwidth$";
  private static final String TIME_FORMULA_REPLACE = "$Time$";
  private static final String NUMBER_FORMULA_PATTERN = "(?<=\\$Number)(.*?)(?=\\$)";

  private final transient HlsHttpClient httpClient;
  private final transient TimeMachine timeMachine;

  private transient long lastVideoSegmentNumber = -1;
  private transient long lastAudioSegmentNumber = -1;
  private transient long lastSubtitleSegmentNumber = -1;
  private transient volatile boolean notifyFirstSampleAfterLoopRestart;
  private transient volatile boolean interrupted = false;

  /*
  we use this class to be able to access some methods on super class and because we can't extend
  HTTPProxySampler class
   */
  public static class HlsHttpClient extends HTTPHC4Impl {

    private HlsHttpClient(HTTPSamplerBase testElement) {
      super(testElement);
    }

    @Override
    protected HTTPSampleResult sample(java.net.URL url, String method, boolean areFollowingRedirect,
        int frameDepth) {
      return super.sample(url, method, areFollowingRedirect, frameDepth);
    }

    @Override
    protected void notifyFirstSampleAfterLoopRestart() {
      super.notifyFirstSampleAfterLoopRestart();
    }

    @Override
    protected void threadFinished() {
      super.threadFinished();
    }

  }

  public HlsSampler() {
    initHttpSampler();
    httpClient = new HlsHttpClient(this);
    timeMachine = TimeMachine.SYSTEM;
  }

  public HlsSampler(HlsHttpClient httpClient, TimeMachine timeMachine) {
    initHttpSampler();
    this.httpClient = httpClient;
    this.timeMachine = timeMachine;
  }

  private void initHttpSampler() {
    setName("Media Sampler");
    setFollowRedirects(true);
    setUseKeepAlive(true);
  }

  private static Set<String> buildSampleTypesSet() {
    Set<String> sampleTypes = Stream.of(SUBTITLES_TYPE_NAME, MEDIA_TYPE_NAME, AUDIO_TYPE_NAME)
        .flatMap(t -> Stream.of(buildPlaylistName(t), buildSegmentName(t)))
        .collect(Collectors.toSet());
    sampleTypes.add(buildPlaylistName(MASTER_TYPE_NAME));
    sampleTypes.add(SUBTITLES_TYPE_NAME);
    return sampleTypes;
  }

  private static String buildPlaylistName(String playlistType) {
    return playlistType + " playlist";
  }

  private static String buildSegmentName(String segmentType) {
    return segmentType + " segment";
  }

  public String getMasterUrl() {
    return this.getPropertyAsString(MASTER_URL_PROPERTY_NAME);
  }

  public void setMasterUrl(String url) {
    this.setProperty(MASTER_URL_PROPERTY_NAME, url);
  }

  public boolean isPlayVideoDuration() {
    return this.getPropertyAsBoolean(PLAY_VIDEO_DURATION_PROPERTY_NAME);
  }

  public void setPlayVideoDuration(boolean res) {
    this.setProperty(PLAY_VIDEO_DURATION_PROPERTY_NAME, res);
  }

  public String getPlaySeconds() {
    return this.getPropertyAsString(PLAY_SECONDS_PROPERTY_NAME);
  }

  public void setPlaySeconds(String seconds) {
    this.setProperty(PLAY_SECONDS_PROPERTY_NAME, seconds);
  }

  public String getAudioLanguage() {
    return this.getPropertyAsString(AUDIO_LANGUAGE_PROPERTY_NAME).trim();
  }

  public void setAudioLanguage(String language) {
    this.setProperty(AUDIO_LANGUAGE_PROPERTY_NAME, language);
  }

  public String getSubtitleLanguage() {
    return this.getPropertyAsString(SUBTITLE_LANGUAGE_PROPERTY_NAME).trim();
  }

  public void setSubtitleLanguage(String language) {
    this.setProperty(SUBTITLE_LANGUAGE_PROPERTY_NAME, language);
  }

  public BandwidthSelector getBandwidthSelector() {
    String bandwidth = getPropertyAsString(CUSTOM_BANDWIDTH_PROPERTY_NAME);
    return BandwidthSelector
        .fromStringAndCustomBandwidth(getPropertyAsString(BANDWIDTH_TYPE_PROPERTY_NAME), bandwidth);
  }

  public void setBandwidthSelector(BandwidthSelector selector) {
    setProperty(BANDWIDTH_TYPE_PROPERTY_NAME, selector.getName());
    setProperty(CUSTOM_BANDWIDTH_PROPERTY_NAME, selector.getCustomBandwidth());
  }

  public ResolutionSelector getResolutionSelector() {
    return ResolutionSelector
        .fromStringAndCustomResolution(getPropertyAsString(RESOLUTION_TYPE_PROPERTY_NAME),
            getPropertyAsString(CUSTOM_RESOLUTION_PROPERTY_NAME));
  }

  public void setResolutionSelector(ResolutionSelector selector) {
    setProperty(RESOLUTION_TYPE_PROPERTY_NAME, selector.getName());
    setProperty(CUSTOM_RESOLUTION_PROPERTY_NAME, selector.getCustomResolution());
  }

  public boolean getResumeVideoStatus() {
    return this.getPropertyAsBoolean(RESUME_DOWNLOAD_PROPERTY_NAME);
  }

  public void setResumeVideoStatus(boolean res) {
    this.setProperty(RESUME_DOWNLOAD_PROPERTY_NAME, res);
  }

  // implemented for backwards compatibility
  @Override
  public CookieManager getCookieManager() {
    CookieManager ret = (CookieManager) getProperty(COOKIE_MANAGER).getObjectValue();
    return ret != null ? ret : super.getCookieManager();
  }

  // implemented for backwards compatibility
  @Override
  public HeaderManager getHeaderManager() {
    HeaderManager ret = (HeaderManager) getProperty(HEADER_MANAGER).getObjectValue();
    return ret != null ? ret : super.getHeaderManager();
  }

  // implemented for backwards compatibility
  @Override
  public CacheManager getCacheManager() {
    CacheManager ret = (CacheManager) getProperty(CACHE_MANAGER).getObjectValue();
    return ret != null ? ret : super.getCacheManager();
  }

  @Override
  public SampleResult sample() {

    if (!this.getResumeVideoStatus()) {
      lastVideoSegmentNumber = -1;
      lastAudioSegmentNumber = -1;
      lastSubtitleSegmentNumber = -1;
    }

    if (notifyFirstSampleAfterLoopRestart) {
      httpClient.notifyFirstSampleAfterLoopRestart();
      notifyFirstSampleAfterLoopRestart = false;
    }

    String url = getMasterUrl();

    if (!url.contains(".mpd")) {
      return parseHlsProtocol(url);
    } else {
      return parseDashProtocol(url);
    }
  }

  @Override
  protected HTTPSampleResult sample(URL url, String method, boolean areFollowingRedirect,
      int frameDepth) {
    return httpClient.sample(url, method, areFollowingRedirect, frameDepth);
  }

  private SampleResult parseHlsProtocol(String url) {
    try {
      URI masterUri = URI.create(url);
      Playlist masterPlaylist = downloadMasterPlaylist(masterUri);

      Playlist mediaPlaylist;
      Playlist audioPlaylist = null;
      Playlist subtitlesPlaylist = null;

      if (masterPlaylist.isMasterPlaylist()) {

        MediaStream mediaStream = masterPlaylist
            .solveMediaStream(getBandwidthSelector(), getResolutionSelector(),
                getAudioLanguage(), getSubtitleLanguage());
        if (mediaStream == null) {
          processSampleResult(buildPlaylistName(MEDIA_TYPE_NAME),
              buildNotMatchingMediaPlaylistResult());
          return null;
        }

        mediaPlaylist = downloadPlaylist(mediaStream.getMediaPlaylistUri(), MEDIA_TYPE_NAME);
        audioPlaylist = tryDownloadPlaylist(mediaStream.getAudioUri(),
            p -> buildPlaylistName(AUDIO_TYPE_NAME));
        subtitlesPlaylist = tryDownloadPlaylist(mediaStream.getSubtitlesUri(),
            p -> p != null ? buildPlaylistName(SUBTITLES_TYPE_NAME) : SUBTITLES_TYPE_NAME);
      } else {
        mediaPlaylist = masterPlaylist;
      }

      int playSeconds = getPlaySecondsOrWarn();

      MediaPlayback mediaPlayback = new MediaPlayback(mediaPlaylist, lastVideoSegmentNumber,
          playSeconds, MEDIA_TYPE_NAME);
      MediaPlayback audioPlayback = new MediaPlayback(audioPlaylist, lastAudioSegmentNumber,
          playSeconds, AUDIO_TYPE_NAME);
      MediaPlayback subtitlesPlayback = new MediaPlayback(subtitlesPlaylist,
          lastSubtitleSegmentNumber, playSeconds, SUBTITLES_TYPE_NAME);

      try {
        while (!mediaPlayback.hasEnded()) {
          mediaPlayback.downloadNextSegment();
          float playedSeconds = mediaPlayback.playedTimeSeconds();
          if (playSeconds > 0 && playSeconds < playedSeconds) {
            playedSeconds = playSeconds;
          }
          audioPlayback.downloadUntilTimeSecond(playedSeconds);
          subtitlesPlayback.downloadUntilTimeSecond(playedSeconds);
        }
      } finally {
        lastVideoSegmentNumber = mediaPlayback.lastSegmentNumber;
        lastSubtitleSegmentNumber = subtitlesPlayback.lastSegmentNumber;
        lastAudioSegmentNumber = audioPlayback.lastSegmentNumber;
      }
    } catch (SamplerInterruptedException e) {
      LOG.debug("Sampler interrupted by JMeter", e);
    } catch (InterruptedException e) {
      LOG.warn("Sampler has been interrupted", e);
      Thread.currentThread().interrupt();
    } catch (PlaylistDownloadException | PlaylistParsingException e) {
      LOG.warn("Problem downloading playlist", e);
    }
    return null;
  }

  private SampleResult parseDashProtocol(String url) {
    try {
      DashPlaylist mediaPlaylist = downloadManifest(url);

      if (mediaPlaylist.getManifest() != null) {
        DashPlaylist audioPlaylist = DashPlaylist
            .fromUriAndManifest(AUDIO_TYPE_NAME, mediaPlaylist.getManifest(), url,
                getAudioLanguage());
        DashPlaylist subtitlesPlaylist = DashPlaylist
            .fromUriAndManifest(SUBTITLES_TYPE_NAME, mediaPlaylist.getManifest(), url,
                getSubtitleLanguage());

        MediaRepresentation mediaRepresentation = mediaPlaylist
            .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector());
        MediaRepresentation audioRepresentation = audioPlaylist
            .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector());
        MediaRepresentation subtitlesRepresentation = subtitlesPlaylist
            .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector());

        int playSeconds = getPlaySecondsOrWarn();

        DashMediaPlayback mediaPlayback = new DashMediaPlayback(mediaPlaylist, mediaRepresentation,
            lastVideoSegmentNumber, playSeconds, MEDIA_TYPE_NAME);
        DashMediaPlayback audioPlayback = new DashMediaPlayback(audioPlaylist, audioRepresentation,
            lastAudioSegmentNumber, playSeconds, AUDIO_TYPE_NAME);
        DashMediaPlayback subtitlesPlayback = new DashMediaPlayback(subtitlesPlaylist,
            subtitlesRepresentation, lastSubtitleSegmentNumber, playSeconds, SUBTITLES_TYPE_NAME);

        try {
          while (!mediaPlayback.hasEnded()) {
            mediaPlayback.downloadNextSegment();
            float playedSeconds = mediaPlayback.getPlayedTime();
            if (playSeconds > 0 && playSeconds < playedSeconds) {
              playedSeconds = playSeconds;
            }
            audioPlayback.downloadUntilTimeSecond(playedSeconds);
            subtitlesPlayback.downloadUntilTimeSecond(playedSeconds);
          }
        } finally {
          lastVideoSegmentNumber = mediaPlayback.lastSegmentNumber;
          lastSubtitleSegmentNumber = subtitlesPlayback.lastSegmentNumber;
          lastAudioSegmentNumber = audioPlayback.lastSegmentNumber;
        }
      }
    } catch (IOException | PlaylistDownloadException e) {
      LOG.warn("Problem downloading manifest from {}", url, e);
      Thread.currentThread().interrupt();
    }

    return null;
  }

  private Playlist downloadMasterPlaylist(URI uri)
      throws PlaylistDownloadException, PlaylistParsingException {
    return downloadPlaylist(uri,
        p -> p != null && !p.isMasterPlaylist() ? buildPlaylistName(MEDIA_TYPE_NAME)
            : buildPlaylistName(MASTER_TYPE_NAME));
  }

  private Playlist downloadPlaylist(URI uri, String type)
      throws PlaylistDownloadException, PlaylistParsingException {
    return downloadPlaylist(uri, p -> buildPlaylistName(type));
  }

  private Playlist downloadPlaylist(URI uri, Function<Playlist, String> name)
      throws PlaylistParsingException, PlaylistDownloadException {
    Instant downloadTimestamp = timeMachine.now();
    HTTPSampleResult playlistResult = downloadUri(uri);
    if (!playlistResult.isSuccessful()) {
      String playlistName = name.apply(null);
      processSampleResult(playlistName, playlistResult);
      throw new PlaylistDownloadException(playlistName, uri);
    }

    // we update uri in case the request was redirected
    try {
      uri = playlistResult.getURL().toURI();
    } catch (URISyntaxException e) {
      LOG.warn("Problem updating uri from downloaded playlist {}. Continue with original uri {}",
          playlistResult.getURL(), uri, e);
    }

    if (!uri.toString().contains(".m3u8")) {
      processSampleResult(name.apply(null), playlistResult);
      return null;
    }

    try {
      Playlist playlist = Playlist
          .fromUriAndBody(uri, playlistResult.getResponseDataAsString(), downloadTimestamp);
      processSampleResult(name.apply(playlist), playlistResult);
      return playlist;
    } catch (PlaylistParsingException e) {
      processSampleResult(name.apply(null), errorResult(e, playlistResult));
      throw e;
    }
  }

  private HTTPSampleResult downloadUri(URI uri) {
    if (interrupted) {
      throw new SamplerInterruptedException();
    }
    try {
      return sample(uri.toURL(), "GET", false, 0);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  // exception created to avoid polluting all the logic checking for interrupted flag
  private static class SamplerInterruptedException extends RuntimeException {

  }

  @VisibleForTesting
  public static HTTPSampleResult buildNotMatchingMediaPlaylistResult() {
    HTTPSampleResult res = new HTTPSampleResult();
    res.setResponseCode("Non HTTP response code: NoMatchingMediaPlaylist");
    res.setResponseMessage("Non HTTP response message: No matching media "
        + "playlist for provided resolution and bandwidth");
    res.setSuccessful(false);
    return res;
  }

  @VisibleForTesting
  private static HTTPSampleResult buildErrorWhileDownloadingMediaSegmentResult(String type) {
    HTTPSampleResult res = new HTTPSampleResult();
    res.setResponseCode("Non HTTP response code: ErrorWhileDownloading" + type);
    res.setResponseMessage("Non HTTP response message: There was an error while downloading " + type
        + " segment. Please check the logs for more information");
    res.setSuccessful(false);
    return res;
  }

  private class MediaPlayback extends VideoStreamingPlayback {

    private Playlist playlist;
    private Iterator<MediaSegment> mediaSegments;

    private MediaPlayback(Playlist playlist, long lastSegmentNumber, int playSeconds, String type) {
      super(playSeconds, lastSegmentNumber, type);
      this.playlist = playlist;
      if (playlist != null) {
        updateMediaSegments();
      }
    }

    private void updateMediaSegments() {
      this.mediaSegments = this.playlist.getMediaSegments().stream()
          .filter(s -> s.getSequenceNumber() > lastSegmentNumber).iterator();
    }

    private void downloadNextSegment()
        throws InterruptedException, PlaylistDownloadException, PlaylistParsingException {

      if (!mediaSegments.hasNext()) {
        updatePlaylist();
      }

      if (mediaSegments.hasNext()) {
        MediaSegment segment = mediaSegments.next();
        SampleResult result = downloadUri(segment.getUri());
        result.setResponseHeaders(
            result.getResponseHeaders() + "X-MEDIA-SEGMENT-DURATION: " + segment
                .getDurationSeconds() + "\n");
        processSampleResult(buildSegmentName(type), result);
        lastSegmentNumber = segment.getSequenceNumber();
        consumedSeconds += segment.getDurationSeconds();
      }
    }

    private void updatePlaylist()
        throws InterruptedException, PlaylistDownloadException, PlaylistParsingException {

      timeMachine.awaitMillis(playlist.getReloadTimeMillisForDurationMultiplier(1,
          timeMachine.now()));
      Playlist updatedPlaylist = downloadPlaylist(playlist.getUri(), this.type);

      while (updatedPlaylist.equals(playlist)) {
        long millis = updatedPlaylist
            .getReloadTimeMillisForDurationMultiplier(0.5, timeMachine.now());

        timeMachine.awaitMillis(millis);
        updatedPlaylist = downloadPlaylist(playlist.getUri(), this.type);
      }

      this.playlist = updatedPlaylist;
      updateMediaSegments();
    }

    private boolean hasEnded() {
      return playedRequestedTime() || (!mediaSegments.hasNext() && playlist.hasEnd());
    }

    private void downloadUntilTimeSecond(float untilTimeSecond) throws InterruptedException {
      if (playlist == null) {
        return;
      }

      try {
        while (consumedSeconds <= untilTimeSecond) {
          downloadNextSegment();
        }
      } catch (PlaylistParsingException | PlaylistDownloadException e) {
        LOG.warn("Problem downloading playlist {}", type, e);
      }
    }
  }

  private Playlist tryDownloadPlaylist(URI uri, Function<Playlist, String> namer) {
    try {
      if (uri != null) {
        return downloadPlaylist(uri, namer);
      }
    } catch (PlaylistDownloadException | PlaylistParsingException e) {
      LOG.warn("Problem downloading {}", namer.apply(null), e);
    }
    return null;
  }

  private void processSampleResult(String name, SampleResult result) {
    result.setSampleLabel(getName() + " - " + name);
    JMeterContext threadContext = getThreadContext();
    updateSampleResultThreadsInfo(result, threadContext);
    getThreadContext().setPreviousResult(result);
    SamplePackage pack = (SamplePackage) threadContext.getVariables()
        .getObject(JMeterThread.PACKAGE_OBJECT);
    runPostProcessors(result, pack.getPostProcessors());
    checkAssertions(result, pack.getAssertions());
    threadContext.getVariables()
        .put(JMeterThread.LAST_SAMPLE_OK, Boolean.toString(result.isSuccessful()));
    notifySampleListeners(result, pack.getSampleListeners());
  }

  private void updateSampleResultThreadsInfo(SampleResult result, JMeterContext threadContext) {
    int totalActiveThreads = JMeterContextService.getNumberOfThreads();
    String threadName = threadContext.getThread().getThreadName();
    int activeThreadsInGroup = threadContext.getThreadGroup().getNumberOfThreads();
    result.setAllThreads(totalActiveThreads);
    result.setThreadName(threadName);
    result.setGroupThreads(activeThreadsInGroup);
    SampleResult[] subResults = result.getSubResults();
    if (subResults != null) {
      for (SampleResult subResult : subResults) {
        subResult.setGroupThreads(activeThreadsInGroup);
        subResult.setAllThreads(totalActiveThreads);
        subResult.setThreadName(threadName);
      }
    }
  }

  private void runPostProcessors(SampleResult result, List<PostProcessor> extractors) {
    for (PostProcessor ex : extractors) {
      TestBeanHelper.prepare((TestElement) ex);
      if (doesTestElementApplyToSampleResult(result, (TestElement) ex)) {
        ex.process();
      }
    }
  }

  private void checkAssertions(SampleResult result, List<Assertion> assertions) {
    for (Assertion assertion : assertions) {
      TestElement testElem = (TestElement) assertion;
      TestBeanHelper.prepare(testElem);
      if (doesTestElementApplyToSampleResult(result, testElem)) {
        AssertionResult assertionResult;
        try {
          assertionResult = assertion.getResult(result);
        } catch (AssertionError e) {
          LOG.debug("Error processing Assertion.", e);
          assertionResult = new AssertionResult(
              "Assertion failed! See log file (debug level, only).");
          assertionResult.setFailure(true);
          assertionResult.setFailureMessage(e.toString());
        } catch (JMeterError e) {
          LOG.error("Error processing Assertion.", e);
          assertionResult = new AssertionResult("Assertion failed! See log file.");
          assertionResult.setError(true);
          assertionResult.setFailureMessage(e.toString());
        } catch (Exception e) {
          LOG.error("Exception processing Assertion.", e);
          assertionResult = new AssertionResult("Assertion failed! See log file.");
          assertionResult.setError(true);
          assertionResult.setFailureMessage(e.toString());
        }
        result.setSuccessful(
            result.isSuccessful() && !(assertionResult.isError() || assertionResult.isFailure()));
        result.addAssertionResult(assertionResult);
      }
    }
  }

  private boolean doesTestElementApplyToSampleResult(SampleResult result, TestElement assertion) {
    String assertionType = extractLabelType(assertion.getName());
    String sampleType = extractLabelType(result.getSampleLabel());
    return sampleType.equals(assertionType) || !SAMPLE_TYPE_NAMES.contains(assertionType);
  }

  private String extractLabelType(String label) {
    int typeSeparatorIndex = label.lastIndexOf('-');
    return typeSeparatorIndex >= 0 ? label.substring(typeSeparatorIndex + 1).trim().toLowerCase()
        : "";
  }

  private void notifySampleListeners(SampleResult sampleResult,
      List<SampleListener> sampleListeners) {
    JMeterContext threadContext = getThreadContext();
    SampleEvent event = new SampleEvent(sampleResult, getThreadName(), threadContext.getVariables(),
        false);
    threadContext.getThread().getNotifier().notifyListeners(event, sampleListeners);
  }

  @Override
  public boolean interrupt() {
    interrupted = true;
    timeMachine.interrupt();
    httpClient.interrupt();
    return interrupted;
  }

  @Override
  public void threadFinished() {
    httpClient.threadFinished();
  }

  @Override
  public void testIterationStart(LoopIterationEvent event) {
    notifyFirstSampleAfterLoopRestart = true;
  }

  private DashPlaylist downloadManifest(String url) throws PlaylistDownloadException, IOException {
    URI manifestUri = URI.create(url);
    HTTPSampleResult manifestResult = downloadUri(manifestUri);

    if (!manifestResult.isSuccessful()) {

      processSampleResult("Manifest", manifestResult);
      throw new PlaylistDownloadException("Manifest", manifestUri);
    }

    // we update uri in case the request was redirected
    try {
      manifestUri = manifestResult.getURL().toURI();
    } catch (URISyntaxException e) {
      LOG.warn("Problem updating uri from downloaded manifest {}. Continue with original uri {}",
          manifestResult.getURL(), manifestUri, e);
    }

    try {
      DashPlaylist videoPlaylist = DashPlaylist
          .fromUriAndBody(VIDEO_TYPE_NAME, manifestResult.getResponseDataAsString(), url, null);
      processSampleResult("Manifest", manifestResult);
      return videoPlaylist;
    } catch (IOException e) { //TODO: Change this to "Playlist parsing exception"
      processSampleResult("Manifest", errorResult(e, manifestResult));
      throw e;
    }
  }

  private class DashMediaPlayback extends VideoStreamingPlayback {

    private SegmentTimelinePlayback segmentTimelinePlayback;
    private MediaRepresentation representation;
    private DashPlaylist playlist;
    private int lastSegmentTimelineNumber;
    private long timePassedSinceLastUpdate;

    private DashMediaPlayback(DashPlaylist playlist, MediaRepresentation representation,
        long lastSegmentNumber, int playSeconds, String type) {
      super(playSeconds, lastSegmentNumber, type);
      this.playlist = playlist;
      this.representation = representation;
      this.segmentTimelinePlayback = null;
      this.lastSegmentTimelineNumber = 0;
      this.timePassedSinceLastUpdate = 0;
    }

    private void updateManifest() throws IOException {
      if (playlist.isDynamic() && timePassedSinceLastUpdate >= playlist.getManifest()
          .getTimeShiftBufferDepth()
          .toMillis() && playlist.liveStreamingContinues()) {
        playlist.updateManifestFromBody(
            (downloadUri(URI.create(playlist.getManifestURL()))).getResponseDataAsString());
        representation = playlist
            .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector());
        timePassedSinceLastUpdate = 0;
      }
    }

    private boolean isWholeVideo() {
      return playSeconds == 0;
    }

    private boolean canDownload() {
      return representation != null && (isWholeVideo() || playSeconds > consumedSeconds);
    }

    private void downloadUntilTimeSecond(float untilTimeSecond) throws IOException {
      while (consumedSeconds < untilTimeSecond) {
        downloadNextSegment();
      }
    }

    private void downloadNextSegment() throws IOException {
      if (!canDownload()) {
        return;
      }

      AdaptationSet adaptationSetUsed = representation.getAdaptationSet();
      SegmentTemplate template = adaptationSetUsed.getSegmentTemplate();

      String adaptationBaseURL = (
          adaptationSetUsed.getBaseURLs() != null && adaptationSetUsed.getBaseURLs().size() > 0
              ? adaptationSetUsed.getBaseURLs().get(0).getValue() : "");

      if (lastSegmentNumber < 1) {
        if (template != null && template.getStartNumber() != null) {
          lastSegmentNumber = template.getStartNumber();
        } else {
          lastSegmentNumber = 1;
        }
      }

      if (lastSegmentNumber < 2 && representation.needManualInitialization()) {
        String initializeURL =
            representation.getBaseURL() + adaptationBaseURL + buildFormula(template,
                INITIALIZATION_SEGMENT);
        LOG.info("Downloading initialization for type {} from url {}", type, initializeURL);

        HTTPSampleResult initializeResult = downloadUri(URI.create(initializeURL));
        if (!initializeResult.isSuccessful()) {
          SampleResult failResult = buildNotMatchingMediaPlaylistResult();
          processSampleResult("Init " + type, failResult);
        } else {
          processSampleResult("Init " + type, initializeResult);
        }
      }

      String segmentURL =
          representation.getBaseURL() + adaptationBaseURL + buildFormula(template, "Media");
      LOG.info("Downloading {}", segmentURL);

      HTTPSampleResult downloadSegmentResult = downloadUri(
          URI.create(segmentURL));
      if (!downloadSegmentResult.isSuccessful()) {
        HTTPSampleResult failDownloadResult = buildErrorWhileDownloadingMediaSegmentResult(type);
        processSampleResult(type + " segment", failDownloadResult);
        LOG.warn("There was an error while downloading {} segment from {}. Code: {}. Message: {}",
            type,
            segmentURL, downloadSegmentResult.getResponseCode(),
            downloadSegmentResult.getResponseMessage());
      } else {
        processSampleResult(type + " segment", downloadSegmentResult);
      }

      lastSegmentNumber++;
      if (isLiveStreamSet() && template != null) {
        consumedSeconds = segmentTimelinePlayback.getTotalDuration() / template.getTimescale();
      } else if (representation.isOneDownloadOnly()) {
        consumedSeconds += representation.getTotalDuration();
      } else {
        consumedSeconds++;
      }
      LOG.info("Consumed seconds for {} updated to {}", type, consumedSeconds);

      updatePeriod();
      updateManifest();
    }

    private String buildFormula(SegmentTemplate template, String formulaType) {
      /*
       * There is some cases where the adaptation's BaseURL doesn't need a formula.
       * In those cases SegmentTemplate don't appear.
       */
      if (template == null) {
        return "";
      }

      String formula = (formulaType.equals(INITIALIZATION_SEGMENT) ? template.getInitialization()
          : template.getMedia());

      formula = formula
          .replace(REPRESENTATIONID_FORMULA_REPLACE, representation.getRepresentation().getId());
      formula = formula.replace(BANDWIDTH_FORMULA_REPLACE,
          Long.toString(representation.getRepresentation().getBandwidth()));

      Pattern pattern = Pattern.compile(NUMBER_FORMULA_PATTERN);
      Matcher matcher = pattern.matcher(formula);
      if (matcher.find()) {
        String lastSegmentFormatted = String.format(matcher.group(1), lastSegmentNumber);
        formula = formula.replaceAll("(?<=\\$)(.*?)(?=\\$)", lastSegmentFormatted).replace("$", "");
      }

      if (formula.contains(TIME_FORMULA_REPLACE)) {
        if (segmentTimelinePlayback == null) {
          Segment segment = template.getSegmentTimeline().get(lastSegmentTimelineNumber);
          segmentTimelinePlayback = new SegmentTimelinePlayback(segment.getT(), segment.getD(),
              (segment.getR() != null ? segment.getR() : 1));
        }

        formula = formula
            .replace(TIME_FORMULA_REPLACE,
                Long.toString(segmentTimelinePlayback.getNextTimeline()));
        segmentTimelinePlayback.updateSegmentTimelinePlayback(template);
      }

      return formula;
    }

    private boolean isLiveStreamSet() {
      return segmentTimelinePlayback != null;
    }

    private void updatePeriod() {
      if (hasReachedEnd() && !playedRequestedTime()) {
        playlist.updatePeriod();
        this.setRepresentation(playlist
            .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector()));
      }
    }

    private boolean hasEnded() {
      return (representation == null || playedRequestedTime() || hasReachedEnd());
    }

    private boolean hasReachedEnd() {
      return playlist.getActualPeriodIndex() == -1;
    }

    public MediaRepresentation getRepresentation() {
      return representation;
    }

    public void setRepresentation(MediaRepresentation representation) {
      this.representation = representation;
    }

    private class SegmentTimelinePlayback {

      private long time;
      private long duration;
      private long repeat;
      private long totalDuration;

      private SegmentTimelinePlayback(long time, long duration, long repeat) {
        this.time = time;
        this.duration = duration;
        this.repeat = repeat;
        this.totalDuration = 0;
      }

      public long getTime() {
        return time;
      }

      public void setTime(long time) {
        this.time = time;
      }

      public long getDuration() {
        return duration;
      }

      public void setDuration(long duration) {
        this.duration = duration;
      }

      public long getRepeat() {
        return repeat;
      }

      private long getTotalDuration() {
        return totalDuration;
      }

      public void setRepeat(long repeat) {
        this.repeat = repeat;
      }

      private long getNextTimeline() {
        long nextTimeline = time + duration;
        time = nextTimeline;
        totalDuration += duration;
        repeat--;
        return nextTimeline;
      }

      private void updateSegmentTimelinePlayback(SegmentTemplate template) {
        if (repeat <= 0) {
          lastSegmentTimelineNumber++;
          Segment segment = template.getSegmentTimeline().get(lastSegmentTimelineNumber);
          time = segment.getT() != null ? segment.getT() : time;
          repeat = segment.getR() != null ? segment.getR() : 1;
          duration = segment.getD();
        }
      }
    }

  }

  private int getPlaySecondsOrWarn() {
    int playSeconds = 0;
    if (isPlayVideoDuration() && !getPlaySeconds().isEmpty()) {
      playSeconds = Integer.parseInt(getPlaySeconds());
      if (playSeconds <= 0) {
        LOG.warn("Provided play seconds ({}) is less than or equal to zero. The sampler will "
            + "reproduce the whole video", playSeconds);
      }
    }
    return playSeconds;
  }
}

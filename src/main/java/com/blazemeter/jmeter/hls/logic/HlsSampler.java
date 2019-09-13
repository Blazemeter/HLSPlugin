package com.blazemeter.jmeter.hls.logic;

import com.google.common.annotations.VisibleForTesting;
import io.lindstrom.mpd.data.AdaptationSet;
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
    setName("HLS Sampler");
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
        .fromStringAndCustomBandwidth(getPropertyAsString(BANDWIDTH_TYPE_PROPERTY_NAME),
            bandwidth != null && !bandwidth.isEmpty() ? Long.valueOf(bandwidth) : null);
  }

  public void setBandwidthSelector(BandwidthSelector selector) {
    setProperty(BANDWIDTH_TYPE_PROPERTY_NAME, selector.getName());
    Long bandwidth = selector.getCustomBandwidth();
    setProperty(CUSTOM_BANDWIDTH_PROPERTY_NAME, bandwidth != null ? bandwidth.toString() : null);
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

        int playSeconds = 0;
        if (isPlayVideoDuration() && !getPlaySeconds().isEmpty()) {
          playSeconds = Integer.parseInt(getPlaySeconds());
          if (playSeconds <= 0) {
            LOG.warn("Provided play seconds ({}) is less than or equal to zero. The sampler will "
                + "reproduce the whole video", playSeconds);
          }
        }

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
    } else {
      try {
        DashPlaylist mediaPlaylist = downloadManifest(url);

        if (!interrupted && mediaPlaylist.getManifest() != null) {
          DashPlaylist audioPlaylist = new DashPlaylist(AUDIO_TYPE_NAME,
              mediaPlaylist.getManifest(),
              timeMachine.now(), url);
          DashPlaylist subtitlesPlaylist = new DashPlaylist(SUBTITLES_TYPE_NAME,
              mediaPlaylist.getManifest(),
              timeMachine.now(), url);

          MediaRepresentation mediaRepresentation = mediaPlaylist
              .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector(), null);
          MediaRepresentation audioRepresentation = audioPlaylist
              .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector(),
                  getAudioLanguage());
          MediaRepresentation subtitlesRepresentation = subtitlesPlaylist
              .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector(),
                  getSubtitleLanguage());

          if (!interrupted
              && ((mediaRepresentation != null && mediaRepresentation.exists())
              || (audioRepresentation != null && audioRepresentation.exists())
              || (audioRepresentation != null && subtitlesRepresentation.exists()))
          ) {
            int playSeconds = getPlaySecondsOrWarn();

            DashMediaPlayback mediaPlayback = new DashMediaPlayback(mediaPlaylist,
                mediaRepresentation,
                lastVideoSegmentNumber, playSeconds, MEDIA_TYPE_NAME, null);
            DashMediaPlayback audioPlayback = new DashMediaPlayback(audioPlaylist,
                audioRepresentation,
                lastAudioSegmentNumber, playSeconds, AUDIO_TYPE_NAME, getAudioLanguage());
            DashMediaPlayback subtitlesPlayback = new DashMediaPlayback(subtitlesPlaylist,
                subtitlesRepresentation,
                lastSubtitleSegmentNumber, playSeconds, SUBTITLES_TYPE_NAME, getSubtitleLanguage());

            while (!interrupted && (!mediaPlayback.hasEnded() || !audioPlayback.hasEnded()
                || !subtitlesPlayback.hasEnded())) {

              if (mediaPlayback.canDownload()) {
                mediaPlayback.downloadNextSegment();
                mediaPlayback.updatePeriod();
              }

              if (audioPlayback.canDownload()) {
                audioPlayback.downloadNextSegment();
                audioPlayback.updatePeriod();
              }

              if (subtitlesPlayback.canDownload()) {
                subtitlesPlayback.downloadNextSegment();
                subtitlesPlayback.updatePeriod();
              }
            }
            lastVideoSegmentNumber = mediaPlayback.lastSegmentNumber;
            lastSubtitleSegmentNumber = subtitlesPlayback.lastSegmentNumber;
            lastAudioSegmentNumber = audioPlayback.lastSegmentNumber;
          }
        }
      } catch (IOException | PlaylistDownloadException e) {
        LOG.warn("Problem downloading manifest from {}", url, e);
        Thread.currentThread().interrupt();
      }
    }
    return null;
  }

  @Override
  protected HTTPSampleResult sample(URL url, String method, boolean areFollowingRedirect,
      int frameDepth) {
    return httpClient.sample(url, method, areFollowingRedirect, frameDepth);
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

  private Playlist downloadPlaylist(URI uri, Function<Playlist, String> namer)
      throws PlaylistParsingException, PlaylistDownloadException {
    Instant downloadTimestamp = timeMachine.now();
    HTTPSampleResult playlistResult = downloadUri(uri);
    if (!playlistResult.isSuccessful()) {
      String playlistName = namer.apply(null);
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
      processSampleResult(namer.apply(null), playlistResult);
      return null;
    }

    try {
      Playlist playlist = Playlist
          .fromUriAndBody(uri, playlistResult.getResponseDataAsString(), downloadTimestamp);
      processSampleResult(namer.apply(playlist), playlistResult);
      return playlist;
    } catch (PlaylistParsingException e) {
      processSampleResult(namer.apply(null), errorResult(e, playlistResult));
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

  private class MediaPlayback {

    private Playlist playlist;
    private final int playSeconds;
    private float consumedSeconds;
    private long lastSegmentNumber;
    private Iterator<MediaSegment> mediaSegments;
    private final String type;

    private MediaPlayback(Playlist playlist, long lastSegmentNumber, int playSeconds, String type) {
      this.playlist = playlist;
      this.lastSegmentNumber = lastSegmentNumber;
      this.playSeconds = playSeconds;
      this.type = type;

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

    private boolean playedRequestedTime() {
      return playSeconds > 0 && playSeconds <= this.consumedSeconds;
    }

    private float playedTimeSeconds() {
      return this.consumedSeconds;
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
    Instant downloadTimestamp = timeMachine.now();

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
          .fromUriAndManifest(VIDEO_TYPE_NAME, manifestResult.getResponseDataAsString(),
              downloadTimestamp, url);
      processSampleResult("Manifest", manifestResult);
      return videoPlaylist;
    } catch (IOException e) { //TODO: Change this to "Playlist parsing exception"
      processSampleResult("Manifest", errorResult(e, manifestResult));
      throw e;
    }
  }

  private class DashMediaPlayback {
    private MediaRepresentation representation;
    private DashPlaylist playlist;
    private long lastSegmentNumber;
    private float consumedSeconds;
    private int playSeconds;
    private String selector;
    private String type;

    private DashMediaPlayback(DashPlaylist playlist, MediaRepresentation representation,
        long lastSegmentNumber,
        int playSeconds, String type, String selector) {
      this.playlist = playlist;
      this.representation = representation;
      this.lastSegmentNumber = lastSegmentNumber;
      this.playSeconds = playSeconds;
      this.consumedSeconds = 0f;
      this.type = type;
      this.selector = selector;
    }

    private boolean canDownload() {
      return representation != null;
    }

    /*
     * TODO: DELETEME (This is just to avoid going to the document)
     * The formula fits the format described in Table 16 — Identifiers for URL templates
     * $RepresentationID$ : This identifier is substituted with the value of the attribute
     *  Representation@id of the containing Representation.
     * $Number$ : This identifier is substituted with the number of the corresponding Segment.
     * $Bandwidth$ : This identifier is substituted with the value of Representation@bandwidth
     *  attribute value.
     * $Time$ : This identifier is substituted with the value of the SegmentTimeline@t attribute
     *  for the Segment being accessed. Either $Number$ or $Time$ may be used but not both at
     * the same time.
     * */

    private void downloadNextSegment() {

      AdaptationSet adaptationSetUsed = representation.getAdaptationSet();
      SegmentTemplate template = adaptationSetUsed.getSegmentTemplate();
      String adaptationBaseURL = adaptationSetUsed.getBaseURLs().get(0).getValue();

      if (lastSegmentNumber < 1) {
        if (template.getStartNumber() != null) {
          lastSegmentNumber = template.getStartNumber();
        } else {
          lastSegmentNumber = 1;
        }
      }

      if (lastSegmentNumber < 2) {
        String initializeURL =
            representation.getBaseURL() + adaptationBaseURL + buildMediaFormula(template
                .getInitialization());
        LOG.info("Downloading {}", initializeURL);
        HTTPSampleResult initializeResult = downloadUri(URI.create(initializeURL));
        if (!initializeResult.isSuccessful()) {
          SampleResult failInitresult = buildNotMatchingMediaPlaylistResult();
          processSampleResult("Init " + type, failInitresult);
          //TODO: Create/Throw exception here
        } else {
          processSampleResult("Init " + type, initializeResult);
        }
      }

      String segmentURL =
          representation.getBaseURL() + adaptationBaseURL + buildMediaFormula(template.getMedia());
      LOG.info("Downloading {}", segmentURL);

      HTTPSampleResult downloadSegmentResult = downloadUri(URI.create(segmentURL));
      if (!downloadSegmentResult.isSuccessful()) {
        processSampleResult(type + " segment", downloadSegmentResult);
      } else {
        processSampleResult(type + " segment", downloadSegmentResult);
      }

      lastSegmentNumber++;
      if (template.getTimescale() != null && template.getTimescale() != 0) {
        consumedSeconds =
            (float) (template.getDuration() / template.getTimescale()) * lastSegmentNumber;
      } else {
        consumedSeconds++;
      }
      LOG.info("Consumed seconds for {} updated to {}", type, consumedSeconds);
    }

    private String buildMediaFormula(String formula) {

      //TODO: Review the case when multiple SegmentTimeline and how to match them
      //formula = formula.replace("$Time$", Long.toString(representation.getAdaptationSet()
      // .getSegmentTemplate().getSegmentTimeline().get(0).getT()))

      formula = formula
          .replace("$RepresentationID$", representation.getRepresentation().getId());
      formula = formula.replace("$Bandwidth$",
          Long.toString(representation.getRepresentation().getBandwidth()));

      Pattern pattern = Pattern.compile("(?<=\\$Number)(.*?)(?=\\$)");
      Matcher matcher = pattern.matcher(formula);
      if (matcher.find()) {
        String lastSegmentFormatted = String.format(matcher.group(1), lastSegmentNumber);
        formula = formula.replaceAll("(?<=\\$)(.*?)(?=\\$)", lastSegmentFormatted).replace("$", "");
      }

      return formula;
    }

    private void updatePeriod() {
      if (hasReachedEnd() && !playedRequestedTime()) {
        playlist.updatePeriod();
        this.setRepresentation(playlist
            .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector(), selector));
      }
    }

    private boolean hasEnded() {
      return (representation == null || playedRequestedTime() || hasReachedEnd());
    }

    private boolean playedRequestedTime() {
      return playSeconds > 0 && playSeconds <= this.consumedSeconds;
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

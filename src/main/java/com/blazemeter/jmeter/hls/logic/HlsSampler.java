package com.blazemeter.jmeter.hls.logic;

import io.lindstrom.mpd.MPDParser;
import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.MPD;
import io.lindstrom.mpd.data.Representation;
import io.lindstrom.mpd.data.SegmentTemplate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.protocol.http.control.CacheManager;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPHC4Impl;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.threads.SamplePackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HlsSampler extends HTTPSamplerBase implements Interruptible {

  private static final Logger LOG = LoggerFactory.getLogger(HlsSampler.class);

  private static final String MASTER_URL_PROPERTY_NAME = "HLS.URL_DATA";
  private static final String CUSTOM_RESOLUTION_PROPERTY_NAME = "HLS.RES_DATA";
  private static final String CUSTOM_BANDWIDTH_PROPERTY_NAME = "HLS.NET_DATA";
  private static final String PLAY_SECONDS_PROPERTY_NAME = "HLS.SECONDS_DATA";
  private static final String PLAY_VIDEO_DURATION_PROPERTY_NAME = "HLS.DURATION";
  private static final String RESOLUTION_TYPE_PROPERTY_NAME = "HLS.RESOLUTION_TYPE";
  private static final String BANDWIDTH_TYPE_PROPERTY_NAME = "HLS.BANDWIDTH_TYPE";
  private static final String RESUME_DOWNLOAD_PROPERTY_NAME = "HLS.RESUME_DOWNLOAD";
  private static final String AUDIO_LANGUAGE_PROPERTY_NAME = "HLS.AUDIO_LANGUAGE";
  private static final String SUBTITLE_LANGUAGE_PROPERTY_NAME = "HLS.SUBTITLE_LANGUAGE";

  private static final String HEADER_MANAGER = "HLSRequest.header_manager";
  private static final String COOKIE_MANAGER = "HLSRequest.cookie_manager";
  private static final String CACHE_MANAGER = "HLSRequest.cache_manager";
  private static final String MEDIA_PLAYLIST_NAME = "media playlist";
  private static final String MASTER_PLAYLIST_NAME = "master playlist";
  private static final String AUDIO_PLAYLIST_NAME = "audio playlist";
  private static final String SUBTITLES_TYPE_NAME = "subtitles";
  private static final String MEDIA_TYPE_NAME = "media";
  private static final String AUDIO_TYPE_NAME = "audio";
  public static final String VIDEO_TYPE_NAME = "video";
  public static final String SUBTITLE = "subtitle";
  public static final String AUDIO = "audio";

  private final transient Function<URI, HTTPSampleResult> uriSampler;
  private final transient Consumer<SampleResult> sampleResultNotifier;
  private final transient TimeMachine timeMachine;

  private transient long lastVideoSegmentNumber = -1;
  private transient long lastAudioSegmentNumber = -1;
  private transient long lastSubtitleSegmentNumber = -1;

  private transient HlsHttpClient httpClient;
  private transient volatile boolean notifyFirstSampleAfterLoopRestart;

  private transient volatile boolean interrupted = false;
  private String baseURL;

  /*
  we use this class to be able to access some methods on super class and because we can't extend
  HTTPProxySampler class
   */
  private static class HlsHttpClient extends HTTPHC4Impl {

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
    uriSampler = this::downloadUri;
    sampleResultNotifier = this::notifySampleListeners;
    timeMachine = TimeMachine.SYSTEM;
  }

  public HlsSampler(Function<URI, HTTPSampleResult> uriSampler,
      Consumer<SampleResult> sampleResultNotifier,
      TimeMachine timeMachine) {
    initHttpSampler();
    this.uriSampler = uriSampler;
    this.sampleResultNotifier = sampleResultNotifier;
    this.timeMachine = timeMachine;
  }

  private void initHttpSampler() {
    setName("HLS Sampler");
    setFollowRedirects(true);
    setUseKeepAlive(true);
    httpClient = new HlsHttpClient(this);
  }

  private HTTPSampleResult downloadUri(URI uri) {
    try {
      return sample(uri.toURL(), "GET", false, 0);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void notifySampleListeners(SampleResult sampleResult) {
    JMeterContext threadContext = getThreadContext();
    JMeterVariables threadContextVariables = threadContext.getVariables();
    if (threadContextVariables != null) {
      SamplePackage pack = (SamplePackage) threadContext.getVariables()
          .getObject(JMeterThread.PACKAGE_OBJECT);
      SampleEvent event = new SampleEvent(sampleResult, getThreadName(),
          threadContext.getVariables(), false);
      pack.getSampleListeners().forEach(l -> l.sampleOccurred(event));
    }
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

  public boolean getResumeVideoStatus() {
    return this.getPropertyAsBoolean(RESUME_DOWNLOAD_PROPERTY_NAME);
  }

  public void setResumeVideoStatus(boolean res) {
    this.setProperty(RESUME_DOWNLOAD_PROPERTY_NAME, res);
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

    if (url.contains(".mpd")) {
      try {
        MPD manifest = downloadManifest(url);

        if (!interrupted && manifest != null) {
          try {
            if (manifest.getBaseURLs() == null || manifest.getBaseURLs().size() < 1) {
              int lastIndex = url.lastIndexOf("/");
              baseURL = url.substring(0, lastIndex + 1);
              LOG.info("Base URL not found, using {} instead", baseURL);
            } else {
              baseURL = manifest.getBaseURLs().get(0).getValue();
            }

            DashPlaylist videoPlaylist = new DashPlaylist(VIDEO_TYPE_NAME, manifest,
                timeMachine.now());
            DashPlaylist audioPlaylist = new DashPlaylist(AUDIO, manifest, timeMachine.now());
            DashPlaylist subtitlePlaylist = new DashPlaylist(SUBTITLE, manifest, timeMachine.now());

            MediaRepresentation videoRepresentation = videoPlaylist
                .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector(), null);
            MediaRepresentation audioRepresentation = audioPlaylist
                .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector(),
                    getAudioLanguage());
            MediaRepresentation subtitleRepresentation = subtitlePlaylist
                .solveMediaRepresentation(getResolutionSelector(), getBandwidthSelector(),
                    getSubtitleLanguage());

            if (!interrupted && (
                (videoRepresentation != null && videoRepresentation.exists()) ||
                    (audioRepresentation != null && audioRepresentation.exists()) ||
                    (audioRepresentation != null && subtitleRepresentation.exists()))
            ) {
              int playSeconds = getPlaySecondsOrWarn();

              DashMediaPlayback videoPlayback = new DashMediaPlayback(videoRepresentation,
                  lastVideoSegmentNumber, playSeconds, "video");
              DashMediaPlayback audioPlayback = new DashMediaPlayback(audioRepresentation,
                  lastAudioSegmentNumber, playSeconds, "audio");
              DashMediaPlayback subtitlePlayback = new DashMediaPlayback(subtitleRepresentation,
                  lastSubtitleSegmentNumber, playSeconds, "subtitle");

              while (!interrupted && (!videoPlayback.hasEnded() || !audioPlayback.hasEnded()
                  || !subtitlePlayback.hasEnded())) {

                if (videoPlayback.canDownload()) {
                  videoPlayback.downloadNextSegment();
                }

                if (audioPlayback.canDownload()) {
                  audioPlayback.downloadNextSegment();
                }

                if (subtitlePlayback.canDownload()) {
                  subtitlePlayback.downloadNextSegment();
                }
              }
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      try {
        URI masterUri = URI.create(url);
        Playlist masterPlaylist = downloadPlaylist(null, masterUri);

        Playlist mediaPlaylist;
        Playlist audioPlaylist = null;
        Playlist subtitlesPlaylist = null;

        if (!interrupted && masterPlaylist.isMasterPlaylist()) {

          MediaStream mediaStream = masterPlaylist
              .solveMediaStream(getResolutionSelector(), getBandwidthSelector(),
                  getAudioLanguage(), getSubtitleLanguage());
          if (mediaStream == null) {
            return buildNotMatchingMediaPlaylistResult();
          }

          URI mediaPlaylistUri = mediaStream.getMediaPlaylistUri();

          mediaPlaylist = downloadPlaylist(MEDIA_PLAYLIST_NAME, mediaPlaylistUri);

          try {
            if (!interrupted && mediaStream.getAudioUri() != null) {
              audioPlaylist = downloadPlaylist(AUDIO_PLAYLIST_NAME, mediaStream.getAudioUri());
            }
          } catch (PlaylistDownloadException | PlaylistParsingException e) {
            LOG.warn("Problem downloading audio playlist", e);
          }

          if (!interrupted && mediaStream.getSubtitlesUri() != null) {
            subtitlesPlaylist = downloadSubtitles(mediaStream.getSubtitlesUri());
          }
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
          while (!interrupted && !mediaPlayback.hasEnded()) {
            mediaPlayback.downloadNextSegment();
            if (interrupted) {
              break;
            }

            audioPlayback.downloadUntilTimeSecond(mediaPlayback.playedTimeSeconds());

            if (interrupted) {
              break;
            }
            subtitlesPlayback.downloadUntilTimeSecond(mediaPlayback.playedTimeSeconds());
          }
        } finally {
          lastVideoSegmentNumber = mediaPlayback.lastSegmentNumber;
          lastSubtitleSegmentNumber = subtitlesPlayback.lastSegmentNumber;
          lastAudioSegmentNumber = audioPlayback.lastSegmentNumber;
        }

      } catch (InterruptedException e) {
        LOG.warn("Sampler has been interrupted", e);
        Thread.currentThread().interrupt();
      } catch (PlaylistDownloadException | PlaylistParsingException e) {
        LOG.warn("Problem downloading playlist", e);
      }
    }

    return null;
  }

  private MPD downloadManifest(String url) throws Exception {
    URI manifestUri = URI.create(url);
    HTTPSampleResult result = uriSampler.apply(manifestUri);

    if (!result.isSuccessful()) {
      throw new Exception("Need to create the Error Parsing Manifest Exception");
    }

    // we update uri in case the request was redirected
    try {
      manifestUri = result.getURL().toURI();
    } catch (URISyntaxException e) {
      LOG.warn("Problem updating uri from downloaded manifest {}. Continue with original uri {}",
          result.getURL(), manifestUri, e);
    }

    try {
      MPD manifest = new MPDParser().parse(result.getResponseDataAsString());
      notifySampleResult("Manifest", result);
      return manifest;
    } catch (IOException e) {
      LOG.error("Error parsing the manifest from url {}. Error log: {}", url, e.getMessage());
    }

    return null;
  }

  private SampleResult buildErrorParsingManifestResult(String url) {
    SampleResult res = new SampleResult();
    res.setSampleLabel(getName() + " - Manifest" );
    res.setResponseCode("Non HTTP response code: ProblemParsingManifest");
    res.setResponseMessage("Non HTTP response message: Invalid Manifest at url "+url);
    res.setSuccessful(false);
    return res;

  }

  public class DashMediaPlayback {

    private MediaRepresentation representation;
    private long lastSegmentNumber;
    private int playSeconds;
    private float consumedSeconds;
    private String type;

    private DashMediaPlayback(MediaRepresentation representation, long lastSegmentNumber,
        int playSeconds, String type) {
      this.representation = representation;
      this.lastSegmentNumber = lastSegmentNumber;
      this.playSeconds = playSeconds;
      this.consumedSeconds = 0f;
      this.type = type;
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
     *  for the Segment being accessed. Either $Number$ or $Time$ may be used but not both at the same time.
     * */

    private void downloadNextSegment() {

      AdaptationSet adaptationSetUsed = representation.getAdaptationSetUsed();
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
        String initializeURL = baseURL + adaptationBaseURL + buildMediaFormula(template
            .getInitialization());
        LOG.info("Downloading {}", initializeURL);
        HTTPSampleResult initializeResult = uriSampler.apply(URI.create(initializeURL));
        if (!initializeResult.isSuccessful()) {
          SampleResult failInitresult = buildNotMatchingMediaPlaylistResult();
          notifySampleResult("Init "+type, failInitresult);
          //TODO: Create/Throw exception here
        }
        else {
          notifySampleResult("Init "+type, initializeResult);
        }
      }

      String segmentURL =
          baseURL + adaptationBaseURL + buildMediaFormula(template.getMedia());
      LOG.info("Downloading {}", segmentURL);

      HTTPSampleResult downloadSegmentResult = uriSampler.apply(URI.create(segmentURL));
      if (!downloadSegmentResult.isSuccessful()) {
        notifySampleResult(type + " segment", downloadSegmentResult);
      }
      else {
        notifySampleResult(type + " segment", downloadSegmentResult);
      }

      long secondsPassed = 1;
      if (template.getTimescale() != null && template.getTimescale() != 0) {
        secondsPassed = template.getTimescale() / 1000;
      }

      consumedSeconds += secondsPassed;
      lastSegmentNumber++;
    }

    private String buildMediaFormula(String formula) {

      //TODO: Review the case when multiple SegmentTimeline and how to match them
      //formula = formula.replace("$Time$", Long.toString(representation.getAdaptationSetUsed().getSegmentTemplate().getSegmentTimeline().get(0).getT()))

      formula = formula
          .replace("$RepresentationID$", representation.getSelectedRepresentation().getId());
      formula = formula.replace("$Bandwidth$",
          Long.toString(representation.getSelectedRepresentation().getBandwidth()));

      Pattern pattern = Pattern.compile("(?<=\\$Number)(.*?)(?=\\$)");
      Matcher matcher = pattern.matcher(formula);
      if (matcher.find()) {
        String lastSegmentFormatted = String.format(matcher.group(1), lastSegmentNumber);
        formula = formula.replaceAll("(?<=\\$)(.*?)(?=\\$)", lastSegmentFormatted).replace("$", "");
      }

      return formula;
    }

    private boolean hasEnded() {
      return (representation == null || playedRequestedTime() || hasReachedEnd());
    }

    private boolean playedRequestedTime() {
      return playSeconds > 0 && playSeconds <= this.consumedSeconds;
    }

    private boolean hasReachedEnd() {
      return lastSegmentNumber >= representation.getAdaptationSetUsed().getSegmentTemplate()
          .getDuration();
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

  @Override
  protected HTTPSampleResult sample(URL url, String method, boolean areFollowingRedirect,
      int frameDepth) {
    return httpClient.sample(url, method, areFollowingRedirect, frameDepth);
  }

  private Playlist downloadPlaylist(String playlistName, URI uri)
      throws PlaylistParsingException, PlaylistDownloadException {
    Instant downloadTimestamp = timeMachine.now();
    HTTPSampleResult playlistResult = uriSampler.apply(uri);
    if (!playlistResult.isSuccessful()) {

      if (playlistName == null) {
        playlistName = MASTER_PLAYLIST_NAME;
      }

      notifySampleResult(playlistName, playlistResult);
      throw new PlaylistDownloadException(playlistName, uri);
    }

    // we update uri in case the request was redirected
    try {
      uri = playlistResult.getURL().toURI();
    } catch (URISyntaxException e) {
      LOG.warn("Problem updating uri from downloaded playlist {}. Continue with original uri {}",
          playlistResult.getURL(), uri, e);
    }

    try {
      Playlist playlist = Playlist
          .fromUriAndBody(uri, playlistResult.getResponseDataAsString(), downloadTimestamp);
      if (playlistName == null) {
        playlistName = playlist.isMasterPlaylist() ? MASTER_PLAYLIST_NAME : MEDIA_PLAYLIST_NAME;
      }
      notifySampleResult(playlistName, playlistResult);
      return playlist;
    } catch (PlaylistParsingException e) {
      notifySampleResult(playlistName, errorResult(e, playlistResult));
      throw e;
    }
  }

  private SampleResult buildNotMatchingMediaPlaylistResult() {
    SampleResult res = new SampleResult();
    res.setSampleLabel(getName() + " - " + MEDIA_PLAYLIST_NAME);
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

      if (!interrupted && playlist != null) {
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

      if (!interrupted && mediaSegments.hasNext()) {
        MediaSegment segment = mediaSegments.next();
        SampleResult result = uriSampler.apply(segment.getUri());
        notifySampleResult(type + " segment", result);
        lastSegmentNumber = segment.getSequenceNumber();
        consumedSeconds += segment.getDurationSeconds();
      }
    }

    private void updatePlaylist()
        throws InterruptedException, PlaylistDownloadException, PlaylistParsingException {

      timeMachine.awaitMillis(playlist.getReloadTimeMillisForDurationMultiplier(1,
          timeMachine.now()));
      String playlistName = this.type + " playlist";
      Playlist updatedPlaylist = downloadPlaylist(playlistName,
          playlist.getUri());

      while (!interrupted && updatedPlaylist != null && updatedPlaylist.equals(playlist)) {
        long millis = updatedPlaylist
            .getReloadTimeMillisForDurationMultiplier(0.5, timeMachine.now());

        timeMachine.awaitMillis(millis);
        updatedPlaylist = downloadPlaylist(playlistName, playlist.getUri());
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
        while (!interrupted && consumedSeconds <= untilTimeSecond) {
          downloadNextSegment();
        }
      } catch (PlaylistParsingException | PlaylistDownloadException e) {
        LOG.warn("Problem downloading playlist {}", type, e);
      }
    }
  }

  private Playlist downloadSubtitles(URI uri) {
    Instant downloadTimestamp = timeMachine.now();
    HTTPSampleResult playlistResult = uriSampler.apply(uri);
    if (!playlistResult.isSuccessful()) {
      notifySampleResult(SUBTITLES_TYPE_NAME, playlistResult);
      LOG.warn("Problem downloading subtitles from {}", uri);
      return null;
    }

    // we update uri in case the request was redirected
    try {
      uri = playlistResult.getURL().toURI();
    } catch (URISyntaxException e) {
      LOG.warn("Problem updating uri from downloaded playlist {}. Continue with original uri {}",
          playlistResult.getURL(), uri, e);
    }

    // The subtitle can be playlist or a plain file, if the later,
    //no need to download anything else
    if (!uri.toString().contains(".m3u8")) {
      notifySampleResult(SUBTITLES_TYPE_NAME, playlistResult);
      return null;
    }

    try {
      return Playlist
          .fromUriAndBody(uri, playlistResult.getResponseDataAsString(), downloadTimestamp);
    } catch (PlaylistParsingException e) {
      LOG.warn("Problem parsing subtitles from {}", uri, e);
      notifySampleResult(SUBTITLES_TYPE_NAME, errorResult(e, playlistResult));
      return null;
    } finally {
      notifySampleResult(SUBTITLES_TYPE_NAME, playlistResult);
    }
  }

  private void notifySampleResult(String name, SampleResult result) {
    result.setAllThreads(JMeterContextService.getNumberOfThreads());
    result.setThreadName(getThreadContext().getThread().getThreadName());
    result.setGroupThreads(getThreadContext().getThreadGroup().getNumberOfThreads());
    result.setSampleLabel(getName() + " - " + (name != null ? name : MASTER_PLAYLIST_NAME));
    getThreadContext().setPreviousResult(result);
    sampleResultNotifier.accept(result);
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

}

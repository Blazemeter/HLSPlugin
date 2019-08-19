package com.blazemeter.jmeter.hls.logic;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
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

  private static final String HEADER_MANAGER = "HLSRequest.header_manager";
  private static final String COOKIE_MANAGER = "HLSRequest.cookie_manager";
  private static final String CACHE_MANAGER = "HLSRequest.cache_manager";
  private static final String MEDIA_PLAYLIST_NAME = "media playlist";
  private static final String MASTER_PLAYLIST_NAME = "master playlist";

  private final transient Function<URI, HTTPSampleResult> uriSampler;
  private final transient Consumer<SampleResult> sampleResultNotifier;
  private final transient TimeMachine timeMachine;

  private transient HlsHttpClient httpClient;
  private transient volatile boolean notifyFirstSampleAfterLoopRestart;
  private transient long lastSegmentNumber = -1;
  private transient volatile boolean interrupted = false;

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
      lastSegmentNumber = -1;
    }

    if (notifyFirstSampleAfterLoopRestart) {
      httpClient.notifyFirstSampleAfterLoopRestart();
      notifyFirstSampleAfterLoopRestart = false;
    }

    URI masterUri = URI.create(getMasterUrl());
    Playlist masterPlaylist = downloadPlaylist(null, masterUri);
    if (masterPlaylist == null) {
      return null;
    }

    Playlist mediaPlaylist;

    if (!interrupted && masterPlaylist.isMasterPlaylist()) {

      URI mediaPlaylistUri = masterPlaylist
          .solveMediaPlaylistUri(getResolutionSelector(), getBandwidthSelector());

      if (mediaPlaylistUri == null) {
        return buildNotMatchingMediaPlaylistResult();
      }

      mediaPlaylist = downloadPlaylist(MEDIA_PLAYLIST_NAME, mediaPlaylistUri);
      if (mediaPlaylist == null) {
        return null;
      }

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
    float consumedSeconds = 0;
    boolean playListEnd = false;

    try {
      while (!interrupted && mediaPlaylist != null && !playedRequestedTime(playSeconds,
          consumedSeconds) && !playListEnd) {
        Iterator<MediaSegment> mediaSegmentsIt = mediaPlaylist.getMediaSegments().iterator();

        while (!interrupted && mediaSegmentsIt.hasNext() && !playedRequestedTime(playSeconds,
            consumedSeconds)) {
          MediaSegment segment = mediaSegmentsIt.next();
          long segmentSequenceNumber = segment.getSequenceNumber();
          if (segmentSequenceNumber > lastSegmentNumber) {
            SampleResult result = uriSampler.apply(segment.getUri());
            notifySampleResult("video segment", result);
            lastSegmentNumber = segmentSequenceNumber;
            consumedSeconds += segment.getDurationSeconds();
          }
        }

        playListEnd = mediaPlaylist.hasEnd() && !mediaSegmentsIt.hasNext();
        if (!interrupted && !playedRequestedTime(playSeconds, consumedSeconds)
            && !playListEnd) {
          mediaPlaylist = getUpdatedPlaylist(mediaPlaylist);
        }
      }
    } catch (InterruptedException e) {
      LOG.warn("Sampler has been interrupted", e);
      Thread.currentThread().interrupt();
    }

    return null;
  }

  @Override
  protected HTTPSampleResult sample(URL url, String method, boolean areFollowingRedirect,
      int frameDepth) {
    return httpClient.sample(url, method, areFollowingRedirect, frameDepth);
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

  private Playlist downloadPlaylist(String playlistName, URI uri) {
    Instant downloadTimestamp = timeMachine.now();
    HTTPSampleResult playlistResult = uriSampler.apply(uri);
    if (!playlistResult.isSuccessful()) {
      notifySampleResult(playlistName, playlistResult);
      LOG.warn("Problem downloading {} {}", playlistName, uri);
      return null;
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
      LOG.warn("Problem parsing {} {}", playlistName, uri, e);
      return null;
    }
  }

  private void notifySampleResult(String name, SampleResult result) {
    result.setSampleLabel(getName() + " - " + (name != null ? name : MASTER_PLAYLIST_NAME));
    sampleResultNotifier.accept(result);
  }

  private boolean playedRequestedTime(int playSeconds, float consumedSeconds) {
    return playSeconds > 0 && playSeconds <= consumedSeconds;
  }

  private Playlist getUpdatedPlaylist(Playlist playlist)
      throws InterruptedException {
    timeMachine.awaitMillis(playlist.getReloadTimeMillisForDurationMultiplier(1,
        timeMachine.now()));
    Playlist updatedMediaPlaylist = downloadPlaylist(MEDIA_PLAYLIST_NAME,
        playlist.getUri());
    while (!interrupted && updatedMediaPlaylist != null && updatedMediaPlaylist.equals(playlist)) {
      timeMachine.awaitMillis(updatedMediaPlaylist
          .getReloadTimeMillisForDurationMultiplier(0.5, timeMachine.now()));
      updatedMediaPlaylist = downloadPlaylist(MEDIA_PLAYLIST_NAME, playlist.getUri());
    }
    return updatedMediaPlaylist;
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
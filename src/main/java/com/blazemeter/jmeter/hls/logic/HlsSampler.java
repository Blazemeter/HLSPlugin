package com.blazemeter.jmeter.hls.logic;

import com.comcast.viper.hlsparserj.*;
import com.comcast.viper.hlsparserj.tags.master.StreamInf;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;

import java.net.URISyntaxException;

import java.time.Instant;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.jmeter.protocol.http.control.CacheManager;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.protocol.http.sampler.HTTPSampler;

import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.threads.SamplePackage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.comcast.viper.hlsparserj.PlaylistVersion.*;

public class HlsSampler extends HTTPSampler {

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
  private final transient Function<URI, SampleResult> uriSampler;
  private final transient Consumer<SampleResult> sampleResultNotifier;
  private final transient TimeMachine timeMachine;
  private transient long lastSegmentNumber = -1;
  private volatile boolean interrupted;

  private static final int CONNECTION_TIMEOUT = 10000;
  private static final int REQUEST_TIMEOUT = 10000;
  private static final int SOCKET_TIMEOUT = 10000;

  private static final String MASTER_PLAYLIST_TEXT = "master playlist";
  private static final String MEDIA_PLAYLIST_TEXT = "media playlist";
  private static final String SEGMENT_TEXT = "segment ";

  private int referenceBandwidth;
  private String referenceResolution;

  /**
   * Create a new HlsSampler setting everything by default.
   */
  public HlsSampler() {
    setName("HLS Sampler");
    uriSampler = this::downloadUri;
    sampleResultNotifier = this::notifySampleListeners;
    timeMachine = TimeMachine.SYSTEM;
    interrupted = false;
  }

  /**
   * Create a new HlsSampler setting providing values.
   */
  public HlsSampler(Function<URI, SampleResult> uriSampler,
                    Consumer<SampleResult> sampleResultNotifier,
                    TimeMachine timeMachine) {
    setName("HLS Sampler");
    this.uriSampler = uriSampler;
    this.sampleResultNotifier = sampleResultNotifier;
    this.timeMachine = timeMachine;
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

    URI masterUri = URI.create(getMasterUrl());
    URI selectedPlayListUri = getMediaPlaylist(masterUri);

    Playlist mediaPlaylist;

    if (!interrupted && !selectedPlayListUri.equals(masterUri)) {
      mediaPlaylist = downloadPlaylist(MEDIA_PLAYLIST_TEXT, selectedPlayListUri);
      if (mediaPlaylist == null) {
        return null;
      }
    } else {
      Playlist masterPlaylist = downloadPlaylist(MASTER_PLAYLIST_TEXT, masterUri);
      if (masterPlaylist == null) {
        return null;
      }

      mediaPlaylist = masterPlaylist;
    }

    int playSeconds = isPlayVideoDuration() && !getPlaySeconds().isEmpty()
        ? Integer.parseInt(getPlaySeconds()) : 0;

    float consumedSeconds = 0;
    boolean playListEnd;

    try {
      do {
        Iterator<MediaSegment> mediaSegmentsIt = mediaPlaylist.getMediaSegments().iterator();

        while (!interrupted && mediaSegmentsIt.hasNext() && !playedRequestedTime(playSeconds,
            consumedSeconds)) {
          MediaSegment segment = mediaSegmentsIt.next();
          long segmentSequenceNumber = segment.getSequenceNumber();
          if (segmentSequenceNumber > lastSegmentNumber) {
            download( SEGMENT_TEXT + segmentSequenceNumber, segment.getUri());
            lastSegmentNumber = segmentSequenceNumber;
            consumedSeconds += segment.getDurationSeconds();
          }
        }

        playListEnd = mediaPlaylist.hasEnd() && !mediaSegmentsIt.hasNext();
        if (!interrupted && !playedRequestedTime(playSeconds, consumedSeconds)
            && !playListEnd) {
          mediaPlaylist = getUpdatedPlaylist(mediaPlaylist);
        }
      } while (!interrupted && mediaPlaylist != null && !playedRequestedTime(playSeconds,
          consumedSeconds) && !playListEnd);
    } catch (InterruptedException e) {
      LOG.warn("Sampler has been interrupted", e);
      Thread.currentThread().interrupt();
    }

    return null;
  }

  /**
   * Get Media PL using a third party library
   * */
  private URI getMediaPlaylist(URI masterURI) {

    URI selectedURI = null;

    try {
      IPlaylist genericPlaylist = PlaylistFactory.parsePlaylist(TWELVE, masterURI.toURL(), CONNECTION_TIMEOUT, REQUEST_TIMEOUT, SOCKET_TIMEOUT);

      if (!genericPlaylist.isMasterPlaylist()) {
        LOG.warn("This is not a valid Master PL");
        notifyInvalidPlaylist(masterURI);
        return null;

      } else {
        LOG.info("This is a valid Master PL");
        download(MASTER_PLAYLIST_TEXT, masterURI);
        MasterPlaylist masterPlaylist = (MasterPlaylist) genericPlaylist;

        ResolutionSelector resolutionSelector = getResolutionSelector();
        BandwidthSelector bandwidthSelector = getBandwidthSelector();

        referenceBandwidth = (bandwidthSelector.getName().equals("minBandwidth") ? Integer.MAX_VALUE : Integer.MIN_VALUE );

        boolean validBandwidth;
        boolean validResolution;

        for (StreamInf stream : masterPlaylist.getVariantStreams()) {

          validBandwidth = validateBandwidth(bandwidthSelector, stream.getBandwidth());

          if (!validBandwidth) {
            continue;
          } else {
            validResolution = validateResolution(resolutionSelector, stream.getResolution());
          }

          if (validBandwidth && validResolution) {
            selectedURI = new URI(stream.getURI());
          }
        }
      }
    } catch (IOException e) {
      notifyInvalidPlaylist(masterURI);
      e.printStackTrace();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }

    return buildAbsoluteUri(masterURI, selectedURI.getPath());
  }

  private boolean validateResolution(ResolutionSelector resolutionSelector, String resolution) {
    if (resolution.equals(resolutionSelector.getCustomResolution())) {
      return true;
    }

    return true;
  }

  private boolean validateBandwidth(BandwidthSelector bandwidthSelector, int bandwidth) {

    String bandwidthType = bandwidthSelector.getName();

    if (bandwidthType.equals("minBandwidth") && bandwidth < referenceBandwidth) {
      referenceBandwidth = bandwidth;
      return true;
    } else if (bandwidthType.equals("maxBandwidth") && bandwidth > referenceBandwidth) {
      referenceBandwidth = bandwidth;
      return true;
    } else if (bandwidthSelector.getCustomBandwidth()!= null && bandwidthSelector.getCustomBandwidth() == bandwidth) {
      return true;
    }

    return false;
  }

  /*
   * This should go to playlist
   * */
  private URI buildAbsoluteUri(URI uri, String str) {
    URI ret = URI.create(str);
    if (ret.getScheme() != null) {
      return ret;
    } else if (ret.getPath().startsWith("/")) {
      return URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + ret.toString());
    } else {
      String basePath = uri.getPath();
      basePath = basePath.substring(0, basePath.lastIndexOf('/') + 1);
      return URI.create(
          uri.getScheme() + "://" + uri.getRawAuthority() + basePath + ret.toString());
    }
  }

  private Playlist downloadPlaylist(String name, URI uri) {
    Instant downloadTimestamp = timeMachine.now();
    SampleResult playlistResult = download(name, uri);
    if (!playlistResult.isSuccessful()) {
      LOG.warn("Problem downloading {} {}", name, uri);
      return null;
    }
    return Playlist
        .fromUriAndBody(uri, playlistResult.getResponseDataAsString(), downloadTimestamp);
  }

  private SampleResult download(String name, URI uri) {
    SampleResult result = uriSampler.apply(uri);
    result.setSampleLabel(getName() + " - " + name);
    sampleResultNotifier.accept(result);
    return result;
  }

  private void notifyInvalidPlaylist(URI uri) {
    SampleResult result = uriSampler.apply(uri);
    result.setSampleLabel(getName() + " - " + MASTER_PLAYLIST_TEXT);
    result.setSuccessful(false);
    result.setResponseData("Invalid Master playlist");
    sampleResultNotifier.accept(result);
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

  private boolean playedRequestedTime(int playSeconds, float consumedSeconds) {
    return playSeconds != 0 && playSeconds <= consumedSeconds;
  }

  private Playlist getUpdatedPlaylist(Playlist playlist)
      throws InterruptedException {
    timeMachine.awaitMillis(playlist.getReloadTimeMillisForDurationMultiplier(1,
        timeMachine.now()));
    Playlist updatedMediaPlaylist = downloadPlaylist(MEDIA_PLAYLIST_TEXT, playlist.getUri());
    while (!interrupted && updatedMediaPlaylist != null && updatedMediaPlaylist.equals(playlist)) {
      timeMachine.awaitMillis(updatedMediaPlaylist
          .getReloadTimeMillisForDurationMultiplier(0.5, timeMachine.now()));
      updatedMediaPlaylist = downloadPlaylist(MEDIA_PLAYLIST_TEXT, playlist.getUri());
    }
    return updatedMediaPlaylist;
  }

  public boolean interrupt() {
    interrupted = true;
    timeMachine.interrupt();
    super.interrupt();

    return interrupted;
  }

}

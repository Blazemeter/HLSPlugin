package com.blazemeter.jmeter.hls.logic;

import java.net.MalformedURLException;
import java.net.URI;
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

  private transient long lastSegmentNumber = -1;

  private volatile boolean interrupted;

  public boolean interrupt() {
    interrupted = super.interrupt();

    return interrupted;
  }

  public HlsSampler() {
    setName("HLS Sampler");
    uriSampler = this::downloadUri;
    sampleResultNotifier = this::notifySampleListeners;
    interrupted = false;
  }

  public HlsSampler(Function<URI, SampleResult> uriSampler,
                    Consumer<SampleResult> sampleResultNotifier) {
    setName("HLS Sampler");
    this.uriSampler = uriSampler;
    this.sampleResultNotifier = sampleResultNotifier;
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
    SampleResult masterListResult = download(masterUri, "master playlist");
    if (!masterListResult.isSuccessful()) {
      LOG.error("Problem downloading master list {}", masterUri);
      return null;
    }

    Playlist masterPlaylist = Playlist
        .fromUriAndBody(masterUri, masterListResult.getResponseDataAsString());
    URI mediaPlaylistUri = masterPlaylist
        .solveMediaPlaylistUri(getResolutionSelector(), getBandwidthSelector());
    Playlist mediaPlaylist;
    if (!mediaPlaylistUri.equals(masterUri)) {
      SampleResult playListResult = download(mediaPlaylistUri, "media playlist");
      if (!playListResult.isSuccessful()) {
        LOG.error("Problem downloading playlist list {}", mediaPlaylistUri);
        return null;
      }
      mediaPlaylist = Playlist
          .fromUriAndBody(mediaPlaylistUri, playListResult.getResponseDataAsString());
    } else {
      mediaPlaylist = masterPlaylist;
    }

    int playSeconds =
        isPlayVideoDuration() && !getPlaySeconds().isEmpty() ? Integer.parseInt(getPlaySeconds())
            : 0;
    float consumedSeconds = 0;
    boolean playListEnd;

    do {
      Iterator<MediaSegment> mediaSegmentsIt = mediaPlaylist.getMediaSegments().iterator();
      while (!interrupted && mediaSegmentsIt.hasNext() && !playedRequestedTime(playSeconds, consumedSeconds)) {
        MediaSegment segment = mediaSegmentsIt.next();
        long segmentSequenceNumber = segment.getSequenceNumber();
        if (segmentSequenceNumber > lastSegmentNumber) {
          download(segment.getUri(), "segment " + segmentSequenceNumber);
          lastSegmentNumber = segmentSequenceNumber;
          consumedSeconds += segment.getDurationSeconds();
        }
      }
      playListEnd = mediaPlaylist.hasEnd() && !mediaSegmentsIt.hasNext();
      if (!playedRequestedTime(playSeconds, consumedSeconds) && !playListEnd) {
        SampleResult playListResult = download(mediaPlaylistUri, "media playlist");
        if (!playListResult.isSuccessful()) {
          LOG.error("Problem downloading playlist list {}", mediaPlaylistUri);
          return null;
        }
        mediaPlaylist = Playlist
            .fromUriAndBody(mediaPlaylistUri, playListResult.getResponseDataAsString());
      }
    } while (!interrupted&& !playedRequestedTime(playSeconds, consumedSeconds) && !playListEnd);
    return null;
  }

  private SampleResult download(URI uri, String name) {
    SampleResult result = uriSampler.apply(uri);
    result.setSampleLabel(getName() + " - " + name);
    sampleResultNotifier.accept(result);
    return result;
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

}

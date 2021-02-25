package com.blazemeter.jmeter.hls.logic;

import com.blazemeter.jmeter.videostreaming.core.Protocol;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import com.helger.commons.annotation.VisibleForTesting;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URL;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.protocol.http.control.CacheManager;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 This will eventually be renamed to VideoStreamingSamplerProxy to reflect that is more generic
 concept not directly tied to HLS.
 We will also change packages, property names and gui class names.
 Not doing the change right now to avoid abrupt change to users, take advantage of HLS plugin
 popularity and release DASH protocol support faster.
 */
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
  private static final String PROTOCOL_PROPERTY_NAME = "VIDEO_STREAMING.PROTOCOL";

  private static final String HEADER_MANAGER = "HLSRequest.header_manager";
  private static final String COOKIE_MANAGER = "HLSRequest.cookie_manager";
  private static final String CACHE_MANAGER = "HLSRequest.cache_manager";

  private transient VideoStreamingHttpClient httpClient;
  private transient TimeMachine timeMachine;
  private transient SampleResultProcessor sampleResultProcessor;
  private transient VideoStreamingSampler<?, ?> sampler;

  private transient String lastMasterUrl = null;
  private transient volatile boolean notifyFirstSampleAfterLoopRestart;
  private transient VideoStreamingSamplerFactory factory;

  public HlsSampler() {
    initHttpSampler();
  }

  @VisibleForTesting
  public HlsSampler(VideoStreamingSamplerFactory factory, VideoStreamingHttpClient client,
      SampleResultProcessor processor, TimeMachine timeMachine) {
    this.factory = factory;
    this.httpClient = client;
    this.sampleResultProcessor = processor;
    this.timeMachine = timeMachine;
  }

  public HlsSampler(VideoStreamingHttpClient httpClient, TimeMachine timeMachine) {
    setInitHttpSamplerConfig();
    this.httpClient = httpClient;
    this.timeMachine = timeMachine;
    sampleResultProcessor = new SampleResultProcessor(this);
    factory = new VideoStreamingSamplerFactory();
  }

  private void initHttpSampler() {
    setInitHttpSamplerConfig();
    factory = new VideoStreamingSamplerFactory();
    httpClient = new VideoStreamingHttpClient(this);
    sampleResultProcessor = new SampleResultProcessor(this);
    timeMachine = TimeMachine.SYSTEM;
  }

  private void setInitHttpSamplerConfig() {
    setName("Media Sampler");
    setFollowRedirects(true);
    setUseKeepAlive(true);
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

  public Protocol getProtocolSelector() {
    return Protocol.valueOf(getPropertyAsString(PROTOCOL_PROPERTY_NAME, Protocol.AUTOMATIC.name()));
  }

  public void setProtocolSelector(Protocol selector) {
    setProperty(PROTOCOL_PROPERTY_NAME, selector.name());
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
    if (notifyFirstSampleAfterLoopRestart) {
      httpClient.notifyFirstSampleAfterLoopRestart();
      notifyFirstSampleAfterLoopRestart = false;
    }

    String url = getMasterUrl();
    if (!url.equals(lastMasterUrl)) {
      try {
        sampler = factory
            .getVideoStreamingSampler(url, this, httpClient, timeMachine, sampleResultProcessor);
      } catch (IllegalArgumentException e) {
        LOG.error("Error initializing the sampler", e);
      }
    } else if (!this.getResumeVideoStatus()) {
      sampler.resetVideoStatus();
    }
    lastMasterUrl = url;
    return sampler.sample();
  }

  @Override
  protected HTTPSampleResult sample(URL url, String method, boolean areFollowingRedirect,
      int frameDepth) {
    return httpClient.sample(url, method, areFollowingRedirect, frameDepth);
  }

  public HTTPSampleResult errorResult(HTTPSampleResult res, Throwable e) {
    return errorResult(res, e.getClass().getName(), e.getMessage());
  }

  public static HTTPSampleResult errorResult(HTTPSampleResult res, String code, String message) {
    res.setResponseCode(NON_HTTP_RESPONSE_CODE + ": " + code);
    res.setResponseMessage(NON_HTTP_RESPONSE_MESSAGE + ": " + message);
    res.setSuccessful(false);
    return res;
  }

  @Override
  public boolean interrupt() {
    timeMachine.interrupt();
    return httpClient.interrupt();
  }

  @Override
  public void threadFinished() {
    httpClient.threadFinished();
  }

  @Override
  public void testIterationStart(LoopIterationEvent event) {
    notifyFirstSampleAfterLoopRestart = true;
  }

  public int getPlaySecondsOrWarn() {
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
  public void testStarted() {
    timeMachine.reset();
  }

  private void readObject(ObjectInputStream inputStream)
      throws IOException, ClassNotFoundException {
    inputStream.defaultReadObject();
    initHttpSampler();
  }
}

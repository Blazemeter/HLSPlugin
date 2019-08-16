package com.blazemeter.jmeter.hls.logic;

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
  private static final String AUDIO_LANGUAGE_PROPERTY_NAME = "HLS.AUDIO_LANGUAGE";
  private static final String SUBTITLE_LANGUAGE_PROPERTY_NAME = "HLS.SUBTITLE_LANGUAGE";

  private static final String HEADER_MANAGER = "HLSRequest.header_manager";
  private static final String COOKIE_MANAGER = "HLSRequest.cookie_manager";
  private static final String CACHE_MANAGER = "HLSRequest.cache_manager";
  private static final String MEDIA_PLAYLIST_NAME = "media playlist";
  private static final String MASTER_PLAYLIST_NAME = "master playlist";
  private static final String AUDIO_PLAYLIST_NAME = "audio playlist";
  private static final String SUBTITLE_PLAYLIST_NAME = "subtitle playlist";

  private final transient Function<URI, SampleResult> uriSampler;
  private final transient Consumer<SampleResult> sampleResultNotifier;
  private final transient TimeMachine timeMachine;

  private transient long lastVideoSegmentNumber = -1;
  private transient long lastAudioSegmentNumber = -1;
  private transient long lastSubtitleSegmentNumber = -1;

  private transient volatile boolean interrupted = false;

  public HlsSampler() {
    initHttpSampler();
    uriSampler = this::downloadUri;
    sampleResultNotifier = this::notifySampleListeners;
    timeMachine = TimeMachine.SYSTEM;
  }

  public HlsSampler(Function<URI, SampleResult> uriSampler,
      Consumer<SampleResult> sampleResultNotifier,
      TimeMachine timeMachine) {
    initHttpSampler();
    this.uriSampler = uriSampler;
    this.sampleResultNotifier = sampleResultNotifier;
    this.timeMachine = timeMachine;
  }

  private void initHttpSampler() {
    setName("HLS Sampler");
    setAutoRedirects(true);
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

    URI masterUri = URI.create(getMasterUrl());
    Playlist masterPlaylist = downloadPlaylist(null, masterUri);
    if (masterPlaylist == null) {
      return null;
    }

    Playlist mediaPlaylist;
    Playlist audioPlaylist = null;
    Playlist subtitlePlaylist = null;

    boolean hasAudio = false;
    boolean hasSubtitle = false;
    boolean subtitleIsPlaylist = false;

    if (!interrupted && masterPlaylist.isMasterPlaylist()) {

      MediaStream mediaStream = masterPlaylist
          .solveMediaStream(getResolutionSelector(), getBandwidthSelector(),
              getAudioLanguage(), getSubtitleLanguage());

      URI mediaPlaylistUri = mediaStream.getMediaPlaylistUri();

      if (mediaPlaylistUri == null) {
        return buildNotMatchingMediaPlaylistResult();
      }

      mediaPlaylist = downloadPlaylist(MEDIA_PLAYLIST_NAME, mediaPlaylistUri);
      if (mediaPlaylist == null) {
        return null;
      }

      if (mediaStream.getAudioUri() != null) {
        audioPlaylist = downloadPlaylist(AUDIO_PLAYLIST_NAME, mediaStream.getAudioUri());
        hasAudio = true;
      }

      if (mediaStream.getSubtitleUri() != null) {
        subtitlePlaylist = downloadPlaylist(SUBTITLE_PLAYLIST_NAME, mediaStream.getAudioUri());
        hasSubtitle = true;

        if (mediaStream.getSubtitleUri().toString().contains(".m3u8")) {
          subtitleIsPlaylist = true;
        }
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

    boolean mediaPlayListEnd = false;
    boolean audioPlayListEnd = false;
    boolean subtitlePlayListEnd = false;

    MediaPosition mediaPosition = new MediaPosition(0, lastVideoSegmentNumber);
    MediaPosition audioPosition = new MediaPosition(0, lastAudioSegmentNumber);
    MediaPosition subtitlePosition = new MediaPosition(0, lastSubtitleSegmentNumber);

    Iterator<MediaSegment> mediaSegments;
    Iterator<MediaSegment> audioSegments;
    Iterator<MediaSegment> subtitleSegment;

    try {
      while (!interrupted && mediaPlaylist != null
          && (!mediaPosition.playedRequestedTime(playSeconds)
          || !audioPosition.playedRequestedTime(playSeconds)
          || !subtitlePosition.playedRequestedTime(playSeconds))
          && (!mediaPlayListEnd || !audioPlayListEnd || !subtitlePlayListEnd)) {

        mediaSegments = mediaPlaylist.getMediaSegments().iterator();
        audioSegments = (hasAudio ? audioPlaylist.getMediaSegments().iterator() : null);
        subtitleSegment = (hasSubtitle ? subtitlePlaylist.getMediaSegments().iterator() : null);

        while (!interrupted
            && (!playedRequestedTime(playSeconds, mediaPosition.consumedSeconds))
            && (mediaSegments.hasNext() || (hasAudio && audioSegments.hasNext())
            || (hasSubtitle && subtitleSegment.hasNext()))) {

          mediaPosition = downloadSegment(mediaPosition, mediaSegments, "video",
              lastVideoSegmentNumber);

          if (hasAudio) {
            audioPosition = downloadSegment(audioPosition, audioSegments, "audio",
                lastAudioSegmentNumber);
          }

          if (hasSubtitle && subtitleIsPlaylist) {
            subtitlePosition = downloadSegment(subtitlePosition, subtitleSegment, "subtitle",
                lastSubtitleSegmentNumber);
          }
        }

        mediaPlayListEnd = mediaPlaylist.hasEnd() && !mediaSegments.hasNext();
        if (!interrupted && !mediaPosition.playedRequestedTime(playSeconds) && !mediaPlayListEnd) {
          mediaPlaylist = getUpdatedPlaylist(mediaPlaylist, MEDIA_PLAYLIST_NAME);
        }

        if (hasAudio) {
          audioPlayListEnd = audioPlaylist.hasEnd() && !audioSegments.hasNext();
          if (!interrupted && audioPosition.playedRequestedTime(playSeconds) && mediaPlayListEnd) {
            audioPlaylist = getUpdatedPlaylist(audioPlaylist, AUDIO_PLAYLIST_NAME);
          }
        }

        if (hasSubtitle && subtitleIsPlaylist) {
          subtitlePlayListEnd = subtitlePlaylist.hasEnd() && !subtitleSegment.hasNext();
          if (!interrupted && subtitlePosition.playedRequestedTime(playSeconds)
              && mediaPlayListEnd) {
            subtitlePlaylist = getUpdatedPlaylist(subtitlePlaylist, SUBTITLE_PLAYLIST_NAME);
          }
        }
      }
    } catch (InterruptedException e) {
      LOG.warn("Sampler has been interrupted", e);
      Thread.currentThread().interrupt();
    }

    return null;
  }

  private MediaPosition downloadSegment(MediaPosition position,
      Iterator<MediaSegment> segmentIterator, String type, long lastSegmentNumber) {

    if (!interrupted && segmentIterator.hasNext()) {
      MediaSegment segment = segmentIterator.next();

      if (segment.getSequenceNumber() > lastSegmentNumber) {
        notifySampleResult(type + " segment", uriSampler.apply(segment.getUri()));
        position.incrementConsumedSeconds(segment.getDurationSeconds());
        position.setLastSegmentNumber(segment.getSequenceNumber());

        if ("video".equals(type)) {
          lastVideoSegmentNumber = segment.getSequenceNumber();
        } else if ("audio".equals(type)) {
          lastAudioSegmentNumber = segment.getSequenceNumber();
        } else {
          lastSubtitleSegmentNumber = segment.getSequenceNumber();
        }
      }
    }

    return position;
  }

  class MediaPosition {

    private float consumedSeconds;
    private long lastSegmentNumber;

    MediaPosition(float consumedSeconds, long lastSegmentNumber) {
      this.consumedSeconds = consumedSeconds;
      this.lastSegmentNumber = lastSegmentNumber;
    }

    public void incrementConsumedSeconds(float consumedSeconds) {
      this.consumedSeconds += consumedSeconds;
    }

    public boolean playedRequestedTime(int playSeconds) {
      return playSeconds > 0 && playSeconds <= this.consumedSeconds;
    }

    public long getLastSegmentNumber() {
      return this.lastSegmentNumber;
    }

    public void setLastSegmentNumber(long lastSegmentNumber) {
      this.lastSegmentNumber = lastSegmentNumber;
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

  private Playlist downloadPlaylist(String playlistName, URI uri) {
    Instant downloadTimestamp = timeMachine.now();
    SampleResult playlistResult = uriSampler.apply(uri);
    if (!playlistResult.isSuccessful()) {
      if (playlistName == null) {
        playlistName = MASTER_PLAYLIST_NAME;
      }

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
      return playlist;
    } catch (PlaylistParsingException e) {
      LOG.warn("Problem parsing {} {}", playlistName, uri, e);
      return null;
    } finally {
      notifySampleResult(playlistName, playlistResult);
    }
  }

  private void notifySampleResult(String name, SampleResult result) {
    result.setSampleLabel(getName() + " - " + name);
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
    return playSeconds > 0 && playSeconds <= consumedSeconds;
  }

  private Playlist getUpdatedPlaylist(Playlist playlist, String name)
      throws InterruptedException {
    timeMachine.awaitMillis(playlist.getReloadTimeMillisForDurationMultiplier(1,
        timeMachine.now()));
    Playlist updatedPlaylist = downloadPlaylist(name,
        playlist.getUri());

    while (!interrupted && updatedPlaylist != null && updatedPlaylist.equals(playlist)) {
      long millis = updatedPlaylist
          .getReloadTimeMillisForDurationMultiplier(0.5, timeMachine.now());

      timeMachine.awaitMillis(millis);
      updatedPlaylist = downloadPlaylist(name, playlist.getUri());
    }
    return updatedPlaylist;
  }

  public boolean interrupt() {
    interrupted = true;
    timeMachine.interrupt();
    super.interrupt();

    return interrupted;
  }
}

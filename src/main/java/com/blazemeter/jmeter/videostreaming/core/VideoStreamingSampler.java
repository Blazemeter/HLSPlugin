package com.blazemeter.jmeter.videostreaming.core;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import com.blazemeter.jmeter.videostreaming.core.exception.SamplerInterruptedException;
import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class VideoStreamingSampler<T, U extends MediaSegment> {

  public static final String SUBTITLES_TYPE_NAME = "subtitles";
  protected static final String MASTER_TYPE_NAME = "master";
  protected static final String MEDIA_TYPE_NAME = "media";
  protected static final String AUDIO_TYPE_NAME = "audio";
  protected static final String VIDEO_TYPE_NAME = "video";
  private static final byte[] BOM_BYTES = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private static final Logger LOG = LoggerFactory.getLogger(VideoStreamingSampler.class);

  protected final VideoStreamingHttpClient httpClient;
  protected final TimeMachine timeMachine;
  protected final SampleResultProcessor sampleResultProcessor;

  protected transient U lastVideoSegment;
  protected transient U lastAudioSegment;
  protected transient U lastSubtitleSegment;

  private final HlsSampler baseSampler;

  public VideoStreamingSampler(HlsSampler baseSampler,
      VideoStreamingHttpClient httpClient, TimeMachine timeMachine,
      SampleResultProcessor sampleResultProcessor) {
    this.baseSampler = baseSampler;
    this.httpClient = httpClient;
    this.timeMachine = timeMachine;
    this.sampleResultProcessor = sampleResultProcessor;
    resetVideoStatus();
  }

  public static Set<String> getSampleTypesSet() {
    Set<String> sampleTypes = Stream
        .of(SUBTITLES_TYPE_NAME, MEDIA_TYPE_NAME, VIDEO_TYPE_NAME, AUDIO_TYPE_NAME)
        .flatMap(t -> Stream.of(buildPlaylistName(t), buildSegmentName(t), buildInitSegmentName(t)))
        .collect(Collectors.toSet());
    sampleTypes.add(buildPlaylistName(MASTER_TYPE_NAME));
    sampleTypes.add(SUBTITLES_TYPE_NAME);
    return sampleTypes;
  }

  protected static String buildPlaylistName(String playlistType) {
    return playlistType + " playlist";
  }

  private static String buildSegmentName(String segmentType) {
    return segmentType + " segment";
  }

  protected static String buildInitSegmentName(String segmentType) {
    return segmentType + " init segment";
  }

  public SampleResult sample() {
    try {
      URI masterUri = URI.create(baseSampler.getMasterUrl());
      sample(masterUri, baseSampler.getBandwidthSelector(), baseSampler.getResolutionSelector(),
          baseSampler.getAudioLanguage(), baseSampler.getSubtitleLanguage(),
          baseSampler.getPlaySecondsOrWarn());
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

  protected abstract void sample(URI masterUri, BandwidthSelector bandwidthSelector,
      ResolutionSelector resolutionSelector, String audioLanguage, String subtitleLanguage,
      int playSeconds)
      throws SamplerInterruptedException, InterruptedException, PlaylistDownloadException,
      PlaylistParsingException;

  protected T downloadPlaylist(URI uri, Function<T, String> name, PlaylistParser<T> playlistParser)
      throws PlaylistParsingException, PlaylistDownloadException {
    Instant downloadTimestamp = timeMachine.now();
    HTTPSampleResult playlistResult = httpClient.downloadUri(uri);
    if (!playlistResult.isSuccessful()) {
      String playlistName = name.apply(null);
      sampleResultProcessor.accept(playlistName, playlistResult);
      throw new PlaylistDownloadException(playlistName, uri);
    }

    // we update uri in case the request was redirected
    try {
      uri = playlistResult.getURL().toURI();
    } catch (URISyntaxException e) {
      LOG.warn("Problem updating uri to redirected one: {}. Continue with original uri {}",
          playlistResult.getURL(), uri, e);
    }

    try {
      String playlistContents = getPlaylistContents(playlistResult);
      T playlist = playlistParser
          .parse(uri, playlistContents, downloadTimestamp);
      sampleResultProcessor.accept(name.apply(playlist), playlistResult);
      return playlist;
    } catch (PlaylistParsingException e) {
      sampleResultProcessor.accept(name.apply(null), baseSampler.errorResult(playlistResult, e));
      throw e;
    }
  }

  /*
   since some playlist may contain a BOM marker, and jmeter HTTPSampleResult.getResponseDataAsString
   doesn't properly handle it, we need to handle it ourselves.
   */
  private String getPlaylistContents(HTTPSampleResult result) {
    byte[] bytes = result.getResponseData();
    return (bytesStartsWith(bytes, BOM_BYTES))
        ? new String(bytes, StandardCharsets.UTF_8) : result.getResponseDataAsString();
  }

  private boolean bytesStartsWith(byte[] bytes, byte[] start) {
    if (bytes == null || bytes.length < start.length) {
      return false;
    }
    for (int i = 0; i < start.length; i++) {
      if (bytes[i] != start[i]) {
        return false;
      }
    }
    return true;
  }

  public void resetVideoStatus() {
    lastVideoSegment = null;
    lastAudioSegment = null;
    lastSubtitleSegment = null;
  }

  protected interface PlaylistParser<T> {

    T parse(URI uri, String playlistBody, Instant downloadTimestamp)
        throws PlaylistParsingException;

  }

  protected void downloadSegment(MediaSegment segment, String type) {
    SampleResult result = httpClient.downloadUri(segment.getUri());
    result.setResponseHeaders(
        result.getResponseHeaders() + "X-MEDIA-SEGMENT-DURATION: " + segment.getDurationSeconds()
            + "\n");
    sampleResultProcessor.accept(VideoStreamingSampler.buildSegmentName(type), result);
  }

  @VisibleForTesting
  public static HTTPSampleResult buildNotMatchingMediaPlaylistResult() {
    return HlsSampler.errorResult(new HTTPSampleResult(), "NoMatchingMediaPlaylist",
        "No matching media playlist for provided resolution and bandwidth");
  }

}

package com.blazemeter.jmeter.videostreaming.core;

import com.blazemeter.jmeter.hls.logic.HlsSampler;
import com.google.common.annotations.VisibleForTesting;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;

public abstract class VideoStreamingSampler {

  protected static final String MASTER_TYPE_NAME = "master";
  protected static final String SUBTITLES_TYPE_NAME = "subtitles";
  protected static final String MEDIA_TYPE_NAME = "media";
  protected static final String AUDIO_TYPE_NAME = "audio";
  protected static final String VIDEO_TYPE_NAME = "video";

  protected final com.blazemeter.jmeter.hls.logic.HlsSampler baseSampler;
  protected final VideoStreamingHttpClient httpClient;
  protected final TimeMachine timeMachine;
  protected final SampleResultProcessor sampleResultProcessor;

  protected transient long lastVideoSegmentNumber = -1;
  protected transient long lastAudioSegmentNumber = -1;
  protected transient long lastSubtitleSegmentNumber = -1;

  public VideoStreamingSampler(HlsSampler baseSampler,
      VideoStreamingHttpClient httpClient, TimeMachine timeMachine,
      SampleResultProcessor sampleResultProcessor) {
    this.baseSampler = baseSampler;
    this.httpClient = httpClient;
    this.timeMachine = timeMachine;
    this.sampleResultProcessor = sampleResultProcessor;
  }

  public static Set<String> getSampleTypesSet() {
    Set<String> sampleTypes = Stream.of(SUBTITLES_TYPE_NAME, MEDIA_TYPE_NAME, AUDIO_TYPE_NAME)
        .flatMap(t -> Stream.of(buildPlaylistName(t), buildSegmentName(t)))
        .collect(Collectors.toSet());
    sampleTypes.add(buildPlaylistName(MASTER_TYPE_NAME));
    sampleTypes.add(SUBTITLES_TYPE_NAME);
    return sampleTypes;
  }

  protected static String buildPlaylistName(String playlistType) {
    return playlistType + " playlist";
  }

  protected static String buildSegmentName(String segmentType) {
    return segmentType + " segment";
  }

  public void resetVideoStatus() {
    lastVideoSegmentNumber = -1;
    lastAudioSegmentNumber = -1;
    lastSubtitleSegmentNumber = -1;
  }

  public abstract SampleResult sample();

  @VisibleForTesting
  public static HTTPSampleResult buildNotMatchingMediaPlaylistResult() {
    return HlsSampler.errorResult("NoMatchingMediaPlaylist",
        "No matching media playlist for provided resolution and bandwidth");
  }

}

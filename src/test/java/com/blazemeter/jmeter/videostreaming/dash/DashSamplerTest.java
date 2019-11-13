package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.VideoStreamingSamplerTest;
import java.io.IOException;
import java.net.URI;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;
import org.junit.Test;

public class DashSamplerTest extends VideoStreamingSamplerTest {

  private static final URI MANIFEST_URI = URI.create(BASE_URI + "/manifest.mpd");
  private static final String MANIFEST_NAME = "HLS - master";
  private static final String DEFAULT_MANIFEST = "defaultManifest.mpd";
  private static final String DEFAULT_VIDEO_ADAPTATION_SET = "minResolutionMinBandwidthVideo";
  private static final String DEFAULT_AUDIO_ADAPTATION_SET = "minBandwidthAudioEnglish";
  private static final String DEFAULT_SUBTITLES_ADAPTATION_SET = "minBandwidthSubtitlesEnglish";
  public static final int SEGMENTS_COUNT = 5;
  private static final double VIDEO_DURATION_SECONDS = 3.0;
  private static final double AUDIO_DURATION_SECONDS = 3.84;
  private static final double SUBTITLES_DURATION_SECONDS = 10.0;
  private static final int PLAYBACK_TIME_SECONDS = 5;

  private DashSampler sampler;

  @Override
  protected void buildSampleImpl() {
    baseSampler.setMasterUrl(MANIFEST_URI.toString());
    sampler = new DashSampler(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  @Test
  public void shouldDownloadDefaultMediaFromManifest() throws IOException {
    String manifest = getResource(DEFAULT_MANIFEST);
    setupUriSamplerManifest(MANIFEST_URI, manifest);

    setupAdaptationSets(DEFAULT_VIDEO_ADAPTATION_SET, DEFAULT_AUDIO_ADAPTATION_SET,
        DEFAULT_SUBTITLES_ADAPTATION_SET);

    setPlaySeconds(PLAYBACK_TIME_SECONDS);
    sampler.sample();

    verifySampleResults(buildDefaultSampleResults(manifest));
  }

  private void setupAdaptationSets(String mediaAdaptationSetId, String audioAdaptationSetId,
      String subtitlesAdaptationSetId) {
    setupAdaptationSet(VIDEO_TYPE_NAME, mediaAdaptationSetId);
    setupAdaptationSet(AUDIO_TYPE_NAME, audioAdaptationSetId);
    setupAdaptationSet(SUBTITLES_TYPE_NAME, subtitlesAdaptationSetId);
  }

  private void setupUriSamplerManifest(URI uri, String manifest) {
    uriSampler
        .setupUriSampleResults(uri, buildBaseSampleResult(SAMPLER_NAME, uri, manifest));
  }

  private void setupAdaptationSet(String mediaType, String adaptationSetId) {
    setupAdaptationSetInitializationUri(mediaType, adaptationSetId);
    for (int i = 1; i <= SEGMENTS_COUNT; i++) {
      URI uri = buildSegmentUri(mediaType, adaptationSetId, i);
      uriSampler.setupUriSampleResults(uri, buildNamedSampleResult(mediaType, uri));
    }
  }

  private void setupAdaptationSetInitializationUri(String mediaType, String adaptationSetId) {
    URI uri = buildInitializationUri(mediaType, adaptationSetId);
    uriSampler.setupUriSampleResults(uri, buildNamedSampleResult(mediaType, uri));
  }

  private URI buildInitializationUri(String type, String adaptationSetId) {
    return buildAdaptationSetBaseUri(type, adaptationSetId).resolve("IS.m4s");
  }

  private URI buildAdaptationSetBaseUri(String type, String adaptationSetId) {
    return URI.create(String.format("%s/%s/%s/", BASE_URI, type, adaptationSetId));
  }

  private URI buildSegmentUri(String mediaType, String adaptationSetId, long sequenceNumber) {
    return buildAdaptationSetBaseUri(mediaType, adaptationSetId)
        .resolve(String.format("%06d.m4s", sequenceNumber));
  }

  private HTTPSampleResult buildNamedSampleResult(String name, URI uri) {
    HTTPSampleResult result = buildSampleResult(uri, "video/m4s", "");
    result.setSampleLabel(name);
    return result;
  }

  private SampleResult[] buildDefaultSampleResults(String manifest) {
    long videoSegmentNumber = 1;
    long audioSegmentNumber = 1;
    return new SampleResult[]{buildManifestResult(manifest),
        buildInitResult(VIDEO_TYPE_NAME, DEFAULT_VIDEO_ADAPTATION_SET),
        buildVideoSegmentResult(DEFAULT_VIDEO_ADAPTATION_SET, videoSegmentNumber++),
        buildInitResult(AUDIO_TYPE_NAME, DEFAULT_AUDIO_ADAPTATION_SET),
        buildAudioSegmentResult(DEFAULT_AUDIO_ADAPTATION_SET, audioSegmentNumber++),
        buildInitResult(SUBTITLES_TYPE_NAME, DEFAULT_SUBTITLES_ADAPTATION_SET),
        buildSubtitlesSegmentResult(DEFAULT_SUBTITLES_ADAPTATION_SET, 1),
        buildVideoSegmentResult(DEFAULT_VIDEO_ADAPTATION_SET, videoSegmentNumber),
        buildAudioSegmentResult(DEFAULT_AUDIO_ADAPTATION_SET, audioSegmentNumber)};
  }

  private HTTPSampleResult buildManifestResult(String manifest) {
    return buildBaseSampleResult(MANIFEST_NAME, MANIFEST_URI, manifest);
  }

  private HTTPSampleResult buildInitResult(String type, String adaptationSetId) {
    return buildNamedSampleResult(buildInitSampleName(type),
        buildInitializationUri(type, adaptationSetId));
  }

  private String buildInitSampleName(String type) {
    return buildSegmentName(type + " init");
  }

  private HTTPSampleResult buildVideoSegmentResult(String adaptationSetId, long segmentNumber) {
    return buildSegmentSampleResult(VIDEO_TYPE_NAME, adaptationSetId, segmentNumber,
        VIDEO_DURATION_SECONDS);
  }

  private HTTPSampleResult buildSegmentSampleResult(String type, String adaptationSetId,
      long sequenceNumber, double duration) {
    return addDurationHeader(buildNamedSampleResult(buildSegmentName(type),
        buildSegmentUri(type, adaptationSetId, sequenceNumber)), duration);
  }

  private HTTPSampleResult buildAudioSegmentResult(String adaptationSetId, long segmentNumber) {
    return buildSegmentSampleResult(AUDIO_TYPE_NAME, adaptationSetId, segmentNumber,
        AUDIO_DURATION_SECONDS);
  }

  private HTTPSampleResult buildSubtitlesSegmentResult(String adaptationSetId, int segmentNumber) {
    return buildSegmentSampleResult(SUBTITLES_TYPE_NAME, adaptationSetId, segmentNumber,
        SUBTITLES_DURATION_SECONDS);
  }

  @Test
  public void shouldDownloadAlternativeAudioAndSubtitlesFromManifestWhenAvailable()
      throws IOException {
    setUpLanguageSelectors("fre", "spa");

    String manifest = getResource(DEFAULT_MANIFEST);
    setupUriSamplerManifest(MANIFEST_URI, manifest);

    String audioAdaptationSetId = "minBandwidthAudioSpanish";
    String subtitlesAdaptationSetId = "minBandwidthSubtitlesFrench";
    setupAdaptationSets(DEFAULT_VIDEO_ADAPTATION_SET, audioAdaptationSetId,
        subtitlesAdaptationSetId);

    setPlaySeconds(PLAYBACK_TIME_SECONDS);
    sampler.sample();

    long videoSegmentNumber = 1;
    long audioSegmentNumber = 1;
    verifySampleResults(
        buildManifestResult(manifest),
        buildInitResult(VIDEO_TYPE_NAME, DEFAULT_VIDEO_ADAPTATION_SET),
        buildVideoSegmentResult(DEFAULT_VIDEO_ADAPTATION_SET, videoSegmentNumber++),
        buildInitResult(AUDIO_TYPE_NAME, audioAdaptationSetId),
        buildAudioSegmentResult(audioAdaptationSetId, audioSegmentNumber++),
        buildInitResult(SUBTITLES_TYPE_NAME, subtitlesAdaptationSetId),
        buildSubtitlesSegmentResult(subtitlesAdaptationSetId, 1),
        buildVideoSegmentResult(DEFAULT_VIDEO_ADAPTATION_SET, videoSegmentNumber),
        buildAudioSegmentResult(audioAdaptationSetId, audioSegmentNumber));
  }

  private void setUpLanguageSelectors(String subtitleLanguage, String audioLanguage) {
    baseSampler.setSubtitleLanguage(subtitleLanguage);
    baseSampler.setAudioLanguage(audioLanguage);
  }

  @Test
  public void shouldDownloadDefaultAudioWhenProvidedLanguageNotFound() throws IOException {
    setUpLanguageSelectors("chi", "chi");

    String manifest = getResource(DEFAULT_MANIFEST);
    setupUriSamplerManifest(MANIFEST_URI, manifest);

    setupAdaptationSets(DEFAULT_VIDEO_ADAPTATION_SET, DEFAULT_AUDIO_ADAPTATION_SET,
        DEFAULT_SUBTITLES_ADAPTATION_SET);
    setPlaySeconds(PLAYBACK_TIME_SECONDS);

    sampler.sample();

    verifySampleResults(buildDefaultSampleResults(manifest));
  }

  @Test
  public void shouldDownloadLiveStreaming() throws IOException {
    setUpLanguageSelectors("fre", "spa");

    String manifest = getResource("liveStreamingDashManifest.mpd");
    setupUriSamplerManifest(MANIFEST_URI, manifest);

    String videoAdaptationSetId = "minBandwidthMinResolutionVideoEnglish";
    setupLiveStreamingAdaptationSet(VIDEO_TYPE_NAME, videoAdaptationSetId);
    setupLiveStreamingAdaptationSet(AUDIO_TYPE_NAME, DEFAULT_AUDIO_ADAPTATION_SET);
    setupLiveStreamingAdaptationSet(SUBTITLES_TYPE_NAME, DEFAULT_SUBTITLES_ADAPTATION_SET);

    setPlaySeconds(PLAYBACK_TIME_SECONDS);
    sampler.sample();

    long videoSegmentNumber = 1;
    long audioSegmentNumber = 1;
    verifySampleResults(
        buildManifestResult(manifest),
        buildLiveStreamingInitSampleResult(VIDEO_TYPE_NAME, videoAdaptationSetId),
        buildLiveStreamingSegmentSampleResult(VIDEO_TYPE_NAME, videoAdaptationSetId,
            videoSegmentNumber++, VIDEO_DURATION_SECONDS),
        buildLiveStreamingInitSampleResult(AUDIO_TYPE_NAME, DEFAULT_AUDIO_ADAPTATION_SET),
        // we use video duration instead of audio duration to simplify building uris in setup which are time based
        buildLiveStreamingSegmentSampleResult(AUDIO_TYPE_NAME, DEFAULT_AUDIO_ADAPTATION_SET,
            audioSegmentNumber++, VIDEO_DURATION_SECONDS),
        buildLiveStreamingInitSampleResult(SUBTITLES_TYPE_NAME, DEFAULT_SUBTITLES_ADAPTATION_SET),
        buildLiveStreamingSegmentSampleResult(SUBTITLES_TYPE_NAME, DEFAULT_SUBTITLES_ADAPTATION_SET,
            1, SUBTITLES_DURATION_SECONDS),
        buildLiveStreamingSegmentSampleResult(VIDEO_TYPE_NAME, videoAdaptationSetId,
            videoSegmentNumber, VIDEO_DURATION_SECONDS),
        buildLiveStreamingSegmentSampleResult(AUDIO_TYPE_NAME, DEFAULT_AUDIO_ADAPTATION_SET,
            audioSegmentNumber, VIDEO_DURATION_SECONDS));
  }

  private void setupLiveStreamingAdaptationSet(String type, String adaptationSetId) {
    URI uri = buildLiveStreamingInitializationUri(type, adaptationSetId);
    uriSampler.setupUriSampleResults(uri, buildNamedSampleResult(SAMPLER_NAME, uri));
    for (int i = 1; i <= SEGMENTS_COUNT; i++) {
      URI mockedURI = buildLiveStreamingSegmentUri(type, adaptationSetId, i);
      uriSampler.setupUriSampleResults(mockedURI, (buildNamedSampleResult(type, mockedURI)));
    }
  }

  private URI buildLiveStreamingInitializationUri(String type, String adaptationSetId) {
    return URI.create(String.format("%s/%s/%s.dash", BASE_URI, type, adaptationSetId));
  }

  private URI buildLiveStreamingSegmentUri(String type, String adaptationSetId,
      long sequenceNumber) {
    return URI.create(String
        .format("%s/%s/%s-%d.dash", BASE_URI, type, adaptationSetId,
            (long) ((sequenceNumber - 1) * VIDEO_DURATION_SECONDS * 1000)));
  }

  private HTTPSampleResult buildLiveStreamingInitSampleResult(String type, String adaptationSetId) {
    return buildNamedSampleResult(buildInitSampleName(type),
        buildLiveStreamingInitializationUri(type, adaptationSetId));
  }

  private HTTPSampleResult buildLiveStreamingSegmentSampleResult(String type,
      String adaptationSetId, long sequenceNumber, double duration) {
    return addDurationHeader(buildNamedSampleResult(buildSegmentName(type),
        buildLiveStreamingSegmentUri(type, adaptationSetId, sequenceNumber)), duration);
  }

}

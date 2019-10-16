package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.VideoStreamingSamplerTest;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;
import org.junit.Test;

public class DashSamplerTest extends VideoStreamingSamplerTest {

  private static final URI MANIFEST_URI = URI.create(BASE_URI + "/manifest.mpd");
  private static final String MANIFEST_NAME = "HLS - Manifest";
  private static final String DEFAULT_MANIFEST = "defaultManifest.mpd";
  private static final String DEFAULT_MEDIA_ADAPTATION_SET = "minResolutionMinBandwidthVideo";
  private static final String DEFAULT_AUDIO_ADAPTATION_SET = "minBandwidthAudioEnglish";
  private static final String DEFAULT_SUBTITLES_ADAPTATION_SET = "minBandwidthSubtitlesEnglish";
  public static final int SEGMENTS_COUNT = 5;

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

    setupAdaptationSets(DEFAULT_MEDIA_ADAPTATION_SET, DEFAULT_AUDIO_ADAPTATION_SET,
        DEFAULT_SUBTITLES_ADAPTATION_SET, SEGMENTS_COUNT);

    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();

    verifySampleResults(buildExpectedSampleResults(manifest, DEFAULT_MEDIA_ADAPTATION_SET,
        DEFAULT_AUDIO_ADAPTATION_SET, DEFAULT_SUBTITLES_ADAPTATION_SET, SEGMENTS_COUNT));
  }

  private void setupAdaptationSets(String mediaAdaptationSetId, String audioAdaptationSetId,
      String subtitlesAdaptationSetId, int segmentsCount) {
    setupAdaptationSet(MEDIA_TYPE_NAME, mediaAdaptationSetId, segmentsCount);
    setupAdaptationSet(AUDIO_TYPE_NAME, audioAdaptationSetId, segmentsCount);
    setupAdaptationSet(SUBTITLES_TYPE_NAME, subtitlesAdaptationSetId, segmentsCount);
  }

  private void setupUriSamplerManifest(URI uri, String manifest) {
    uriSampler
        .setupUriSampleResults(uri, buildBaseSampleResult(SAMPLER_NAME, uri, manifest));
  }

  private void setupAdaptationSet(String mediaType, String adaptationSetId, int segmentsCount) {
    setupAdaptationSetInitializationUri(mediaType, adaptationSetId);
    for (int i = 1; i <= segmentsCount; i++) {
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

  private URI buildSegmentUri(String mediaType, String adaptationSetId, int sequenceNumber) {
    return buildAdaptationSetBaseUri(mediaType, adaptationSetId)
        .resolve(String.format("%06d.m4s", sequenceNumber));
  }

  private HTTPSampleResult buildNamedSampleResult(String name, URI uri) {
    HTTPSampleResult result = buildSampleResult(uri, "video/m4s", "");
    result.setSampleLabel(name);
    return result;
  }

  private List<SampleResult> buildExpectedSampleResults(String manifest,
      String mediaAdaptationSetId, String audioAdaptationSetId, String subtitlesAdaptationSetId,
      int segmentsCount) {
    return buildExpectedSampleResults(manifest, mediaAdaptationSetId, audioAdaptationSetId,
        subtitlesAdaptationSetId, segmentsCount, this::buildInitSampleResult,
        this::buildSegmentSampleResult);
  }

  private List<SampleResult> buildExpectedSampleResults(String manifest,
      String mediaAdaptationSetId, String audioAdaptationSetId, String subtitlesAdaptationSetId,
      int segmentsCount, BiFunction<String, String, SampleResult> initSampleResultBuilder,
      SegmentSampleResultBuilder segmentSampleResultBuilder) {
    List<SampleResult> ret = new ArrayList<>();
    ret.add(buildBaseSampleResult(MANIFEST_NAME, MANIFEST_URI, manifest));
    addInitAndFirstSegmentSampleResults(MEDIA_TYPE_NAME, mediaAdaptationSetId, ret,
        initSampleResultBuilder, segmentSampleResultBuilder);
    addInitAndFirstSegmentSampleResults(AUDIO_TYPE_NAME, audioAdaptationSetId, ret,
        initSampleResultBuilder, segmentSampleResultBuilder);
    addInitAndFirstSegmentSampleResults(SUBTITLES_TYPE_NAME, subtitlesAdaptationSetId, ret,
        initSampleResultBuilder, segmentSampleResultBuilder);
    for (int i = 2; i <= segmentsCount; i++) {
      ret.add(segmentSampleResultBuilder.accept(MEDIA_TYPE_NAME, mediaAdaptationSetId, i));
      ret.add(segmentSampleResultBuilder.accept(AUDIO_TYPE_NAME, audioAdaptationSetId, i));
      ret.add(segmentSampleResultBuilder.accept(SUBTITLES_TYPE_NAME, subtitlesAdaptationSetId, i));
    }
    return ret;
  }

  private interface SegmentSampleResultBuilder {

    SampleResult accept(String mediaType, String adaptationSetId, int sequenceNumber);

  }

  private void addInitAndFirstSegmentSampleResults(String mediaType, String mediaAdaptationSetId,
      List<SampleResult> ret, BiFunction<String, String, SampleResult> initSampleResultBuilder,
      SegmentSampleResultBuilder segmentSampleResultBuilder) {
    ret.add(initSampleResultBuilder.apply(mediaType, mediaAdaptationSetId));
    ret.add(segmentSampleResultBuilder.accept(mediaType, mediaAdaptationSetId, 1));
  }

  private HTTPSampleResult buildInitSampleResult(String type, String adaptationSetId) {
    return buildNamedSampleResult(buildInitSampleName(type),
        buildInitializationUri(type, adaptationSetId));
  }

  private String buildInitSampleName(String type) {
    return "HLS - Init " + type;
  }

  private HTTPSampleResult buildSegmentSampleResult(String type, String adaptationSetId,
      int sequenceNumber) {
    return buildNamedSampleResult(buildSegmentSampleName(type),
        buildSegmentUri(type, adaptationSetId, sequenceNumber));
  }

  private String buildSegmentSampleName(String type) {
    return String.format("HLS - %s segment", type);
  }

  @Test
  public void shouldDownloadAlternativeAudioAndSubtitlesFromManifestWhenAvailable()
      throws IOException {
    setUpLanguageSelectors("fre", "spa");

    String manifest = getResource(DEFAULT_MANIFEST);
    setupUriSamplerManifest(MANIFEST_URI, manifest);

    String audioAdaptationSetId = "minBandwidthAudioSpanish";
    String subtitlesAdaptationSetId = "minBandwidthSubtitlesFrench";
    setupAdaptationSets(DEFAULT_MEDIA_ADAPTATION_SET, audioAdaptationSetId,
        subtitlesAdaptationSetId, SEGMENTS_COUNT);

    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();

    verifySampleResults(
        buildExpectedSampleResults(manifest, DEFAULT_MEDIA_ADAPTATION_SET, audioAdaptationSetId,
            subtitlesAdaptationSetId, SEGMENTS_COUNT));
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

    setupAdaptationSets(DEFAULT_MEDIA_ADAPTATION_SET, DEFAULT_AUDIO_ADAPTATION_SET,
        DEFAULT_SUBTITLES_ADAPTATION_SET, SEGMENTS_COUNT);

    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();

    verifySampleResults(buildExpectedSampleResults(manifest, DEFAULT_MEDIA_ADAPTATION_SET,
        DEFAULT_AUDIO_ADAPTATION_SET, DEFAULT_SUBTITLES_ADAPTATION_SET, SEGMENTS_COUNT));
  }

  @Test
  public void shouldDownloadLiveStreaming() throws IOException {
    setUpLanguageSelectors("fre", "spa");

    String manifest = getResource("liveStreamingDashManifest.mpd");
    setupUriSamplerManifest(MANIFEST_URI, manifest);

    String videoAdaptationSetId = "minBandwidthMinResolutionVideoEnglish";
    setupLiveStreamingAdaptationSet(MEDIA_TYPE_NAME, videoAdaptationSetId, SEGMENTS_COUNT);
    setupLiveStreamingAdaptationSet(AUDIO_TYPE_NAME, DEFAULT_AUDIO_ADAPTATION_SET, SEGMENTS_COUNT);
    setupLiveStreamingAdaptationSet(SUBTITLES_TYPE_NAME, DEFAULT_SUBTITLES_ADAPTATION_SET,
        SEGMENTS_COUNT);

    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();

    verifySampleResults(
        buildExpectedSampleResults(manifest, videoAdaptationSetId, DEFAULT_AUDIO_ADAPTATION_SET,
            DEFAULT_SUBTITLES_ADAPTATION_SET, SEGMENTS_COUNT,
            this::buildLiveStreamingInitSampleResult, this::buildLiveStreamingSegmentSampleResult));
  }

  private void setupLiveStreamingAdaptationSet(String type, String adaptationSetId,
      int segmentsCount) {
    URI uri = buildLiveStreamingInitializationUri(type, adaptationSetId);
    uriSampler.setupUriSampleResults(uri, buildNamedSampleResult(SAMPLER_NAME, uri));
    for (int i = 1; i <= segmentsCount; i++) {
      URI mockedURI = buildLiveStreamingSegmentUri(type, adaptationSetId, i);
      uriSampler.setupUriSampleResults(mockedURI, (buildNamedSampleResult(type, mockedURI)));
    }
  }

  private URI buildLiveStreamingInitializationUri(String type, String adaptationSetId) {
    return URI.create(String.format("%s/%s/%s.dash", BASE_URI, type, adaptationSetId));
  }

  private URI buildLiveStreamingSegmentUri(String type, String adaptationSetId,
      int sequenceNumber) {
    return URI.create(String
        .format("%s/%s/%s-%s.dash", BASE_URI, type, adaptationSetId,
            String.format("%s000", sequenceNumber)));
  }

  private HTTPSampleResult buildLiveStreamingInitSampleResult(String type, String adaptationSetId) {
    return buildNamedSampleResult(buildInitSampleName(type),
        buildLiveStreamingInitializationUri(type, adaptationSetId));
  }

  private HTTPSampleResult buildLiveStreamingSegmentSampleResult(String type,
      String adaptationSetId, int sequenceNumber) {
    return buildNamedSampleResult(buildSegmentSampleName(type),
        buildLiveStreamingSegmentUri(type, adaptationSetId, sequenceNumber));
  }

  @Test
  public void shouldParseNaturalNumberWhenNoExtraFormat() throws IOException {
    String manifest = getResource("manifestWithoutNumberFormatting.mpd");
    setupUriSamplerManifest(MANIFEST_URI, manifest);

    setupAdaptationSetWithoutFormatting(MEDIA_TYPE_NAME, DEFAULT_MEDIA_ADAPTATION_SET,
        SEGMENTS_COUNT);
    setupAdaptationSetWithoutFormatting(AUDIO_TYPE_NAME, DEFAULT_AUDIO_ADAPTATION_SET,
        SEGMENTS_COUNT);
    setupAdaptationSetWithoutFormatting(SUBTITLES_TYPE_NAME, DEFAULT_SUBTITLES_ADAPTATION_SET,
        SEGMENTS_COUNT);

    setPlaySeconds(MEDIA_SEGMENT_DURATION);
    sampler.sample();

    verifySampleResults(
        buildExpectedSampleResults(manifest, DEFAULT_MEDIA_ADAPTATION_SET,
            DEFAULT_AUDIO_ADAPTATION_SET, DEFAULT_SUBTITLES_ADAPTATION_SET, SEGMENTS_COUNT,
            this::buildInitSampleResult, this::buildSegmentSampleResultWithoutFormat));
  }

  private void setupAdaptationSetWithoutFormatting(String mediaType, String adaptationSetId,
      int segmentsCount) {
    setupAdaptationSetInitializationUri(mediaType, adaptationSetId);
    for (int i = 1; i <= segmentsCount; i++) {
      URI uri = buildSegmentUriWithoutFormat(mediaType, adaptationSetId, i);
      uriSampler.setupUriSampleResults(uri, buildNamedSampleResult(mediaType, uri));
    }
  }

  private URI buildSegmentUriWithoutFormat(String type, String adaptationSetId,
      int sequenceNumber) {
    return buildAdaptationSetBaseUri(type, adaptationSetId)
        .resolve(String.format("%d.m4s", sequenceNumber));
  }

  private HTTPSampleResult buildSegmentSampleResultWithoutFormat(String type,
      String adaptationSetId, int sequenceNumber) {
    return buildNamedSampleResult(buildSegmentSampleName(type),
        buildSegmentUriWithoutFormat(type, adaptationSetId, sequenceNumber));
  }

}

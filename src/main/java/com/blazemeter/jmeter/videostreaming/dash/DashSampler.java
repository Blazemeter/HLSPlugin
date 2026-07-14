package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.MediaStreamSelector;
import com.blazemeter.jmeter.videostreaming.core.PlaybackSession;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.Variants;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamSelector;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingPlayback;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import io.lindstrom.mpd.data.Representation;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.jmeter.samplers.SampleResult;

public class DashSampler extends VideoStreamingSampler<Manifest, DashMediaSegment> {

  // Persisted playback state across slices. complements are stored in a fixed order
  // [video, audio, subtitles] so a continuing session can rebuild the loop tracks and resume
  // segments without re-downloading the manifest.
  private final transient PlaybackSession<MediaPlayback, Manifest> session =
      new PlaybackSession<>();

  public DashSampler(HlsSampler baseSampler, VideoStreamingHttpClient httpClient,
      TimeMachine timeMachine, SampleResultProcessor sampleResultProcessor) {
    super(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  @Override
  public void clearPlaybackSession() {
    session.clear();
  }

  public void sample(URI masterUri, BandwidthSelector bandwidthSelector,
      ResolutionSelector resolutionSelector, String audioLanguage, String subtitleLanguage,
      int playSeconds)
      throws PlaylistDownloadException, PlaylistParsingException, InterruptedException {
    boolean active = StreamingSliceCoordinator.isActive();
    boolean continuingSession = active && session.isInitialized();

    Manifest manifest;
    MediaPlayback videoPlayback;
    MediaPlayback audioPlayback;
    MediaPlayback subtitlesPlayback;
    MediaPlayback mediaPlayback;

    if (continuingSession) {
      manifest = session.getManifest();
      masterUri = manifest.getUri();
      videoPlayback = session.getComplements().get(0);
      audioPlayback = session.getComplements().get(1);
      subtitlesPlayback = session.getComplements().get(2);
      mediaPlayback = session.getPrimary();
    } else {
      manifest = downloadPlaylist(masterUri, p -> MASTER_TYPE_NAME, Manifest::fromUriAndBody);

      // we update masterUri in case the request was redirected
      masterUri = manifest.getUri();

      MediaStreamSelector<MediaRepresentation> alternativeMediaSelector = buildMediaSelector(
          bandwidthSelector);

      videoPlayback = new MediaPlayback(manifest, VIDEO_TYPE_NAME,
          new VideoStreamSelector<>(bandwidthSelector, MediaRepresentation::getBandwidth,
              resolutionSelector, MediaRepresentation::getResolution), null, lastVideoSegment,
          playSeconds);
      audioPlayback = new MediaPlayback(manifest, AUDIO_TYPE_NAME, alternativeMediaSelector,
          audioLanguage, lastAudioSegment, playSeconds);
      subtitlesPlayback = new MediaPlayback(manifest, SUBTITLES_TYPE_NAME, alternativeMediaSelector,
          subtitleLanguage, lastSubtitleSegment, playSeconds);

      // check whether is a video or audio playback
      mediaPlayback = videoPlayback.hasContents() ? videoPlayback : audioPlayback;

      if (active) {
        session.setManifest(manifest);
        session.setPrimary(mediaPlayback);
        session.setComplements(Arrays.asList(videoPlayback, audioPlayback, subtitlesPlayback));
        session.markInitialized();
      }
    }

    List<MediaPlayback> complementTracks = new ArrayList<>();
    if (mediaPlayback == videoPlayback) {
      complementTracks.add(audioPlayback);
    }
    complementTracks.add(subtitlesPlayback);

    boolean finished = false;
    try {
      /*
      we use this variable to avoid requesting manifest before even trying downloading segments due
      to potential low min update period and time taken downloading and processing manifest.
      On a continuing session we already downloaded segments in a previous slice, so we allow a
      manifest refresh immediately when needed.
       */
      boolean initialLoop = !continuingSession;
      while (!mediaPlayback.hasEnded() && !shouldYieldForSlice()) {
        if (mediaPlayback.needsManifestUpdate() && !initialLoop) {
          long awaitMillis = manifest.getReloadTimeMillis(
              mediaPlayback.getLastSegment().getDurationMillis(), timeMachine.now());
          if (awaitMillis > 0) {
            timeMachine.awaitMillis(clampAwaitMillis(awaitMillis));
          }
          manifest = downloadPlaylist(masterUri, p -> MASTER_TYPE_NAME, Manifest::fromUriAndBody);
          mediaPlayback.updateManifest(manifest);
          for (MediaPlayback complementTrack : complementTracks) {
            complementTrack.updateManifest(manifest);
          }
          if (active) {
            session.setManifest(manifest);
          }
        }
        initialLoop = false;

        while (mediaPlayback.shouldAdvancePeriod()) {
          MediaPeriod period = mediaPlayback.nextPeriod();
          for (MediaPlayback complementTrack : complementTracks) {
            complementTrack.updatePeriod(period);
          }
        }

        mediaPlayback.downloadNextSegment();
        double playedSeconds = mediaPlayback.getPlayedTimeSeconds();
        if (playSeconds > 0 && playSeconds < playedSeconds) {
          playedSeconds = playSeconds;
        }
        for (MediaPlayback complementTrack : complementTracks) {
          complementTrack.downloadUntilTimeSecond(playedSeconds);
        }
      }
      finished = mediaPlayback.hasEnded();
    } finally {
      playbackFinishedThisSample = finished;
      if (finished || !active) {
        lastVideoSegment = mediaPlayback.getLastSegment();
        lastSubtitleSegment = subtitlesPlayback.getLastSegment();
        lastAudioSegment = audioPlayback.getLastSegment();
        session.clear();
      }
    }
  }

  private static MediaStreamSelector<MediaRepresentation> buildMediaSelector(
      BandwidthSelector bandwidthSelector) {
    return new MediaStreamSelector<MediaRepresentation>() {
      @Override
      public MediaRepresentation findMatchingVariant(List<MediaRepresentation> variants) {
        return findVariantPerAttribute(MediaRepresentation::getBandwidth, bandwidthSelector,
            variants);
      }
    };
  }

  @Override
  public Variants getVariants(URI masterUri)
      throws PlaylistParsingException, PlaylistDownloadException {
    Variants variants = new Variants();
    Manifest manifest = getManifest(masterUri, p -> MASTER_TYPE_NAME, Manifest::fromUriAndBody);
    variants.setAudioLanguageList(manifest.getVideoLanguages());
    variants.setSubtitleList(manifest.getSubtitleLanguages());
    variants.setResolutionList(manifest.getResolutions());
    variants.setBandwidthList(manifest.getBandwidths());
    variants.setBandwidthResolutionMap(buildBandwidthResolutionMap(manifest));
    return variants;
  }

  private Map<String, String> buildBandwidthResolutionMap(Manifest manifest) {
    Map<String, String> bandwidthResolutionMap = new HashMap<>();
    for (MediaPeriod period : manifest.getPeriods()) {
      for (Representation r : period.getVideoRepresentations()) {
        bandwidthResolutionMap.put(String.valueOf(r.getBandwidth()),
            r.getWidth() + "x" + r.getHeight());
      }
    }
    return bandwidthResolutionMap;
  }

  private class MediaPlayback extends VideoStreamingPlayback<DashMediaSegment> {

    private final MediaStreamSelector<MediaRepresentation> selector;
    private final String languageSelector;
    private Manifest manifest;
    private MediaPeriod period;
    private Iterator<MediaPeriod> periods;
    private SegmentBuilder<?> segmentBuilder;
    private boolean initializedMedia;

    private MediaPlayback(Manifest manifest, String type,
        MediaStreamSelector<MediaRepresentation> selector, String languageSelector,
        DashMediaSegment lastSegment, int playSeconds) {
      super(type, lastSegment, playSeconds);
      this.selector = selector;
      this.languageSelector = languageSelector;
      updateManifest(manifest);
    }

    private void updateManifest(Manifest manifest) {
      this.manifest = manifest;
      MediaPeriod period;
      if (lastSegment == null) {
        periods = manifest.getPeriods().iterator();
        if (manifest.isDynamic()) {
          do {
            period = periods.next();
          } while (periods.hasNext());
        } else {
          period = periods.next();
        }
      } else {
        period = lastSegment.getPeriod();
        periods = manifest.getPeriods().iterator();
        MediaPeriod cur = null;
        while (periods.hasNext() && (cur == null || !cur.equals(period))) {
          cur = periods.next();
        }
        period = cur;
      }
      updatePeriod(period);
    }

    private boolean shouldAdvancePeriod() {
      return !segmentBuilder.hasNext() && periods.hasNext();
    }

    private MediaPeriod nextPeriod() {
      MediaPeriod period = periods.next();
      updatePeriod(period);
      return period;
    }

    private void updatePeriod(MediaPeriod period) {
      if (!period.equals(this.period)) {
        this.initializedMedia = false;
      }
      this.period = period;
      this.segmentBuilder = period.findSegmentBuilder(type, selector, languageSelector);
      if (segmentBuilder != null) {
        this.segmentBuilder.advanceUntil(lastSegment);
      }
    }

    private void downloadNextSegment() throws InterruptedException {
      if (!segmentBuilder.hasNext()) {
        return;
      }

      if (!initializedMedia) {
        downloadInitializationSegment();
        initializedMedia = true;
      }
      DashMediaSegment segment = segmentBuilder.next();
      awaitSegmentAvailable(segment);
      downloadSegment(segment, type);
      lastSegment = segment;
      consumedSeconds += segment.getDurationSeconds();
    }

    private void awaitSegmentAvailable(DashMediaSegment segment) throws InterruptedException {
      Instant availabilityTime = segment.getStartAvailabilityTime().plus(segment.getDuration());
      //The clocks have to be synchronized to avoid error on segments availability
      Instant nowSynchronized = timeMachine.now().plus(manifest.getClocksDiff());
      if (availabilityTime.isAfter(nowSynchronized)) {
        timeMachine.awaitMillis(Duration.between(nowSynchronized, availabilityTime).toMillis());
      }
    }

    private void downloadInitializationSegment() {
      URI uri = segmentBuilder.getInitializationUrl();
      if (uri == null) {
        return;
      }
      SampleResult result = httpClient.downloadUri(uri);
      sampleResultProcessor.accept(buildInitSegmentName(type), result);
      releaseSegmentResponseBodyIfEnabled(result);
    }

    private void downloadUntilTimeSecond(double untilTimeSecond) throws InterruptedException {
      if (segmentBuilder == null) {
        return;
      }
      while (consumedSeconds < untilTimeSecond && segmentBuilder.hasNext()
          && !shouldYieldForSlice()) {
        downloadNextSegment();
      }
    }

    private boolean hasEnded() {
      return playedRequestedTime() || segmentBuilder == null || (!segmentBuilder.hasNext()
          && !periods.hasNext() && (!manifest.isDynamic()
          || manifest.getMinimumUpdatePeriod() == null));
    }

    private boolean hasContents() {
      return segmentBuilder != null;
    }

    private boolean needsManifestUpdate() {
      return !segmentBuilder.hasNext() && !periods.hasNext();
    }

  }

}

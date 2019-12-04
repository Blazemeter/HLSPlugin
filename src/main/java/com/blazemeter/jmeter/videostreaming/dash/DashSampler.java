package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.MediaStreamSelector;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamSelector;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingPlayback;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.jmeter.samplers.SampleResult;

public class DashSampler extends VideoStreamingSampler<Manifest, DashMediaSegment> {

  public DashSampler(HlsSampler baseSampler, VideoStreamingHttpClient httpClient,
      TimeMachine timeMachine, SampleResultProcessor sampleResultProcessor) {
    super(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  public void sample(URI masterUri, BandwidthSelector bandwidthSelector,
      ResolutionSelector resolutionSelector, String audioLanguage, String subtitleLanguage,
      int playSeconds)
      throws PlaylistDownloadException, PlaylistParsingException, InterruptedException {
    Manifest manifest = downloadPlaylist(masterUri, p -> MASTER_TYPE_NAME,
        Manifest::fromUriAndBody);

    MediaStreamSelector<MediaRepresentation> alternativeMediaSelector =
        new MediaStreamSelector<MediaRepresentation>() {
          @Override
          public MediaRepresentation findMatchingVariant(List<MediaRepresentation> variants) {
            return findVariantPerAttribute(MediaRepresentation::getBandwidth, bandwidthSelector,
                variants);
          }
        };

    MediaPlayback videoPlayback = new MediaPlayback(manifest, VIDEO_TYPE_NAME,
        new VideoStreamSelector<>(bandwidthSelector, MediaRepresentation::getBandwidth,
            resolutionSelector, MediaRepresentation::getResolution),
        null, lastVideoSegment, playSeconds);
    MediaPlayback audioPlayback = new MediaPlayback(manifest, AUDIO_TYPE_NAME,
        alternativeMediaSelector, audioLanguage, lastAudioSegment, playSeconds);
    MediaPlayback subtitlesPlayback = new MediaPlayback(manifest, SUBTITLES_TYPE_NAME,
        alternativeMediaSelector, subtitleLanguage, lastSubtitleSegment, playSeconds);

    // check whether is a video or audio playback
    MediaPlayback mediaPlayback = videoPlayback.hasContents() ? videoPlayback : audioPlayback;
    List<MediaPlayback> complementTracks = new ArrayList<>();
    if (mediaPlayback == videoPlayback) {
      complementTracks.add(audioPlayback);
    }
    complementTracks.add(subtitlesPlayback);
    try {
      /*
      we use this variable to avoid requesting manifest before even trying downloading segments due
      to potential low min update period and time taken downloading and processing manifest
       */
      boolean initialLoop = true;
      while (!mediaPlayback.hasEnded()) {
        if (mediaPlayback.needsManifestUpdate() && !initialLoop) {
          long awaitMillis = manifest.getReloadTimeMillis(timeMachine.now());
          if (awaitMillis > 0) {
            timeMachine.awaitMillis(awaitMillis);
          }
          manifest = downloadPlaylist(masterUri, p -> MASTER_TYPE_NAME,
              Manifest::fromUriAndBody);
          mediaPlayback.updateManifest(manifest);
          for (MediaPlayback complementTrack : complementTracks) {
            complementTrack.updateManifest(manifest);
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
    } finally {
      lastVideoSegment = mediaPlayback.getLastSegment();
      lastSubtitleSegment = subtitlesPlayback.getLastSegment();
      lastAudioSegment = audioPlayback.getLastSegment();
    }
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
      Instant availabilityTime = segment.getStartAvailabilityTime();
      Instant now = timeMachine.now();
      if (availabilityTime.isAfter(now)) {
        timeMachine.awaitMillis(Duration.between(availabilityTime, now).toMillis());
      }
    }

    private void downloadInitializationSegment() {
      URI uri = segmentBuilder.getInitializationUrl();
      if (uri == null) {
        return;
      }
      SampleResult result = httpClient.downloadUri(uri);
      sampleResultProcessor.accept(buildInitSegmentName(type), result);
    }

    private void downloadUntilTimeSecond(double untilTimeSecond) throws InterruptedException {
      if (segmentBuilder == null) {
        return;
      }
      while (consumedSeconds < untilTimeSecond && segmentBuilder.hasNext()) {
        downloadNextSegment();
      }
    }

    private boolean hasEnded() {
      return playedRequestedTime()
          || (!segmentBuilder.hasNext() && !periods.hasNext() &&
          (!manifest.isDynamic() || manifest.getMinimumUpdatePeriod() == null));
    }

    private boolean hasContents() {
      return segmentBuilder != null;
    }

    private boolean needsManifestUpdate() {
      return manifest.isDynamic()
          && manifest.getMinimumUpdatePeriod() != null
          && (manifest.getReloadTimeMillis(timeMachine.now()) <= 0
          || !segmentBuilder.hasNext() && !periods.hasNext());
    }

  }

}

package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import io.lindstrom.mpd.MPDParser;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.MPD;
import io.lindstrom.mpd.data.Period;
import io.lindstrom.mpd.data.PresentationType;
import io.lindstrom.mpd.data.Representation;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Manifest extends com.blazemeter.jmeter.videostreaming.core.Manifest {

  private final MPD mpd;
  private final Instant lastDownloadTime;
  private Instant playbackStartTime;
  private final List<MediaPeriod> periods;

  private Manifest(URI uri, MPD mpd, Instant timestamp) {
    super(uri);
    this.mpd = mpd;
    this.lastDownloadTime = timestamp;
    this.periods = buildPeriods(mpd);
  }

  private List<MediaPeriod> buildPeriods(MPD mpd) {
    List<MediaPeriod> ret = new ArrayList<>();
    MediaPeriod previousPeriod = null;
    for (int i = 0; i < mpd.getPeriods().size(); i++) {
      Period period = mpd.getPeriods().get(i);
      if (period.getHref() != null) {
        throw new UnsupportedOperationException(
            "External periods references are not supported yet.");
      }
      previousPeriod = MediaPeriod.builder()
          .withPeriod(mpd.getPeriods().get(i))
          .withManifest(this)
          .withIndex(i)
          .withPreviousPeriod(previousPeriod)
          .withNextPeriod(i + 1 < mpd.getPeriods().size() ? mpd.getPeriods().get(i + 1) : null)
          .build();
      ret.add(previousPeriod);
    }
    return ret;
  }

  public static Manifest fromUriAndBody(URI uri, String body, Instant timestamp)
      throws PlaylistParsingException {
    try {
      return new Manifest(uri, new MPDParser().parse(body), timestamp);
    } catch (Exception e) {
      throw new PlaylistParsingException(uri, e);
    }
  }

  public URI getUri() {
    return uri;
  }

  public List<MediaPeriod> getPeriods() {
    return periods;
  }

  public boolean isDynamic() {
    return mpd.getType() == PresentationType.DYNAMIC;
  }

  public Duration getMediaPresentationDuration() {
    return mpd.getMediaPresentationDuration();
  }

  public List<BaseURL> getBaseURLs() {
    return mpd.getBaseURLs();
  }

  public Duration getMinimumUpdatePeriod() {
    return mpd.getMinimumUpdatePeriod();
  }

  public long getReloadTimeMillis(long segmentDurationMillis) {
    // wait at least segment duration if minUpdatePeriod is 0 (or very small)
    long maxIntervalTime = Math.max(
        mpd.getMinimumUpdatePeriod().toMillis(), segmentDurationMillis);

    return Math.max(maxIntervalTime - Duration.between(
        lastDownloadTime, Instant.now()).toMillis(), 0);
  }

  public Duration getBufferStartTime() {
    Duration bufferTime = mpd.getMinBufferTime();
    if (bufferTime == null) {
      bufferTime = getMinimumUpdatePeriod();
    }
    return Duration.between(mpd.getAvailabilityStartTime(), mpd.getPublishTime())
        .minus(bufferTime);
  }

  public Duration getClocksDiff() {
    OffsetDateTime publishTime = mpd.getPublishTime();
    return publishTime != null ? Duration.between(lastDownloadTime, publishTime) : Duration.ZERO;
  }

  public Instant getAvailabilityStartTime() {
    OffsetDateTime time = mpd.getAvailabilityStartTime();
    if (time == null && playbackStartTime == null) {
      playbackStartTime = Instant.now();
    }
    return time != null ? time.toInstant() : playbackStartTime;
  }

  public List<String> getVideoLanguages() {
    return periods.stream()
        .map(p -> p.getLanguagesByType("audio"))
        .flatMap(List::stream).distinct()
        .collect(Collectors.toList());
  }

  public List<String> getSubtitleLanguages() {
    return periods.stream()
        .map(p -> p.getLanguagesByType("text"))
        .flatMap(List::stream).distinct()
        .collect(Collectors.toList());
  }

  public List<String> getBandwidths() {
    List<String> bandwidths = new ArrayList<>();
    for (MediaPeriod period : periods) {
      for (Representation r : period.getVideoRepresentations()) {
        bandwidths.add(String.valueOf(r.getBandwidth()));
      }
    }
    return bandwidths;
  }

  public List<String> getResolutions() {
    List<String> resolutions = new ArrayList<>();
    for (MediaPeriod period : periods) {
      resolutions.addAll(period.getResolutionFromAdaptationSet());
    }

    return resolutions.stream()
        .filter(resolution -> !resolution.equals("nullxnull")).collect(Collectors.toList());
  }

  @Override
  protected String getManifestType() {
    return String.valueOf(mpd.getType());
  }
}

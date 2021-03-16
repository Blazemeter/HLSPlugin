package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import io.lindstrom.mpd.MPDParser;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.MPD;
import io.lindstrom.mpd.data.Period;
import io.lindstrom.mpd.data.PresentationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class Manifest {

  private final URI uri;
  private final MPD mpd;
  private final Instant lastDownLoadTime;
  private final List<MediaPeriod> periods;
  private Instant playbackStartTime;
  private static final Logger LOG = LoggerFactory.getLogger(Manifest.class);

  private Manifest(URI uri, MPD mpd, Instant timestamp) {
    this.uri = uri;
    this.mpd = mpd;
    this.lastDownLoadTime = timestamp;
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
    Duration minUpdatePeriod = mpd.getMinimumUpdatePeriod();
    // wait at least segment duration if minUpdatePeriod is 0 (or very small)
    long maxIntervalTime = Math.max(minUpdatePeriod.toMillis(), segmentDurationMillis);
    Instant now = Instant.now();

    return Math.max(maxIntervalTime - Duration.between(lastDownLoadTime, now).toMillis(), 0);
  }

  public Duration getBufferStartTime() {
    Duration bufferTime = mpd.getMinBufferTime();
    if (bufferTime == null) {
      bufferTime = getMinimumUpdatePeriod();
    }
    return Duration.between(mpd.getAvailabilityStartTime(), mpd.getPublishTime())
        .minus(bufferTime);
  }

  public Instant getAvailabilityStartTime() {
    OffsetDateTime time = mpd.getAvailabilityStartTime();
    // simulating availabilityStartTime for VOD enables pacing of segment GETs according to segment duration
    if (time == null && playbackStartTime == null) {
      LOG.info("setting playbackStartTime to now()");
      playbackStartTime = Instant.now();
    }
    return time != null ? time.toInstant() : playbackStartTime;
  }

}

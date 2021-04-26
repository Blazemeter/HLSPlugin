package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import io.lindstrom.mpd.MPDParser;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.MPD;
import io.lindstrom.mpd.data.Period;
import io.lindstrom.mpd.data.PresentationType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class Manifest {

  private final URI uri;
  private final MPD mpd;
  private final Instant downloadTime;
  private final List<MediaPeriod> periods;

  private Manifest(URI uri, MPD mpd, Instant timestamp) {
    this.uri = uri;
    this.mpd = mpd;
    this.downloadTime = timestamp;
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

  public long getReloadTimeMillis(Instant now) {
    return Math
        .max(mpd.getMinimumUpdatePeriod().minus(Duration.between(downloadTime, now)).toMillis(), 0);
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
    return publishTime != null ? Duration.between(downloadTime, publishTime) : Duration.ZERO;
  }

  public Instant getAvailabilityStartTime() {
    OffsetDateTime time = mpd.getAvailabilityStartTime();
    return time != null ? time.toInstant() : Instant.MIN;
  }

}

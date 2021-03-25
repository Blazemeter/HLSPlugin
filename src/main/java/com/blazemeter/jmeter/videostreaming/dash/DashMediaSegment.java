package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.core.MediaSegment;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

public class DashMediaSegment extends MediaSegment {

  private final Duration startTime;
  private final MediaPeriod period;
  private final Duration presentationTimeOffset;

  public DashMediaSegment(MediaPeriod period, long sequenceNumber, URI uri, Duration duration,
      Duration startTime, Duration presentationTimeOffset) {
    super(sequenceNumber, uri, duration);
    this.startTime = startTime;
    this.period = period;
    this.presentationTimeOffset = presentationTimeOffset;
  }

  public MediaPeriod getPeriod() {
    return period;
  }

  public Duration getEndTime() {
    return startTime.plus(duration);
  }

  public Instant getStartAvailabilityTime() {
    return period.getAvailabilityStartTime().plus(getEndTime());
  }

  public Duration getDuration() {
    return duration;
  }

  public TemporalAmount getPresentationTimeOffset() {
    return presentationTimeOffset;
  }
}

package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.core.MediaSegment;
import java.net.URI;
import java.time.Duration;

public class DashMediaSegment extends MediaSegment {

  private final Duration startTime;
  private final MediaPeriod period;

  public DashMediaSegment(MediaPeriod period, long sequenceNumber, URI uri, Duration duration,
      Duration startTime) {
    super(sequenceNumber, uri, duration);
    this.startTime = startTime;
    this.period = period;
  }

  public MediaPeriod getPeriod() {
    return period;
  }

  public Duration getStartTime() {
    return startTime;
  }

}

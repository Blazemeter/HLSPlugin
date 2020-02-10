package com.blazemeter.jmeter.videostreaming.core;

import java.net.URI;
import java.time.Duration;

public class MediaSegment {

  protected final Duration duration;
  private final long sequenceNumber;
  private final URI uri;

  public MediaSegment(long sequenceNumber, URI uri, Duration duration) {
    this.sequenceNumber = sequenceNumber;
    this.uri = uri;
    this.duration = duration;
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }

  public URI getUri() {
    return uri;
  }

  public double getDurationSeconds() {
    return (double) duration.toMillis() / 1000;
  }

  @Override
  public String toString() {
    return "MediaSegment{" +
        "sequenceNumber=" + sequenceNumber +
        ", uri=" + uri +
        ", duration=" + duration +
        '}';
  }

}

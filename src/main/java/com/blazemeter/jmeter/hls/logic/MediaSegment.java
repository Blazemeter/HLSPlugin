package com.blazemeter.jmeter.hls.logic;

import java.net.URI;

public class MediaSegment {

  private final long sequenceNumber;
  private final URI uri;
  private final float durationSeconds;

  public MediaSegment(long sequenceNumber, URI uri, float durationSeconds) {
    this.sequenceNumber = sequenceNumber;
    this.uri = uri;
    this.durationSeconds = durationSeconds;
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }

  public URI getUri() {
    return uri;
  }

  public float getDurationSeconds() {
    return durationSeconds;
  }

  @Override
  public String toString() {
    return "MediaSegment{" +
        "sequenceNumber=" + sequenceNumber +
        ", uri=" + uri +
        ", durationSeconds=" + durationSeconds +
        '}';
  }

}

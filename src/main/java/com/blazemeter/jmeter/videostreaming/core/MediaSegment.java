package com.blazemeter.jmeter.videostreaming.core;

import java.net.URI;
import java.time.Duration;

public class MediaSegment {

  protected final Duration duration;
  private final long sequenceNumber;
  private URI uri;
  private int byteLength;
  private int byteOffset;

  public MediaSegment(long sequenceNumber, URI uri, Duration duration) {
    this.sequenceNumber = sequenceNumber;
    this.uri = uri;
    this.duration = duration;
  }

  public MediaSegment(long sequenceNumber, Duration duration) {
    this.sequenceNumber = sequenceNumber;
    this.duration = duration;
  }

  public boolean hasSubRange() {
    return byteLength != 0;
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }

  public URI getUri() {
    return uri;
  }

  public double getDurationSeconds() {
    return (double) duration.getSeconds();
  }

  public long getDurationMillis() {
    return duration.toMillis();
  }

  public int getByteLength() {
    return byteLength;
  }

  public int getByteOffset() {
    return byteOffset;
  }

  public void setByteRangeInfo(URI uri, int byteLength, int byteOffset) {
    this.uri = uri;
    this.byteLength = byteLength;
    this.byteOffset = byteOffset;
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

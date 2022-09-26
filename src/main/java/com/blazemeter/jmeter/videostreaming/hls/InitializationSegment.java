package com.blazemeter.jmeter.videostreaming.hls;

import java.net.URI;

public class InitializationSegment {

  private final URI uri;
  private final int byteLength;
  private final int byteOffset;

  public InitializationSegment(URI uri, int byteLength, int byteOffset) {
    this.uri = uri;
    this.byteLength = byteLength;
    this.byteOffset = byteOffset;
  }

  public URI getUri() {
    return uri;
  }

  public int getByteLength() {
    return byteLength;
  }

  public int getByteOffset() {
    return byteOffset;
  }
}

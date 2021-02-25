package com.blazemeter.jmeter.videostreaming.core;

public enum Protocol {
  AUTOMATIC("Automatic"),
  HLS("HLS"),
  MPEG_DASH("MPEG-DASH");

  private final String name;

  Protocol(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }

}

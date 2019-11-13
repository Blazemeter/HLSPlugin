package com.blazemeter.jmeter.videostreaming.core;

public class VideoStreamingPlayback<T extends MediaSegment> {

  protected final String type;
  protected double consumedSeconds;
  protected T lastSegment;
  private final int playSeconds;

  protected VideoStreamingPlayback(String type, T lastSegment, int playSeconds) {
    this.type = type;
    this.playSeconds = playSeconds;
    this.lastSegment = lastSegment;
  }

  protected boolean playedRequestedTime() {
    return playSeconds > 0 && playSeconds <= this.consumedSeconds;
  }

  public double getPlayedTimeSeconds() {
    return this.consumedSeconds;
  }

  public T getLastSegment() {
    return lastSegment;
  }

}

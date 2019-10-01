package com.blazemeter.jmeter.hls.logic;

public class VideoStreamingPlayback {

  protected final int playSeconds;
  protected final String type;
  protected float consumedSeconds;
  protected long lastSegmentNumber;

  protected VideoStreamingPlayback(int playSeconds, long lastSegmentNumber, String type) {
    this.playSeconds = playSeconds;
    this.lastSegmentNumber = lastSegmentNumber;
    this.type = type;
  }

  protected float getPlayedTime() {
    return consumedSeconds;
  }

  protected boolean playedRequestedTime() {
    return playSeconds > 0 && playSeconds <= this.consumedSeconds;
  }

  protected float playedTimeSeconds() {
    return this.consumedSeconds;
  }
}

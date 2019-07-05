package com.blazemeter.jmeter.hls.logic;

public class DataFragment {
  private String duration;
  private String tsUri;
  private int fragmentNumber;

  public DataFragment(String duration, String tsUri) {
    this.duration = duration;
    this.tsUri = tsUri;
  }

  public String getDuration() {
    return duration;
  }

  public void setDuration(String duration) {
    this.duration = duration;
  }

  public String getTsUri() {
    return tsUri;
  }

  public void setTsUri(String tsUri) {
    this.tsUri = tsUri;
  }

  public int getFragmentNumber() {
    return fragmentNumber;
  }

  public void setFragmentNumber(int fragmentNumber) {
    this.fragmentNumber = fragmentNumber;
  }
}

package com.blazemeter.jmeter.hls.logic;

public class DataFragment {
  private String duration;
  private String tsUri;
  private int fragmentOrder;

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

  public int getFragmentOrder() {
    return fragmentOrder;
  }

  public void setFragmentOrder(int fragmentOrder) {
    this.fragmentOrder = fragmentOrder;
  }
}

package com.blazemeter.jmeter.hls.logic;

public abstract class BandwidthSelector {

  public static final BandwidthSelector MIN = new BandwidthSelector("min", null) {
    @Override
    public boolean matches(int bandwidth, Integer lastMatch) {
      return lastMatch == null || bandwidth <= lastMatch;
    }
  };

  public static final BandwidthSelector MAX = new BandwidthSelector("max", null) {
    @Override
    public boolean matches(int bandwidth, Integer lastMatch) {
      return lastMatch == null || bandwidth >= lastMatch;
    }
  };

  private static final String STRING_SUFFIX = "Bandwidth";

  protected final Integer customBandwidth;
  private final String name;

  public BandwidthSelector(String name, Integer customBandwidth) {
    this.name = name;
    this.customBandwidth = customBandwidth;
  }

  public String getName() {
    return name + STRING_SUFFIX;
  }

  public Integer getCustomBandwidth() {
    return customBandwidth;
  }

  public static class CustomBandwidthSelector extends BandwidthSelector {

    public CustomBandwidthSelector(Integer customBandwidth) {
      super("custom", customBandwidth);
    }

    @Override
    public boolean matches(int bandwidth, Integer lastMatch) {
      return bandwidth == customBandwidth;
    }
  }

  public static BandwidthSelector fromStringAndCustomBandwidth(String str,
      Integer customBandwidth) {
    if (str == null || str.isEmpty() || str.equals(MIN.getName())) {
      return MIN;
    } else if (str.equals(MAX.getName())) {
      return MAX;
    } else {
      return new CustomBandwidthSelector(customBandwidth);
    }
  }

  public abstract boolean matches(int bandwidth, Integer lastMatch);

}

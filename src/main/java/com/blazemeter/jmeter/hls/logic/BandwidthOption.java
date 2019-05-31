package com.blazemeter.jmeter.hls.logic;

public enum BandwidthOption {
  CUSTOM {
    @Override
    public boolean matches(int bandwidth, Integer lastMatch, Integer customBandwidth) {
      return bandwidth == customBandwidth;
    }
  }, MIN {
    @Override
    public boolean matches(int bandwidth, Integer lastMatch, Integer customBandwidth) {
      return lastMatch == null || bandwidth <= lastMatch;
    }
  }, MAX {
    @Override
    public boolean matches(int bandwidth, Integer lastMatch, Integer customBandwidth) {
      return lastMatch == null || bandwidth >= lastMatch;
    }
  };

  public static final String STRING_SUFFIX = "Bandwidth";

  public static BandwidthOption fromString(String str) {
    return str != null && !str.isEmpty() ? BandwidthOption
        .valueOf(str.substring(0, str.length() - STRING_SUFFIX.length()).toUpperCase()) : MIN;
  }

  @Override
  public String toString() {
    return super.toString().toLowerCase() + STRING_SUFFIX;
  }

  public abstract boolean matches(int bandwidth, Integer lastMatch, Integer customBandwidth);

}

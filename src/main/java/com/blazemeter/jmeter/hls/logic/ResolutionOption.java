package com.blazemeter.jmeter.hls.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ResolutionOption {
  CUSTOM {
    @Override
    public boolean matches(String resolution, String lastMatch, String customResolution) {
      if (customResolution == null) {
        LOG.error("selection mode is {}, but no custom resolution set", this);
        return false;
      } else {
        return customResolution.equals(resolution);
      }
    }
  }, MIN {
    @Override
    public boolean matches(String resolution, String lastMatch, String customResolution) {
      return lastMatch == null
          || resolution != null && resolutionCompare(resolution, lastMatch) <= 0;
    }
  }, MAX {
    @Override
    public boolean matches(String resolution, String lastMatch, String customResolution) {
      return lastMatch == null
          || resolution != null && resolutionCompare(resolution, lastMatch) >= 0;
    }
  };

  public static final String STRING_SUFFIX = "Resolution";
  private static final Logger LOG = LoggerFactory.getLogger(ResolutionOption.class);

  public static ResolutionOption fromString(String str) {
    return str != null && !str.isEmpty() ? ResolutionOption
        .valueOf(str.substring(0, str.length() - STRING_SUFFIX.length()).toUpperCase()) : MIN;
  }

  @Override
  public String toString() {
    return super.toString().toLowerCase() + STRING_SUFFIX;
  }

  public abstract boolean matches(String resolution, String lastMatch, String customResolution);

  protected int resolutionCompare(String r1, String r2) {
    return Integer.compare(getResolutionPixels(r1), getResolutionPixels(r2));
  }

  private int getResolutionPixels(String resolution) {
    String[] dimensions = resolution.split("x");
    return Integer.parseInt(dimensions[0]) * Integer.parseInt(dimensions[1]);
  }

}

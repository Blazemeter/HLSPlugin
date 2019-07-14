package com.blazemeter.jmeter.hls.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ResolutionSelector {

  public static final ResolutionSelector MIN = new ResolutionSelector("min", null) {
    @Override
    public boolean matches(String resolution, String lastMatch) {
      return lastMatch == null
          || resolution != null && resolutionCompare(resolution, lastMatch) <= 0;
    }
  };

  public static final ResolutionSelector MAX = new ResolutionSelector("max", null) {
    @Override
    public boolean matches(String resolution, String lastMatch) {
      return lastMatch == null
          || resolution != null && resolutionCompare(resolution, lastMatch) >= 0;
    }
  };

  private static final String STRING_SUFFIX = "Resolution";

  protected final String customResolution;
  private final String name;

  public ResolutionSelector(String name, String customResolution) {
    this.name = name;
    this.customResolution = customResolution;
  }

  public String getName() {
    return name + STRING_SUFFIX;
  }

  public String getCustomResolution() {
    return customResolution;
  }

  public static class CustomResolutionSelector extends ResolutionSelector {

    private static final Logger LOG = LoggerFactory.getLogger(CustomResolutionSelector.class);

    public CustomResolutionSelector(String customResolution) {
      super("custom", customResolution);
    }

    @Override
    public boolean matches(String resolution, String lastMatch) {
      if (customResolution == null) {
        LOG.error("selection mode is {}, but no custom resolution set", this);
        return false;
      } else {
        return customResolution.equals(resolution);
      }
    }
  }

  public static ResolutionSelector fromStringAndCustomResolution(String str,
      String customResolution) {
    if (str == null || str.isEmpty() || str.equals(MIN.getName())) {
      return MIN;
    } else if (str.equals(MAX.getName())) {
      return MAX;
    } else {
      return new ResolutionSelector.CustomResolutionSelector(customResolution);
    }
  }

  public abstract boolean matches(String resolution, String lastMatch);

  protected int resolutionCompare(String r1, String r2) {
    return Integer.compare(getResolutionPixels(r1), getResolutionPixels(r2));
  }

  private int getResolutionPixels(String resolution) {
    String[] dimensions = resolution.split("x");
    return Integer.parseInt(dimensions[0]) * Integer.parseInt(dimensions[1]);
  }

}

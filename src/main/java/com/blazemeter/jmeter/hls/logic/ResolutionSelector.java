package com.blazemeter.jmeter.hls.logic;

import java.util.Objects;
import java.util.function.BiPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ResolutionSelector implements BiPredicate<String, String> {

  public static final ResolutionSelector MIN = new ResolutionSelector("min", null) {
    @Override
    public boolean test(String resolution, String lastMatch) {
      return lastMatch == null
          || resolution != null && resolutionCompare(resolution, lastMatch) <= 0;
    }
  };

  public static final ResolutionSelector MAX = new ResolutionSelector("max", null) {
    @Override
    public boolean test(String resolution, String lastMatch) {
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
    public boolean test(String resolution, String lastMatch) {
      if (customResolution == null) {
        LOG.error("selection mode is {}, but no custom resolution set", this);
        return false;
      } else {
        return customResolution.equals(resolution);
      }
    }

    @Override
    public String toString() {
      return getName() + ":" + customResolution;
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

  protected int resolutionCompare(String r1, String r2) {
    return Long.compare(getResolutionPixels(r1), getResolutionPixels(r2));
  }

  private long getResolutionPixels(String resolution) {
    String[] dimensions = resolution.split("x");
    return Long.parseLong(dimensions[0]) * Long.parseLong(dimensions[1]);
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ResolutionSelector that = (ResolutionSelector) o;
    return Objects.equals(customResolution, that.customResolution) &&
        Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customResolution, name);
  }

}

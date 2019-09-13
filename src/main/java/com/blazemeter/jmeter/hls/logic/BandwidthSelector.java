package com.blazemeter.jmeter.hls.logic;

import java.util.Objects;
import java.util.function.BiPredicate;

public abstract class BandwidthSelector implements BiPredicate<Long, Long> {

  public static final BandwidthSelector MIN = new BandwidthSelector("min", null) {
    @Override
    public boolean test(Long bandwidth, Long lastMatch) {
      return lastMatch == null || bandwidth.compareTo(lastMatch) <= 0;
    }
  };

  public static final BandwidthSelector MAX = new BandwidthSelector("max", null) {
    @Override
    public boolean test(Long bandwidth, Long lastMatch) {
      return lastMatch == null || bandwidth.compareTo(lastMatch) >= 0;
    }
  };

  private static final String STRING_SUFFIX = "Bandwidth";

  protected final Long customBandwidth;
  private final String name;

  public BandwidthSelector(String name, Long customBandwidth) {
    this.name = name;
    this.customBandwidth = customBandwidth;
  }

  public String getName() {
    return name + STRING_SUFFIX;
  }

  public Long getCustomBandwidth() {
    return customBandwidth;
  }

  public static class CustomBandwidthSelector extends BandwidthSelector {

    public CustomBandwidthSelector(Long customBandwidth) {
      super("custom", customBandwidth);
    }

    @Override
    public boolean test(Long bandwidth, Long lastMatch) {
      return bandwidth.equals(customBandwidth);
    }

    @Override
    public String toString() {
      return getName() + ":" + customBandwidth;
    }
  }

  public static BandwidthSelector fromStringAndCustomBandwidth(String str,
      Long customBandwidth) {
    if (str == null || str.isEmpty() || str.equals(MIN.getName())) {
      return MIN;
    } else if (str.equals(MAX.getName())) {
      return MAX;
    } else {
      return new CustomBandwidthSelector(customBandwidth);
    }
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
    BandwidthSelector that = (BandwidthSelector) o;
    return Objects.equals(customBandwidth, that.customBandwidth) &&
        Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customBandwidth, name);
  }

}

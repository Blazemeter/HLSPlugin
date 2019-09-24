package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class BandwidthSelectorTest {

  public static final long MIN_BANDWIDTH = 123;
  public static final long MID_BANDWIDTH = 246;
  public static final long MAX_BANDWIDTH = 456;

  @Test
  public void shouldSelectMinBandwidthWhenMinSelector() {
    assertThat(selectBandwidth(BandwidthSelector.MIN)).isEqualTo(MIN_BANDWIDTH);
  }

  private static Long selectBandwidth(BandwidthSelector selector) {
    Long lastMatch = null;
    for (long bandwidth : new long[]{MAX_BANDWIDTH, MIN_BANDWIDTH, MID_BANDWIDTH}) {
      if (selector.test(bandwidth, lastMatch)) {
        lastMatch = bandwidth;
      }
    }
    return lastMatch;
  }

  @Test
  public void shouldSelectMaxBandwidthWhenMaxSelector() {
    assertThat(selectBandwidth(BandwidthSelector.MAX)).isEqualTo(MAX_BANDWIDTH);
  }

  @Test
  public void shouldSelectMatchingBandwidthWhenCustomSelector() {
    assertThat(selectBandwidth(
        new BandwidthSelector.CustomBandwidthSelector(String.valueOf(MID_BANDWIDTH))))
        .isEqualTo(MID_BANDWIDTH);
  }

  @Test
  public void shouldNoMatchWhenCustomSelectorNotMatchingAny() {
    assertThat(selectBandwidth(new BandwidthSelector.CustomBandwidthSelector("10")))
        .isNull();
  }

  @Test
  public void shouldBuildMinSelectorWhenFromMinString() {
    assertThat(BandwidthSelector.fromStringAndCustomBandwidth("minBandwidth", null))
        .isEqualTo(BandwidthSelector.MIN);
  }

  @Test
  public void shouldBuildMaxSelectorWhenFromMaxString() {
    assertThat(BandwidthSelector.fromStringAndCustomBandwidth("maxBandwidth", null))
        .isEqualTo(BandwidthSelector.MAX);
  }

  @Test
  public void shouldBuildCustomSelectorWhenFromCustomString() {
    String bandwidth = String.valueOf(MID_BANDWIDTH);
    assertThat(BandwidthSelector.fromStringAndCustomBandwidth("customBandwidth", bandwidth))
        .isEqualTo(new BandwidthSelector.CustomBandwidthSelector(bandwidth));
  }

  @Test
  public void shouldBuildCustomSelectorWhenFromOtherString() {
    String bandwidth = String.valueOf(MID_BANDWIDTH);
    assertThat(BandwidthSelector.fromStringAndCustomBandwidth("test", bandwidth))
        .isEqualTo(new BandwidthSelector.CustomBandwidthSelector(bandwidth));
  }

  @Test
  public void shouldBuildMinSelectorWhenFromEmptyString() {
    assertThat(BandwidthSelector.fromStringAndCustomBandwidth("", String.valueOf(MID_BANDWIDTH)))
        .isEqualTo(BandwidthSelector.MIN);
  }

  @Test
  public void shouldBuildMinSelectorWhenFromNullString() {
    assertThat(BandwidthSelector.fromStringAndCustomBandwidth(null, String.valueOf(MID_BANDWIDTH)))
        .isEqualTo(BandwidthSelector.MIN);
  }

}

package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ResolutionSelectorTest {

  public static final String MIN_RESOLUTION = "640x480";
  public static final String MID_RESOLUTION = "800x600";
  public static final String MAX_RESOLUTION = "1024x768";

  @Test
  public void shouldSelectMinResolutionWhenMinSelector() {
    assertThat(selectResolution(ResolutionSelector.MIN)).isEqualTo(MIN_RESOLUTION);
  }

  private static String selectResolution(ResolutionSelector selector) {
    String lastMatch = null;
    for (String resolution : new String[]{MAX_RESOLUTION, MIN_RESOLUTION, MID_RESOLUTION}) {
      if (selector.matches(resolution, lastMatch)) {
        lastMatch = resolution;
      }
    }
    return lastMatch;
  }

  @Test
  public void shouldSelectMaxResolutionWhenMaxSelector() {
    assertThat(selectResolution(ResolutionSelector.MAX)).isEqualTo(MAX_RESOLUTION);
  }

  @Test
  public void shouldSelectMatchingResolutionWhenCustomSelector() {
    assertThat(selectResolution(new ResolutionSelector.CustomResolutionSelector(MID_RESOLUTION)))
        .isEqualTo(MID_RESOLUTION);
  }

  @Test
  public void shouldNoMatchWhenCustomSelectorNotMatchingAny() {
    assertThat(selectResolution(new ResolutionSelector.CustomResolutionSelector("1240x1080")))
        .isNull();
  }

  @Test
  public void shouldBuildMinSelectorWhenFromMinString() {
    assertThat(ResolutionSelector.fromStringAndCustomResolution("minResolution", null))
        .isEqualTo(ResolutionSelector.MIN);
  }

  @Test
  public void shouldBuildMaxSelectorWhenFromMaxString() {
    assertThat(ResolutionSelector.fromStringAndCustomResolution("maxResolution", null))
        .isEqualTo(ResolutionSelector.MAX);
  }

  @Test
  public void shouldBuildCustomSelectorWhenFromCustomString() {
    assertThat(ResolutionSelector.fromStringAndCustomResolution("customResolution", MID_RESOLUTION))
        .isEqualTo(new ResolutionSelector.CustomResolutionSelector(MID_RESOLUTION));
  }

  @Test
  public void shouldBuildCustomSelectorWhenFromOtherString() {
    assertThat(ResolutionSelector.fromStringAndCustomResolution("test", MID_RESOLUTION))
        .isEqualTo(new ResolutionSelector.CustomResolutionSelector(MID_RESOLUTION));
  }

  @Test
  public void shouldBuildMinSelectorWhenFromEmptyString() {
    assertThat(ResolutionSelector.fromStringAndCustomResolution("", MID_RESOLUTION))
        .isEqualTo(ResolutionSelector.MIN);
  }

  @Test
  public void shouldBuildMinSelectorWhenFromNullString() {
    assertThat(ResolutionSelector.fromStringAndCustomResolution(null, MID_RESOLUTION))
        .isEqualTo(ResolutionSelector.MIN);
  }

}

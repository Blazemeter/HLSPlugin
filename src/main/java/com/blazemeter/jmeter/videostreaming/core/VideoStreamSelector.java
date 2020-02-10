package com.blazemeter.jmeter.videostreaming.core;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import java.util.List;
import java.util.function.Function;

public class VideoStreamSelector<T> implements MediaStreamSelector<T> {

  private final BandwidthSelector bandwidthSelector;
  private final Function<T, Long> bandwidthAccessor;
  private final ResolutionSelector resolutionSelector;
  private final Function<T, String> resolutionAccessor;

  public VideoStreamSelector(BandwidthSelector bandwidthSelector,
      Function<T, Long> bandwidthAccessor,
      ResolutionSelector resolutionSelector, Function<T, String> resolutionAccessor) {
    this.bandwidthSelector = bandwidthSelector;
    this.bandwidthAccessor = bandwidthAccessor;
    this.resolutionSelector = resolutionSelector;
    this.resolutionAccessor = resolutionAccessor;
  }

  public T findMatchingVariant(List<T> variants) {
    if (bandwidthSelector.getCustomBandwidth() == null
        && resolutionSelector.getCustomResolution() != null) {
      return findSelectedVariant(resolutionAccessor, resolutionSelector, bandwidthAccessor,
          bandwidthSelector, variants);
    } else {
      return findSelectedVariant(bandwidthAccessor, bandwidthSelector, resolutionAccessor,
          resolutionSelector, variants);
    }
  }

}

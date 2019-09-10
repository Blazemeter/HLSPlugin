package com.blazemeter.jmeter.hls.logic;

import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.Representation;

public class MediaRepresentation {

  private AdaptationSet adaptationSetUsed;
  private Representation selectedRepresentation;

  public MediaRepresentation(Representation representation,
      AdaptationSet adaptationSet) {
    this.adaptationSetUsed = adaptationSet;
    this.selectedRepresentation = representation;
  }

  public AdaptationSet getAdaptationSetUsed() {
    return adaptationSetUsed;
  }

  public Representation getSelectedRepresentation() {
    return selectedRepresentation;
  }

  public boolean exists() {
    return (adaptationSetUsed != null && selectedRepresentation != null);
  }
}

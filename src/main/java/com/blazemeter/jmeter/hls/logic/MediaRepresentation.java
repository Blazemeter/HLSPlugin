package com.blazemeter.jmeter.hls.logic;

import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.Representation;

public class MediaRepresentation {

  private AdaptationSet adaptationSet;
  private Representation representation;
  private String baseURL;

  public MediaRepresentation(Representation representation,
      AdaptationSet adaptationSet, String baseURL) {
    this.adaptationSet = adaptationSet;
    this.representation = representation;
    this.baseURL = baseURL;
  }

  public AdaptationSet getAdaptationSet() {
    return adaptationSet;
  }

  public Representation getRepresentation() {
    return representation;
  }

  public boolean exists() {
    return (adaptationSet != null && representation != null);
  }

  public String getBaseURL() {
    return baseURL;
  }

  public boolean needManualInitialization() {
    return adaptationSet.getSegmentTemplate() != null;
  }

  public boolean isOneDownloadOnly() {
    return representation.getSegmentBase() != null;
  }

  public long getTotalDuration() {
    return (long) Long.valueOf(representation.getSegmentBase().getPresentationDuration())
        / representation.getSegmentBase().getTimescale();
  }
}

package com.blazemeter.jmeter.videostreaming.dash.segmentbuilders;

import com.blazemeter.jmeter.videostreaming.dash.DashMediaSegment;
import com.blazemeter.jmeter.videostreaming.dash.MediaRepresentation;
import com.blazemeter.jmeter.videostreaming.dash.SegmentBuilder;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.SegmentBase;
import io.lindstrom.mpd.data.URLType;
import java.net.URI;
import java.time.Duration;
import java.util.List;

public class SegmentBaseBuilder extends BaseSegmentBuilder<SegmentBase> {

  private URI segmentUrl;
  private boolean generated;

  public SegmentBaseBuilder(MediaRepresentation representation) {
    super(representation);
  }

  @Override
  public boolean hasNext() {
    return segmentUrl != null && !generated;
  }

  @Override
  public SegmentBuilder<SegmentBase> withSegmentInfo(SegmentBase segmentInfo,
      List<List<BaseURL>> baseUrls) {
    if (segmentUrl != null) {
      return this;
    }
    String url = solveAbsoluteUrl("", baseUrls);
    //since above logic will always add a final slash, we remove it
    url = url.substring(0, url.length() - 1);
    // if url ends with / then it is not a valid segment uri.
    if (!url.endsWith("/")) {
      segmentUrl = URI.create(url);
    }
    return this;
  }

  @Override
  protected SegmentBase solveAbsoluteUrls(SegmentBase segmentInfo, List<List<BaseURL>> baseUrls) {
    return segmentInfo.buildUpon()
        .withInitialization(solveAbsoluteUrl(segmentInfo.getInitialization(), baseUrls))
        .withRepresentationIndex(
            solveAbsoluteUrl(segmentInfo.getRepresentationIndex(), baseUrls))
        .build();
  }

  @Override
  protected SegmentBase mergeSegmentInfo(SegmentBase segmentInfo) {
    return this.segmentInfo;
  }

  @Override
  public URI getInitializationUrl() {
    if (segmentInfo == null) {
      return null;
    }
    URLType initialization = segmentInfo.getInitialization();
    return initialization != null && initialization.getSourceURL() != null ? URI
        .create(initialization.getSourceURL()) : null;
  }

  @Override
  public void advanceUntil(DashMediaSegment lastSegment) {
    if (lastSegment != null && lastSegment.getPeriod().equals(period)) {
      generated = true;
    }
  }

  @Override
  public DashMediaSegment next() {
    generated = true;
    return new DashMediaSegment(period, 1, segmentUrl, period.getDuration(), Duration.ZERO,
        Duration.ZERO);
  }

}

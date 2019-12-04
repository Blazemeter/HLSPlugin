package com.blazemeter.jmeter.videostreaming.dash.segmentbuilders;

import com.blazemeter.jmeter.videostreaming.dash.MediaRepresentation;
import com.blazemeter.jmeter.videostreaming.dash.UrlTemplateSolver;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.Segment;
import io.lindstrom.mpd.data.SegmentTemplate;
import io.lindstrom.mpd.data.URLType;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class SegmentTemplateBuilder extends MultiSegmentBuilder<SegmentTemplate> {

  public SegmentTemplateBuilder(MediaRepresentation representation) {
    super(representation);
  }

  @Override
  protected SegmentTemplate solveAbsoluteUrls(SegmentTemplate segmentInfo,
      List<List<BaseURL>> baseUrls) {
    return segmentInfo.buildUpon()
        .withBitstreamSwitching(solveAbsoluteUrl(segmentInfo.getBitstreamSwitching(), baseUrls))
        .withIndex(solveAbsoluteUrl(segmentInfo.getIndex(), baseUrls))
        .withInitialization(solveAbsoluteUrl(segmentInfo.getInitialization(), baseUrls))
        .withMedia(solveAbsoluteUrl(segmentInfo.getMedia(), baseUrls))
        .withInitializationElement(
            solveAbsoluteUrl(segmentInfo.getInitializationElement(), baseUrls))
        .withBitstreamswitchingElement(
            solveAbsoluteUrl(segmentInfo.getBitstreamswitchingElement(), baseUrls))
        .withRepresentationIndex(
            solveAbsoluteUrl(segmentInfo.getRepresentationIndex(), baseUrls))
        .build();
  }

  @Override
  protected SegmentTemplate mergeSegmentInfo(SegmentTemplate segmentInfo) {
    return this.segmentInfo;
  }

  @Override
  public URI getInitializationUrl() {
    String initialization = segmentInfo.getInitialization();
    URLType initElement = segmentInfo.getInitializationElement();
    if (initialization == null && initElement != null) {
      initialization = initElement.getSourceURL();
    }
    return initialization != null
        ? new UrlTemplateSolver(representation, 0, 0).solveUrlTemplate(initialization)
        : null;
  }

  @Override
  protected Supplier<Long> getStartNumberSupplier() {
    return segmentInfo::getStartNumber;
  }

  @Override
  protected Supplier<Long> getTimescaleSupplier() {
    return segmentInfo::getTimescale;
  }

  @Override
  protected Supplier<Long> getPresentationTimeOffsetSupplier() {
    return segmentInfo::getPresentationTimeOffset;
  }

  @Override
  protected Function<Long, URI> getUrlSolver(long segmentNumber) {
    return t -> new UrlTemplateSolver(representation, segmentNumber, t)
        .solveUrlTemplate(segmentInfo.getMedia());
  }

  @Override
  protected Supplier<Long> getSegmentDurationSupplier() {
    return segmentInfo::getDuration;
  }

  @Override
  protected Supplier<List<Segment>> getSegmentTimelineSupplier() {
    return segmentInfo::getSegmentTimeline;
  }

}

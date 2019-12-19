package com.blazemeter.jmeter.videostreaming.dash.segmentbuilders;

import com.blazemeter.jmeter.videostreaming.dash.MediaRepresentation;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.Segment;
import io.lindstrom.mpd.data.SegmentList;
import io.lindstrom.mpd.data.URLType;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SegmentListBuilder extends MultiSegmentBuilder<SegmentList> {

  public SegmentListBuilder(MediaRepresentation representation) {
    super(representation);
  }

  @Override
  protected SegmentList solveAbsoluteUrls(SegmentList segmentInfo, List<List<BaseURL>> baseUrls) {
    return segmentInfo.buildUpon()
        .withBitstreamswitchingElement(
            solveAbsoluteUrl(segmentInfo.getBitstreamswitchingElement(), baseUrls))
        .withHref(solveAbsoluteUrl(segmentInfo.getHref(), baseUrls))
        .withInitialization(solveAbsoluteUrl(segmentInfo.getInitialization(), baseUrls))
        .withRepresentationIndex(
            solveAbsoluteUrl(segmentInfo.getRepresentationIndex(), baseUrls))
        .withSegmentURLs(segmentInfo.getSegmentURLs().stream()
            .map(s -> s.buildUpon()
                .withMedia(solveAbsoluteUrl(s.getMedia(), baseUrls))
                .withIndex(solveAbsoluteUrl(s.getIndex(), baseUrls))
                .build())
            .collect(Collectors.toList()))
        .build();
  }

  @Override
  protected SegmentList mergeSegmentInfo(SegmentList segmentInfo) {
    return this.segmentInfo.buildUpon()
        .withInitialization(
            this.segmentInfo.getInitialization() != null ? this.segmentInfo.getInitialization()
                : segmentInfo.getInitialization())
        .build();
  }

  @Override
  public URI getInitializationUrl() {
    URLType initialization = segmentInfo.getInitialization();
    return initialization != null ? URI.create(initialization.getSourceURL()) : null;
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
    URI segmentUrl = URI
        .create(segmentInfo.getSegmentURLs().get(getSegmentIndex(segmentNumber)).getMedia());
    return t -> segmentUrl;
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

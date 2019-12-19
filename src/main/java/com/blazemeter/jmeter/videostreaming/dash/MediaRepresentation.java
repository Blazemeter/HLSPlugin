package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.dash.segmentbuilders.SegmentBaseBuilder;
import com.blazemeter.jmeter.videostreaming.dash.segmentbuilders.SegmentListBuilder;
import com.blazemeter.jmeter.videostreaming.dash.segmentbuilders.SegmentTemplateBuilder;
import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.Representation;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MediaRepresentation {

  private final URI baseUri;
  private final Manifest manifest;
  private final MediaPeriod period;
  private final AdaptationSet adaptationSet;
  private final Representation representation;

  public MediaRepresentation(Manifest manifest, MediaPeriod period, AdaptationSet adaptationSet,
      Representation representation) {
    this.baseUri = buildBaseUri(manifest.getUri());
    this.manifest = manifest;
    this.period = period;
    this.adaptationSet = adaptationSet;
    this.representation = representation;
  }

  private URI buildBaseUri(URI uri) {
    String basePath = uri.getPath();
    basePath = basePath.substring(0, basePath.lastIndexOf('/') + 1);
    return URI.create(
        uri.getScheme() + "://" + uri.getRawAuthority() + basePath);
  }

  public MediaPeriod getPeriod() {
    return period;
  }

  public SegmentBuilder getSegmentBuilder() {
    List<List<BaseURL>> representationBaseUrls = Arrays
        .asList(representation.getBaseURLs(), adaptationSet.getBaseURLs(), period.getBaseURLs(),
            manifest.getBaseURLs(), buildBaseUriBaseUrls());
    List<List<BaseURL>> adaptationSetBaseUrls = representationBaseUrls
        .subList(1, representationBaseUrls.size());
    List<List<BaseURL>> periodBaseUrls = adaptationSetBaseUrls
        .subList(1, adaptationSetBaseUrls.size());

    SegmentBuilder segmentBuilder = new SegmentTemplateBuilder(this)
        .withSegmentInfo(representation.getSegmentTemplate(), representationBaseUrls)
        .withSegmentInfo(adaptationSet.getSegmentTemplate(), adaptationSetBaseUrls)
        .withSegmentInfo(period.getSegmentTemplate(), periodBaseUrls);
    if (!segmentBuilder.isEmpty()) {
      return segmentBuilder;
    }

    segmentBuilder = new SegmentListBuilder(this)
        .withSegmentInfo(representation.getSegmentList(), representationBaseUrls)
        .withSegmentInfo(adaptationSet.getSegmentList(), adaptationSetBaseUrls)
        .withSegmentInfo(period.getSegmentList(), periodBaseUrls);
    if (!segmentBuilder.isEmpty()) {
      return segmentBuilder;
    }

    return new SegmentBaseBuilder(this)
        .withSegmentInfo(representation.getSegmentBase(), representationBaseUrls)
        .withSegmentInfo(adaptationSet.getSegmentBase(), adaptationSetBaseUrls)
        .withSegmentInfo(period.getSegmentBase(), periodBaseUrls);
  }

  public String getId() {
    return representation.getId();
  }

  private List<BaseURL> buildBaseUriBaseUrls() {
    return Collections.singletonList(BaseURL.builder().withValue(baseUri.toString()).build());
  }

  public long getBandwidth() {
    return representation.getBandwidth();
  }

  public String getResolution() {
    return representation.getWidth() + "x" + representation.getHeight();
  }

}

package com.blazemeter.jmeter.videostreaming.dash.segmentbuilders;

import com.blazemeter.jmeter.videostreaming.dash.Manifest;
import com.blazemeter.jmeter.videostreaming.dash.MediaPeriod;
import com.blazemeter.jmeter.videostreaming.dash.MediaRepresentation;
import com.blazemeter.jmeter.videostreaming.dash.SegmentBuilder;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.URLType;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

public abstract class BaseSegmentBuilder<T> implements SegmentBuilder<T> {

  protected final MediaRepresentation representation;
  protected final MediaPeriod period;
  protected final Manifest manifest;
  protected T segmentInfo;

  protected BaseSegmentBuilder(MediaRepresentation representation) {
    this.representation = representation;
    this.period = representation.getPeriod();
    this.manifest = period.getManifest();
  }

  @Override
  public boolean isEmpty() {
    return segmentInfo == null;
  }

  @Override
  public SegmentBuilder<T> withSegmentInfo(T segmentInfo, List<List<BaseURL>> baseUrls) {
    if (segmentInfo != null) {
      segmentInfo = solveAbsoluteUrls(segmentInfo, baseUrls);
      this.segmentInfo = this.segmentInfo == null ? segmentInfo : mergeSegmentInfo(segmentInfo);
    }
    return this;
  }

  protected URLType solveAbsoluteUrl(URLType url, List<List<BaseURL>> baseUrls) {
    if (url == null) {
      return null;
    }
    return url.buildUpon()
        .withSourceURL(solveAbsoluteUrl(url.getSourceURL(), baseUrls))
        .build();
  }

  protected final String solveAbsoluteUrl(String originalUrl, List<List<BaseURL>> baseUrls) {
    if (originalUrl == null) {
      return null;
    }
    String ret = originalUrl;
    Iterator<List<BaseURL>> it = baseUrls.iterator();
    while (!URI.create(ret).isAbsolute() && it.hasNext()) {
      List<BaseURL> curr = it.next();
      if (curr != null && !curr.isEmpty()) {
        String val = curr.get(0).getValue();
        ret = (val.endsWith("/") ? val : val + "/") + ret;
      }
    }
    return ret;
  }

  protected abstract T solveAbsoluteUrls(T segmentInfo, List<List<BaseURL>> baseUrls);

  protected abstract T mergeSegmentInfo(T segmentInfo);

}

package com.blazemeter.jmeter.videostreaming.dash;

import io.lindstrom.mpd.data.BaseURL;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

public interface SegmentBuilder<T> extends Iterator<DashMediaSegment> {

  SegmentBuilder<T> withSegmentInfo(T segmentInfo, List<List<BaseURL>> baseUrls);

  URI getInitializationUrl();

  void advanceUntil(DashMediaSegment lastSegment);

  boolean isEmpty();

}


package com.blazemeter.jmeter.hls.logic;

import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import com.blazemeter.jmeter.videostreaming.dash.DashSampler;

public class VideoStreamingSamplerFactory {

  public VideoStreamingSampler<?, ?> getVideoStreamingSampler(String url, HlsSampler baseSampler,
      VideoStreamingHttpClient httpClient, TimeMachine timeMachine,
      SampleResultProcessor sampleResultProcessor) throws IllegalArgumentException {
    switch (baseSampler.getProtocolSelector()) {
      case HLS:
        return createHlsSampler(baseSampler, httpClient, timeMachine, sampleResultProcessor);
      case MPEG_DASH:
        return createDashSampler(baseSampler, httpClient, timeMachine, sampleResultProcessor);
      default:
        //HLS Master Playlist must contain this .m3u8 extension in their URLs
        if (url.contains(".m3u8")) {
          return createHlsSampler(baseSampler, httpClient, timeMachine, sampleResultProcessor);
        } else {
          return createDashSampler(baseSampler, httpClient, timeMachine, sampleResultProcessor);
        }
    }
  }

  private DashSampler createDashSampler(HlsSampler baseSampler, VideoStreamingHttpClient httpClient,
      TimeMachine timeMachine, SampleResultProcessor sampleResultProcessor) {
    return new DashSampler(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  private com.blazemeter.jmeter.videostreaming.hls.HlsSampler createHlsSampler(
      HlsSampler baseSampler, VideoStreamingHttpClient httpClient, TimeMachine timeMachine,
      SampleResultProcessor sampleResultProcessor) {
    return new com.blazemeter.jmeter.videostreaming.hls.HlsSampler(baseSampler, httpClient,
        timeMachine, sampleResultProcessor);
  }
}

package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class VideoStreamingSamplerFactoryTest {

  @Mock
  private HlsSampler proxy;
  @Mock
  private VideoStreamingHttpClient client;
  @Mock
  private TimeMachine timeMachine;
  @Mock
  private SampleResultProcessor processor;

  private VideoStreamingSamplerFactory factory;

  @Before
  public void setUp() {
    factory = new VideoStreamingSamplerFactory();
  }

  @Test
  public void shouldCreateHlsSamplerWhenUrlContainsExtension() {
    assertThat(factory
        .getVideoStreamingSampler("test.com/master.m3u8", proxy, client, timeMachine, processor))
        .isInstanceOf(com.blazemeter.jmeter.videostreaming.hls.HlsSampler.class);
  }

  @Test
  public void shouldNotCreateHlsSamplerWhenUrlNotContainsExtension() {
    assertThat(factory
        .getVideoStreamingSampler("test.com/master.mpd", proxy, client, timeMachine, processor))
        .isNotInstanceOf(com.blazemeter.jmeter.videostreaming.hls.HlsSampler.class);
  }
}
package com.blazemeter.jmeter.hls.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.blazemeter.jmeter.videostreaming.core.Protocol;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
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
    doReturn(Protocol.AUTOMATIC).when(proxy).getProtocolSelector();
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

  @Test
  public void shouldCreateHlsSamplerWhenHlsProtocolIsSelected() {
    doReturn(Protocol.HLS).when(proxy).getProtocolSelector();
    assertThat(factory
        .getVideoStreamingSampler("test.com/master", proxy, client, timeMachine, processor))
        .isInstanceOf(com.blazemeter.jmeter.videostreaming.hls.HlsSampler.class);
  }

  @Test
  public void shouldCreateDashSamplerWhenDashProtocolIsSelected() {
    doReturn(Protocol.MPEG_DASH).when(proxy).getProtocolSelector();
    assertThat(factory
        .getVideoStreamingSampler("test.com/master", proxy, client, timeMachine, processor))
        .isInstanceOf(com.blazemeter.jmeter.videostreaming.dash.DashSampler.class);
  }
}

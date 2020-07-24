package com.blazemeter.jmeter.hls.logic;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

import com.blazemeter.jmeter.videostreaming.core.MediaSegment;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import com.blazemeter.jmeter.videostreaming.hls.Playlist;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HlsSamplerTest {

  @Mock
  private VideoStreamingSampler<Playlist, MediaSegment> videoStreamingSampler;
  @Mock
  private VideoStreamingSamplerFactory factory;
  @Mock
  private VideoStreamingHttpClient client;
  @Mock
  private TimeMachine timeMachine;
  @Mock
  private SampleResultProcessor processor;

  private HlsSampler hlsSampler;

  @Before
  public void setUp() {
    hlsSampler = new HlsSampler(factory, client, processor, timeMachine);
  }

  @Test
  public void shouldFactoryGetVideoStreamingSamplerWhenSample() {
    String masterUrl = "hls_master_playlist.m3u8";
    hlsSampler.setMasterUrl(masterUrl);

    doReturn(videoStreamingSampler)
        .when(factory)
        .getVideoStreamingSampler(masterUrl, hlsSampler, client, timeMachine, processor);

    hlsSampler.sample();
    verify(factory, only())
        .getVideoStreamingSampler(masterUrl, hlsSampler, client, timeMachine, processor);
  }
}
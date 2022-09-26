package com.blazemeter.jmeter.hls.gui;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blazemeter.jmeter.JMeterTestUtils;
import com.blazemeter.jmeter.SwingTestRunner;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.VerificationCollector;

@RunWith(SwingTestRunner.class)
public class HlsSamplerGuiTest {

  private static final String MASTER_URL = "http://test/test.m3u8";
  public static final String PLAY_SECONDS = "30";
  public static final String CUSTOM_BANDWIDTH = "12000";
  public static final String CUSTOM_RESOLUTION = "640x460";

  @Rule
  public final VerificationCollector collector = MockitoJUnit.collector();

  private HlsSamplerGui gui;

  @Mock
  private HlsSamplerPanel panel;
  @Mock
  private HlsSampler sampler;

  @BeforeClass
  public static void setupClass() {
    JMeterTestUtils.setupJmeterEnv();
  }

  @Before
  public void setUp() {
    gui = new HlsSamplerGui(panel);
  }

  @Test
  public void shouldSetPanelFieldsWithTestElementPropertiesWhenConfigure() {
    when(sampler.getMasterUrl()).thenReturn(MASTER_URL);
    when(sampler.isPlayVideoDuration()).thenReturn(true);
    when(sampler.getPlaySeconds()).thenReturn(PLAY_SECONDS);
    when(sampler.getResumeVideoStatus()).thenReturn(true);
    when(sampler.getIncludeTypeInHeadersStatus()).thenReturn(true);
    when(sampler.getBandwidthSelected()).thenReturn(CUSTOM_BANDWIDTH);
    when(sampler.getResolutionSelected()).thenReturn(CUSTOM_RESOLUTION);
    gui.configure(sampler);
    verify(panel).setMasterUrl(MASTER_URL);
    verify(panel).setPlayVideoDuration(true);
    verify(panel).setPlaySeconds(PLAY_SECONDS);
    verify(panel).setResumeStatus(true);
    verify(panel).setIncludeTypeInHeaders(true);
    verify(panel).setBandwidthSelected(CUSTOM_BANDWIDTH);
    verify(panel).setResolutionSelected(CUSTOM_RESOLUTION);
  }

  @Test
  public void shouldSetTestElementPropertiesWithPanelFieldsWhenModifyTestElement() {
    when(panel.getMasterUrl()).thenReturn(MASTER_URL);
    when(panel.isPlayVideoDuration()).thenReturn(true);
    when(panel.getPlaySeconds()).thenReturn(PLAY_SECONDS);
    when(panel.getResumeVideoStatus()).thenReturn(true);
    when(panel.getBandwidthSelected()).thenReturn(CUSTOM_BANDWIDTH);
    when(panel.getResolutionSelected()).thenReturn(CUSTOM_RESOLUTION);
    gui.modifyTestElement(sampler);
    verify(sampler).setMasterUrl(MASTER_URL);
    verify(sampler).setPlayVideoDuration(true);
    verify(sampler).setPlaySeconds(PLAY_SECONDS);
    verify(sampler).setResumeVideoStatus(true);
    verify(sampler).setBandwidthSelected(CUSTOM_BANDWIDTH);
    verify(sampler).setResolutionSelected(CUSTOM_RESOLUTION);
  }

  @Test
  public void shouldClearPanelFieldsWhenClear() {
    gui.clearGui();
    verify(panel).setMasterUrl("");
    verify(panel).setDefaultForAllBoxes();
    verify(panel).setPlayVideoDuration(false);
    verify(panel).setPlaySeconds("");
    verify(panel).setResumeStatus(false);
  }

}

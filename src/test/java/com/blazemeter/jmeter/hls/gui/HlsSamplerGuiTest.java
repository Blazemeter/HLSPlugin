package com.blazemeter.jmeter.hls.gui;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blazemeter.jmeter.JMeterTestUtils;
import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.BandwidthSelector.CustomBandwidthSelector;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector.CustomResolutionSelector;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.VerificationCollector;

@RunWith(MockitoJUnitRunner.class)
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
    CustomBandwidthSelector bandwidthSelector = new CustomBandwidthSelector(
        CUSTOM_BANDWIDTH);
    CustomResolutionSelector resolutionSelector = new CustomResolutionSelector(
        CUSTOM_RESOLUTION);
    when(sampler.getMasterUrl()).thenReturn(MASTER_URL);
    when(sampler.isPlayVideoDuration()).thenReturn(true);
    when(sampler.getPlaySeconds()).thenReturn(PLAY_SECONDS);
    when(sampler.getResumeVideoStatus()).thenReturn(true);
    when(sampler.getBandwidthSelector()).thenReturn(bandwidthSelector);
    when(sampler.getResolutionSelector()).thenReturn(resolutionSelector);
    gui.configure(sampler);
    verify(panel).setMasterUrl(MASTER_URL);
    verify(panel).setPlayVideoDuration(true);
    verify(panel).setPlaySeconds(PLAY_SECONDS);
    verify(panel).setResumeStatus(true);
    verify(panel).setBandwidthSelector(bandwidthSelector);
    verify(panel).setResolutionSelector(resolutionSelector);
  }

  @Test
  public void shouldSetTestElementPropertiesWithPanelFieldsWhenModifyTestElement() {

    CustomBandwidthSelector bandwidthSelector = new CustomBandwidthSelector(
        CUSTOM_BANDWIDTH);
    CustomResolutionSelector resolutionSelector = new CustomResolutionSelector(
        CUSTOM_RESOLUTION);
    when(panel.getMasterUrl()).thenReturn(MASTER_URL);
    when(panel.isPlayVideoDuration()).thenReturn(true);
    when(panel.getPlaySeconds()).thenReturn(PLAY_SECONDS);
    when(panel.getResumeVideoStatus()).thenReturn(true);
    when(panel.getBandwidthSelector()).thenReturn(bandwidthSelector);
    when(panel.getResolutionSelector()).thenReturn(resolutionSelector);
    gui.modifyTestElement(sampler);
    verify(sampler).setMasterUrl(MASTER_URL);
    verify(sampler).setPlayVideoDuration(true);
    verify(sampler).setPlaySeconds(PLAY_SECONDS);
    verify(sampler).setResumeVideoStatus(true);
    verify(sampler).setBandwidthSelector(bandwidthSelector);
    verify(sampler).setResolutionSelector(resolutionSelector);
  }

  @Test
  public void shouldClearPanelFieldsWhenClear() {
    gui.clearGui();
    verify(panel).setMasterUrl("");
    verify(panel).setPlayVideoDuration(false);
    verify(panel).setPlaySeconds("");
    verify(panel).setResumeStatus(false);
    verify(panel).setBandwidthSelector(BandwidthSelector.MIN);
    verify(panel).setResolutionSelector(ResolutionSelector.MIN);
  }

}

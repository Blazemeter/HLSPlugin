package com.blazemeter.jmeter.hls.gui;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.Protocol;
import com.google.common.annotations.VisibleForTesting;
import java.awt.BorderLayout;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

public class HlsSamplerGui extends AbstractSamplerGui {

  private HlsSamplerPanel hlsSamplerPanel;

  public HlsSamplerGui() {
    hlsSamplerPanel = new HlsSamplerPanel();
    setLayout(new BorderLayout(0, 5));
    setBorder(makeBorder());
    add(makeTitlePanel(), BorderLayout.NORTH);
    add(hlsSamplerPanel, BorderLayout.CENTER);
  }

  @VisibleForTesting
  public HlsSamplerGui(HlsSamplerPanel panel) {
    hlsSamplerPanel = panel;
  }

  @Override
  public String getStaticLabel() {
    return "bzm - Streaming Sampler";
  }

  @Override
  public String getLabelResource() {
    throw new IllegalStateException("This shouldn't be called");
  }

  @Override
  public TestElement createTestElement() {
    HlsSampler sampler = new HlsSampler();
    modifyTestElement(sampler);
    return sampler;
  }

  @Override
  public void configure(TestElement el) {
    super.configure(el);
    HlsSampler sampler = (HlsSampler) el;
    hlsSamplerPanel.setMasterUrl(sampler.getMasterUrl());
    hlsSamplerPanel.setPlayVideoDuration(sampler.isPlayVideoDuration());
    hlsSamplerPanel.setPlaySeconds(sampler.getPlaySeconds());
    hlsSamplerPanel.setAudioLanguage(sampler.getAudioLanguage());
    hlsSamplerPanel.setSubtitleLanguage(sampler.getSubtitleLanguage());
    hlsSamplerPanel.setBandwidthSelector(sampler.getBandwidthSelector());
    hlsSamplerPanel.setResolutionSelector(sampler.getResolutionSelector());
    hlsSamplerPanel.setResumeStatus(sampler.getResumeVideoStatus());
    hlsSamplerPanel.setProtocolSelector(sampler.getProtocolSelector());
  }

  @Override
  public void modifyTestElement(TestElement s) {
    this.configureTestElement(s);
    if (s instanceof HlsSampler) {
      HlsSampler sampler = (HlsSampler) s;
      sampler.setMasterUrl(hlsSamplerPanel.getMasterUrl());
      sampler.setPlayVideoDuration(hlsSamplerPanel.isPlayVideoDuration());
      sampler.setPlaySeconds(hlsSamplerPanel.getPlaySeconds());
      sampler.setAudioLanguage(hlsSamplerPanel.getAudioLanguage());
      sampler.setSubtitleLanguage(hlsSamplerPanel.getSubtitleLanguage());
      sampler.setBandwidthSelector(hlsSamplerPanel.getBandwidthSelector());
      sampler.setResolutionSelector(hlsSamplerPanel.getResolutionSelector());
      sampler.setResumeVideoStatus(hlsSamplerPanel.getResumeVideoStatus());
      sampler.setProtocolSelector(hlsSamplerPanel.getProtocolSelector());
    }
  }

  @Override
  public void clearGui() {
    super.clearGui();
    hlsSamplerPanel.setMasterUrl("");
    hlsSamplerPanel.setPlayVideoDuration(false);
    hlsSamplerPanel.setPlaySeconds("");
    hlsSamplerPanel.setAudioLanguage("");
    hlsSamplerPanel.setSubtitleLanguage("");
    hlsSamplerPanel.setBandwidthSelector(BandwidthSelector.MIN);
    hlsSamplerPanel.setResolutionSelector(ResolutionSelector.MIN);
    hlsSamplerPanel.setResumeStatus(false);
    hlsSamplerPanel.setProtocolSelector(Protocol.AUTOMATIC);
  }

}

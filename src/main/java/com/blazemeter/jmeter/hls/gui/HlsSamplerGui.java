package com.blazemeter.jmeter.hls.gui;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
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

  public String getStaticLabel() {
    return "bzm - HLS Sampler";
  }

  @Override
  public String getLabelResource() {
    throw new IllegalStateException("This shouldn't be called");
  }

  public TestElement createTestElement() {
    HlsSampler sampler = new HlsSampler();
    modifyTestElement(sampler);
    return sampler;
  }

  public void configure(TestElement el) {
    super.configure(el);
    HlsSampler sampler = (HlsSampler) el;
    hlsSamplerPanel.setMasterUrl(sampler.getMasterUrl());
    hlsSamplerPanel.setPlayVideoDuration(sampler.isPlayVideoDuration());
    hlsSamplerPanel.setPlaySeconds(sampler.getPlaySeconds());
    hlsSamplerPanel.setResumeStatus(sampler.getResumeVideoStatus());
    hlsSamplerPanel.setResolutionSelector(sampler.getResolutionSelector());
    hlsSamplerPanel.setBandwidthSelector(sampler.getBandwidthSelector());
  }

  public void modifyTestElement(TestElement s) {
    this.configureTestElement(s);
    if (s instanceof HlsSampler) {
      HlsSampler sampler = (HlsSampler) s;
      sampler.setMasterUrl(hlsSamplerPanel.getMasterUrl());
      sampler.setPlayVideoDuration(hlsSamplerPanel.isPlayVideoDuration());
      sampler.setPlaySeconds(hlsSamplerPanel.getPlaySeconds());
      sampler.setResumeVideoStatus(hlsSamplerPanel.getResumeVideoStatus());
      sampler.setResolutionSelector(hlsSamplerPanel.getResolutionSelector());
      sampler.setBandwidthSelector(hlsSamplerPanel.getBandwidthSelector());
    }
  }

  @Override
  public void clearGui() {
    super.clearGui();
    hlsSamplerPanel.setMasterUrl("");
    hlsSamplerPanel.setResumeStatus(false);
    hlsSamplerPanel.setPlayVideoDuration(false);
    hlsSamplerPanel.setPlaySeconds("");
    hlsSamplerPanel.setResolutionSelector(ResolutionSelector.MIN);
    hlsSamplerPanel.setBandwidthSelector(BandwidthSelector.MIN);
  }
}

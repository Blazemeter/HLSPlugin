package com.blazemeter.jmeter.hls.gui;

import com.blazemeter.jmeter.hls.logic.HlsSampler;
import java.awt.BorderLayout;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

public class HlsSamplerGui extends AbstractSamplerGui {

  private HlsSamplerPanel hlsSamplerPanel;

  public HlsSamplerGui() {
    hlsSamplerPanel = new HlsSamplerPanel();
    setLayout(new BorderLayout(0, 5));
    setBorder(makeBorder());

    this.add(makeTitlePanel(), BorderLayout.NORTH);
    this.add(hlsSamplerPanel, BorderLayout.CENTER);
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
    hlsSamplerPanel.setUrlData(sampler.getURLData());
    hlsSamplerPanel.setResData(sampler.getResData());
    hlsSamplerPanel.setPlaySecondsData(sampler.getPlaySecondsData());
    hlsSamplerPanel.setVideoDuration(sampler.getVideoDuration());
    hlsSamplerPanel.setVideoType(sampler.getVideoType());
    hlsSamplerPanel.setProtocol(sampler.getProtocol());
    hlsSamplerPanel.setNetData(sampler.getNetwordData());
    hlsSamplerPanel.setResolutionType(sampler.getResolutionType());
    hlsSamplerPanel.setBandwidthType(sampler.getBandwidthType());
    hlsSamplerPanel.setResumeStatus(sampler.getResumeVideoStatus());

  }

  public void modifyTestElement(TestElement s) {
    this.configureTestElement(s);
    if (s instanceof HlsSampler) {
      HlsSampler sampler = (HlsSampler) s;
      sampler.setURLData(hlsSamplerPanel.getUrlData());
      sampler.setResData(hlsSamplerPanel.getResData());
      sampler.setPlaySecondsData(hlsSamplerPanel.getPlaySecondsData());
      sampler.setVideoType(hlsSamplerPanel.videoType());
      sampler.setVideoDuration(hlsSamplerPanel.getVideoDuration());
      sampler.setProtocol(hlsSamplerPanel.getProtocol());
      sampler.setNetworkData(hlsSamplerPanel.getNetData());
      sampler.setResolutionType(hlsSamplerPanel.getResolutionType());
      sampler.setBandwidthType(hlsSamplerPanel.getBandwidthType());
      sampler.setResumeVideoStatus(hlsSamplerPanel.getResumeVideoStatus());
    }

  }

  public void clearGui() {
    super.clearGui();
    this.hlsSamplerPanel.setPlaySecondsData("");
    this.hlsSamplerPanel.setResData("");
    this.hlsSamplerPanel.setUrlData("");
    this.hlsSamplerPanel.setNetData("");
  }

}

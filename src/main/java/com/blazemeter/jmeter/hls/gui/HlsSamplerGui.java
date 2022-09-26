package com.blazemeter.jmeter.hls.gui;

import com.blazemeter.jmeter.hls.logic.HlsSampler;
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
    hlsSamplerPanel.setAudioLanguageOptions(sampler.getAudioLanguageOptions());
    hlsSamplerPanel.setAudioLanguage(sampler.getAudioLanguage());
    hlsSamplerPanel.setSubtitleOptions(sampler.getSubtitleOptions());
    hlsSamplerPanel.setSubtitleLanguage(sampler.getSubtitleLanguage());
    hlsSamplerPanel.setBandwidthOptions(sampler.getBandwidthOptions());
    hlsSamplerPanel.setBandwidthSelected(sampler.getBandwidthSelected());
    hlsSamplerPanel.setResolutionOptions(sampler.getResolutionOptions());
    hlsSamplerPanel.setResolutionSelected(sampler.getResolutionSelected());
    hlsSamplerPanel.setResumeStatus(sampler.getResumeVideoStatus());
    hlsSamplerPanel.setIncludeTypeInHeaders(sampler.getIncludeTypeInHeadersStatus());
    hlsSamplerPanel.setProtocolSelector(sampler.getProtocolSelector());
    hlsSamplerPanel.setVariantsProvider(sampler);
  }

  @Override
  public void modifyTestElement(TestElement s) {
    this.configureTestElement(s);
    if (s instanceof HlsSampler) {
      HlsSampler sampler = (HlsSampler) s;
      sampler.setMasterUrl(hlsSamplerPanel.getMasterUrl());
      sampler.setPlayVideoDuration(hlsSamplerPanel.isPlayVideoDuration());
      sampler.setPlaySeconds(hlsSamplerPanel.getPlaySeconds());
      sampler.setAudioLanguageOptions(hlsSamplerPanel.getAudioLanguageOptions());
      sampler.setAudioLanguage(hlsSamplerPanel.getAudioLanguage());
      sampler.setSubtitleOptions(hlsSamplerPanel.getSubtitleOptions());
      sampler.setSubtitleLanguage(hlsSamplerPanel.getSubtitleLanguage());
      sampler.setBandwidthOptions(hlsSamplerPanel.getBandwidthOptions());
      sampler.setBandwidthSelected(hlsSamplerPanel.getBandwidthSelected());
      sampler.setResolutionOptions(hlsSamplerPanel.getResolutionOptions());
      sampler.setResolutionSelected(hlsSamplerPanel.getResolutionSelected());
      sampler.setResumeVideoStatus(hlsSamplerPanel.getResumeVideoStatus());
      sampler.setIncludeTypeInHeadersStatus(hlsSamplerPanel.getIncludeTypeInHeadersStatus());
      sampler.setProtocolSelector(hlsSamplerPanel.getProtocolSelector());
    }
  }

  @Override
  public void clearGui() {
    super.clearGui();
    hlsSamplerPanel.setMasterUrl("");
    hlsSamplerPanel.setDefaultForAllBoxes();
    hlsSamplerPanel.setPlayVideoDuration(false);
    hlsSamplerPanel.setPlaySeconds("");
    hlsSamplerPanel.setResumeStatus(false);
    hlsSamplerPanel.setIncludeTypeInHeaders(false);
    hlsSamplerPanel.setProtocolSelector(Protocol.AUTOMATIC);
  }

}

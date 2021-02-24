package com.blazemeter.jmeter.hls.gui;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.Protocol;
import java.awt.event.ItemEvent;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

public class HlsSamplerPanel extends JPanel {

  private JTextField masterUrlField;

  private JRadioButton playVideoDurationOption;
  private JTextField playSecondsField;

  private JTextField audioField;
  private JTextField subtitleField;

  private JRadioButton customBandwidthOption;
  private JTextField customBandwidthField;
  private JRadioButton maxBandwidthOption;
  private JRadioButton minBandwidthOption;

  private JRadioButton customResolutionOption;
  private JTextField customResolutionField;
  private JRadioButton minResolutionOption;
  private JRadioButton maxResolutionOption;

  private JRadioButton hlsProtocolOption;
  private JRadioButton mpegDashProtocolOption;
  private JRadioButton automaticProtocolOption;

  private JCheckBox resumeDownloadOption;

  public HlsSamplerPanel() {
    initComponents();
  }

  private void initComponents() {
    JPanel urlPanel = buildUrlPanel();
    JPanel durationPanel = buildDurationPanel();
    JPanel trackPanel = buildTracksPanel();
    JPanel bandwidthPanel = buildBandwidthPanel();
    JPanel resolutionPanel = buildResolutionPanel();
    JPanel resumeDownloadPanel = buildResumeDownloadPanel();
    JPanel protocolSelectionPanel = buildProtocolSelectionPanel();

    BlazeMeterLabsLogo blazeMeterLabsLogo = new BlazeMeterLabsLogo();
    GroupLayout layout = new GroupLayout(this);
    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup()
        .addComponent(urlPanel, GroupLayout.PREFERRED_SIZE,
            GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
        .addComponent(protocolSelectionPanel, GroupLayout.PREFERRED_SIZE,
            GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
        .addGroup(layout.createSequentialGroup()
            .addComponent(durationPanel)
            .addComponent(trackPanel))
        .addGroup(layout.createSequentialGroup()
            .addComponent(bandwidthPanel)
            .addComponent(resolutionPanel))
        .addComponent(resumeDownloadPanel)
        .addComponent(blazeMeterLabsLogo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
            Short.MAX_VALUE)
    );
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addComponent(urlPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
            GroupLayout.PREFERRED_SIZE)
        .addComponent(protocolSelectionPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
            GroupLayout.PREFERRED_SIZE)
        .addGroup(layout.createParallelGroup()
            .addComponent(durationPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE)
            .addComponent(trackPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE))
        .addGroup(layout.createParallelGroup()
            .addComponent(bandwidthPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE)
            .addComponent(resolutionPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE))
        .addComponent(resumeDownloadPanel)
        .addComponent(blazeMeterLabsLogo)
    );
  }

  private JPanel buildUrlPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Video"));

    JLabel urlLabel = new JLabel("URL");
    masterUrlField = namedComponent("masterUrlField", new JTextField(80));

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createSequentialGroup()
        .addComponent(urlLabel)
        .addComponent(masterUrlField));
    layout.setVerticalGroup(layout.createParallelGroup(Alignment.BASELINE)
        .addComponent(urlLabel)
        .addComponent(masterUrlField));
    return panel;
  }

  private static <T extends JComponent> T namedComponent(String name, T component) {
    component.setName(name);
    return component;
  }

  private JPanel buildTracksPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Tracks"));

    JLabel subtitleLabel = new JLabel("Subtitle (e.g.: sp): ");
    JLabel audioLabel = new JLabel("Audio (e.g.: sp): ");

    audioField = namedComponent("audioField", new JTextField());
    subtitleField = namedComponent("subtitleField", new JTextField());

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup()
        .addGroup(layout.createSequentialGroup()
            .addComponent(audioLabel)
            .addComponent(audioField))
        .addGroup(layout.createSequentialGroup()
            .addComponent(subtitleLabel)
            .addComponent(subtitleField)));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup()
            .addComponent(audioLabel)
            .addComponent(audioField))
        .addGroup(layout.createParallelGroup()
            .addComponent(subtitleLabel)
            .addComponent(subtitleField)));

    return panel;
  }

  private JPanel buildDurationPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Duration"));

    JRadioButton playWholeVideoOption = namedComponent("playWholeVideoOption",
        new JRadioButton("Whole video", true));
    playSecondsField = namedComponent("playSecondsField", new JTextField());
    playVideoDurationOption = namedComponent("playVideoDurationOption",
        new JRadioButton("Video duration (seconds):"));

    ButtonGroup wholePartRadiosGroup = new ButtonGroup();
    wholePartRadiosGroup.add(playWholeVideoOption);
    wholePartRadiosGroup.add(playVideoDurationOption);

    playSecondsField.setEnabled(false);
    playVideoDurationOption.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        playSecondsField.setEnabled(true);
      } else if (e.getStateChange() == ItemEvent.DESELECTED) {
        playSecondsField.setText("");
        playSecondsField.setEnabled(false);
      }
      validate();
      repaint();
    });

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup()
        .addComponent(playWholeVideoOption)
        .addGroup(layout.createSequentialGroup()
            .addComponent(playVideoDurationOption)
            .addComponent(playSecondsField)));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addComponent(playWholeVideoOption)
        .addGroup(layout.createParallelGroup()
            .addComponent(playVideoDurationOption)
            .addComponent(playSecondsField)));
    return panel;
  }

  private JPanel buildBandwidthPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Bandwidth"));

    maxBandwidthOption = namedComponent("maxBandwidthOption",
        new JRadioButton("Max bandwidth available"));
    minBandwidthOption = namedComponent("minBandwidthOption",
        new JRadioButton("Min bandwidth available", true));
    customBandwidthOption = namedComponent("customBandwidthOption",
        new JRadioButton("Custom bandwidth (bits/sec): "));
    customBandwidthField = namedComponent("customBandwidthField",
        new JTextField());

    ButtonGroup bandwidthRadiosGroup = new ButtonGroup();
    bandwidthRadiosGroup.add(customBandwidthOption);
    bandwidthRadiosGroup.add(minBandwidthOption);
    bandwidthRadiosGroup.add(maxBandwidthOption);

    customBandwidthField.setEnabled(false);
    customBandwidthOption.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        customBandwidthField.setEnabled(true);
      } else if (e.getStateChange() == ItemEvent.DESELECTED) {
        customBandwidthField.setText("");
        customBandwidthField.setEnabled(false);
      }
      validate();
      repaint();
    });

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup()
        .addGroup(layout.createSequentialGroup()
            .addComponent(customBandwidthOption)
            .addComponent(customBandwidthField))
        .addComponent(minBandwidthOption)
        .addComponent(maxBandwidthOption));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup()
            .addComponent(customBandwidthOption)
            .addComponent(customBandwidthField))
        .addComponent(minBandwidthOption)
        .addComponent(maxBandwidthOption));
    return panel;
  }

  private JPanel buildResolutionPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Resolution"));

    maxResolutionOption = namedComponent("maxResolutionOption",
        new JRadioButton("Max resolution available"));
    minResolutionOption = namedComponent("minResolutionOption",
        new JRadioButton("Min resolution available", true));
    customResolutionOption = namedComponent("customResolutionOption",
        new JRadioButton("Custom resolution (e.g.: 640x480): "));
    customResolutionField = namedComponent("customResolutionField",
        new JTextField());

    ButtonGroup resolutionRadiosGroup = new ButtonGroup();
    resolutionRadiosGroup.add(customResolutionOption);
    resolutionRadiosGroup.add(maxResolutionOption);
    resolutionRadiosGroup.add(minResolutionOption);

    customResolutionField.setEnabled(false);
    customResolutionOption.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        customResolutionField.setEnabled(true);
      } else if (e.getStateChange() == ItemEvent.DESELECTED) {
        customResolutionField.setText("");
        customResolutionField.setEnabled(false);
      }
      validate();
      repaint();
    });

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup()
        .addGroup(layout.createSequentialGroup()
            .addComponent(customResolutionOption)
            .addComponent(customResolutionField))
        .addComponent(minResolutionOption)
        .addComponent(maxResolutionOption));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup()
            .addComponent(customResolutionOption)
            .addComponent(customResolutionField))
        .addComponent(minResolutionOption)
        .addComponent(maxResolutionOption));
    return panel;
  }

  private JPanel buildResumeDownloadPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Other"));

    resumeDownloadOption = namedComponent("resumeDownloadOption", new JCheckBox(
        "Resume video download between iterations"));

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup()
        .addComponent(resumeDownloadOption));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addComponent(resumeDownloadOption));
    return panel;
  }

  private JPanel buildProtocolSelectionPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Protocol"));

    automaticProtocolOption = namedComponent("automaticProtocolOption",
        new JRadioButton(Protocol.AUTOMATIC.toString()));
    hlsProtocolOption = namedComponent("hlsProtocolOption",
        new JRadioButton(Protocol.HLS.toString()));
    mpegDashProtocolOption = namedComponent("mpegDashProtocolOption",
        new JRadioButton(Protocol.MPEG_DASH.toString()));

    ButtonGroup protocolRadiosGroup = new ButtonGroup();
    protocolRadiosGroup.add(automaticProtocolOption);
    protocolRadiosGroup.add(hlsProtocolOption);
    protocolRadiosGroup.add(mpegDashProtocolOption);

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createSequentialGroup()
        .addComponent(automaticProtocolOption)
        .addComponent(hlsProtocolOption)
        .addComponent(mpegDashProtocolOption));
    layout.setVerticalGroup(layout.createParallelGroup()
        .addComponent(automaticProtocolOption)
        .addComponent(hlsProtocolOption)
        .addComponent(mpegDashProtocolOption));
    return panel;
  }

  public void setMasterUrl(String masterUrl) {
    masterUrlField.setText(masterUrl);
  }

  public String getMasterUrl() {
    return masterUrlField.getText();
  }

  public boolean isPlayVideoDuration() {
    return playVideoDurationOption.isSelected();
  }

  public void setPlayVideoDuration(boolean check) {
    playVideoDurationOption.setSelected(check);
  }

  public String getPlaySeconds() {
    return playSecondsField.getText();
  }

  public void setPlaySeconds(String seconds) {
    playSecondsField.setText(seconds);
  }

  public String getAudioLanguage() {
    return audioField.getText();
  }

  public void setAudioLanguage(String audioLanguage) {
    this.audioField.setText(audioLanguage);
  }

  public String getSubtitleLanguage() {
    return subtitleField.getText();
  }

  public void setSubtitleLanguage(String subtitleLanguage) {
    this.subtitleField.setText(subtitleLanguage);
  }

  public BandwidthSelector getBandwidthSelector() {
    if (customBandwidthOption.isSelected()) {
      return new BandwidthSelector.CustomBandwidthSelector(customBandwidthField.getText());
    } else if (minBandwidthOption.isSelected()) {
      return BandwidthSelector.MIN;
    } else {
      return BandwidthSelector.MAX;
    }
  }

  public void setBandwidthSelector(BandwidthSelector option) {
    if (option == BandwidthSelector.MIN) {
      minBandwidthOption.setSelected(true);
    } else if (option == BandwidthSelector.MAX) {
      maxBandwidthOption.setSelected(true);
    } else {
      customBandwidthOption.setSelected(true);
      customBandwidthField.setText(option.getCustomBandwidth());
    }
  }

  public ResolutionSelector getResolutionSelector() {
    if (customResolutionOption.isSelected()) {
      return new ResolutionSelector.CustomResolutionSelector(customResolutionField.getText());
    } else if (minResolutionOption.isSelected()) {
      return ResolutionSelector.MIN;
    } else {
      return ResolutionSelector.MAX;
    }
  }

  public void setResolutionSelector(ResolutionSelector option) {
    if (option == ResolutionSelector.MIN) {
      minResolutionOption.setSelected(true);
    } else if (option == ResolutionSelector.MAX) {
      maxResolutionOption.setSelected(true);
    } else {
      customResolutionOption.setSelected(true);
      customResolutionField.setText(option.getCustomResolution());
    }
  }

  public Protocol getProtocolSelector() {
    if (mpegDashProtocolOption.isSelected()) {
      return Protocol.MPEG_DASH;
    } else if (hlsProtocolOption.isSelected()) {
      return Protocol.HLS;
    } else {
      return Protocol.AUTOMATIC;
    }
  }

  public void setProtocolSelector(Protocol option) {
    if (option == Protocol.AUTOMATIC) {
      automaticProtocolOption.setSelected(true);
    } else if (option == Protocol.HLS) {
      hlsProtocolOption.setSelected(true);
    } else {
      mpegDashProtocolOption.setSelected(true);
    }
  }

  public boolean getResumeVideoStatus() {
    return resumeDownloadOption.isSelected();
  }

  public void setResumeStatus(boolean check) {
    resumeDownloadOption.setSelected(check);
  }

}

package com.blazemeter.jmeter.hls.gui;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import java.awt.event.ItemEvent;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;

public class HlsSamplerPanel extends JPanel {

  private JTextField masterUrlField = new JTextField();

  private JRadioButton playVideoDurationOption = new JRadioButton("Video duration (seconds):");
  private JTextField playSecondsField = new JTextField();

  private JRadioButton customResolutionOption = new JRadioButton("Custom resolution: ");
  private JTextField customResolutionField = new JTextField();
  private JRadioButton maxResolutionOption = new JRadioButton("Max resolution available");
  private JRadioButton minResolutionOption = new JRadioButton("Min resolution available", true);

  private JCheckBox resumeDownloadOption = new JCheckBox(
      "Resume video download between iterations");

  private JRadioButton customBandwidthOption = new JRadioButton("Custom bandwidth: ");
  private JTextField customBandwidthField = new JTextField();
  private JRadioButton maxBandwidthOption = new JRadioButton("Max bandwidth available");
  private JRadioButton minBandwidthOption = new JRadioButton("Min bandwidth available", true);

  public HlsSamplerPanel() {
    initComponents();
  }

  private void initComponents() {
    JPanel videoPanel = buildVideoPanel();
    JPanel playOptions = buildPlayOptionsPanel();
    JPanel networkOptions = buildNetworkOptionsPanel();
    BlazeMeterLabsLogo blazeMeterLabsLogo = new BlazeMeterLabsLogo();
    GroupLayout layout = new GroupLayout(this);
    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addComponent(videoPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
            Short.MAX_VALUE)
        .addGroup(layout.createSequentialGroup()
            .addComponent(playOptions, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE)
            .addComponent(networkOptions, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE))
        .addComponent(blazeMeterLabsLogo, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
            Short.MAX_VALUE)

    );
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addComponent(videoPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
            GroupLayout.PREFERRED_SIZE)
        .addGroup(layout.createParallelGroup()
            .addComponent(networkOptions, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE)
            .addComponent(playOptions, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE))
        .addComponent(blazeMeterLabsLogo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
            GroupLayout.PREFERRED_SIZE)
    );
  }

  private JPanel buildVideoPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Video"));
    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    JLabel urlFieldLabel = new JLabel("URL  ");
    layout.setHorizontalGroup(layout.createSequentialGroup()
        .addComponent(urlFieldLabel)
        .addComponent(masterUrlField));
    layout.setVerticalGroup(layout.createParallelGroup()
        .addComponent(urlFieldLabel)
        .addComponent(masterUrlField));
    return panel;
  }

  private JPanel buildPlayOptionsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Play options"));

    ButtonGroup wholePartRadiosGroup = new ButtonGroup();
    JRadioButton playWholeVideoOption = new JRadioButton("Whole video", true);
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
    layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addComponent(playWholeVideoOption)
        .addGroup(layout.createSequentialGroup()
            .addComponent(playVideoDurationOption)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(playSecondsField))
        .addGroup(layout.createSequentialGroup()
            .addComponent(customResolutionOption)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(customResolutionField))
        .addComponent(minResolutionOption)
        .addComponent(maxResolutionOption)
        .addComponent(resumeDownloadOption));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addComponent(playWholeVideoOption)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(playVideoDurationOption)
            .addComponent(playSecondsField))
        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(customResolutionOption)
            .addComponent(customResolutionField))
        .addComponent(minResolutionOption)
        .addComponent(maxResolutionOption)
        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(resumeDownloadOption));
    return panel;
  }

  private JPanel buildNetworkOptionsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Network options"));

    JLabel bitsPerSecond = new JLabel("bits / s");
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
    layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addGroup(layout.createSequentialGroup()
            .addComponent(customBandwidthOption)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(customBandwidthField)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(bitsPerSecond))
        .addComponent(minBandwidthOption)
        .addComponent(maxBandwidthOption));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(customBandwidthOption)
            .addComponent(customBandwidthField)
            .addComponent(bitsPerSecond))
        .addComponent(minBandwidthOption)
        .addComponent(maxBandwidthOption));
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

  public boolean getResumeVideoStatus() {
    return resumeDownloadOption.isSelected();
  }

  public void setResumeStatus(boolean check) {
    resumeDownloadOption.setSelected(check);
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
      String customResolution = option.getCustomResolution();
      customResolutionField.setText(customResolution == null ? "" : customResolution);
    }
  }

  public BandwidthSelector getBandwidthSelector() {
    if (customBandwidthOption.isSelected()) {
      String bandwidth = customBandwidthField.getText();
      return new BandwidthSelector.CustomBandwidthSelector(
          !bandwidth.isEmpty() ? Integer.valueOf(bandwidth) : null);
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
      Integer customBandwidth = option.getCustomBandwidth();
      customBandwidthField.setText(customBandwidth == null ? "" : customBandwidth.toString());
    }
  }

}

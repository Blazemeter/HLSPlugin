package com.blazemeter.jmeter.hls.gui;

import com.blazemeter.jmeter.hls.logic.BandwidthOption;
import com.blazemeter.jmeter.hls.logic.ResolutionOption;
import com.blazemeter.jmeter.hls.logic.VideoType;
import java.awt.event.ItemEvent;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;

public class HlsSamplerPanel extends JPanel {

  private JTextField urlField = new JTextField();
  private JTextField resolField = new JTextField();
  private JTextField playSecondsField = new JTextField();
  private JTextField bandwidthField = new JTextField();

  private JRadioButton rPlayPartBtn = new JRadioButton("Video duration (seconds):");

  private JRadioButton rVodStream = new JRadioButton("VOD", true);
  private JRadioButton rliveStream = new JRadioButton("Live Stream");
  private JRadioButton rEventStream = new JRadioButton("Event Stream");

  private JRadioButton rCustomResol = new JRadioButton("Custom Resolution: ");
  private JRadioButton rMaximumResol = new JRadioButton("Max resolution available");
  private JRadioButton rMinimumResol = new JRadioButton("Min resolution available", true);
  private JRadioButton rResumeDownload = new JRadioButton("Resume video Download");

  private JRadioButton rCustomBandwidth = new JRadioButton("Custom Bandwidth: ");
  private JRadioButton rMaximumBandwidth = new JRadioButton("Max bandwidth available");
  private JRadioButton rMinimumBandwidth = new JRadioButton("Min bandwidth available", true);

  private JComboBox<String> jProtocolCombo = new JComboBox<>(new String[]{"https", "http"});

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
    this.setLayout(layout);
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
        .addComponent(videoPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
            GroupLayout.DEFAULT_SIZE)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(networkOptions, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE)
            .addComponent(playOptions, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE))
        .addComponent(blazeMeterLabsLogo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
            GroupLayout.PREFERRED_SIZE)
    );
  }

  private JPanel buildVideoPanel() {
    JLabel urlFieldLabel = new JLabel("URL  ");
    ButtonGroup videoTypeRadiosGroup = new ButtonGroup();
    videoTypeRadiosGroup.add(rVodStream);
    videoTypeRadiosGroup.add(rliveStream);
    videoTypeRadiosGroup.add(rEventStream);

    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Video"));
    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createSequentialGroup()
        .addComponent(urlFieldLabel)
        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(urlField)
        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(rVodStream)
        .addComponent(rliveStream)
        .addComponent(rEventStream));
    layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(urlFieldLabel)
        .addComponent(urlField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
            GroupLayout.PREFERRED_SIZE)
        .addComponent(rVodStream)
        .addComponent(rliveStream)
        .addComponent(rEventStream));
    return panel;
  }

  private JPanel buildPlayOptionsPanel() {
    JRadioButton rPlayVideoBtn = new JRadioButton("Whole video", true);
    ButtonGroup wholePartRadiosGroup = new ButtonGroup();
    wholePartRadiosGroup.add(rPlayVideoBtn);
    wholePartRadiosGroup.add(rPlayPartBtn);

    playSecondsField.setEnabled(false);
    rPlayPartBtn.addItemListener(e -> {
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
    resolutionRadiosGroup.add(rCustomResol);
    resolutionRadiosGroup.add(rMaximumResol);
    resolutionRadiosGroup.add(rMinimumResol);

    resolField.setEnabled(false);
    rCustomResol.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        resolField.setEnabled(true);
      } else if (e.getStateChange() == ItemEvent.DESELECTED) {
        resolField.setText("");
        resolField.setEnabled(false);
      }
      validate();
      repaint();
    });

    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Play Options"));
    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addComponent(rPlayVideoBtn)
        .addGroup(layout.createSequentialGroup()
            .addComponent(rPlayPartBtn)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(playSecondsField))
        .addGroup(layout.createSequentialGroup()
            .addComponent(rCustomResol)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(resolField))
        .addComponent(rMinimumResol)
        .addComponent(rMaximumResol)
        .addComponent(rResumeDownload));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addComponent(rPlayVideoBtn)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(rPlayPartBtn)
            .addComponent(playSecondsField))
        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(rCustomResol)
            .addComponent(resolField))
        .addComponent(rMinimumResol)
        .addComponent(rMaximumResol)
        .addComponent(rResumeDownload));
    return panel;
  }

  private JPanel buildNetworkOptionsPanel() {
    JLabel bitsPerSecond = new JLabel("bits / s");
    JLabel protocol = new JLabel("Protocol");

    ButtonGroup bandwidthRadiosGroup = new ButtonGroup();
    bandwidthRadiosGroup.add(rCustomBandwidth);
    bandwidthRadiosGroup.add(rMinimumBandwidth);
    bandwidthRadiosGroup.add(rMaximumBandwidth);

    bandwidthField.setEnabled(false);
    rCustomBandwidth.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        bandwidthField.setEnabled(true);
      } else if (e.getStateChange() == ItemEvent.DESELECTED) {
        bandwidthField.setText("");
        bandwidthField.setEnabled(false);
      }
      validate();
      repaint();
    });

    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Network Options"));
    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addGroup(layout.createSequentialGroup()
            .addComponent(protocol)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jProtocolCombo))
        .addGroup(layout.createSequentialGroup()
            .addComponent(rCustomBandwidth)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(bandwidthField)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(bitsPerSecond))
        .addComponent(rMinimumBandwidth)
        .addComponent(rMaximumBandwidth));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup()
            .addComponent(protocol, 25, 25, 25)
            .addComponent(jProtocolCombo))
        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(rCustomBandwidth)
            .addComponent(bandwidthField)
            .addComponent(bitsPerSecond))
        .addComponent(rMinimumBandwidth)
        .addComponent(rMaximumBandwidth));
    return panel;
  }

  public void setUrlData(String urlData) {
    urlField.setText(urlData);
  }

  public String getUrlData() {
    return urlField.getText();
  }

  public void setResData(String resData) {
    resolField.setText(resData);
  }

  public String getResData() {
    return resolField.getText();
  }

  public void setNetData(String netData) {
    bandwidthField.setText(netData);
  }

  public String getNetData() {
    return bandwidthField.getText();
  }

  public void setPlaySecondsData(String seconds) {
    playSecondsField.setText(seconds);
  }

  public String getPlaySecondsData() {
    return playSecondsField.getText();
  }

  public void setVideoDuration(boolean check) {
    rPlayPartBtn.setSelected(check);
  }

  public void setResumeStatus(boolean check) {
    rResumeDownload.setSelected(check);
  }

  public boolean getVideoDuration() {
    return rPlayPartBtn.isSelected();
  }

  public void setVideoType(VideoType option) {
    switch (option) {
      case EVENT:
        rEventStream.setSelected(true);
        break;
      case LIVE:
        rliveStream.setSelected(true);
        break;
      default:
        rVodStream.setSelected(true);
    }
  }

  public void setResolutionType(ResolutionOption option) {
    switch (option) {
      case MIN:
        rMinimumResol.setSelected(true);
        break;
      case MAX:
        rMaximumResol.setSelected(true);
        break;
      default:
        rCustomResol.setSelected(true);
    }
  }

  public void setBandwidthType(BandwidthOption option) {
    switch (option) {
      case MIN:
        rMinimumBandwidth.setSelected(true);
        break;
      case MAX:
        rMaximumBandwidth.setSelected(true);
        break;
      default:
        rCustomBandwidth.setSelected(true);
    }
  }

  public void setProtocol(String protocolValue) {
    jProtocolCombo.setSelectedItem(protocolValue);
  }

  public String getProtocol() {
    return jProtocolCombo.getSelectedItem().toString();
  }

  public ResolutionOption getResolutionType() {
    if (rCustomResol.isSelected()) {
      return ResolutionOption.CUSTOM;
    } else if (rMinimumResol.isSelected()) {
      return ResolutionOption.MIN;
    } else {
      return ResolutionOption.MAX;
    }
  }

  public BandwidthOption getBandwidthType() {
    if (rCustomBandwidth.isSelected()) {
      return BandwidthOption.CUSTOM;
    } else if (rMinimumBandwidth.isSelected()) {
      return BandwidthOption.MIN;
    } else {
      return BandwidthOption.MAX;
    }
  }

  public VideoType videoType() {
    if (rVodStream.isSelected()) {
      return VideoType.VOD;
    } else if (rliveStream.isSelected()) {
      return VideoType.LIVE;
    } else {
      return VideoType.EVENT;
    }
  }
}

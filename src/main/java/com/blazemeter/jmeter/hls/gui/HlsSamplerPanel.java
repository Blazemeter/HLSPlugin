package com.blazemeter.jmeter.hls.gui;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector.CustomResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.Protocol;
import com.blazemeter.jmeter.videostreaming.core.Variants;
import com.blazemeter.jmeter.videostreaming.core.VariantsProvider;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;

import org.apache.jorphan.gui.JLabeledChoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HlsSamplerPanel extends JPanel implements ActionListener {

  private static final Logger LOG = LoggerFactory
      .getLogger(HlsSamplerPanel.class);

  private static final String LOAD = "LOAD";
  private JTextField masterUrlField;

  private JRadioButton playVideoDurationOption;
  private JTextField playSecondsField;

  private JLabeledChoice audioBox;
  private JLabeledChoice subtitleBox;

  private JLabeledChoice bandwidthBox;
  private JRadioButton customBandwidthOption;
  private JTextField customBandwidthField;
  private JRadioButton maxBandwidthOption;
  private JRadioButton minBandwidthOption;

  private JLabeledChoice resolutionBox;
  private JRadioButton customResolutionOption;
  private JTextField customResolutionField;
  private JRadioButton minResolutionOption;
  private JRadioButton maxResolutionOption;

  private JRadioButton hlsProtocolOption;
  private JRadioButton mpegDashProtocolOption;
  private JRadioButton automaticProtocolOption;

  private JCheckBox resumeDownloadOption;
  private JCheckBox includeTypeInHeaders;
  private final String min = "Min";
  private final String max = "Max";

  private VariantsProvider variantsProvider;

  public HlsSamplerPanel() {
    initComponents();
  }

  public void setVariantsProvider(VariantsProvider variantsProvider) {
    this.variantsProvider = variantsProvider;
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
            .addComponent(resolutionPanel)
            .addComponent(resumeDownloadPanel))
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
                GroupLayout.PREFERRED_SIZE)
            .addComponent(resumeDownloadPanel))
        .addComponent(blazeMeterLabsLogo)
    );
  }

  private JPanel buildUrlPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Video"));

    JLabel urlLabel = new JLabel("URL");
    masterUrlField = namedComponent("masterUrlField", new JTextField(60));

    JButton loadButton = namedComponent("loadButton", new JButton("Load Playlist"));
    loadButton.setActionCommand(LOAD);
    loadButton.addActionListener(this);

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createSequentialGroup()
        .addComponent(urlLabel)
        .addComponent(masterUrlField)
        .addComponent(loadButton));
    layout.setVerticalGroup(layout.createParallelGroup(Alignment.BASELINE)
        .addComponent(urlLabel)
        .addComponent(masterUrlField)
        .addComponent(loadButton));
    return panel;
  }

  private static <T extends JComponent> T namedComponent(String name, T component) {
    component.setName(name);
    return component;
  }

  private JPanel buildTracksPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Tracks"));

    JLabel subtitleLabel = new JLabel("Subtitle: ");
    JLabel audioLabel = new JLabel("Audio: ");

    audioBox = namedComponent("audioBox", new JLabeledChoice());
    subtitleBox = namedComponent("subtitleBox", new JLabeledChoice());
    String[] items = {"Default"};
    audioBox.setValues(items);
    subtitleBox.setValues(items);

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup()
        .addGroup(layout.createParallelGroup()
            .addComponent(audioLabel)
            .addComponent(audioBox)
            .addGroup(layout.createParallelGroup()
                .addComponent(subtitleLabel)
                .addComponent(subtitleBox))));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addGroup(layout.createSequentialGroup()
            .addComponent(audioLabel)
            .addComponent(audioBox))
        .addGroup(layout.createSequentialGroup()
            .addComponent(subtitleLabel)
            .addComponent(subtitleBox)));

    audioBox.setEditable(true);
    subtitleBox.setEditable(true);

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

    bandwidthBox = namedComponent("bandwidthBox", new JLabeledChoice());
    bandwidthBox.setEditable(true);
    bandwidthBox.addValue(min);
    bandwidthBox.addValue(max);

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
            .addComponent(bandwidthBox)));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup()
            .addComponent(bandwidthBox)));
    return panel;
  }

  private JPanel buildResolutionPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Resolution"));

    resolutionBox = namedComponent("resolutionBox", new JLabeledChoice());
    resolutionBox.setEditable(true);
    resolutionBox.addValue(min);
    resolutionBox.addValue(max);

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
            .addComponent(resolutionBox)));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup()
            .addComponent(resolutionBox)));

    return panel;
  }

  private JPanel buildResumeDownloadPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder("Other"));

    resumeDownloadOption = namedComponent("resumeDownloadOption", new JCheckBox(
        "Resume video download between iterations"));
    includeTypeInHeaders = namedComponent("includeTypeInHeaders", new JCheckBox(
        "Include video type in request and response headers"));
    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup()
        .addComponent(resumeDownloadOption)
        .addComponent(includeTypeInHeaders));
    layout.setVerticalGroup(layout.createSequentialGroup()
        .addComponent(resumeDownloadOption)
        .addComponent(includeTypeInHeaders));
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
    return audioBox.getText();
  }

  public void setAudioLanguage(String audioLanguage) {
    this.audioBox.setText(audioLanguage);
  }

  public String getSubtitleLanguage() {
    return subtitleBox.getText();
  }

  public void setSubtitleLanguage(String subtitleLanguage) {
    this.subtitleBox.setText(subtitleLanguage);
  }

  public BandwidthSelector getBandwidthSelector() {
    String bandwidth = bandwidthBox.getText();
    switch (bandwidth) {
      case "Max":
        return BandwidthSelector.MAX;
      case "Min":
        return BandwidthSelector.MIN;
      default:
        return new BandwidthSelector.CustomBandwidthSelector(bandwidth);
    }
  }

  public void setBandwidthSelector(BandwidthSelector option) {
    if (option == BandwidthSelector.MIN) {
      minBandwidthOption.setSelected(true);
    } else if (option == BandwidthSelector.MAX) {
      maxBandwidthOption.setSelected(true);
    } else {
      customBandwidthOption.setSelected(true);
      bandwidthBox.setText(option.getCustomBandwidth());
    }
  }

  public ResolutionSelector getResolutionSelector() {
    String resolution = resolutionBox.getText();
    switch (resolution) {
      case "Max":
        return ResolutionSelector.MAX;
      case "Min":
        return ResolutionSelector.MIN;
      default:
        return new CustomResolutionSelector(resolution);
    }
  }

  public void setResolutionSelector(ResolutionSelector option) {
    if (option == ResolutionSelector.MIN) {
      minResolutionOption.setSelected(true);
    } else if (option == ResolutionSelector.MAX) {
      maxResolutionOption.setSelected(true);
    } else {
      customResolutionOption.setSelected(true);
      resolutionBox.setText(option.getCustomResolution());
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

  public boolean getIncludeTypeInHeadersStatus() {
    return includeTypeInHeaders.isSelected();
  }

  public void setResumeStatus(boolean check) {
    resumeDownloadOption.setSelected(check);
  }

  public void setIncludeTypeInHeaders(boolean check) {
    includeTypeInHeaders.setSelected(check);
  }

  // implemented for avoiding loading the url when changing context
  public String getAudioLanguageOptions() {
    return String.join(",", audioBox.getItems());
  }

  public void setAudioLanguageOptions(String audioOptions) {
    this.audioBox.setValues(audioOptions.split(","));
  }

  public String getSubtitleOptions() {
    return String.join(",", subtitleBox.getItems());
  }

  public void setSubtitleOptions(String subtitleOptions) {
    this.subtitleBox.setValues(subtitleOptions.split(","));
  }

  public String getBandwidthOptions() {
    return String.join(",", bandwidthBox.getItems());
  }

  public void setBandwidthOptions(String bandwidthOptions) {
    this.bandwidthBox.setValues(bandwidthOptions.split(","));
  }

  public String getBandwidthSelected() {
    return bandwidthBox.getText();
  }

  public void setBandwidthSelected(String selected) {
    this.bandwidthBox.setText(selected);
  }

  public String getResolutionOptions() {
    return String.join(",", resolutionBox.getItems());
  }

  public void setResolutionOptions(String resolutionOptions) {
    this.resolutionBox.setValues((resolutionOptions.split(",")));
  }

  public String getResolutionSelected() {
    return resolutionBox.getText();
  }

  public void setResolutionSelected(String selected) {
    this.resolutionBox.setText(selected);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (LOAD.equals(e.getActionCommand())) {
      setDefaultForAllBoxes();
      Variants variants = null;
      try {
        variants = variantsProvider.getVariantsFromURL(getMasterUrl());
      } catch (NullPointerException | IllegalArgumentException
               | PlaylistParsingException | PlaylistDownloadException ex) {
        LOG.error("Error obtaining the playlist's variants or invalid URL", ex);
        JOptionPane.showMessageDialog(this,
            "There was an error while trying to load the URL, check the logs.");
      }

      setupComboBox(variants.getAudioLanguageList(), audioBox);
      setupComboBox(variants.getSubtitleList(), subtitleBox);
      setupComboBox(variants.getBandwidthList(), bandwidthBox);
      setupComboBox(variants.getResolutionList(), resolutionBox);
      bandwidthBox.addChangeListener(bandwidthListener(bandwidthBox, resolutionBox,
          variants.getBandwidthResolutionMap()));
      LOG.info("Finished loading variants");
    }
  }

  private void setupComboBox(List<String> list, JLabeledChoice box) {
    box.setToolTipText("This field is editable");
    setDefaultValues(box);
    clearComboBox(box);

    if (!list.isEmpty()) {
      list.stream().filter(Objects::nonNull).forEach(box::addValue);
    } else {
      setDefaultValues(box);
    }
  }

  private ChangeListener bandwidthListener(JLabeledChoice bandwidthBox,
                                           JLabeledChoice resolutionBox, Map<String,
                                           String> bandwidthResolutionMap) {
    Set<String> newResolutions = new HashSet<>();
    return e -> {
      String resolutionSelection = resolutionBox != null ? resolutionBox.getText() : "";
      clearComboBox(resolutionBox);
      newResolutions.clear();
      if (bandwidthResolutionMap.get(bandwidthBox.getText()) != null) {
        for (String key : bandwidthResolutionMap.keySet()) {
          if (bandwidthBox.getText().equals(key)) {
            newResolutions.add(bandwidthResolutionMap.get(key));
          }
        }
      }
      newResolutions.add(min);
      newResolutions.add(max);

      resolutionBox.setValues(newResolutions.toArray(new String[0]));
      if (newResolutions.contains(resolutionSelection)) {
        resolutionBox.setText(resolutionSelection);
      }
    };
  }

  private void setDefaultValues(JLabeledChoice box) {
    String[] minMax = new String[]{min, max};
    if (box == subtitleBox || box == audioBox) {
      box.setText("Default");
    } else {
      box.setValues(minMax);
    }
  }

  public void setDefaultForAllBoxes() {
    clearAllComboBoxes();
    setDefaultValues(audioBox);
    setDefaultValues(subtitleBox);
    setDefaultValues(bandwidthBox);
    setDefaultValues(resolutionBox);
  }

  private void clearComboBox(JLabeledChoice box) {
    String[] empty = new String[0];
    box.setValues(empty);
  }

  private void clearAllComboBoxes() {
    clearComboBox(audioBox);
    clearComboBox(subtitleBox);
    clearComboBox(bandwidthBox);
    clearComboBox(resolutionBox);
  }
}

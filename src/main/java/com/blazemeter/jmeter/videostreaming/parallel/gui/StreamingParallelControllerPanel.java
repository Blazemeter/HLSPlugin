package com.blazemeter.jmeter.videostreaming.parallel.gui;

import com.blazemeter.jmeter.commons.BlazemeterLabsLogo;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class StreamingParallelControllerPanel extends JPanel {

  private static final String PLUGIN_REPOSITORY_URL = "https://github.com/Blazemeter/HLSPlugin";
  private static final String HELP_TEXT =
      "Runs a heartbeat branch in parallel with a streaming playback on a single thread.\n\n"
          + "How to use:\n"
          + "- Add a 'bzm - Streaming Sampler' as a DIRECT child of this controller (the playback "
          + "track).\n"
          + "- Add the periodic requests (the heartbeat) as the remaining children. They may be "
          + "grouped under Transaction/Logic controllers.\n"
          + "- Set the heartbeat interval (in seconds). The heartbeat branch runs once every "
          + "interval while playback continues in between.\n\n" + "Notes:\n"
          + "- Do not add Timers to the heartbeat branch: they block the shared thread and add "
          + "drift.\n"
          + "- Heartbeat and playback share one thread, so a slow heartbeat delays segment "
          + "polling.";

  private final JTextField intervalField = new JTextField();
  private final JCheckBox runImmediatelyBox = new JCheckBox(
      "Run heartbeat immediately (do not wait the first interval)");

  public StreamingParallelControllerPanel() {
    setLayout(new GridBagLayout());
    buildLayout();
  }

  public String getInterval() {
    return intervalField.getText();
  }

  public void setInterval(String interval) {
    intervalField.setText(interval);
  }

  public boolean isRunImmediately() {
    return runImmediatelyBox.isSelected();
  }

  public void setRunImmediately(boolean runImmediately) {
    runImmediatelyBox.setSelected(runImmediately);
  }

  private void buildLayout() {
    add(buildSettingsPanel(), constraintsForTopSection(0));
    add(buildHelpPanel(), constraintsForTopSection(1));
    add(new BlazemeterLabsLogo(PLUGIN_REPOSITORY_URL), constraintsForBottomLogo(2));
    add(new JPanel(), constraintsForFlexibleBottomFiller(3));
  }

  private GridBagConstraints constraintsForTopSection(int row) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.insets = new Insets(0, 0, 6, 0);
    return constraints;
  }

  private GridBagConstraints constraintsForBottomLogo(int row) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.NONE;
    constraints.anchor = GridBagConstraints.CENTER;
    return constraints;
  }

  private GridBagConstraints constraintsForFlexibleBottomFiller(int row) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.fill = GridBagConstraints.BOTH;
    return constraints;
  }

  private JPanel buildSettingsPanel() {
    JPanel settings = new JPanel(new GridBagLayout());
    settings.setBorder(BorderFactory.createTitledBorder("Heartbeat settings"));

    GridBagConstraints labelConstraints = new GridBagConstraints();
    labelConstraints.gridx = 0;
    labelConstraints.gridy = 0;
    labelConstraints.anchor = GridBagConstraints.WEST;
    labelConstraints.insets = new Insets(2, 2, 2, 5);

    GridBagConstraints fieldConstraints = new GridBagConstraints();
    fieldConstraints.gridx = 1;
    fieldConstraints.gridy = 0;
    fieldConstraints.weightx = 1;
    fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
    fieldConstraints.insets = new Insets(2, 2, 2, 2);

    settings.add(new JLabel("Heartbeat interval (seconds):"), labelConstraints);
    intervalField.setName("intervalField");
    settings.add(intervalField, fieldConstraints);

    GridBagConstraints checkConstraints = new GridBagConstraints();
    checkConstraints.gridx = 0;
    checkConstraints.gridy = 1;
    checkConstraints.gridwidth = 2;
    checkConstraints.anchor = GridBagConstraints.WEST;
    checkConstraints.insets = new Insets(2, 2, 2, 2);
    runImmediatelyBox.setName("runImmediatelyBox");
    settings.add(runImmediatelyBox, checkConstraints);
    return settings;
  }

  private JPanel buildHelpPanel() {
    JPanel help = new JPanel(new BorderLayout());
    help.setBorder(BorderFactory.createTitledBorder("How it works"));
    JTextArea helpArea = new JTextArea(HELP_TEXT);
    helpArea.setEditable(false);
    helpArea.setLineWrap(true);
    helpArea.setWrapStyleWord(true);
    helpArea.setOpaque(false);
    helpArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    help.add(helpArea, BorderLayout.CENTER);
    return help;
  }

}

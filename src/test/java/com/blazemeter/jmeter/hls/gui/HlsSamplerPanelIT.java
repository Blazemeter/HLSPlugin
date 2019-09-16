package com.blazemeter.jmeter.hls.gui;

import static org.assertj.swing.fixture.Containers.showInFrame;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import org.assertj.core.api.JUnitSoftAssertions;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JCheckBoxFixture;
import org.assertj.swing.fixture.JRadioButtonFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HlsSamplerPanelIT {

  private static final String MASTER_URL = "http://test/test.m3u8";
  public static final String PLAY_SECONDS = "30";
  public static final String CUSTOM_BANDWIDTH = "12000";
  public static final String CUSTOM_RESOLUTION = "640x460";

  @Rule
  public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

  private HlsSamplerPanel panel;
  private FrameFixture frame;

  @Before
  public void setup() {
    panel = new HlsSamplerPanel();
    frame = showInFrame(panel);
  }

  @After
  public void tearDown() {
    frame.cleanUp();
  }

  @Test
  public void shouldGetConfiguredPropertiesWhenSetFields() {
    getMasterUrlField().setText(MASTER_URL);
    getPlayVideoDurationOption().check();
    getPlaySecondsField().setText(PLAY_SECONDS);
    getResumeDownloadOption().check();
    getCustomResolutionOption().check();
    getCustomResolutionField().setText(CUSTOM_RESOLUTION);
    getCustomBandwidthOption().check();
    getCustomBandwidthField().setText(CUSTOM_BANDWIDTH);

    softly.assertThat(panel.getMasterUrl()).isEqualTo(MASTER_URL);
    softly.assertThat(panel.isPlayVideoDuration()).isEqualTo(true);
    softly.assertThat(panel.getPlaySeconds()).isEqualTo(PLAY_SECONDS);
    softly.assertThat(panel.getResumeVideoStatus()).isEqualTo(true);
    softly.assertThat(panel.getResolutionSelector())
        .isEqualTo(new ResolutionSelector.CustomResolutionSelector(CUSTOM_RESOLUTION));
    softly.assertThat(panel.getBandwidthSelector())
        .isEqualTo(new BandwidthSelector.CustomBandwidthSelector(CUSTOM_BANDWIDTH));
  }

  private JTextComponentFixture getMasterUrlField() {
    return frame.textBox("masterUrlField");
  }

  private JRadioButtonFixture getPlayVideoDurationOption() {
    return frame.radioButton("playVideoDurationOption");
  }

  private JTextComponentFixture getPlaySecondsField() {
    return frame.textBox("playSecondsField");
  }

  private JCheckBoxFixture getResumeDownloadOption() {
    return frame.checkBox("resumeDownloadOption");
  }

  private JRadioButtonFixture getCustomResolutionOption() {
    return frame.radioButton("customResolutionOption");
  }

  private JTextComponentFixture getCustomResolutionField() {
    return frame.textBox("customResolutionField");
  }

  private JRadioButtonFixture getCustomBandwidthOption() {
    return frame.radioButton("customBandwidthOption");
  }

  private JTextComponentFixture getCustomBandwidthField() {
    return frame.textBox("customBandwidthField");
  }

  @Test
  public void shouldGetExpectedFieldsWhenSetProperties() {
    panel.setMasterUrl(MASTER_URL);
    panel.setPlayVideoDuration(true);
    panel.setPlaySeconds(PLAY_SECONDS);
    panel.setResumeStatus(true);
    panel.setResolutionSelector(new ResolutionSelector.CustomResolutionSelector(CUSTOM_RESOLUTION));
    panel.setBandwidthSelector(new BandwidthSelector.CustomBandwidthSelector(CUSTOM_BANDWIDTH));

    softly.assertThat(getMasterUrlField().text()).isEqualTo(MASTER_URL);
    softly.assertThat(getPlayVideoDurationOption().target().isSelected()).isEqualTo(true);
    softly.assertThat(getPlaySecondsField().text()).isEqualTo(PLAY_SECONDS);
    softly.assertThat(getResumeDownloadOption().target().isSelected()).isEqualTo(true);
    softly.assertThat(getCustomResolutionOption().target().isSelected()).isEqualTo(true);
    softly.assertThat(getCustomResolutionField().text()).isEqualTo(CUSTOM_RESOLUTION);
    softly.assertThat(getCustomBandwidthOption().target().isSelected()).isEqualTo(true);
    softly.assertThat(getCustomBandwidthField().text()).isEqualTo(CUSTOM_BANDWIDTH);
  }

  @Test
  public void shouldEnablePlaySecondsFieldWhenSelectPlayVideoDurationOption() {
    switchRadios(getPlayWholeVideoOption(), getPlayVideoDurationOption());
    getPlaySecondsField().requireEnabled();
  }

  private JRadioButtonFixture getPlayWholeVideoOption() {
    return frame.radioButton("playWholeVideoOption");
  }

  private void switchRadios(JRadioButtonFixture fromOption, JRadioButtonFixture toOption) {
    fromOption.check();
    toOption.check();
  }

  @Test
  public void shouldDisableAndClearPlaySecondsFieldWhenSelectPlayWholeVideoOption() {
    getPlayVideoDurationOption().check();
    JTextComponentFixture playSecondsField = getPlaySecondsField();
    playSecondsField.setText(PLAY_SECONDS);
    getPlayWholeVideoOption().check();
    playSecondsField.requireDisabled();
    playSecondsField.requireText("");
  }

  @Test
  public void shouldEnableCustomResolutionFieldWhenSelectCustomResolutionOption() {
    switchRadios(getMinResolutionOption(), getCustomResolutionOption());
    getCustomResolutionField().requireEnabled();
  }

  private JRadioButtonFixture getMinResolutionOption() {
    return frame.radioButton("minResolutionOption");
  }

  @Test
  public void shouldDisableAndClearCustomResolutionFieldWhenSelectNonCustomResolutionOption() {
    getCustomResolutionOption().check();
    JTextComponentFixture customResolutionField = getCustomResolutionField();
    customResolutionField.setText(CUSTOM_RESOLUTION);
    getMinResolutionOption().check();
    customResolutionField.requireDisabled();
    customResolutionField.requireText("");
  }

  @Test
  public void shouldEnableCustomBandwidthFieldWhenSelectCustomBandwidthOption() {
    switchRadios(getMinBandwidthOption(), getCustomBandwidthOption());
    getCustomBandwidthField().requireEnabled();
  }

  private JRadioButtonFixture getMinBandwidthOption() {
    return frame.radioButton("minBandwidthOption");
  }

  @Test
  public void shouldDisableAndClearCustomBandwidthFieldWhenSelectNonCustomBandwidthOption() {
    getCustomBandwidthOption().check();
    JTextComponentFixture customBandwidthField = getCustomBandwidthField();
    customBandwidthField.setText(CUSTOM_BANDWIDTH);
    getMinBandwidthOption().check();
    customBandwidthField.requireDisabled();
    customBandwidthField.requireText("");
  }

}

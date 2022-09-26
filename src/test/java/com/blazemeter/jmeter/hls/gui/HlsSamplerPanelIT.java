package com.blazemeter.jmeter.hls.gui;

import static org.assertj.swing.fixture.Containers.showInFrame;

import com.blazemeter.jmeter.SwingTestRunner;
import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.blazemeter.jmeter.videostreaming.core.Variants;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.jorphan.gui.JLabeledChoice;
import org.assertj.core.api.JUnitSoftAssertions;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JCheckBoxFixture;
import org.assertj.swing.fixture.JRadioButtonFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SwingTestRunner.class)
public class HlsSamplerPanelIT {

  private static final String MASTER_URL = "http://test/test.m3u8";
  public static final String PLAY_SECONDS = "30";
  public static final String CUSTOM_BANDWIDTH = "12000";
  public static final String CUSTOM_RESOLUTION = "640x460";
  private static final List<String> BANDWIDTH_LIST = Arrays.asList("1000", "2000", "3000");
  private static final List<String> RESOLUTION_LIST = Arrays.asList("100x200", "200x300",
      "300x400");
  private static final List<String> SUBTITLE_LIST = Arrays.asList("en", "es");
  private static final List<String> AUDIO_LIST = Arrays.asList("en", "dubbing");

  @Rule
  public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

  private HlsSamplerPanel panel;
  private FrameFixture frame;

  @Before
  public void setup() {
    setupPanel();
    frame = showInFrame(panel);
  }

  private void setupPanel() {
    Variants variants = new Variants();
    variants.setBandwidthList(BANDWIDTH_LIST);
    variants.setResolutionList(RESOLUTION_LIST);
    variants.setSubtitleList(new ArrayList<>(SUBTITLE_LIST));
    variants.setAudioLanguageList(new ArrayList<>(AUDIO_LIST));

    panel = new HlsSamplerPanel();
    panel.setVariantsProvider((url) -> {
          if (url.equals(MASTER_URL)) {
            return variants;
          }
          throw new PlaylistParsingException(null, "");
        }
    );
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
    getResolutionBox().setText(CUSTOM_RESOLUTION);
    getBandwidthBox().setText(CUSTOM_BANDWIDTH);

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

  private JLabeledChoice getResolutionBox() {
    return frame.robot().finder().findByName("resolutionBox", JLabeledChoice.class);
  }

  private JLabeledChoice getAudioBox() {
    return frame.robot().finder().findByName("audioBox", JLabeledChoice.class);
  }

  private JLabeledChoice getSubtitleBox() {
    return frame.robot().finder().findByName("subtitleBox", JLabeledChoice.class);
  }

  private JLabeledChoice getBandwidthBox() {
    return frame.robot().finder().findByName("bandwidthBox", JLabeledChoice.class);
  }

  private void setMasterUrl() {
    getMasterUrlField().setText(MASTER_URL);
  }

  @Test
  public void shouldGetExpectedFieldsWhenSetProperties() {
    panel.setMasterUrl(MASTER_URL);
    panel.setPlayVideoDuration(true);
    panel.setPlaySeconds(PLAY_SECONDS);
    panel.setResumeStatus(true);
    panel.setResolutionSelector(new ResolutionSelector.CustomResolutionSelector(CUSTOM_RESOLUTION));
    panel.setResolutionSelector(new ResolutionSelector.CustomResolutionSelector(CUSTOM_BANDWIDTH));
    getResolutionBox().setText(CUSTOM_RESOLUTION);
    getBandwidthBox().setText(CUSTOM_BANDWIDTH);

    softly.assertThat(getMasterUrlField().text()).isEqualTo(MASTER_URL);
    softly.assertThat(getPlayVideoDurationOption().target().isSelected()).isEqualTo(true);
    softly.assertThat(getPlaySecondsField().text()).isEqualTo(PLAY_SECONDS);
    softly.assertThat(getResolutionBox().getText()).isEqualTo(CUSTOM_RESOLUTION);
    softly.assertThat(getBandwidthBox().getText()).isEqualTo(CUSTOM_BANDWIDTH);
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
  public void shouldHaveComboBoxDefaultOptionsWhenLoaded() {
    setMasterUrl();
    String[] expected = {"Min", "Max"};
    String aDefault = "Default";

    softly.assertThat(getBandwidthBox().getItems()).isEqualTo(expected);
    softly.assertThat(getResolutionBox().getItems()).isEqualTo(expected);
    softly.assertThat(getAudioBox().getText()).isEqualTo(aDefault);
    softly.assertThat(getSubtitleBox().getText()).isEqualTo(aDefault);
  }

  @Test
  public void shouldPopUpWhenWrongUrlSet() {
    frame.textBox("masterUrlField").setText("wrong url");
    frame.button("loadButton").click();
    frame.optionPane()
        .requireMessage("There was an error while trying to load the URL, check the logs.");
  }

  @Test
  public void shouldDisplayMinMaxWhenMinSelected() {
    setMasterUrl();
    frame.button("loadButton").click();
    getBandwidthBox().setSelectedIndex(1);
    getBandwidthBox().setSelectedIndex(0);
    assertThat(getResolutionBox().getItems()).contains("Min", "Max");
  }

  @Test
  public void shouldDisplayMinMaxWhenMaxSelected() {
    setMasterUrl();
    frame.button("loadButton").click();
    getBandwidthBox().setSelectedIndex(1);
    assertThat(getResolutionBox().getItems()).contains("Min", "Max");
  }

  @Test
  public void shouldResolutionRestrainedWhenBandwidthChosen() {
    setMasterUrl();
    frame.button("loadButton").click();
    getBandwidthBox().setSelectedIndex(2);
  }

  @Test
  public void shouldDisplayBandwidthAndResolutionOptionsWhenUrlLoaded() {
    setMasterUrl();
    frame.button("loadButton").click();

    List<String> expectedBandwidths = new ArrayList<>(BANDWIDTH_LIST);
    expectedBandwidths.add(0, "Min");
    expectedBandwidths.add(1, "Max");
    softly.assertThat(getBandwidthBox().getItems()).isEqualTo(expectedBandwidths.toArray());

    List<String> expectedResolutions = new ArrayList<>(RESOLUTION_LIST);
    expectedResolutions.add(0, "Min");
    expectedResolutions.add(1, "Max");
    softly.assertThat(getResolutionBox().getItems()).isEqualTo(expectedResolutions.toArray());
  }

  @Test
  public void shouldDisplayTracksPanelOptionsWhenUrlLoaded() {
    setMasterUrl();
    frame.button("loadButton").click();

    softly.assertThat(getAudioBox().getItems()).isEqualTo(new String[]{"default", "dubbing", "en"});
    softly.assertThat(getSubtitleBox().getItems()).isEqualTo(new String[]{"default", "en", "es"});
  }

}

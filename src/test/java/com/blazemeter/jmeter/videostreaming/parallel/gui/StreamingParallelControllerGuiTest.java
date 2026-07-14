package com.blazemeter.jmeter.videostreaming.parallel.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.blazemeter.jmeter.JMeterTestUtils;
import com.blazemeter.jmeter.SwingTestRunner;
import com.blazemeter.jmeter.videostreaming.parallel.StreamingParallelController;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SwingTestRunner.class)
public class StreamingParallelControllerGuiTest {

  private StreamingParallelControllerGui gui;

  @BeforeClass
  public static void setupClass() {
    JMeterTestUtils.setupJmeterEnv();
  }

  @Before
  public void setUp() {
    gui = new StreamingParallelControllerGui();
  }

  @Test
  public void shouldCreateStreamingParallelController() {
    assertThat(gui.createTestElement()).isInstanceOf(StreamingParallelController.class);
  }

  @Test
  public void shouldPreservePropertiesWhenConfiguringAndModifying() {
    StreamingParallelController source = new StreamingParallelController();
    source.setInterval("30");
    source.setRunImmediately(true);
    gui.configure(source);

    StreamingParallelController target = new StreamingParallelController();
    gui.modifyTestElement(target);

    assertThat(target.getInterval()).isEqualTo("30");
    assertThat(target.isRunImmediately()).isTrue();
  }

  @Test
  public void shouldResetPropertiesWhenClearingGui() {
    StreamingParallelController source = new StreamingParallelController();
    source.setInterval("30");
    source.setRunImmediately(true);
    gui.configure(source);

    gui.clearGui();

    StreamingParallelController target = new StreamingParallelController();
    gui.modifyTestElement(target);
    assertThat(target.getInterval()).isEmpty();
    assertThat(target.isRunImmediately()).isFalse();
  }
}

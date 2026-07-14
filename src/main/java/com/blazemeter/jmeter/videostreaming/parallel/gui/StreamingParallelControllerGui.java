package com.blazemeter.jmeter.videostreaming.parallel.gui;

import com.blazemeter.jmeter.videostreaming.parallel.StreamingParallelController;
import com.google.common.annotations.VisibleForTesting;
import java.awt.BorderLayout;
import org.apache.jmeter.control.gui.AbstractControllerGui;
import org.apache.jmeter.testelement.TestElement;

public class StreamingParallelControllerGui extends AbstractControllerGui {

  private final StreamingParallelControllerPanel panel;

  public StreamingParallelControllerGui() {
    this(new StreamingParallelControllerPanel());
  }

  @VisibleForTesting
  StreamingParallelControllerGui(StreamingParallelControllerPanel panel) {
    this.panel = panel;
    setLayout(new BorderLayout(0, 5));
    setBorder(makeBorder());
    add(makeTitlePanel(), BorderLayout.NORTH);
    add(panel, BorderLayout.CENTER);
  }

  @Override
  public String getStaticLabel() {
    return "bzm - Streaming Parallel Controller";
  }

  @Override
  public String getLabelResource() {
    throw new IllegalStateException("This shouldn't be called");
  }

  @Override
  public TestElement createTestElement() {
    StreamingParallelController controller = new StreamingParallelController();
    modifyTestElement(controller);
    return controller;
  }

  @Override
  public void configure(TestElement element) {
    super.configure(element);
    if (element instanceof StreamingParallelController) {
      StreamingParallelController controller = (StreamingParallelController) element;
      panel.setInterval(controller.getInterval());
      panel.setRunImmediately(controller.isRunImmediately());
    }
  }

  @Override
  public void modifyTestElement(TestElement element) {
    configureTestElement(element);
    if (element instanceof StreamingParallelController) {
      StreamingParallelController controller = (StreamingParallelController) element;
      controller.setInterval(panel.getInterval());
      controller.setRunImmediately(panel.isRunImmediately());
    }
  }

  @Override
  public void clearGui() {
    super.clearGui();
    panel.setInterval("");
    panel.setRunImmediately(false);
  }
}

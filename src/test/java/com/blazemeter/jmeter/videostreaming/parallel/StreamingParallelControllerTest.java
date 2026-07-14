package com.blazemeter.jmeter.videostreaming.parallel;

import static org.assertj.core.api.Assertions.assertThat;

import com.blazemeter.jmeter.JMeterTestUtils;
import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator;
import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator.SliceExit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class StreamingParallelControllerTest {

  private static final long SECOND_NANOS = 1_000_000_000L;

  @BeforeClass
  public static void setupClass() {
    JMeterTestUtils.setupJmeterEnv();
  }

  @After
  public void tearDown() {
    StreamingSliceCoordinator.clear();
  }

  private StreamingParallelController controller(String interval, boolean runImmediately) {
    StreamingParallelController controller = new StreamingParallelController();
    controller.setName("SPC");
    controller.setInterval(interval);
    controller.setRunImmediately(runImmediately);
    return controller;
  }

  private List<String> drive(StreamingParallelController controller,
      FakeStreamingSampler streaming, int maxSteps) {
    List<String> emitted = new ArrayList<>();
    for (int i = 0; i < maxSteps; i++) {
      Sampler next = controller.next();
      if (next == null) {
        emitted.add("NULL");
        break;
      }
      emitted.add(next.getName());
      if (next == streaming) {
        streaming.sample();
      }
    }
    return emitted;
  }

  @Test
  public void shouldPlayWholeStreamInSingleSliceWhenNoHeartbeat() {
    StreamingParallelController controller = controller("10", false);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.FINISHED);
    controller.addTestElement(streaming);

    List<String> emitted = drive(controller, streaming, 10);

    assertThat(emitted).containsExactly("stream", "NULL");
    assertThat(streaming.sampleCalls).isEqualTo(1);
  }

  @Test
  public void shouldKeepStreamingAcrossYieldsUntilFinished() {
    StreamingParallelController controller = controller("100", false);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.YIELD);
    streaming.script.add(SliceExit.YIELD);
    streaming.script.add(SliceExit.FINISHED);
    controller.addTestElement(streaming);

    List<String> emitted = drive(controller, streaming, 10);

    assertThat(emitted).containsExactly("stream", "stream", "stream", "NULL");
    assertThat(streaming.sampleCalls).isEqualTo(3);
  }

  @Test
  public void shouldRunHeartbeatFirstWhenRunImmediatelyEnabled() {
    StreamingParallelController controller = controller("10", true);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.FINISHED);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    List<String> emitted = drive(controller, streaming, 10);

    assertThat(emitted).containsExactly("hb", "stream", "NULL");
  }

  @Test
  public void shouldDispatchHeartbeatOnlyAfterIntervalElapses() {
    StreamingParallelController controller = controller("10", false);
    AtomicLong clock = new AtomicLong(0);
    controller.setNanoClock(clock::get);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.YIELD);
    streaming.script.add(SliceExit.YIELD);
    streaming.script.add(SliceExit.FINISHED);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    List<String> emitted = new ArrayList<>();
    emitted.add(step(controller, streaming));
    emitted.add(step(controller, streaming));
    clock.set(11 * SECOND_NANOS);
    emitted.add(step(controller, streaming));
    emitted.add(step(controller, streaming));
    emitted.add(step(controller, streaming));

    assertThat(emitted).containsExactly("stream", "stream", "hb", "stream", "NULL");
  }

  @Test
  public void shouldEmitFailedSampleWhenNoStreamingChild() {
    StreamingParallelController controller = controller("10", false);
    controller.addTestElement(new NamedSampler("hb"));

    Sampler first = controller.next();
    SampleResult result = first.sample(null);

    assertThat(result.isSuccessful()).isFalse();
    assertThat(controller.next().getName()).isEqualTo("hb");
    assertThat(controller.next()).isNull();
  }

  @Test
  public void shouldEmitFailedSampleWhenIntervalIsInvalid() {
    StreamingParallelController controller = controller("not-a-number", false);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    controller.addTestElement(streaming);

    Sampler first = controller.next();
    SampleResult result = first.sample(null);

    assertThat(result.isSuccessful()).isFalse();
    assertThat(controller.next()).isNull();
  }

  private String step(StreamingParallelController controller, FakeStreamingSampler streaming) {
    Sampler next = controller.next();
    if (next == null) {
      return "NULL";
    }
    if (next == streaming) {
      streaming.sample();
    }
    return next.getName();
  }

  private static class FakeStreamingSampler extends com.blazemeter.jmeter.hls.logic.HlsSampler {

    private final Deque<SliceExit> script = new ArrayDeque<>();
    private int sampleCalls;

    private FakeStreamingSampler() {
      super(null, null, null, null);
      setName("stream");
    }

    @Override
    public SampleResult sample() {
      sampleCalls++;
      SliceExit exit = script.isEmpty() ? SliceExit.FINISHED : script.poll();
      StreamingSliceCoordinator.setExit(exit);
      return null;
    }
  }

  private static class NamedSampler extends AbstractSampler {

    private NamedSampler(String name) {
      setName(name);
    }

    @Override
    public SampleResult sample(Entry e) {
      return null;
    }
  }
}

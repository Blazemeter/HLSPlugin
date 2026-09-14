package com.blazemeter.jmeter.videostreaming.parallel;

import static org.assertj.core.api.Assertions.assertThat;

import com.blazemeter.jmeter.JMeterTestUtils;
import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator;
import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator.SliceExit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
  public void shouldEndSessionWhenStopRequestedAfterHeartbeatEvenIfLastExitWasYield() {
    StreamingParallelController controller = controller("10", false);
    AtomicLong clock = new AtomicLong(0);
    controller.setNanoClock(clock::get);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.YIELD);
    streaming.script.add(SliceExit.YIELD);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    assertThat(step(controller, streaming)).isEqualTo("stream");
    clock.set(11 * SECOND_NANOS);
    assertThat(step(controller, streaming)).isEqualTo("hb");
    StreamingSliceCoordinator.requestStop();
    assertThat(step(controller, streaming)).isEqualTo("NULL");
  }

  @Test
  public void shouldStartFreshSessionAfterStopDrivenEnd() {
    StreamingParallelController controller = controller("10", true);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.FINISHED);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    assertThat(step(controller, streaming)).isEqualTo("hb");
    StreamingSliceCoordinator.requestStop();
    assertThat(step(controller, streaming)).isEqualTo("NULL");

    // beginIteration() clears stopRequested; a new session must not terminate immediately
    assertThat(step(controller, streaming)).isEqualTo("hb");
    assertThat(step(controller, streaming)).isEqualTo("stream");
    assertThat(step(controller, streaming)).isEqualTo("NULL");
  }

  @Test
  public void shouldFireHeartbeatOnFirstDueCycleAfterStop() {
    StreamingParallelController controller = controller("10", false);
    AtomicLong clock = new AtomicLong(0);
    controller.setNanoClock(clock::get);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.YIELD);
    streaming.script.add(SliceExit.YIELD);
    streaming.script.add(SliceExit.YIELD);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    assertThat(step(controller, streaming)).isEqualTo("stream");
    clock.set(11 * SECOND_NANOS);
    assertThat(step(controller, streaming)).isEqualTo("hb");
    StreamingSliceCoordinator.requestStop();
    assertThat(step(controller, streaming)).isEqualTo("NULL");

    assertThat(step(controller, streaming)).isEqualTo("stream");
    clock.set(22 * SECOND_NANOS);
    assertThat(step(controller, streaming)).isEqualTo("hb");
  }

  @Test
  public void shouldEndSessionOnNextWhenStopRequestedDuringHeartbeatPhase() {
    StreamingParallelController controller = controller("10", true);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.YIELD);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb1"));
    controller.addTestElement(new NamedSampler("hb2"));

    assertThat(step(controller, streaming)).isEqualTo("hb1");
    StreamingSliceCoordinator.requestStop();
    assertThat(step(controller, streaming)).isEqualTo("NULL");
  }

  @Test
  public void shouldStillFinishYieldStreamWhenStopIsNeverRequested() {
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
  public void shouldRestartCleanlyAcrossManyStopCycles() {
    StreamingParallelController controller = controller("10", true);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    for (int i = 0; i < 50; i++) {
      assertThat(step(controller, streaming)).isEqualTo("hb");
      assertThat(StreamingSliceCoordinator.isStopRequested()).isFalse();
      StreamingSliceCoordinator.requestStop();
      assertThat(step(controller, streaming)).isEqualTo("NULL");
      assertThat(StreamingSliceCoordinator.isActive()).isFalse();
      assertThat(StreamingSliceCoordinator.isStopRequested()).isFalse();
    }
    assertThat(streaming.sampleCalls).isEqualTo(0);
  }

  @Test
  public void shouldEndSessionSilentlyWithoutEmittingControllerSample() {
    StreamingParallelController controller = controller("10", true);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.YIELD);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    Sampler heartbeat = controller.next();
    assertThat(heartbeat.getName()).isEqualTo("hb");
    StreamingSliceCoordinator.requestStop();
    Sampler afterStop = controller.next();
    assertThat(afterStop).isNull();
  }

  @Test
  public void shouldEndSessionOnNextWhenStopRequestedDuringStreamingPhase() {
    StreamingParallelController controller = controller("10", false);
    AtomicLong clock = new AtomicLong(0);
    controller.setNanoClock(clock::get);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.YIELD);
    streaming.script.add(SliceExit.YIELD);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    assertThat(step(controller, streaming)).isEqualTo("stream");
    StreamingSliceCoordinator.requestStop();
    assertThat(step(controller, streaming)).isEqualTo("NULL");
  }

  @Test
  public void shouldEndSessionWhenSamplerYieldsAfterStopDuringSlice() {
    StreamingParallelController controller = controller("100", false);
    YieldingFakeSampler streaming = new YieldingFakeSampler();
    streaming.segmentsBeforeStop = 3;
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    assertThat(step(controller, streaming)).isEqualTo("stream");
    assertThat(streaming.segments).isEqualTo(3);
    assertThat(StreamingSliceCoordinator.getExit()).isEqualTo(SliceExit.YIELD);
    assertThat(step(controller, streaming)).isEqualTo("NULL");
  }

  @Test
  public void shouldEndSessionWhenStopRequestedOnNoHeartbeatController() {
    StreamingParallelController controller = controller("10", false);
    YieldingFakeSampler streaming = new YieldingFakeSampler();
    streaming.segmentsBeforeStop = 1;
    controller.addTestElement(streaming);

    assertThat(step(controller, streaming)).isEqualTo("stream");
    assertThat(step(controller, streaming)).isEqualTo("NULL");
  }

  @Test
  public void shouldLeaveCoordinatorIdleAfterStopDrivenEndSession() {
    StreamingParallelController controller = controller("10", true);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    assertThat(step(controller, streaming)).isEqualTo("hb");
    StreamingSliceCoordinator.requestStop();
    assertThat(step(controller, streaming)).isEqualTo("NULL");
    assertThat(StreamingSliceCoordinator.isActive()).isFalse();
    assertThat(StreamingSliceCoordinator.isStopRequested()).isFalse();
    assertThat(StreamingSliceCoordinator.getExit()).isNull();
  }

  @Test
  public void reInitializeShouldClearPendingStop() {
    StreamingParallelController controller = controller("10", true);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.FINISHED);
    controller.addTestElement(streaming);
    controller.addTestElement(new NamedSampler("hb"));

    assertThat(step(controller, streaming)).isEqualTo("hb");
    StreamingSliceCoordinator.requestStop();
    controller.reInitialize();
    assertThat(StreamingSliceCoordinator.isStopRequested()).isFalse();
    assertThat(StreamingSliceCoordinator.isActive()).isFalse();
    assertThat(step(controller, streaming)).isNotEqualTo("NULL");
  }

  @Test
  public void shouldEndSessionOnErrorExitWithoutStop() {
    StreamingParallelController controller = controller("10", false);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.ERROR);
    controller.addTestElement(streaming);

    assertThat(drive(controller, streaming, 10)).containsExactly("stream", "NULL");
  }

  @Test
  public void shouldEndSessionOnInterruptedExitWithoutStop() {
    StreamingParallelController controller = controller("10", false);
    FakeStreamingSampler streaming = new FakeStreamingSampler();
    streaming.script.add(SliceExit.INTERRUPTED);
    controller.addTestElement(streaming);

    assertThat(drive(controller, streaming, 10)).containsExactly("stream", "NULL");
  }

  @Test
  public void shouldNotEndOtherThreadControllerWhenStopRequested() throws Exception {
    CyclicBarrier bothReady = new CyclicBarrier(2);
    CountDownLatch aStopped = new CountDownLatch(1);
    AtomicReference<String> aAfterStop = new AtomicReference<>();
    AtomicReference<String> bAfterAStopped = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Thread threadA = new Thread(() -> {
      try {
        StreamingParallelController controller = controller("10", true);
        FakeStreamingSampler streaming = new FakeStreamingSampler();
        streaming.script.add(SliceExit.YIELD);
        controller.addTestElement(streaming);
        controller.addTestElement(new NamedSampler("hb"));
        assertThat(step(controller, streaming)).isEqualTo("hb");
        bothReady.await(5, TimeUnit.SECONDS);
        StreamingSliceCoordinator.requestStop();
        aAfterStop.set(step(controller, streaming));
      } catch (Throwable t) {
        failure.compareAndSet(null, t);
      } finally {
        StreamingSliceCoordinator.clear();
        aStopped.countDown();
      }
    }, "spc-stop-a");

    Thread threadB = new Thread(() -> {
      try {
        StreamingParallelController controller = controller("10", false);
        FakeStreamingSampler streaming = new FakeStreamingSampler();
        streaming.script.add(SliceExit.YIELD);
        streaming.script.add(SliceExit.YIELD);
        controller.addTestElement(streaming);
        controller.addTestElement(new NamedSampler("hb"));
        assertThat(step(controller, streaming)).isEqualTo("stream");
        bothReady.await(5, TimeUnit.SECONDS);
        assertThat(aStopped.await(5, TimeUnit.SECONDS)).isTrue();
        bAfterAStopped.set(step(controller, streaming));
      } catch (Throwable t) {
        failure.compareAndSet(null, t);
      } finally {
        StreamingSliceCoordinator.clear();
      }
    }, "spc-stop-b");

    threadA.start();
    threadB.start();
    threadA.join(10_000);
    threadB.join(10_000);
    assertThat(failure.get()).isNull();
    assertThat(aAfterStop.get()).isEqualTo("NULL");
    assertThat(bAfterAStopped.get()).isEqualTo("stream");
  }

  @Test
  public void shouldPassthroughNestedControllerWithoutIndependentSlicing() {
    StreamingParallelController outer = controller("10", false);
    FakeStreamingSampler outerStream = new FakeStreamingSampler();
    outerStream.script.add(SliceExit.YIELD);
    outer.addTestElement(outerStream);
    assertThat(step(outer, outerStream)).isEqualTo("stream");

    StreamingParallelController inner = controller("10", true);
    inner.setName("inner");
    FakeStreamingSampler innerStream = new FakeStreamingSampler();
    innerStream.script.add(SliceExit.FINISHED);
    inner.addTestElement(innerStream);
    inner.addTestElement(new NamedSampler("inner-hb"));

    assertThat(inner.next().getName()).isEqualTo("stream");
    assertThat(inner.next().getName()).isEqualTo("inner-hb");
    assertThat(inner.next()).isNull();
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

  private String step(StreamingParallelController controller,
      com.blazemeter.jmeter.hls.logic.HlsSampler streaming) {
    Sampler next = controller.next();
    if (next == null) {
      return "NULL";
    }
    if (next == streaming) {
      streaming.sample();
    }
    return next.getName();
  }

  private static class YieldingFakeSampler extends com.blazemeter.jmeter.hls.logic.HlsSampler {

    private int segmentsBeforeStop = Integer.MAX_VALUE;
    private int segments;

    private YieldingFakeSampler() {
      super(null, null, null, null);
      setName("stream");
    }

    @Override
    public SampleResult sample() {
      while (!StreamingSliceCoordinator.shouldYield()) {
        segments++;
        if (segments >= segmentsBeforeStop) {
          StreamingSliceCoordinator.requestStop();
        }
      }
      StreamingSliceCoordinator.setExit(SliceExit.YIELD);
      return null;
    }
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

package com.blazemeter.jmeter.videostreaming.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator.SliceExit;
import org.junit.After;
import org.junit.Test;

public class StreamingSliceCoordinatorTest {

  @After
  public void tearDown() {
    StreamingSliceCoordinator.clear();
  }

  @Test
  public void shouldBehaveAsNullObjectWhenNoControllerIsActive() {
    assertThat(StreamingSliceCoordinator.isActive()).isFalse();
    assertThat(StreamingSliceCoordinator.shouldYield()).isFalse();
    assertThat(StreamingSliceCoordinator.clampMillis(1234)).isEqualTo(1234);
  }

  @Test
  public void shouldBeActiveAndSignalNewSessionAfterBeginIteration() {
    StreamingSliceCoordinator.beginIteration();
    assertThat(StreamingSliceCoordinator.isActive()).isTrue();
    assertThat(StreamingSliceCoordinator.consumeNewSession()).isTrue();
    assertThat(StreamingSliceCoordinator.consumeNewSession()).isFalse();
  }

  @Test
  public void shouldNotYieldWhenDeadlineIsInTheFuture() {
    StreamingSliceCoordinator.beginIteration();
    StreamingSliceCoordinator.setDeadlineNanos(System.nanoTime() + 60_000_000_000L);
    assertThat(StreamingSliceCoordinator.shouldYield()).isFalse();
  }

  @Test
  public void shouldYieldWhenDeadlineHasPassed() {
    StreamingSliceCoordinator.beginIteration();
    StreamingSliceCoordinator.setDeadlineNanos(System.nanoTime() - 1);
    assertThat(StreamingSliceCoordinator.shouldYield()).isTrue();
  }

  @Test
  public void shouldClampAwaitToRemainingSliceTime() {
    StreamingSliceCoordinator.beginIteration();
    StreamingSliceCoordinator.setDeadlineNanos(System.nanoTime() + 50_000_000L);
    assertThat(StreamingSliceCoordinator.clampMillis(10_000)).isLessThanOrEqualTo(50);
  }

  @Test
  public void shouldClampToZeroWhenDeadlineAlreadyPassed() {
    StreamingSliceCoordinator.beginIteration();
    StreamingSliceCoordinator.setDeadlineNanos(System.nanoTime() - 1);
    assertThat(StreamingSliceCoordinator.clampMillis(10_000)).isEqualTo(0);
  }

  @Test
  public void shouldStoreAndReturnLastExit() {
    StreamingSliceCoordinator.beginIteration();
    StreamingSliceCoordinator.setExit(SliceExit.FINISHED);
    assertThat(StreamingSliceCoordinator.getExit()).isEqualTo(SliceExit.FINISHED);
  }

  @Test
  public void shouldResetStateOnClear() {
    StreamingSliceCoordinator.beginIteration();
    StreamingSliceCoordinator.setExit(SliceExit.YIELD);
    StreamingSliceCoordinator.clear();
    assertThat(StreamingSliceCoordinator.isActive()).isFalse();
    assertThat(StreamingSliceCoordinator.getExit()).isNull();
  }

  @Test
  public void shouldEnforceDeadlineWhenSamplerLoopsOnYield() {
    StreamingSliceCoordinator.beginIteration();
    long sliceDurationNanos = 100_000_000L; // 100ms
    long deadline = System.nanoTime() + sliceDurationNanos;
    StreamingSliceCoordinator.setDeadlineNanos(deadline);

    int iterations = 0;
    while (!StreamingSliceCoordinator.shouldYield() && iterations < 1000) {
      iterations++;
      busyWaitNanos(5_000_000L); // 5ms busy wait
    }

    long elapsed = System.nanoTime() - (deadline - sliceDurationNanos);
    assertThat(elapsed).isGreaterThanOrEqualTo(sliceDurationNanos);
    assertThat(StreamingSliceCoordinator.shouldYield()).isTrue();
  }

  @Test
  public void shouldPreserveNewSessionFlagAcrossMultipleSlices() {
    StreamingSliceCoordinator.beginIteration();
    boolean firstNewSession = StreamingSliceCoordinator.consumeNewSession();
    assertThat(firstNewSession).isTrue();

    long deadline = System.nanoTime() + 1_000_000_000L;
    StreamingSliceCoordinator.setDeadlineNanos(deadline);

    boolean secondNewSession = StreamingSliceCoordinator.consumeNewSession();
    assertThat(secondNewSession).isFalse();

    deadline = System.nanoTime() - 1;
    StreamingSliceCoordinator.setDeadlineNanos(deadline);
    StreamingSliceCoordinator.setExit(SliceExit.YIELD);

    boolean thirdNewSession = StreamingSliceCoordinator.consumeNewSession();
    assertThat(thirdNewSession).isFalse();
  }

  @Test
  public void shouldRespectClampWhenAwaitingPlaylistRefresh() {
    StreamingSliceCoordinator.beginIteration();
    long sliceDurationNanos = 50_000_000L; // 50ms
    long deadline = System.nanoTime() + sliceDurationNanos;
    StreamingSliceCoordinator.setDeadlineNanos(deadline);

    long requestedMillis = 1000;
    long clamped = StreamingSliceCoordinator.clampMillis(requestedMillis);

    assertThat(clamped).isGreaterThan(0).isLessThan(requestedMillis);

    busyWaitNanos(clamped * 1_000_000L);

    long remaining = (deadline - System.nanoTime()) / 1_000_000L;
    assertThat(remaining).isLessThanOrEqualTo(10);
  }

  @Test
  public void shouldSignalYieldExitWhenSliceEnds() {
    StreamingSliceCoordinator.beginIteration();
    long deadline = System.nanoTime() + 50_000_000L;
    StreamingSliceCoordinator.setDeadlineNanos(deadline);

    while (!StreamingSliceCoordinator.shouldYield()) {
      busyWaitNanos(10_000_000L);
    }

    StreamingSliceCoordinator.setExit(SliceExit.YIELD);
    assertThat(StreamingSliceCoordinator.getExit()).isEqualTo(SliceExit.YIELD);
  }

  @Test
  public void shouldResetExitOnNewSliceStart() {
    StreamingSliceCoordinator.beginIteration();
    StreamingSliceCoordinator.setExit(SliceExit.FINISHED);

    StreamingSliceCoordinator.setDeadlineNanos(System.nanoTime() + 100_000_000L);
    StreamingSliceCoordinator.setExit(null);

    assertThat(StreamingSliceCoordinator.getExit()).isNull();
  }

  @Test
  public void shouldIntegrateSamplerYieldLoopWithControllerSlicing() {
    StreamingSliceCoordinator.beginIteration();
    assertThat(StreamingSliceCoordinator.consumeNewSession()).isTrue();

    SliceExit[] exits = {SliceExit.YIELD, SliceExit.FINISHED};
    for (int sliceIndex = 0; sliceIndex < exits.length; sliceIndex++) {
      long sliceDeadline = System.nanoTime() + 30_000_000L; // 30ms slice
      StreamingSliceCoordinator.setDeadlineNanos(sliceDeadline);
      StreamingSliceCoordinator.setExit(null);

      while (!StreamingSliceCoordinator.shouldYield()) {
        busyWaitNanos(5_000_000L);
      }

      StreamingSliceCoordinator.setExit(exits[sliceIndex]);

      if (sliceIndex == 0) {
        assertThat(StreamingSliceCoordinator.getExit()).isEqualTo(SliceExit.YIELD);
        assertThat(StreamingSliceCoordinator.consumeNewSession()).isFalse();
      }
    }
    assertThat(StreamingSliceCoordinator.getExit()).isEqualTo(SliceExit.FINISHED);
  }

  private void busyWaitNanos(long nanos) {
    long start = System.nanoTime();
    while (System.nanoTime() - start < nanos) {
      // spin
    }
  }
}

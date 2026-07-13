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
}

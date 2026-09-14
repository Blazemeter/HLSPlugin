package com.blazemeter.jmeter.videostreaming.core;

/**
 * Per-thread handoff between the Streaming Parallel Controller and the streaming sampler.
 *
 * <p>The controller owns the lifecycle ({@link #beginIteration()}) and
 * the slice deadline; the sampler only reads the deadline (via {@link #shouldYield()} /
 * {@link #clampMillis(long)}) and writes a single {@link SliceExit} in a {@code finally} block.
 *
 * <p>When no controller is driving the current thread the coordinator behaves as a null object:
 * {@link #isActive()} and {@link #shouldYield()} return {@code false} and
 * {@link #clampMillis(long)} returns its argument unchanged, so standalone behavior is untouched.
 */
public final class StreamingSliceCoordinator {

  public enum SliceExit {
    YIELD, FINISHED, ERROR, INTERRUPTED
  }

  private static final class State {

    private boolean active;
    private boolean newSession;
    private long sliceDeadlineNanos = Long.MAX_VALUE;
    private SliceExit lastExit;
    private boolean stopRequested;
  }

  private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

  /**
   * Called by the controller at the start of each of its iterations. Marks the thread as sliced and
   * signals a new playback session.
   */
  public static void beginIteration() {
    State s = STATE.get();
    s.active = true;
    s.newSession = true;
    s.sliceDeadlineNanos = Long.MAX_VALUE;
    s.lastExit = null;
    s.stopRequested = false;
  }

  public static void setDeadlineNanos(long deadlineNanos) {
    STATE.get().sliceDeadlineNanos = deadlineNanos;
  }

  public static boolean isActive() {
    return STATE.get().active;
  }

  /**
   * Requests that the current thread's streaming session stop promptly and the
   * Streaming Parallel Controller end its current iteration (so an enclosing While
   * controller re-evaluates and can restart the flow). Scope is the CURRENT THREAD
   * ONLY — it never affects other virtual users. Safe to call from a JSR223 element
   * running on the same JMeter thread (e.g. a PostProcessor on the heartbeat request).
   * Idempotent within a session; cleared automatically when the next session begins.
   */
  public static void requestStop() {
    STATE.get().stopRequested = true;
  }

  public static boolean isStopRequested() {
    return STATE.get().stopRequested;
  }

  /**
   * Reads and clears the new-session flag. Only meaningful while {@link #isActive()}.
   */
  public static boolean consumeNewSession() {
    State s = STATE.get();
    boolean value = s.newSession;
    s.newSession = false;
    return value;
  }

  /**
   * True when a controller is slicing and either an external stop was requested or the current
   * slice deadline has passed. Gated on {@link #isActive()} so standalone (no controller)
   * behavior is unchanged.
   */
  public static boolean shouldYield() {
    State s = STATE.get();
    return s.active && (s.stopRequested || System.nanoTime() >= s.sliceDeadlineNanos);
  }

  /**
   * Bounds a requested await so the streaming loop never sleeps past the current slice deadline.
   * Returns the request unchanged when no controller is active. Returns {@code 0} when an external
   * stop was requested so playlist-reload waits return immediately.
   */
  public static long clampMillis(long requestedMillis) {
    State s = STATE.get();
    if (!s.active) {
      return requestedMillis;
    }
    if (s.stopRequested) {
      return 0;
    }
    long remaining = (s.sliceDeadlineNanos - System.nanoTime()) / 1_000_000L;
    if (remaining <= 0) {
      return 0;
    }
    return Math.min(requestedMillis, remaining);
  }

  public static void setExit(SliceExit exit) {
    STATE.get().lastExit = exit;
  }

  public static SliceExit getExit() {
    return STATE.get().lastExit;
  }

  public static void clear() {
    State s = STATE.get();
    s.active = false;
    s.newSession = false;
    s.sliceDeadlineNanos = Long.MAX_VALUE;
    s.lastExit = null;
    s.stopRequested = false;
  }
}

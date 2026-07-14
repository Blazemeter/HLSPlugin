package com.blazemeter.jmeter.videostreaming.parallel;

import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator;
import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator.SliceExit;
import java.util.function.LongSupplier;
import org.apache.jmeter.control.GenericController;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a heartbeat branch "in parallel" with a streaming sampler on a single JMeter thread by time
 * slicing the streaming playback.
 *
 * <p>The streaming sampler (a {@code bzm - Streaming Sampler}) must be a direct child. Every other
 * direct child forms the heartbeat branch, which is dispatched once every configured interval while
 * playback keeps running in bounded slices in between. This avoids the per-thread cost of the
 * JMeter Parallel Controller while still emitting periodic requests during playback.
 */
public class StreamingParallelController extends GenericController {

  public static final String INTERVAL = "StreamingParallelController.interval";
  public static final String RUN_IMMEDIATELY = "StreamingParallelController.runImmediately";

  private static final Logger LOG = LoggerFactory.getLogger(StreamingParallelController.class);
  private static final long NANOS_PER_MILLI = 1_000_000L;
  private static final long DEFAULT_INTERVAL_MILLIS = 1000L;
  // hard cap to keep interval * NANOS_PER_MILLI well within long range and reject absurd values
  private static final long MAX_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L;
  private static final long MAX_SLICE_MILLIS =
      JMeterUtils.getPropDefault("hls.parallel.max_slice_millis", 5000L);

  private enum Phase {
    STREAMING, HEARTBEAT
  }

  private transient Sampler streamingSampler;
  private transient GenericController heartbeatBranch;
  private transient int heartbeatChildCount;
  private transient Phase phase;
  private transient boolean started;
  private transient long intervalMillis;
  private transient long dueNanos;
  private transient boolean nestedWarned;
  private transient boolean pendingMessageEmitted;
  private transient LongSupplier nanoClock = System::nanoTime;

  @Override
  public void addTestElement(TestElement child) {
    if (child instanceof com.blazemeter.jmeter.hls.logic.HlsSampler) {
      if (streamingSampler == null) {
        streamingSampler = (Sampler) child;
      } else {
        LOG.warn("StreamingParallelController '{}' has more than one Streaming Sampler as a direct "
            + "child; only the first one is used as the primary playback track.", getName());
      }
    } else {
      heartbeatBranch().addTestElement(child);
      heartbeatChildCount++;
    }
  }

  private GenericController heartbeatBranch() {
    if (heartbeatBranch == null) {
      heartbeatBranch = new GenericController();
      heartbeatBranch.setName(getName() + " - heartbeat");
    }
    return heartbeatBranch;
  }

  @Override
  public void initialize() {
    super.initialize();
    resetIteration();
    if (heartbeatBranch != null) {
      heartbeatBranch.initialize();
    }
  }

  @Override
  protected void reInitialize() {
    // a running iteration is being cut short (thread stop / start-next-loop); release slice state
    StreamingSliceCoordinator.clear();
    resetIteration();
    super.reInitialize();
  }

  private void resetIteration() {
    started = false;
    phase = Phase.STREAMING;
    dueNanos = 0;
    pendingMessageEmitted = false;
    setFirst(true);
  }

  @Override
  public Sampler next() {
    if (streamingSampler == null) {
      return misconfiguredNext();
    }

    if (!started) {
      fireIterEvents();
      if (StreamingSliceCoordinator.isActive()) {
        return nestedPassthroughNext();
      }
      if (!parseInterval()) {
        return invalidIntervalNext();
      }
      StreamingSliceCoordinator.beginIteration();
      long now = nowNanos();
      dueNanos = now + (isRunImmediately() ? 0L : intervalMillis * NANOS_PER_MILLI);
      phase = Phase.STREAMING;
      started = true;
    }

    if (phase == Phase.HEARTBEAT) {
      Sampler heartbeat = heartbeatBranch.next();
      if (heartbeat != null) {
        return heartbeat;
      }
      dueNanos = nowNanos() + intervalMillis * NANOS_PER_MILLI;
      phase = Phase.STREAMING;
    }

    SliceExit exit = StreamingSliceCoordinator.getExit();
    if (exit != null && exit != SliceExit.YIELD) {
      return endSession();
    }

    long now = nowNanos();
    if (hasHeartbeat() && now >= dueNanos) {
      phase = Phase.HEARTBEAT;
      Sampler heartbeat = heartbeatBranch.next();
      if (heartbeat != null) {
        return heartbeat;
      }
      dueNanos = now + intervalMillis * NANOS_PER_MILLI;
      phase = Phase.STREAMING;
    }

    return dispatchStreamingSlice();
  }

  private long nowNanos() {
    return nanoClock == null ? System.nanoTime() : nanoClock.getAsLong();
  }

  void setNanoClock(LongSupplier clock) {
    this.nanoClock = clock;
  }

  private Sampler dispatchStreamingSlice() {
    long now = nowNanos();
    // when there is no heartbeat branch there is nothing to interleave, so let playback run whole
    long deadline = hasHeartbeat()
        ? Math.min(dueNanos, now + MAX_SLICE_MILLIS * NANOS_PER_MILLI)
        : Long.MAX_VALUE;
    StreamingSliceCoordinator.setDeadlineNanos(deadline);
    StreamingSliceCoordinator.setExit(null);
    return streamingSampler;
  }

  private Sampler endSession() {
    StreamingSliceCoordinator.clear();
    resetIteration();
    return null;
  }

  private boolean hasHeartbeat() {
    return heartbeatChildCount > 0;
  }

  private boolean parseInterval() {
    String raw = getPropertyAsString(INTERVAL, "").trim();
    if (raw.isEmpty()) {
      intervalMillis = DEFAULT_INTERVAL_MILLIS;
      return true;
    }
    double seconds;
    try {
      seconds = Double.parseDouble(raw);
    } catch (NumberFormatException e) {
      LOG.error("StreamingParallelController '{}' has an invalid interval '{}'; expected a "
          + "positive number of seconds.", getName(), raw);
      return false;
    }
    if (!(seconds > 0) || Double.isInfinite(seconds)) {
      LOG.error("StreamingParallelController '{}' interval must be greater than zero but was '{}'.",
          getName(), raw);
      return false;
    }
    long millis = Math.round(seconds * 1000d);
    if (millis <= 0) {
      millis = DEFAULT_INTERVAL_MILLIS;
    }
    if (millis > MAX_INTERVAL_MILLIS) {
      LOG.warn("StreamingParallelController '{}' interval '{}' exceeds the {} ms maximum; "
          + "clamping.", getName(), raw, MAX_INTERVAL_MILLIS);
      millis = MAX_INTERVAL_MILLIS;
    }
    intervalMillis = millis;
    return true;
  }

  private Sampler misconfiguredNext() {
    if (!pendingMessageEmitted) {
      pendingMessageEmitted = true;
      return new MessageSampler(getName(),
          "StreamingParallelController: no Streaming Sampler found as a direct child. Add a "
              + "'bzm - Streaming Sampler' directly under this controller.");
    }
    // still run any heartbeat children once so their configuration is not silently dropped
    Sampler heartbeat = heartbeatBranch != null ? heartbeatBranch.next() : null;
    if (heartbeat == null) {
      pendingMessageEmitted = false;
    }
    return heartbeat;
  }

  private Sampler invalidIntervalNext() {
    if (!pendingMessageEmitted) {
      pendingMessageEmitted = true;
      return new MessageSampler(getName(),
          "StreamingParallelController: invalid interval '" + getPropertyAsString(INTERVAL, "")
              + "'. Set a positive number of seconds.");
    }
    resetIteration();
    return null;
  }

  private Sampler nestedPassthroughNext() {
    if (!nestedWarned) {
      LOG.warn("StreamingParallelController '{}' is nested inside another sliced controller; "
          + "nesting is not supported. Running its children without independent slicing.",
          getName());
      nestedWarned = true;
    }
    if (!pendingMessageEmitted) {
      pendingMessageEmitted = true;
      return streamingSampler;
    }
    Sampler heartbeat = heartbeatBranch != null ? heartbeatBranch.next() : null;
    if (heartbeat == null) {
      resetIteration();
    }
    return heartbeat;
  }

  public String getInterval() {
    return getPropertyAsString(INTERVAL, "");
  }

  public void setInterval(String interval) {
    setProperty(INTERVAL, interval);
  }

  public boolean isRunImmediately() {
    return getPropertyAsBoolean(RUN_IMMEDIATELY, false);
  }

  public void setRunImmediately(boolean runImmediately) {
    setProperty(RUN_IMMEDIATELY, runImmediately);
  }

  private static class MessageSampler extends AbstractSampler {

    private final String message;

    MessageSampler(String name, String message) {
      setName(name);
      this.message = message;
    }

    @Override
    public SampleResult sample(Entry e) {
      SampleResult result = new SampleResult();
      result.setSampleLabel(getName());
      result.sampleStart();
      result.sampleEnd();
      result.setSuccessful(false);
      result.setResponseCode("500");
      result.setResponseMessage(message);
      return result;
    }
  }
}

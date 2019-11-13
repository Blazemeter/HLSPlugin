package com.blazemeter.jmeter.videostreaming.dash.segmentbuilders;

import com.blazemeter.jmeter.videostreaming.dash.DashMediaSegment;
import com.blazemeter.jmeter.videostreaming.dash.MediaRepresentation;
import io.lindstrom.mpd.data.Segment;
import java.net.URI;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class MultiSegmentBuilder<T> extends BaseSegmentBuilder<T> {

  private long segmentNumber;
  private long startTime;
  private TimelineSegment timelineSegment;
  private int timelineSegmentRepetitions;
  private Iterator<Segment> timelineIterator;

  protected MultiSegmentBuilder(MediaRepresentation representation) {
    super(representation);
  }

  @Override
  public void advanceUntil(DashMediaSegment lastSegment) {
    segmentNumber = getStartNumber();
    startTime = 0;
    List<Segment> timeline = getSegmentTimelineSupplier().get();
    if (timeline != null && !timeline.isEmpty()) {
      timelineIterator = timeline.iterator();
      timelineSegment = new TimelineSegment(timelineIterator.next(), 0);
      startTime = timelineSegment.startTime;
    }
    if (lastSegment == null) {
      if (manifest.isDynamic()) {
        advanceUntilStartOfBufferTime();
      }
    } else if (lastSegment.getPeriod().equals(period)) {
      while (scaledTimeToDuration(startTime).compareTo(lastSegment.getStartTime()) <= 0) {
        moveToNextSegment();
      }
    }
  }

  private void advanceUntilStartOfBufferTime() {
    Duration reproductionStart = manifest.getBufferStartTime()
        .minus(period.getStartTime());
    Long segmentDuration = getSegmentDurationSupplier().get();
    if (segmentDuration != null) {
      segmentNumber +=
          reproductionStart.toMillis() / scaledTimeToDuration(segmentDuration).toMillis() - 1;
      startTime = getSegmentIndex(segmentNumber) * segmentDuration;
    } else {
      while (hasNext()
          && reproductionStart.compareTo(scaledTimeToDuration(startTime + timelineSegment.duration))
          > 0) {
        moveToNextSegment();
      }
    }
  }

  protected abstract Supplier<Long> getSegmentDurationSupplier();

  private Duration scaledTimeToDuration(long scaledTime) {
    double seconds = (double) scaledTime / getTimescale();
    return Duration.ofMillis((long) (seconds * 1000));
  }

  private long getStartNumber() {
    Long ret = getStartNumberSupplier().get();
    return ret != null ? ret : 1L;
  }

  protected abstract Supplier<Long> getStartNumberSupplier();

  protected abstract Supplier<List<Segment>> getSegmentTimelineSupplier();

  private long getTimescale() {
    Long scale = getTimescaleSupplier().get();
    return scale != null ? scale : 1L;
  }

  protected abstract Supplier<Long> getTimescaleSupplier();

  private static class TimelineSegment {

    private long startTime;
    private long duration;
    private long repetitions;

    private TimelineSegment(Segment base, long start) {
      startTime = base.getT() != null ? base.getT() : start;
      duration = base.getD();
      repetitions = base.getR() != null ? base.getR() : 0;
    }

  }

  protected int getSegmentIndex(long segmentNumber) {
    return (int) (segmentNumber - getStartNumber());
  }

  @Override
  public boolean hasNext() {
    return (period.getEndTime() == null
        || period.getEndTime().compareTo(scaledTimeToDuration(startTime)) > 0)
        && (timelineIterator == null || hasNextSegmentInTimeline());
  }

  private boolean hasNextSegmentInTimeline() {
    return timelineIterator.hasNext() || timelineSegmentRepetitions <= timelineSegment.repetitions;
  }

  @Override
  public DashMediaSegment next() {
    DashMediaSegment ret = new DashMediaSegment(period, segmentNumber,
        getUrlSolver(segmentNumber).apply(startTime), scaledTimeToDuration(getDuration()),
        scaledTimeToDuration(startTime));
    moveToNextSegment();
    return ret;
  }

  protected abstract Function<Long, URI> getUrlSolver(long segmentNumber);

  private void moveToNextSegment() {
    segmentNumber++;
    startTime += getDuration();
    if (timelineIterator == null) {
      return;
    }
    timelineSegmentRepetitions++;
    if (timelineSegmentRepetitions > timelineSegment.repetitions && timelineIterator.hasNext()) {
      timelineSegment = new TimelineSegment(timelineIterator.next(), startTime);
      /*
       we update startTime in case of jumps (timeline segment might report another time than
       expected)
       */
      startTime = timelineSegment.startTime;
      timelineSegmentRepetitions = 0;
    }
  }

  private long getDuration() {
    Long duration = getSegmentDurationSupplier().get();
    return duration != null ? duration : timelineSegment.duration;
  }

}

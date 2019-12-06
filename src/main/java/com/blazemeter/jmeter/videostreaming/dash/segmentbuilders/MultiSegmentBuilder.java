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

  @Override
  public void advanceUntil(DashMediaSegment lastSegment) {
    segmentNumber = getStartNumber();
    startTime = getPresentationTimeOffset();
    List<Segment> timeline = getSegmentTimelineSupplier().get();
    if (timeline != null && !timeline.isEmpty()) {
      timelineIterator = timeline.iterator();
      timelineSegment = new TimelineSegment(timelineIterator.next(), 0);
      startTime = timelineSegment.startTime;
    }
    if (lastSegment == null) {
      if (manifest.isDynamic()) {
        Duration reproductionStart = manifest.getBufferStartTime()
            .minus(period.getStartTime())
            .plus(scaledTimeToDuration(startTime));
        advanceUntilTime(reproductionStart, true);
      }
    } else if (lastSegment.getPeriod().equals(period)) {
      advanceUntilTime(lastSegment.getEndTime(), false);
    }
  }

  private long getStartNumber() {
    Long ret = getStartNumberSupplier().get();
    return ret != null ? ret : 1L;
  }

  protected abstract Supplier<Long> getStartNumberSupplier();

  protected abstract Supplier<List<Segment>> getSegmentTimelineSupplier();

  protected abstract Supplier<Long> getSegmentDurationSupplier();

  private Duration scaledTimeToDuration(long scaledTime) {
    double seconds = (double) scaledTime / getTimescale();
    return Duration.ofMillis((long) (seconds * 1000));
  }

  private long getTimescale() {
    Long scale = getTimescaleSupplier().get();
    return scale != null ? scale : 1L;
  }

  protected abstract Supplier<Long> getTimescaleSupplier();

  protected int getSegmentIndex(long segmentNumber) {
    return (int) (segmentNumber - getStartNumber());
  }

  private void advanceUntilTime(Duration time, boolean init) {
    Long segmentDuration = getSegmentDurationSupplier().get();
    if (segmentDuration != null) {
      double incrDec = (double) time.toMillis() / scaledTimeToDuration(segmentDuration).toMillis();
      // we round times due to double precision issues
      long incr = init ? (long) incrDec : Math.round(incrDec);
      segmentNumber += incr;
      startTime += incr * segmentDuration;
    } else {
      while (hasNext()
          && scaledTimeToDuration(startTime + timelineSegment.duration).compareTo(time) <= 0) {
        /*
        instead converting scaling time to seconds (dividing by 1000), we scale it to millis
        to and work with millis which requires smaller doubles to operate and less precision
        issues
        */
        long repetitionsUntilTime =
            Math.round((double) (time.toMillis() * getTimescale() - startTime * 1000) / (
                timelineSegment.duration * 1000));
        long pendingRepetitions = timelineSegment.repetitions - timelineSegmentRepetitions + 1;
        long repetitions = Math.min(pendingRepetitions, repetitionsUntilTime);
        segmentNumber += repetitions;
        startTime += repetitions * timelineSegment.duration;
        timelineSegmentRepetitions += repetitions;
        moveToNextTimelineSegmentIfNeeded();
      }
      /*
      when initializing segments on manifest, at least return one segment.
      This might happen due to rounding and double precision issues and due to potential no segments
      yet generated containing publish time - minimum time buffer (which we use as reproduction
      starting point)
       */
      //TODO fix this and use proper buffering time instead of reproduction start time which is not
      // accurate and requires this workaround
      if (init && !hasNext()) {
        segmentNumber--;
        startTime -= timelineSegment.duration;
        timelineSegmentRepetitions--;
      }
    }
  }

  private void moveToNextTimelineSegmentIfNeeded() {
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

  @Override
  public boolean hasNext() {
    return (period.getEndTime() == null
        ||
        period.getEndTime().compareTo(scaledTimeToDuration(startTime - getPresentationTimeOffset()))
            > 0)
        && (timelineIterator == null || hasNextSegmentInTimeline());
  }

  private long getPresentationTimeOffset() {
    Long offset = getPresentationTimeOffsetSupplier().get();
    return offset != null ? offset : 0L;
  }

  protected abstract Supplier<Long> getPresentationTimeOffsetSupplier();

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
    moveToNextTimelineSegmentIfNeeded();
  }

  private long getDuration() {
    Long duration = getSegmentDurationSupplier().get();
    return duration != null ? duration : timelineSegment.duration;
  }

}

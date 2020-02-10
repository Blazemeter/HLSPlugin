package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.videostreaming.core.MediaStreamSelector;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.BaseURL;
import io.lindstrom.mpd.data.Period;
import io.lindstrom.mpd.data.SegmentBase;
import io.lindstrom.mpd.data.SegmentList;
import io.lindstrom.mpd.data.SegmentTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaPeriod {

  private static final Logger LOG = LoggerFactory.getLogger(MediaPeriod.class);

  private final Period period;
  private final Manifest manifest;
  private final int index;
  private final Duration startTime;
  private final Duration duration;

  private MediaPeriod(Period period, Manifest manifest, int index,
      Duration startTime, Duration duration) {
    this.period = period;
    this.manifest = manifest;
    this.index = index;
    this.startTime = startTime;
    this.duration = duration;
  }

  public Manifest getManifest() {
    return manifest;
  }

  public String getId() {
    return period.getId();
  }

  public Duration getStartTime() {
    return startTime;
  }

  public Duration getDuration() {
    return duration;
  }

  public Duration getEndTime() {
    return startTime != null && duration != null ? startTime.plus(duration) : null;
  }

  public List<BaseURL> getBaseURLs() {
    return period.getBaseURLs();
  }

  public SegmentTemplate getSegmentTemplate() {
    return period.getSegmentTemplate();
  }

  public SegmentList getSegmentList() {
    return period.getSegmentList();
  }

  public SegmentBase getSegmentBase() {
    return period.getSegmentBase();
  }

  public SegmentBuilder findSegmentBuilder(String type,
      MediaStreamSelector<MediaRepresentation> streamSelector, String languageSelector) {

    AdaptationSet adaptationSet = findAdaptationSet(period.getAdaptationSets(), type,
        languageSelector);
    if (adaptationSet == null) {
      return null;
    }
    List<MediaRepresentation> mediaRepresentations = adaptationSet.getRepresentations().stream()
        .map(r -> new MediaRepresentation(manifest, this, adaptationSet, r))
        .collect(Collectors.toList());
    MediaRepresentation representation = streamSelector.findMatchingVariant(mediaRepresentations);
    return representation != null ? representation.getSegmentBuilder() : null;
  }

  private AdaptationSet findAdaptationSet(List<AdaptationSet> adaptationsSets, String type,
      String languageSelector) {
    String contentType =
        VideoStreamingSampler.SUBTITLES_TYPE_NAME.equals(type) ? "text" : type;
    List<AdaptationSet> adaptationSets = adaptationsSets
        .stream()
        .filter(adaptationSet -> isMatchingTypeAdaptationSet(contentType, adaptationSet))
        .collect(Collectors.toList());

    if (adaptationSets.isEmpty()) {
      return null;
    }

    return adaptationSets.stream()
        .filter(adaptationSet -> languageSelector == null || languageSelector.isEmpty()
            || adaptationSet.getLang() != null && languageSelector.toLowerCase()
            .contains(adaptationSet.getLang()))
        .findFirst()
        .orElseGet(() -> {
          LOG.warn(
              "No adaptation set of type {} was found for the language {}. "
                  + "Using the first one in the list instead.",
              type, languageSelector);
          return adaptationSets.get(0);
        });
  }

  private boolean isMatchingTypeAdaptationSet(String contentType, AdaptationSet adaptationSet) {
    return (adaptationSet.getMimeType() != null
        && adaptationSet.getMimeType().startsWith(contentType))
        || contentType.equals(adaptationSet.getContentType())
        || (adaptationSet.getMimeType() == null && adaptationSet.getContentType() == null
        && !adaptationSet.getRepresentations().isEmpty()
        && adaptationSet.getRepresentations().get(0).getMimeType().startsWith(contentType));
  }

  public static Builder builder() {
    return new Builder();
  }

  public Instant getAvailabilityStartTime() {
    return manifest.getAvailabilityStartTime().plus(startTime);
  }

  public static class Builder {

    private Period period;
    private Manifest manifest;
    private int index;
    private MediaPeriod previousPeriod;
    private Period nextPeriod;

    public Builder withPeriod(Period period) {
      this.period = period;
      return this;
    }

    public Builder withManifest(Manifest manifest) {
      this.manifest = manifest;
      return this;
    }

    public Builder withIndex(int index) {
      this.index = index;
      return this;
    }

    public Builder withPreviousPeriod(MediaPeriod previousPeriod) {
      this.previousPeriod = previousPeriod;
      return this;
    }

    public Builder withNextPeriod(Period nextPeriod) {
      this.nextPeriod = nextPeriod;
      return this;
    }

    public MediaPeriod build() {
      Duration startTime = period.getStart();
      if (startTime == null) {
        if (previousPeriod != null) {
          startTime =
              previousPeriod.duration != null ? previousPeriod.duration
                  .plus(previousPeriod.startTime)
                  : null;
        } else {
          startTime = !manifest.isDynamic() ? Duration.ZERO : null;
        }
      }
      Duration duration = period.getDuration() != null ? period.getDuration()
          : manifest.getMediaPresentationDuration();
      if (duration == null && startTime != null && nextPeriod != null
          && nextPeriod.getStart() != null) {
        duration = nextPeriod.getStart().minus(startTime);
      }
      return new MediaPeriod(period, manifest, index, startTime, duration);
    }

  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MediaPeriod periodId1 = (MediaPeriod) o;
    if (period.getId() != null) {
      return period.getId().equals(periodId1.period.getId());
    } else {
      return periodId1.period.getId() == null && index == periodId1.index;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(period.getId() != null ? period.getId() : index);
  }

}

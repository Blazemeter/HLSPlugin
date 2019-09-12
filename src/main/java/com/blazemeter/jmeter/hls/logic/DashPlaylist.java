package com.blazemeter.jmeter.hls.logic;

import io.lindstrom.mpd.MPDParser;
import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.MPD;
import io.lindstrom.mpd.data.Period;
import io.lindstrom.mpd.data.Representation;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: update the adaptation set when multiple Periods
public class DashPlaylist {

  private static final String VIDEO_TYPE_NAME = "video";
  private static final Logger LOG = LoggerFactory.getLogger(DashPlaylist.class);
  private final Instant downloadTimestamp;
  private final MPD manifest;
  private String lastPeriodId;
  private final String type;

  public DashPlaylist(String type, MPD manifest, Instant downloadTimestamp) {
    this.manifest = manifest;
    this.downloadTimestamp = downloadTimestamp;
    this.type = type;
  }

  public static DashPlaylist fromUriAndManifest(String type, String manifestAsStrng,
      Instant downloadTimestamp)
      throws IOException {
    MPD manifest = new MPDParser().parse(manifestAsStrng);
    return new DashPlaylist(type, manifest, downloadTimestamp);
  }

  public Period getNextPeriod() {
    List<Period> periods = manifest.getPeriods();
    boolean foundLast = false;
    for (Period p : periods) {
      if (foundLast) {
        lastPeriodId = p.getId();
        return p;
      }
      if (p.getId().equals(lastPeriodId)) {
        foundLast = true;
      }
    }

    return null;
  }

  public MediaRepresentation solveMediaRepresentation(ResolutionSelector resolutionSelector,
      BandwidthSelector bandwidthSelector, String baseURL, String languageSelector) {

    //TODO: Need to make the logic for multiple Periods.
    Period period = manifest.getPeriods().get(0);

    AdaptationSet adaptationSet = getAdaptationSetByType(type, period, languageSelector);
    if (adaptationSet != null) {
      Representation representation = solveRepresentation(type, adaptationSet,
          resolutionSelector, bandwidthSelector);
      if (representation != null) {
        LOG.info("Representation found, using {}", representation.getId());
        return new MediaRepresentation(representation, adaptationSet, solveBaseURL(baseURL));
      }
    }

    return null;
  }

  private String solveBaseURL(String url) {
    String baseURL;
    if (manifest.getBaseURLs() == null || manifest.getBaseURLs().size() < 1) {
      int lastIndex = url.lastIndexOf("/");
      baseURL = url.substring(0, lastIndex + 1);
      LOG.info("Base URL not found, using {} instead", baseURL);
    } else {
      baseURL = manifest.getBaseURLs().get(0).getValue();
    }
    return baseURL;
  }

  private AdaptationSet getAdaptationSetByType(String type, Period period,
      String typeLanguageSelector) {

    List<AdaptationSet> adaptationSetByTypes = period.getAdaptationSets()
        .stream()
        .filter(adaptationSet ->
            adaptationSet.getMimeType().contains(type))
        .collect(Collectors.toList());

    if (adaptationSetByTypes.size() > 0) {
      if (type.equals(VIDEO_TYPE_NAME)) {
        return adaptationSetByTypes.get(0);
      }

      return adaptationSetByTypes.stream().filter(adaptationSet ->
          adaptationSet.getLang().contains(typeLanguageSelector)).findAny().orElse(null);
    }

    return null;
  }

  private Representation solveRepresentation(String type, AdaptationSet adaptationSet,
      ResolutionSelector resolutionSelector, BandwidthSelector bandwidthSelector) {

    if (adaptationSet == null) {
      return null;
    }

    String lastMatchedResolution = null;
    Long lastMatchedBandwidth = null;
    Representation lastMatchedRepresentation = null;

    for (Representation representation : adaptationSet.getRepresentations()) {
      if (bandwidthSelector.matches(representation.getBandwidth(), lastMatchedBandwidth)) {
        lastMatchedBandwidth = representation.getBandwidth();
        if (type.equals(VIDEO_TYPE_NAME)) {
          if (resolutionSelector.matches(representation.getWidth() + "x" +
              representation.getHeight(), lastMatchedResolution)) {
            lastMatchedResolution = representation.getWidth() + "x" + representation.getHeight();
            lastMatchedRepresentation = representation;
          }
        } else {
          lastMatchedRepresentation = representation;
        }
      }
    }

    return lastMatchedRepresentation;
  }

  public MPD getManifest() {
    return manifest;
  }
}

package com.blazemeter.jmeter.hls.logic;

import com.comcast.viper.hlsparserj.MasterPlaylist;
import com.comcast.viper.hlsparserj.tags.master.StreamInf;
import io.lindstrom.mpd.MPDParser;
import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.MPD;
import io.lindstrom.mpd.data.Period;
import io.lindstrom.mpd.data.Representation;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: update the adaptation set when multiple Periods
public class DashPlaylist {

  private static final String VIDEO_TYPE_NAME = "video";
  private static final Logger LOG = LoggerFactory.getLogger(DashPlaylist.class);
  private final Instant downloadTimestamp;
  private final MPD manifest;
  private final String type;
  private int actualPeriodIndex;
  private String manifestURL;

  public DashPlaylist(String type, MPD manifest, Instant downloadTimestamp, String manifestURL) {
    this.manifest = manifest;
    this.downloadTimestamp = downloadTimestamp;
    this.type = type;
    this.actualPeriodIndex = 0;
    this.manifestURL = manifestURL;
  }

  public static DashPlaylist fromUriAndManifest(String type, String manifestAsStrng,
      Instant downloadTimestamp, String manifestURL)
      throws IOException {
    MPD manifest = new MPDParser().parse(manifestAsStrng);
    return new DashPlaylist(type, manifest, downloadTimestamp, manifestURL);
  }

  public void updatePeriod() {
    if (actualPeriodIndex + 1 <= manifest.getPeriods().size() - 1) {
      actualPeriodIndex++;
    } else {
      actualPeriodIndex = -1;
    }
  }

  public MediaRepresentation solveMediaRepresentation(ResolutionSelector resolutionSelector,
      BandwidthSelector bandwidthSelector, String languageSelector) {

    //TODO: Need to make the logic for multiple Periods.
    Period period = manifest.getPeriods().get(actualPeriodIndex);

    AdaptationSet adaptationSet = getAdaptationSetByType(type, period, languageSelector);
    if (adaptationSet != null) {
      Representation representation = solveRepresentation(type, adaptationSet,
          resolutionSelector, bandwidthSelector);
      if (representation != null) {
        LOG.info("Representation found, using {}", representation.getId());

        if (manifest.getBaseURLs().size() > 0) {
          LOG.info("Manifest has {} Base URLs. Using the first", manifest.getBaseURLs().size());
        } else {
          LOG.info("Manifest has no Base URL tag. Using {} instead.", manifestURL);
        }

        return new MediaRepresentation(representation, adaptationSet,
            solveBaseURL(
                manifest.getBaseURLs().size() > 0 ? manifest.getBaseURLs().get(0).getValue()
                    : manifestURL));
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
        .filter(adaptationSet -> adaptationSet.getMimeType()
            .contains(type))
        .collect(Collectors.toList());

    if (adaptationSetByTypes.size() > 0) {
      if (type.equals(VIDEO_TYPE_NAME)) {
        return adaptationSetByTypes.get(0);
      }

      return adaptationSetByTypes.stream().filter(adaptationSet ->
          adaptationSet.getLang()
              .contains(typeLanguageSelector))
          .findAny()
          .orElse(null);
    }

    return null;
  }




  private Representation solveRepresentation(String type, AdaptationSet adaptationSet,
      ResolutionSelector resolutionSelector, BandwidthSelector bandwidthSelector) {

    if (adaptationSet == null) {
      return null;
    }

    Function<Representation, Long> bandwidthAccessor = v -> (long) v.getBandwidth();
    if (bandwidthSelector.getCustomBandwidth() == null
        && resolutionSelector.getCustomResolution() != null) {
      return findSelectedVariant(Representation::getResolution, resolutionSelector, bandwidthAccessor,
          bandwidthSelector, adaptationSet.getRepresentations());
    } else {
      return findSelectedVariant(bandwidthAccessor, bandwidthSelector, Representation::getResolution,
          resolutionSelector, adaptationSet.getRepresentations());
    }
  }

  private <T, U> Representation findSelectedVariant(Function<Representation, T> firstAttribute,
      BiPredicate<T, T> firstAttributeSelector, Function<Representation, U> secondAttribute,
      BiPredicate<U, U> secondAttributeSelector, List<Representation> variants) {
    Representation matchedVariant = findVariantPerAttribute(firstAttribute, firstAttributeSelector,
        variants);
    if (matchedVariant == null) {
      return null;
    }
    T selectedAttribute = firstAttribute.apply(matchedVariant);
    List<Representation> matchingVariants = variants.stream()
        .filter(v -> firstAttribute.apply(v).equals(selectedAttribute))
        .collect(Collectors.toList());
    return findVariantPerAttribute(secondAttribute, secondAttributeSelector, matchingVariants);
  }

  private <T> Representation findVariantPerAttribute(Function<Representation, T> attribute,
      BiPredicate<T, T> attributeSelector, List<Representation> variants) {
    T lastMatchAttribute = null;
    Representation lastMatchVariant = null;
    for (Representation variant : variants) {
      T attr = attribute.apply(variant);
      if (attributeSelector.test(attr, lastMatchAttribute)) {
        lastMatchAttribute = attr;
        lastMatchVariant = variant;
      }
    }
    return lastMatchVariant;
  }


  public MPD getManifest() {
    return manifest;
  }

  public int getActualPeriodIndex() {
    return actualPeriodIndex;
  }

}

package com.blazemeter.jmeter.videostreaming.dash;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.HlsSampler;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingPlayback;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.Segment;
import io.lindstrom.mpd.data.SegmentTemplate;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashSampler extends VideoStreamingSampler {

  private static final String INITIALIZATION_SEGMENT = "Initialization";
  private static final String REPRESENTATION_ID_FORMULA_REPLACE = "$RepresentationID$";
  private static final String BANDWIDTH_FORMULA_REPLACE = "$Bandwidth$";
  private static final String TIME_FORMULA_REPLACE = "$Time$";
  private static final String NUMBER_FORMULA_REPLACE = "$Number$";
  private static final String FORMATTED_NUMBER_FORMULA_PATTERN = "(?<=\\$Number)(.*?)(?=\\$)";

  private static final Logger LOG = LoggerFactory.getLogger(DashSampler.class);

  public DashSampler(HlsSampler baseSampler, VideoStreamingHttpClient httpClient,
      TimeMachine timeMachine, SampleResultProcessor sampleResultProcessor) {
    super(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  public SampleResult sample() {
    String url = baseSampler.getMasterUrl();
    try {
      DashPlaylist mediaPlaylist = downloadManifest(url);

      if (mediaPlaylist.getManifest() != null) {
        DashPlaylist audioPlaylist = DashPlaylist
            .fromUriAndManifest(AUDIO_TYPE_NAME, mediaPlaylist.getManifest(), url,
                baseSampler.getAudioLanguage());
        DashPlaylist subtitlesPlaylist = DashPlaylist
            .fromUriAndManifest(SUBTITLES_TYPE_NAME, mediaPlaylist.getManifest(), url,
                baseSampler.getSubtitleLanguage());

        ResolutionSelector resolutionSelector = baseSampler.getResolutionSelector();
        BandwidthSelector bandwidthSelector = baseSampler.getBandwidthSelector();
        MediaRepresentation mediaRepresentation = mediaPlaylist
            .solveMediaRepresentation(resolutionSelector, bandwidthSelector);
        MediaRepresentation audioRepresentation = audioPlaylist
            .solveMediaRepresentation(resolutionSelector, bandwidthSelector);
        MediaRepresentation subtitlesRepresentation = subtitlesPlaylist
            .solveMediaRepresentation(resolutionSelector, bandwidthSelector);

        int playSeconds = baseSampler.getPlaySecondsOrWarn();

        DashMediaPlayback mediaPlayback = new DashMediaPlayback(mediaPlaylist, mediaRepresentation,
            lastVideoSegmentNumber, playSeconds, MEDIA_TYPE_NAME);
        DashMediaPlayback audioPlayback = new DashMediaPlayback(audioPlaylist, audioRepresentation,
            lastAudioSegmentNumber, playSeconds, AUDIO_TYPE_NAME);
        DashMediaPlayback subtitlesPlayback = new DashMediaPlayback(subtitlesPlaylist,
            subtitlesRepresentation, lastSubtitleSegmentNumber, playSeconds, SUBTITLES_TYPE_NAME);

        try {
          while (!mediaPlayback.hasEnded()) {
            mediaPlayback.downloadNextSegment();
            float playedSeconds = mediaPlayback.getPlayedTimeSeconds();
            if (playSeconds > 0 && playSeconds < playedSeconds) {
              playedSeconds = playSeconds;
            }
            audioPlayback.downloadUntilTimeSecond(playedSeconds);
            subtitlesPlayback.downloadUntilTimeSecond(playedSeconds);
          }
        } finally {
          lastVideoSegmentNumber = mediaPlayback.getLastSegmentNumber();
          lastSubtitleSegmentNumber = subtitlesPlayback.getLastSegmentNumber();
          lastAudioSegmentNumber = audioPlayback.getLastSegmentNumber();
        }
      }
    } catch (IOException | PlaylistDownloadException e) {
      LOG.warn("Problem downloading manifest from {}", url, e);
      Thread.currentThread().interrupt();
    }

    return null;
  }

  private DashPlaylist downloadManifest(String url) throws PlaylistDownloadException, IOException {
    URI manifestUri = URI.create(url);
    HTTPSampleResult manifestResult = httpClient.downloadUri(manifestUri);

    if (!manifestResult.isSuccessful()) {
      sampleResultProcessor.accept("Manifest", manifestResult);
      throw new PlaylistDownloadException("Manifest", manifestUri);
    }

    // we update uri in case the request was redirected
    try {
      manifestUri = manifestResult.getURL().toURI();
    } catch (URISyntaxException e) {
      LOG.warn("Problem updating uri from downloaded manifest {}. Continue with original uri {}",
          manifestResult.getURL(), manifestUri, e);
    }

    try {
      DashPlaylist videoPlaylist = DashPlaylist
          .fromUriAndBody(VIDEO_TYPE_NAME, manifestResult.getResponseDataAsString(), url, null);
      sampleResultProcessor.accept("Manifest", manifestResult);
      return videoPlaylist;
    } catch (IOException e) {
      sampleResultProcessor.accept("Manifest", baseSampler.errorResult(e, manifestResult));
      throw e;
    }
  }

  private class DashMediaPlayback extends VideoStreamingPlayback {

    private SegmentTimelinePlayback segmentTimelinePlayback;
    private MediaRepresentation representation;
    private DashPlaylist playlist;
    private int lastSegmentTimelineNumber;
    private long timePassedSinceLastUpdate;

    private DashMediaPlayback(DashPlaylist playlist, MediaRepresentation representation,
        long lastSegmentNumber, int playSeconds, String type) {
      super(playSeconds, lastSegmentNumber, type);
      this.playlist = playlist;
      this.representation = representation;
      this.segmentTimelinePlayback = null;
      this.lastSegmentTimelineNumber = 0;
      this.timePassedSinceLastUpdate = 0;
    }

    private void updateManifest() throws IOException {
      if (playlist.isDynamic() && timePassedSinceLastUpdate >= playlist.getManifest()
          .getTimeShiftBufferDepth()
          .toMillis() && playlist.liveStreamingContinues()) {
        playlist.updateManifestFromBody(
            httpClient.downloadUri(URI.create(playlist.getManifestURL()))
                .getResponseDataAsString());
        representation = playlist
            .solveMediaRepresentation(baseSampler.getResolutionSelector(),
                baseSampler.getBandwidthSelector());
        timePassedSinceLastUpdate = 0;
      }
    }

    private boolean canDownload() {
      return representation != null && (isWholeVideo() || playSeconds > consumedSeconds);
    }

    private boolean isWholeVideo() {
      return playSeconds == 0;
    }

    private void downloadUntilTimeSecond(float untilTimeSecond) throws IOException {
      while (consumedSeconds < untilTimeSecond && canDownload()) {
        downloadNextSegment();
      }
    }

    private void downloadNextSegment() throws IOException {
      AdaptationSet adaptationSetUsed = representation.getAdaptationSet();
      SegmentTemplate template = adaptationSetUsed.getSegmentTemplate();

      String adaptationBaseURL = (
          adaptationSetUsed.getBaseURLs() != null && adaptationSetUsed.getBaseURLs().size() > 0
              ? adaptationSetUsed.getBaseURLs().get(0).getValue() : "");

      if (lastSegmentNumber < 1) {
        if (template != null && template.getStartNumber() != null) {
          lastSegmentNumber = template.getStartNumber();
        } else {
          lastSegmentNumber = 1;
        }
      }

      if (lastSegmentNumber < 2 && representation.needManualInitialization()) {
        String initializeURL =
            representation.getBaseURL() + adaptationBaseURL + buildFormula(template,
                INITIALIZATION_SEGMENT);
        LOG.info("Downloading initialization for type {} from url {}", type, initializeURL);

        HTTPSampleResult initializeResult = httpClient.downloadUri(URI.create(initializeURL));
        if (!initializeResult.isSuccessful()) {
          SampleResult failResult = buildNotMatchingMediaPlaylistResult();
          sampleResultProcessor.accept("Init " + type, failResult);
        } else {
          sampleResultProcessor.accept("Init " + type, initializeResult);
        }
      }

      String segmentURL =
          representation.getBaseURL() + adaptationBaseURL + buildFormula(template, "Media");
      LOG.info("Downloading {}", segmentURL);

      HTTPSampleResult downloadSegmentResult = httpClient.downloadUri(URI.create(segmentURL));
      if (!downloadSegmentResult.isSuccessful()) {
        HTTPSampleResult failDownloadResult = buildErrorWhileDownloadingMediaSegmentResult(type);
        sampleResultProcessor.accept(type + " segment", failDownloadResult);
        LOG.warn("There was an error while downloading {} segment from {}. Code: {}. Message: {}",
            type,
            segmentURL, downloadSegmentResult.getResponseCode(),
            downloadSegmentResult.getResponseMessage());
      } else {
        sampleResultProcessor.accept(type + " segment", downloadSegmentResult);
      }

      lastSegmentNumber++;
      if (isLiveStreamSet() && template != null) {
        consumedSeconds =
            (float) segmentTimelinePlayback.getTotalDuration() / template.getTimescale();
      } else if (representation.isOneDownloadOnly()) {
        consumedSeconds += representation.getTotalDuration();
      } else {
        consumedSeconds++;
      }
      LOG.info("Consumed seconds for {} updated to {}", type, consumedSeconds);

      updatePeriod();
      updateManifest();
    }

    private String buildFormula(SegmentTemplate template, String formulaType) {
      /*
       * There is some cases where the adaptation's BaseURL doesn't need a formula.
       * In those cases SegmentTemplate don't appear.
       */
      if (template == null) {
        return "";
      }

      String formula = (formulaType.equals(INITIALIZATION_SEGMENT) ? template.getInitialization()
          : template.getMedia());

      formula = formula
          .replace(REPRESENTATION_ID_FORMULA_REPLACE, representation.getRepresentation().getId());
      formula = formula.replace(BANDWIDTH_FORMULA_REPLACE,
          Long.toString(representation.getRepresentation().getBandwidth()));
      formula = formula.replace(NUMBER_FORMULA_REPLACE, Long.toString(lastSegmentNumber));

      Pattern pattern = Pattern.compile(FORMATTED_NUMBER_FORMULA_PATTERN);
      Matcher matcher = pattern.matcher(formula);
      if (matcher.find()) {
        String lastSegmentFormatted = String.format(matcher.group(1), lastSegmentNumber);
        formula = formula.replaceAll("(?<=\\$)(.*?)(?=\\$)", lastSegmentFormatted).replace("$", "");
      }

      if (formula.contains(TIME_FORMULA_REPLACE)) {
        if (segmentTimelinePlayback == null) {
          Segment segment = template.getSegmentTimeline().get(lastSegmentTimelineNumber);
          segmentTimelinePlayback = new SegmentTimelinePlayback(segment.getT(), segment.getD(),
              (segment.getR() != null ? segment.getR() : 1));
        }

        formula = formula
            .replace(TIME_FORMULA_REPLACE,
                Long.toString(segmentTimelinePlayback.getNextTimeline()));
        segmentTimelinePlayback.updateSegmentTimelinePlayback(template);
      }

      return formula;
    }

    private boolean isLiveStreamSet() {
      return segmentTimelinePlayback != null;
    }

    private void updatePeriod() {
      if (hasReachedEnd() && !playedRequestedTime()) {
        playlist.updatePeriod();
        representation = playlist
            .solveMediaRepresentation(baseSampler.getResolutionSelector(),
                baseSampler.getBandwidthSelector());
      }
    }

    private boolean hasEnded() {
      return (representation == null || playedRequestedTime() || hasReachedEnd());
    }

    private boolean hasReachedEnd() {
      return playlist.getActualPeriodIndex() == -1;
    }

    private class SegmentTimelinePlayback {

      private long time;
      private long duration;
      private long repeat;
      private long totalDuration;

      private SegmentTimelinePlayback(long time, long duration, long repeat) {
        this.time = time;
        this.duration = duration;
        this.repeat = repeat;
        this.totalDuration = 0;
      }

      private long getTotalDuration() {
        return totalDuration;
      }

      private long getNextTimeline() {
        long nextTimeline = time + duration;
        time = nextTimeline;
        totalDuration += duration;
        repeat--;
        return nextTimeline;
      }

      private void updateSegmentTimelinePlayback(SegmentTemplate template) {
        if (repeat <= 0) {
          lastSegmentTimelineNumber++;
          Segment segment = template.getSegmentTimeline().get(lastSegmentTimelineNumber);
          time = segment.getT() != null ? segment.getT() : time;
          repeat = segment.getR() != null ? segment.getR() : 1;
          duration = segment.getD();
        }
      }
    }

  }

  private static HTTPSampleResult buildErrorWhileDownloadingMediaSegmentResult(String type) {
    return HlsSampler.errorResult("ErrorWhileDownloading" + type,
        "There was an error while downloading " + type
            + " segment. Please check the logs for more information");
  }

}

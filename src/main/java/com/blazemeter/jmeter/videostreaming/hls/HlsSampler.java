package com.blazemeter.jmeter.videostreaming.hls;

import static com.blazemeter.jmeter.hls.logic.VideoStreamingSamplerFactory.isHLSPlaylistLink;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.MediaSegment;
import com.blazemeter.jmeter.videostreaming.core.PlaybackSession;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.StreamingSliceCoordinator;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.Variants;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingPlayback;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import com.comcast.viper.hlsparserj.MasterPlaylist;
import com.comcast.viper.hlsparserj.tags.master.StreamInf;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HlsSampler extends VideoStreamingSampler<Playlist, MediaSegment> {

  private static final Logger LOG = LoggerFactory.getLogger(HlsSampler.class);

  // RFC 8216 Section 6.3.3: start this many target durations from the live edge
  private static final int RFC_8216_LIVE_EDGE_DURATIONS = 3;

  private final transient PlaybackSession<MediaPlayback, Playlist> session =
      new PlaybackSession<>();

  public HlsSampler(com.blazemeter.jmeter.hls.logic.HlsSampler baseSampler,
      VideoStreamingHttpClient httpClient, TimeMachine timeMachine,
      SampleResultProcessor sampleResultProcessor) {
    super(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  @Override
  public void clearPlaybackSession() {
    session.clear();
  }

  public void sample(URI masterUri, BandwidthSelector bandwidthSelector,
      ResolutionSelector resolutionSelector, String audioLanguage, String subtitleLanguage,
      int playSeconds)
      throws PlaylistDownloadException, PlaylistParsingException, InterruptedException {
    boolean active = StreamingSliceCoordinator.isActive();
    MediaPlayback mediaPlayback;
    MediaPlayback audioPlayback;
    MediaPlayback subtitlesPlayback;

    if (active && session.isInitialized()) {
      mediaPlayback = session.getPrimary();
      audioPlayback = session.getComplements().get(0);
      subtitlesPlayback = session.getComplements().get(1);
    } else {
      Playlist masterPlaylist = downloadMasterPlaylist(masterUri);

      Playlist mediaPlaylist;
      Playlist audioPlaylist = null;
      Playlist subtitlesPlaylist = null;

      if (masterPlaylist.isMasterPlaylist()) {
        List<String> bandwidths = getBandwidths((MasterPlaylist) masterPlaylist.getPlaylist());
        List<String> resolutions = getResolutions((MasterPlaylist) masterPlaylist.getPlaylist());
        if (bandwidthSelector.getCustomBandwidth() != null
            && !bandwidths.contains(bandwidthSelector.getCustomBandwidth())) {
          sampleResultProcessor.accept(buildPlaylistName(MEDIA_TYPE_NAME),
              buildNotMatchingMediaPlaylistResult(variantsToString(bandwidths), "bandwidth"));
          endSessionOnSetupFailure();
          return;
        } else if (resolutionSelector.getCustomResolution() != null
            && !resolutions.contains(resolutionSelector.getCustomResolution())) {
          sampleResultProcessor.accept(buildPlaylistName(MEDIA_TYPE_NAME),
              buildNotMatchingMediaPlaylistResult(variantsToString(resolutions), "resolution"));
          endSessionOnSetupFailure();
          return;
        }

        MediaStream mediaStream = masterPlaylist.solveMediaStream(bandwidthSelector,
            resolutionSelector, audioLanguage, subtitleLanguage);

        mediaPlaylist = downloadPlaylist(mediaStream.getMediaPlaylistUri(), MEDIA_TYPE_NAME);
        audioPlaylist = tryDownloadPlaylist(mediaStream.getAudioUri(),
            p -> buildPlaylistName(AUDIO_TYPE_NAME));
        subtitlesPlaylist = tryDownloadPlaylist(mediaStream.getSubtitlesUri(),
            p -> p != null ? buildPlaylistName(SUBTITLES_TYPE_NAME) : SUBTITLES_TYPE_NAME);
      } else {
        mediaPlaylist = masterPlaylist;
      }

      boolean liveEdge = isStartFromLiveEdge();
      mediaPlayback = new MediaPlayback(mediaPlaylist, MEDIA_TYPE_NAME,
          lastVideoSegment, playSeconds, liveEdge);
      audioPlayback = new MediaPlayback(audioPlaylist, AUDIO_TYPE_NAME,
          lastAudioSegment, playSeconds, liveEdge);
      subtitlesPlayback = new MediaPlayback(subtitlesPlaylist, SUBTITLES_TYPE_NAME,
          lastSubtitleSegment, playSeconds, liveEdge);
      mediaPlayback.downloadInitializationSegment();

      if (active) {
        session.setPrimary(mediaPlayback);
        session.setComplements(Arrays.asList(audioPlayback, subtitlesPlayback));
        session.markInitialized();
      }
    }

    boolean finished = false;
    try {
      while (!mediaPlayback.hasEnded() && !shouldYieldForSlice()) {
        mediaPlayback.downloadNextSegment();
        double playedSeconds = mediaPlayback.getPlayedTimeSeconds();
        if (playSeconds > 0 && playSeconds < playedSeconds) {
          playedSeconds = playSeconds;
        }
        audioPlayback.downloadUntilTimeSecond(playedSeconds);
        subtitlesPlayback.downloadUntilTimeSecond(playedSeconds);
      }
      finished = mediaPlayback.hasEnded();
    } finally {
      playbackFinishedThisSample = finished;
      if (finished || !active) {
        lastVideoSegment = mediaPlayback.getLastSegment();
        lastAudioSegment = audioPlayback.getLastSegment();
        lastSubtitleSegment = subtitlesPlayback.getLastSegment();
        session.clear();
      }
    }
  }

  // A variant/resolution mismatch means playback cannot proceed; treat it as terminal so the
  // parallel controller ends the iteration instead of re-entering and re-fetching forever.
  private void endSessionOnSetupFailure() {
    playbackFinishedThisSample = true;
    session.clear();
  }

  @Override
  public Variants getVariants(URI masterUri) {
    Playlist masterPlaylist = null;
    try {
      masterPlaylist = downloadPlaylist(masterUri, Playlist::fromUriAndBody);
    } catch (PlaylistDownloadException | PlaylistParsingException e) {
      LOG.warn("Problem downloading the playlist {}", masterUri, e);
    }

    Variants variants = new Variants();
    variants.setBandwidthResolutionMap(getBandwidthResolutionMap(
        (MasterPlaylist) masterPlaylist.getPlaylist()));
    variants.setBandwidthList(getBandwidths((MasterPlaylist) masterPlaylist.getPlaylist()));
    variants.setResolutionList(getResolutions((MasterPlaylist) masterPlaylist.getPlaylist()));
    variants.setAudioLanguageList(getAudioLanguage((MasterPlaylist) masterPlaylist.getPlaylist()));
    variants.setSubtitleList(masterPlaylist.getSubtitles());

    return variants;
  }

  public Map<String, String> getBandwidthResolutionMap(MasterPlaylist masterPlaylist) {
    Map<String, String> bandwidthResolutionMap = new HashMap<>();
    for (StreamInf streamInf : masterPlaylist.getVariantStreams()) {
      bandwidthResolutionMap.put(String.valueOf(streamInf.getBandwidth()),
          streamInf.getResolution());
    }
    return bandwidthResolutionMap;
  }

  private String variantsToString(List<String> variants) {
    StringBuilder result = new StringBuilder();
    for (String v : variants) {
      if (v != null) {
        result.append(v).append("\n");
      }
    }
    return result.toString();
  }

  private List<String> getBandwidths(MasterPlaylist masterPlaylist) {
    List<String> bandwidths = new ArrayList<>();
    masterPlaylist.getVariantStreams().forEach(si ->
        bandwidths.add(String.valueOf(si.getBandwidth())));
    return new ArrayList<>(bandwidths);
  }

  private List<String> getResolutions(MasterPlaylist masterPlaylist) {
    List<String> resolutions = new ArrayList<>();
    for (StreamInf si : masterPlaylist.getVariantStreams()) {
      resolutions.add(si.getResolution());
    }
    return new ArrayList<>(resolutions);
  }

  private List<String> getAudioLanguage(MasterPlaylist masterPlaylist) {
    Set<String> audioLanguage = new HashSet<>();
    masterPlaylist.getAlternateRenditions().forEach(si -> audioLanguage.add(si.getLanguage()));
    return new ArrayList<>(audioLanguage);
  }

  private Playlist downloadMasterPlaylist(URI uri)
      throws PlaylistDownloadException, PlaylistParsingException {
    return downloadPlaylist(uri,
        p -> p != null && !p.isMasterPlaylist() ? buildPlaylistName(MEDIA_TYPE_NAME)
            : buildPlaylistName(MASTER_TYPE_NAME));
  }

  private Playlist downloadPlaylist(URI uri, String type)
      throws PlaylistDownloadException, PlaylistParsingException {
    return downloadPlaylist(uri, p -> buildPlaylistName(type));
  }

  private Playlist downloadPlaylist(URI uri, Function<Playlist, String> namer)
      throws PlaylistDownloadException, PlaylistParsingException {
    return downloadPlaylist(uri, namer, Playlist::fromUriAndBody);
  }

  private class MediaPlayback extends VideoStreamingPlayback<MediaSegment> {

    private Playlist playlist;
    private Iterator<MediaSegment> mediaSegments;
    private InitializationSegment initializationSegment;
    private final boolean startFromLiveEdge;

    private MediaPlayback(Playlist playlist, String type, MediaSegment lastSegment,
        int playSeconds, boolean startFromLiveEdge) {
      super(type, lastSegment, playSeconds);
      this.playlist = playlist;
      this.startFromLiveEdge = startFromLiveEdge;
      if (playlist != null) {
        updateMediaSegments();
        if (playlist.hasByteRange()) {
          updateInitializationSegment();
        }
      }
    }

    private void updateInitializationSegment() {
      initializationSegment = playlist.getInitializationSegment();
    }

    private void updateMediaSegments() {
      if (lastSegment == null) {
        List<MediaSegment> segments = playlist.getMediaSegments();
        if (startFromLiveEdge && !playlist.hasEnd()) {
          int skipIndex = computeLiveEdgeSkipIndex(segments);
          mediaSegments = segments.subList(skipIndex, segments.size()).iterator();
        } else {
          mediaSegments = segments.iterator();
        }
      } else {
        mediaSegments = playlist.getMediaSegments().stream()
            .filter(s -> s.getSequenceNumber() > lastSegment.getSequenceNumber())
            .iterator();
      }
    }

    private int computeLiveEdgeSkipIndex(List<MediaSegment> segments) {
      long thresholdMs = playlist.getTargetDurationSeconds()
          * RFC_8216_LIVE_EDGE_DURATIONS * 1000;
      int size = segments.size();
      if (thresholdMs <= 0 || size == 0) {
        return Math.max(0, size - 1);
      }
      long accumulatedMs = 0;
      int count = 0;
      for (int i = size - 1; i >= 0; i--) {
        accumulatedMs += segments.get(i).getDurationMillis();
        count++;
        if (accumulatedMs >= thresholdMs) {
          break;
        }
      }
      return Math.max(0, size - count);
    }

    private void downloadNextSegment()
        throws InterruptedException, PlaylistDownloadException, PlaylistParsingException {
      if (!mediaSegments.hasNext() && !playlist.hasEnd()) {
        updatePlaylist();
      }
      if (!mediaSegments.hasNext()) {
        return;
      }
      MediaSegment segment = mediaSegments.next();
      downloadSegment(segment, type);
      lastSegment = segment;
      consumedSeconds += segment.getDurationSeconds();
    }

    private void downloadInitializationSegment() {
      if (initializationSegment != null) {
        downloadInitSegment(initializationSegment, type);
      }
    }

    private void updatePlaylist()
        throws InterruptedException, PlaylistDownloadException, PlaylistParsingException {

      timeMachine.awaitMillis(clampAwaitMillis(playlist.getReloadTimeMillisForDurationMultiplier(1,
          timeMachine.now())));
      Playlist updatedPlaylist = downloadPlaylist(playlist.getUri(), this.type);

      while (updatedPlaylist.equals(playlist) && !shouldYieldForSlice()) {
        long millis = updatedPlaylist
            .getReloadTimeMillisForDurationMultiplier(0.5, timeMachine.now());

        timeMachine.awaitMillis(clampAwaitMillis(millis));
        updatedPlaylist = downloadPlaylist(playlist.getUri(), this.type);
      }

      this.playlist = updatedPlaylist;
      updateMediaSegments();
    }

    private boolean hasEnded() {
      return playedRequestedTime() || (!mediaSegments.hasNext() && playlist.hasEnd());
    }

    private void downloadUntilTimeSecond(double untilTimeSecond) throws InterruptedException {
      if (playlist == null) {
        return;
      }

      try {
        while (consumedSeconds <= untilTimeSecond && !shouldYieldForSlice()) {
          downloadNextSegment();
        }
      } catch (PlaylistParsingException | PlaylistDownloadException e) {
        LOG.warn("Problem downloading {} segment", type, e);
      }
    }
  }

  private Playlist tryDownloadPlaylist(URI uri, Function<Playlist, String> namer) {
    if (uri == null) {
      return null;
    }
    try {
      HTTPSampleResult playlistResult = httpClient.downloadUri(uri);
      if (!isHLSPlaylistLink(uri.toString())) {
        String playlistName = namer.apply(null);
        sampleResultProcessor.accept(playlistName, playlistResult);
        return null;
      } else {
        return downloadPlaylist(uri, namer);
      }
    } catch (PlaylistDownloadException | PlaylistParsingException e) {
      LOG.warn("Problem downloading {}", namer.apply(null), e);
      return null;
    }
  }

}

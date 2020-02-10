package com.blazemeter.jmeter.videostreaming.hls;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.MediaSegment;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingPlayback;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import java.net.URI;
import java.util.Iterator;
import java.util.function.Function;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HlsSampler extends VideoStreamingSampler<Playlist, MediaSegment> {

  private static final Logger LOG = LoggerFactory.getLogger(HlsSampler.class);

  public HlsSampler(com.blazemeter.jmeter.hls.logic.HlsSampler baseSampler,
      VideoStreamingHttpClient httpClient, TimeMachine timeMachine,
      SampleResultProcessor sampleResultProcessor) {
    super(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  public void sample(URI masterUri, BandwidthSelector bandwidthSelector,
      ResolutionSelector resolutionSelector, String audioLanguage, String subtitleLanguage,
      int playSeconds)
      throws PlaylistDownloadException, PlaylistParsingException, InterruptedException {
    Playlist masterPlaylist = downloadMasterPlaylist(masterUri);

    Playlist mediaPlaylist;
    Playlist audioPlaylist = null;
    Playlist subtitlesPlaylist = null;

    if (masterPlaylist.isMasterPlaylist()) {

      MediaStream mediaStream = masterPlaylist
          .solveMediaStream(bandwidthSelector, resolutionSelector, audioLanguage, subtitleLanguage);
      if (mediaStream == null) {
        sampleResultProcessor.accept(buildPlaylistName(MEDIA_TYPE_NAME),
            buildNotMatchingMediaPlaylistResult());
        return;
      }

      mediaPlaylist = downloadPlaylist(mediaStream.getMediaPlaylistUri(), MEDIA_TYPE_NAME);
      audioPlaylist = tryDownloadPlaylist(mediaStream.getAudioUri(),
          p -> buildPlaylistName(AUDIO_TYPE_NAME));
      subtitlesPlaylist = tryDownloadPlaylist(mediaStream.getSubtitlesUri(),
          p -> p != null ? buildPlaylistName(SUBTITLES_TYPE_NAME) : SUBTITLES_TYPE_NAME);
    } else {
      mediaPlaylist = masterPlaylist;
    }

    MediaPlayback mediaPlayback = new MediaPlayback(mediaPlaylist, MEDIA_TYPE_NAME,
        lastVideoSegment, playSeconds);
    MediaPlayback audioPlayback = new MediaPlayback(audioPlaylist, AUDIO_TYPE_NAME,
        lastAudioSegment, playSeconds);
    MediaPlayback subtitlesPlayback = new MediaPlayback(subtitlesPlaylist, SUBTITLES_TYPE_NAME,
        lastSubtitleSegment, playSeconds);

    try {
      while (!mediaPlayback.hasEnded()) {
        mediaPlayback.downloadNextSegment();
        double playedSeconds = mediaPlayback.getPlayedTimeSeconds();
        if (playSeconds > 0 && playSeconds < playedSeconds) {
          playedSeconds = playSeconds;
        }
        audioPlayback.downloadUntilTimeSecond(playedSeconds);
        subtitlesPlayback.downloadUntilTimeSecond(playedSeconds);
      }
    } finally {
      lastVideoSegment = mediaPlayback.getLastSegment();
      lastAudioSegment = audioPlayback.getLastSegment();
      lastSubtitleSegment = subtitlesPlayback.getLastSegment();
    }
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

    private MediaPlayback(Playlist playlist, String type, MediaSegment lastSegment,
        int playSeconds) {
      super(type, lastSegment, playSeconds);
      this.playlist = playlist;
      if (playlist != null) {
        updateMediaSegments();
      }
    }

    private void updateMediaSegments() {
      if (lastSegment == null) {
        mediaSegments = playlist.getMediaSegments().iterator();
      } else {
        mediaSegments = playlist.getMediaSegments().stream()
            .filter(s -> s.getSequenceNumber() > lastSegment.getSequenceNumber())
            .iterator();
      }
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

    private void updatePlaylist()
        throws InterruptedException, PlaylistDownloadException, PlaylistParsingException {

      timeMachine.awaitMillis(playlist.getReloadTimeMillisForDurationMultiplier(1,
          timeMachine.now()));
      Playlist updatedPlaylist = downloadPlaylist(playlist.getUri(), this.type);

      while (updatedPlaylist.equals(playlist)) {
        long millis = updatedPlaylist
            .getReloadTimeMillisForDurationMultiplier(0.5, timeMachine.now());

        timeMachine.awaitMillis(millis);
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
        while (consumedSeconds <= untilTimeSecond) {
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
      if (!uri.toString().contains(".m3u8")) {
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

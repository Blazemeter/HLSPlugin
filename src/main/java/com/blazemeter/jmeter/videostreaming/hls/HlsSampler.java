package com.blazemeter.jmeter.videostreaming.hls;

import com.blazemeter.jmeter.videostreaming.core.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.SampleResultProcessor;
import com.blazemeter.jmeter.videostreaming.core.SamplerInterruptedException;
import com.blazemeter.jmeter.videostreaming.core.TimeMachine;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingHttpClient;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingPlayback;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamingSampler;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.Function;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HlsSampler extends VideoStreamingSampler {

  private static final Logger LOG = LoggerFactory.getLogger(HlsSampler.class);

  public HlsSampler(com.blazemeter.jmeter.hls.logic.HlsSampler baseSampler,
      VideoStreamingHttpClient httpClient, TimeMachine timeMachine,
      SampleResultProcessor sampleResultProcessor) {
    super(baseSampler, httpClient, timeMachine, sampleResultProcessor);
  }

  public SampleResult sample() {
    try {
      URI masterUri = URI.create(baseSampler.getMasterUrl());
      Playlist masterPlaylist = downloadMasterPlaylist(masterUri);

      Playlist mediaPlaylist;
      Playlist audioPlaylist = null;
      Playlist subtitlesPlaylist = null;

      if (masterPlaylist.isMasterPlaylist()) {

        MediaStream mediaStream = masterPlaylist
            .solveMediaStream(baseSampler.getBandwidthSelector(),
                baseSampler.getResolutionSelector(),
                baseSampler.getAudioLanguage(), baseSampler.getSubtitleLanguage());
        if (mediaStream == null) {
          sampleResultProcessor.process(buildPlaylistName(MEDIA_TYPE_NAME),
              buildNotMatchingMediaPlaylistResult());
          return null;
        }

        mediaPlaylist = downloadPlaylist(mediaStream.getMediaPlaylistUri(), MEDIA_TYPE_NAME);
        audioPlaylist = tryDownloadPlaylist(mediaStream.getAudioUri(),
            p -> buildPlaylistName(AUDIO_TYPE_NAME));
        subtitlesPlaylist = tryDownloadPlaylist(mediaStream.getSubtitlesUri(),
            p -> p != null ? buildPlaylistName(SUBTITLES_TYPE_NAME) : SUBTITLES_TYPE_NAME);
      } else {
        mediaPlaylist = masterPlaylist;
      }

      int playSeconds = baseSampler.getPlaySecondsOrWarn();

      MediaPlayback mediaPlayback = new MediaPlayback(mediaPlaylist, lastVideoSegmentNumber,
          playSeconds, MEDIA_TYPE_NAME);
      MediaPlayback audioPlayback = new MediaPlayback(audioPlaylist, lastAudioSegmentNumber,
          playSeconds, AUDIO_TYPE_NAME);
      MediaPlayback subtitlesPlayback = new MediaPlayback(subtitlesPlaylist,
          lastSubtitleSegmentNumber, playSeconds, SUBTITLES_TYPE_NAME);

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
    } catch (SamplerInterruptedException e) {
      LOG.debug("Sampler interrupted by JMeter", e);
    } catch (InterruptedException e) {
      LOG.warn("Sampler has been interrupted", e);
      Thread.currentThread().interrupt();
    } catch (PlaylistDownloadException | PlaylistParsingException e) {
      LOG.warn("Problem downloading playlist", e);
    }
    return null;
  }

  private Playlist downloadPlaylist(URI uri, String type)
      throws PlaylistDownloadException, PlaylistParsingException {
    return downloadPlaylist(uri, p -> buildPlaylistName(type));
  }

  private Playlist downloadPlaylist(URI uri, Function<Playlist, String> name)
      throws PlaylistParsingException, PlaylistDownloadException {
    Instant downloadTimestamp = timeMachine.now();
    HTTPSampleResult playlistResult = httpClient.downloadUri(uri);
    if (!playlistResult.isSuccessful()) {
      String playlistName = name.apply(null);
      sampleResultProcessor.process(playlistName, playlistResult);
      throw new PlaylistDownloadException(playlistName, uri);
    }

    // we update uri in case the request was redirected
    try {
      uri = playlistResult.getURL().toURI();
    } catch (URISyntaxException e) {
      LOG.warn("Problem updating uri from downloaded playlist {}. Continue with original uri {}",
          playlistResult.getURL(), uri, e);
    }

    if (!uri.toString().contains(".m3u8")) {
      sampleResultProcessor.process(name.apply(null), playlistResult);
      return null;
    }

    try {
      Playlist playlist = Playlist
          .fromUriAndBody(uri, playlistResult.getResponseDataAsString(), downloadTimestamp);
      sampleResultProcessor.process(name.apply(playlist), playlistResult);
      return playlist;
    } catch (PlaylistParsingException e) {
      sampleResultProcessor.process(name.apply(null), baseSampler.errorResult(e, playlistResult));
      throw e;
    }
  }

  private Playlist downloadMasterPlaylist(URI uri)
      throws PlaylistDownloadException, PlaylistParsingException {
    return downloadPlaylist(uri,
        p -> p != null && !p.isMasterPlaylist() ? buildPlaylistName(MEDIA_TYPE_NAME)
            : buildPlaylistName(MASTER_TYPE_NAME));
  }

  private class MediaPlayback extends VideoStreamingPlayback {

    private Playlist playlist;
    private Iterator<MediaSegment> mediaSegments;

    private MediaPlayback(Playlist playlist, long lastSegmentNumber, int playSeconds, String type) {
      super(playSeconds, lastSegmentNumber, type);
      this.playlist = playlist;
      if (playlist != null) {
        updateMediaSegments();
      }
    }

    private void updateMediaSegments() {
      this.mediaSegments = this.playlist.getMediaSegments().stream()
          .filter(s -> s.getSequenceNumber() > lastSegmentNumber).iterator();
    }

    private void downloadNextSegment()
        throws InterruptedException, PlaylistDownloadException, PlaylistParsingException {

      if (!mediaSegments.hasNext()) {
        updatePlaylist();
      }

      if (mediaSegments.hasNext()) {
        MediaSegment segment = mediaSegments.next();
        SampleResult result = httpClient.downloadUri(segment.getUri());
        result.setResponseHeaders(
            result.getResponseHeaders() + "X-MEDIA-SEGMENT-DURATION: " + segment
                .getDurationSeconds() + "\n");
        sampleResultProcessor.process(buildSegmentName(type), result);
        lastSegmentNumber = segment.getSequenceNumber();
        consumedSeconds += segment.getDurationSeconds();
      }
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

    private void downloadUntilTimeSecond(float untilTimeSecond) throws InterruptedException {
      if (playlist == null) {
        return;
      }

      try {
        while (consumedSeconds <= untilTimeSecond) {
          downloadNextSegment();
        }
      } catch (PlaylistParsingException | PlaylistDownloadException e) {
        LOG.warn("Problem downloading playlist {}", type, e);
      }
    }
  }

  private Playlist tryDownloadPlaylist(URI uri, Function<Playlist, String> namer) {
    try {
      if (uri != null) {
        return downloadPlaylist(uri, namer);
      }
    } catch (PlaylistDownloadException | PlaylistParsingException e) {
      LOG.warn("Problem downloading {}", namer.apply(null), e);
    }
    return null;
  }

}

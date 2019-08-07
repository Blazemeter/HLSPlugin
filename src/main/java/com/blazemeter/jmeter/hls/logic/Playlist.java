package com.blazemeter.jmeter.hls.logic;

import static com.comcast.viper.hlsparserj.PlaylistVersion.TWELVE;

import com.comcast.viper.hlsparserj.IPlaylist;
import com.comcast.viper.hlsparserj.MasterPlaylist;
import com.comcast.viper.hlsparserj.MediaPlaylist;
import com.comcast.viper.hlsparserj.PlaylistFactory;
import com.comcast.viper.hlsparserj.tags.master.StreamInf;
import com.comcast.viper.hlsparserj.tags.media.MediaSequence;

import com.comcast.viper.hlsparserj.tags.media.PlaylistType;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Playlist {

  private static final Logger LOG = LoggerFactory.getLogger(Playlist.class);
  private static IPlaylist playlist;

  private final URI uri;
  private final String body; //This field is only used for comparing objects
  private final Instant downloadTimestamp;

  private Playlist(URI uri, String body, Instant downloadTimestamp) {
    this.uri = uri;
    this.downloadTimestamp = downloadTimestamp;
    this.body = body;
  }

  public static Playlist fromUriAndBody(URI uri, String body, Instant timestamp)
      throws PlaylistParsingException {

    try {
      playlist = PlaylistFactory.parsePlaylist(TWELVE, body);
    } catch (Exception e) {
      throw new PlaylistParsingException(e, uri.toString());
    }
    return new Playlist(uri, body, timestamp);
  }

  public URI getUri() {
    return uri;
  }

  public URI solveMediaPlaylistUri(ResolutionSelector resolutionSelector,
                            BandwidthSelector bandwidthSelector) {
    Long lastMatchedBandwidth = null;
    String lastMatchedResolution = null;
    String mediaPlaylistUri = null;

    MasterPlaylist masterplaylist = (MasterPlaylist) playlist;
    for (StreamInf variant : masterplaylist.getVariantStreams()) {

      long streamBandwidth = variant.getBandwidth();
      String streamResolution = variant.getResolution();

      if (bandwidthSelector.matches(streamBandwidth, lastMatchedBandwidth)) {

        lastMatchedBandwidth = streamBandwidth;
        LOG.info("resolution match: {}, {}, {}, {}", streamResolution, lastMatchedResolution,
            resolutionSelector.getName(), resolutionSelector.getCustomResolution());

        if (resolutionSelector.matches(streamResolution, lastMatchedResolution)) {
          lastMatchedResolution = streamResolution;
          mediaPlaylistUri = variant.getURI();
        }
      }
    }

    return mediaPlaylistUri != null ? buildAbsoluteUri(mediaPlaylistUri) : uri;
  }

  private URI buildAbsoluteUri(String str) {
    URI ret = URI.create(str);
    if (ret.getScheme() != null) {
      return ret;
    } else if (ret.getPath().startsWith("/")) {
      return URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + ret.toString());
    } else {
      String basePath = uri.getPath();
      basePath = basePath.substring(0, basePath.lastIndexOf('/') + 1);
      return URI.create(
          uri.getScheme() + "://" + uri.getRawAuthority() + basePath + ret.toString());
    }
  }

  public List<MediaSegment> getMediaSegments() {
    MediaPlaylist mediaPlaylist = (MediaPlaylist) playlist;
    MediaSequence mediaSequence = mediaPlaylist.getMediaSequence();
    int sequence = (mediaSequence != null ? mediaSequence.getSequenceNumber() : 0);

    AtomicInteger sequenceNumber = new AtomicInteger(sequence);

    return mediaPlaylist.getSegments().stream()
        .map(s -> new MediaSegment(sequenceNumber.getAndIncrement(),
            buildAbsoluteUri(s.getURI()), s.getDuration()))
        .collect(Collectors.toList());

  }

  public boolean hasEnd() {
    return "VOD".equals(getPlaylistType()) || hasEndMarker();
  }

  private String getPlaylistType() {
    //By definition, the type doesn't appear in the master playlist
    if (!playlist.isMasterPlaylist()) {
      PlaylistType playlistType = ((MediaPlaylist) playlist).getPlaylistType();
      if (playlistType != null) {
        return playlistType.getType();
      }
    }

    return null;
  }

  private boolean hasEndMarker() {
    return playlist.getTags().stream()
        .anyMatch(t -> t.getTagName().contains("EXT-X-ENDLIST"));
  }

  public long getReloadTimeMillisForDurationMultiplier(double targetDurationMultiplier,
                                                Instant now) {
    MediaPlaylist media = (MediaPlaylist) playlist;
    long targetDuration = media.getTargetDuration().getDuration();
    long timeDiffMillis = downloadTimestamp.until(now, ChronoUnit.MILLIS);
    long reloadPeriodMillis = TimeUnit.SECONDS.toMillis(Math
        .round(targetDuration * targetDurationMultiplier));
    return Math.max(0, reloadPeriodMillis - timeDiffMillis);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Playlist playlist = (Playlist) o;
    return Objects.equals(uri, playlist.uri) &&
        Objects.equals(body, playlist.body) &&
        Objects.equals(downloadTimestamp, playlist.downloadTimestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uri, body, downloadTimestamp);
  }

  public boolean isMasterPlaylist() {
    return playlist.isMasterPlaylist();
  }
}

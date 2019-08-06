package com.blazemeter.jmeter.hls.logic;

import static com.comcast.viper.hlsparserj.PlaylistVersion.TWELVE;

import com.comcast.viper.hlsparserj.IPlaylist;
import com.comcast.viper.hlsparserj.MasterPlaylist;
import com.comcast.viper.hlsparserj.MediaPlaylist;
import com.comcast.viper.hlsparserj.PlaylistFactory;
import com.comcast.viper.hlsparserj.tags.UnparsedTag;
import com.comcast.viper.hlsparserj.tags.master.StreamInf;
import com.comcast.viper.hlsparserj.tags.media.ExtInf;
import com.comcast.viper.hlsparserj.tags.media.MediaSequence;

import java.net.URI;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Playlist {

  private static final Logger LOG = LoggerFactory.getLogger(Playlist.class);
  private static IPlaylist playlist;

  private final URI uri;
  private final Instant downloadTimestamp;

  private Playlist(URI uri, Instant downloadTimestamp) {
    this.uri = uri;
    this.downloadTimestamp = downloadTimestamp;
  }

  public static Playlist fromUriAndBody(URI uri, String body, Instant timestamp) {

    try {
      playlist = PlaylistFactory.parsePlaylist(TWELVE, body);
    } catch (Exception e) {
      return null;
    }
    return new Playlist(uri, timestamp);
  }

  URI getUri() {
    return uri;
  }

  URI solveMediaPlaylistUri(ResolutionSelector resolutionSelector,
                            BandwidthSelector bandwidthSelector) {
    Long lastMatchedBandwidth = null;
    String lastMatchedResolution = null;
    String mediaPlaylistUri = null;

    if (!playlist.isMasterPlaylist()) {
      return null;
    }

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
    int sequenceNumber = getPlaylistMediaSequence();
    MediaPlaylist mediaPlaylist = (MediaPlaylist) playlist;
    List<ExtInf> list = mediaPlaylist.getSegments();
    final List<MediaSegment> segments = new ArrayList<>();

    for (ExtInf segment : list) {
      segments.add(new MediaSegment(sequenceNumber++, buildAbsoluteUri(segment.getURI()),
          segment.getDuration()));
    }

    return segments;
  }

  private int getPlaylistMediaSequence() {
    MediaSequence mediaSequence = ((MediaPlaylist) playlist).getMediaSequence();
    return mediaSequence.getSequenceNumber();
  }

  public boolean hasEnd() {
    return "VOD".equals(getPlaylistType()) || hasEndMarker();
  }

  private String getPlaylistType() {
    //By definition, the type doesn't appear in the master playlist
    if (!playlist.isMasterPlaylist()) {
      return ((MediaPlaylist) playlist).getPlaylistType().getType();
    }

    return null;
  }

  private boolean hasEndMarker() {
    List<UnparsedTag> tags = playlist.getTags();
    for  (UnparsedTag tag : tags) {
      if (tag.getTagName().contains("EXT-X-ENDLIST")) {
        return true;
      }
    }

    return false;
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
        Objects.equals(downloadTimestamp, playlist.downloadTimestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uri, downloadTimestamp);
  }

  public boolean isMasterPlaylist() {
    return playlist.isMasterPlaylist();
  }
}

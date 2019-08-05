package com.blazemeter.jmeter.hls.logic;

import static com.comcast.viper.hlsparserj.PlaylistVersion.TWELVE;

import com.comcast.viper.hlsparserj.IPlaylist;
import com.comcast.viper.hlsparserj.MasterPlaylist;
import com.comcast.viper.hlsparserj.MediaPlaylist;
import com.comcast.viper.hlsparserj.PlaylistFactory;
import com.comcast.viper.hlsparserj.tags.master.StreamInf;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Playlist {

  private static final Logger LOG = LoggerFactory.getLogger(Playlist.class);
  private static final Pattern STREAM_PATTERN = Pattern
      .compile("(#EXT-X-STREAM-INF.*)\\r?\\n(.*)");
  private static final Pattern BANDWIDTH_PATTERN = Pattern.compile("[:|,]BANDWIDTH=(\\d+)");
  private static final Pattern RESOLUTION_PATTERN = Pattern.compile("[:|,]RESOLUTION=(\\d+x\\d+)");
  private static final Pattern PLAYLIST_TYPE_PATTERN = Pattern
      .compile("#EXT-X-PLAYLIST-TYPE:(\\w+)");
  private static final Pattern MEDIA_SEGMENT_PATTERN = Pattern
      .compile("#EXTINF:(\\d+\\.?\\d*).*\\r?\\n((?:#.*:.*\\r?\\n)*)(.*)");
  private static final Pattern MEDIA_SEQUENCE_PATTERN = Pattern
      .compile("#EXT-X-MEDIA-SEQUENCE:(\\d+)");

  private final URI uri;
  private final String body;
  private final long targetDuration;
  private final Instant downloadTimestamp;

  private Playlist(URI uri, String body, long targetDuration, Instant downloadTimestamp) {
    this.uri = uri;
    this.body = body;
    this.targetDuration = targetDuration;
    this.downloadTimestamp = downloadTimestamp;
  }

  static Playlist fromUriAndBody(URI uri, String body, Instant timestamp) {
    Matcher m = Pattern.compile("#EXT-X-TARGETDURATION:(\\d+)").matcher(body);
    long targetDuration = m.find() ? Long.parseLong(m.group(1)) : 0;
    return new Playlist(uri, body, targetDuration, timestamp);
  }

  static Playlist fromUriAndBodyHlsParserj(URI uri, String body, Instant timestamp) {
    IPlaylist playlist;

    try {
      InputStream inputStream = new ByteArrayInputStream(body.getBytes(Charset.forName("UTF-8")));
      playlist = PlaylistFactory.parsePlaylist(TWELVE, inputStream);

    } catch (IOException e) {
      return null;
    }

    if (playlist.isMasterPlaylist()) {
      return new Playlist(uri, body, 0, timestamp);
    } else {
      MediaPlaylist media = (MediaPlaylist) playlist;
      return new Playlist(uri, body, media.getTargetDuration().getDuration(), timestamp);
    }
  }

  URI getUri() {
    return uri;
  }

  URI solveMediaPlaylistUri(ResolutionSelector resolutionSelector,
                            BandwidthSelector bandwidthSelector) {
    Long lastMatchedBandwidth = null;
    String lastMatchedResolution = null;
    String mediaPlaylistUri = null;

    IPlaylist genericPlaylist = PlaylistFactory.parsePlaylist(TWELVE, body);

    if (!genericPlaylist.isMasterPlaylist()) {
      return null;
    }

    MasterPlaylist playlist = (MasterPlaylist) genericPlaylist;
    for (StreamInf variant : playlist.getVariantStreams()) {

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

    if (mediaPlaylistUri == null || lastMatchedBandwidth == null || lastMatchedResolution == null) {
      return null;
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

  List<MediaSegment> getMediaSegments() {
    int sequenceNumber = getPlaylistMediaSequence(body);
    final List<MediaSegment> segments = new ArrayList<>();
    Matcher m = MEDIA_SEGMENT_PATTERN.matcher(body);
    while (m.find()) {
      URI segmentUri = buildAbsoluteUri(m.group(3));
      float durationSecs = Float.parseFloat(m.group(1));
      segments.add(new MediaSegment(sequenceNumber++, segmentUri, durationSecs));
    }
    return segments;
  }

  private int getPlaylistMediaSequence(String tags) {
    Matcher matcher = MEDIA_SEQUENCE_PATTERN.matcher(tags);
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
  }

  boolean hasEnd() {
    return "VOD".equals(getPlaylistType(body)) || hasEndMarker(body);
  }

  private String getPlaylistType(String playlist) {
    Matcher m = PLAYLIST_TYPE_PATTERN.matcher(playlist);
    return m.find() ? m.group(1).toUpperCase() : null;
  }

  private boolean hasEndMarker(String playlist) {
    return playlist.contains("\n#EXT-X-ENDLIST");
  }

  long getReloadTimeMillisForDurationMultiplier(double targetDurationMultiplier,
                                                Instant now) {
    long timeDiffMillis = downloadTimestamp.until(now, ChronoUnit.MILLIS);
    long reloadPeriodMillis = TimeUnit.SECONDS.toMillis(Math
        .round(this.targetDuration * targetDurationMultiplier));
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
    return Objects.equals(uri, playlist.uri)
        && Objects.equals(body, playlist.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uri, body);
  }

  boolean isMasterPlaylist() {
    return body.contains("#EXT-X-STREAM-INF");
  }
}

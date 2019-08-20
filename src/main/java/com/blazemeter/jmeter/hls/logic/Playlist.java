package com.blazemeter.jmeter.hls.logic;

import static com.comcast.viper.hlsparserj.PlaylistVersion.TWELVE;

import com.comcast.viper.hlsparserj.IPlaylist;
import com.comcast.viper.hlsparserj.MasterPlaylist;
import com.comcast.viper.hlsparserj.MediaPlaylist;
import com.comcast.viper.hlsparserj.PlaylistFactory;
import com.comcast.viper.hlsparserj.tags.master.Media;
import com.comcast.viper.hlsparserj.tags.master.StreamInf;
import com.comcast.viper.hlsparserj.tags.media.MediaSequence;
import com.comcast.viper.hlsparserj.tags.media.PlaylistType;
import com.comcast.viper.hlsparserj.tags.media.TargetDuration;
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
  private IPlaylist playlist;

  private final URI uri;
  private final String body; //This field is only used for comparing objects
  private final Instant downloadTimestamp;

  private Playlist(URI uri, String body, Instant downloadTimestamp, IPlaylist playlist) {
    this.uri = uri;
    this.downloadTimestamp = downloadTimestamp;
    this.body = body;
    this.playlist = playlist;
  }

  public static Playlist fromUriAndBody(URI uri, String body, Instant timestamp)
      throws PlaylistParsingException {
    try {
      return new Playlist(uri, body, timestamp,
          PlaylistFactory.parsePlaylist(TWELVE, body.replace("\r", "")));
    } catch (Exception e) {
      throw new PlaylistParsingException(e, uri);
    }
  }

  public URI getUri() {
    return uri;
  }

  private StreamInf solveStream(ResolutionSelector resolutionSelector,
      BandwidthSelector bandwidthSelector) {

    Long lastMatchedBandwidth = null;
    String lastMatchedResolution = null;

    StreamInf lastMatchStreamInf = null;

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
          LOG.info("Matched Stream: Audio {} | Subtitle {}", variant.getAudio(),
              variant.getSubtitle());
          lastMatchStreamInf = variant;
        }
      }
    }

    return lastMatchStreamInf;
  }

  public MediaStream solveMediaStream(ResolutionSelector resolutionSelector,
      BandwidthSelector bandwidthSelector, String audioLanguageSelector,
      String subtitleLanguageSelector) {

    StreamInf mediaStream = solveStream(resolutionSelector, bandwidthSelector);

    if (mediaStream == null) {
      return null;
    }

    URI audioPlayListUri = getRenditionUri("AUDIO", mediaStream.getAudio(),
        audioLanguageSelector);

    /*
    The lists of renditions could have either SUBTITLE or SUBTITLES tags but, the library
    is expecting it to be "SUBTITLE". If we do mediaStream.getSubtitle(), could return
    null. Because of that, we check with mediaStream.getTag().getAttributes() instead.
    */
    String type = "SUBTITLES";
    String subtitlesGroupId = mediaStream.getTag().getAttributes().get(type);
    if (subtitlesGroupId == null) {
      type = "SUBTITLE";
      subtitlesGroupId = mediaStream.getTag().getAttributes().get(type);
    }

    URI subtitlePlayListUri = getRenditionUri(type, subtitlesGroupId,
        subtitleLanguageSelector);

    return new MediaStream(buildAbsoluteUri(mediaStream.getURI()),
        (audioPlayListUri != null ? buildAbsoluteUri(audioPlayListUri.toString()) : null),
        (subtitlePlayListUri != null ? buildAbsoluteUri(subtitlePlayListUri.toString()) : null));
  }

  private URI getRenditionUri(String type, String groupId, String selector) {

    MasterPlaylist masterPlaylist = (MasterPlaylist) this.playlist;

    Media lastDefaultRendition = null;
    Media matchedRendition = null;

    for (Media rendition : masterPlaylist.getAlternateRenditions()) {
      String renditionType = rendition.getType();
      String renditionGroupId = rendition.getGroupId();

      if (renditionType.equals(type) && renditionGroupId.equals(groupId)) {

        if (rendition.getDefault()) {
          lastDefaultRendition = rendition;
        }

        if (rendition.getName().toLowerCase().trim().equals(selector.toLowerCase())
            || rendition.getLanguage().toLowerCase().trim().equals(selector.toLowerCase())) {
          matchedRendition = rendition;
        }
      }
    }

    if (matchedRendition == null) {
      LOG.warn("No {} was found for the selected param provided '{}', using default if exists.",
          type.toLowerCase(), selector.toLowerCase());

      if (lastDefaultRendition != null) {
        return URI.create(lastDefaultRendition.getURI());
      } else {
        return null;
      }

    }

    return URI.create(matchedRendition.getURI());
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
    TargetDuration mediaTargetDuration = media.getTargetDuration();
    long targetDuration = (mediaTargetDuration != null
        ? media.getTargetDuration().getDuration() : 0);
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
        Objects.equals(body, playlist.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uri, body);
  }

  public boolean isMasterPlaylist() {
    return playlist.isMasterPlaylist();
  }
}

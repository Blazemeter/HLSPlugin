package com.blazemeter.jmeter.videostreaming.hls;

import static com.comcast.viper.hlsparserj.PlaylistVersion.TWELVE;

import com.blazemeter.jmeter.hls.logic.BandwidthSelector;
import com.blazemeter.jmeter.hls.logic.ResolutionSelector;
import com.blazemeter.jmeter.videostreaming.core.Manifest;
import com.blazemeter.jmeter.videostreaming.core.MediaSegment;
import com.blazemeter.jmeter.videostreaming.core.VideoStreamSelector;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import com.comcast.viper.hlsparserj.AbstractPlaylist;
import com.comcast.viper.hlsparserj.IPlaylist;
import com.comcast.viper.hlsparserj.MasterPlaylist;
import com.comcast.viper.hlsparserj.MediaPlaylist;
import com.comcast.viper.hlsparserj.PlaylistFactory;
import com.comcast.viper.hlsparserj.tags.UnparsedTag;
import com.comcast.viper.hlsparserj.tags.master.Media;
import com.comcast.viper.hlsparserj.tags.master.StreamInf;
import com.comcast.viper.hlsparserj.tags.media.ByteRange;
import com.comcast.viper.hlsparserj.tags.media.ExtMap;
import com.comcast.viper.hlsparserj.tags.media.MediaSequence;
import com.comcast.viper.hlsparserj.tags.media.PlaylistType;
import com.comcast.viper.hlsparserj.tags.media.TargetDuration;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Playlist extends Manifest {

  private static final Logger LOG = LoggerFactory.getLogger(Playlist.class);
  private final String body; //This field is only used for comparing objects
  private final Instant downloadTimestamp;
  private IPlaylist playlist;

  private Playlist(URI uri, String body, Instant downloadTimestamp, IPlaylist playlist) {
    super(uri);

    this.downloadTimestamp = downloadTimestamp;
    this.body = body;
    this.playlist = playlist;
  }

  public static Playlist fromUriAndBody(URI uri, String body, Instant timestamp)
      throws PlaylistParsingException {
    try {
      AbstractPlaylist p = PlaylistFactory.parsePlaylist(TWELVE, body.replace("\r", ""));
      if (p.getTags().isEmpty()) {
        throw new PlaylistParsingException(uri, "No playlist tags found");
      }
      return new Playlist(uri, body, timestamp, p);
    } catch (Exception e) {
      throw new PlaylistParsingException(uri, e);
    }
  }

  public URI getUri() {
    return uri;
  }

  public IPlaylist getPlaylist() {
    return playlist;
  }

  public MediaStream solveMediaStream(BandwidthSelector bandwidthSelector,
                                      ResolutionSelector resolutionSelector,
                                      String audioLanguageSelector,
                                      String subtitleLanguageSelector) {

    StreamInf mediaStream = solveStream(bandwidthSelector, resolutionSelector);
    if (mediaStream == null) {
      return null;
    }
    String audioPlayListUri = getRenditionUri("AUDIO", mediaStream.getAudio(),
        audioLanguageSelector);
    String subtitlePlayListUri = getRenditionUri("SUBTITLES", mediaStream.getSubtitle(),
        subtitleLanguageSelector);
    return new MediaStream(uri.resolve(mediaStream.getURI()),
        (audioPlayListUri != null ? uri.resolve(audioPlayListUri) : null),
        (subtitlePlayListUri != null ? uri.resolve(subtitlePlayListUri) : null));
  }

  private StreamInf solveStream(BandwidthSelector bandwidthSelector,
                                ResolutionSelector resolutionSelector) {
    return new VideoStreamSelector<>(bandwidthSelector, v -> (long) v.getBandwidth(),
        resolutionSelector, StreamInf::getResolution)
        .findMatchingVariant(((MasterPlaylist) playlist).getVariantStreams());
  }

  private String getRenditionUri(String type, String groupId, String selector) {
    if (groupId == null) {
      return null;
    }
    MasterPlaylist masterPlaylist = (MasterPlaylist) this.playlist;
    Media defaultRendition = null;

    for (Media rendition : masterPlaylist.getAlternateRenditions()) {
      String renditionType = rendition.getType();
      String renditionGroupId = rendition.getGroupId();
      if (renditionType.equals(type) && renditionGroupId.equals(groupId)) {
        if (rendition.getDefault()) {
          defaultRendition = rendition;
        }
        if (rendition.getName().toLowerCase().trim().equals(selector.toLowerCase())
            || (rendition.getLanguage() != null
            && rendition.getLanguage().toLowerCase().trim().equals(selector.toLowerCase()))) {
          return rendition.getURI();
        }
      }
    }

    if (!selector.isEmpty()) {
      LOG.warn("No {} was found for the selected param provided '{}', using default if exists.",
          type, selector);
    }
    return (defaultRendition != null ? defaultRendition.getURI() : null);
  }

  public List<MediaSegment> getMediaSegments() {
    MediaPlaylist mediaPlaylist = (MediaPlaylist) playlist;
    MediaSequence mediaSequence = mediaPlaylist.getMediaSequence();
    int sequence = (mediaSequence != null ? mediaSequence.getSequenceNumber() : 0);

    AtomicInteger sequenceNumber = new AtomicInteger(sequence);

    if (!hasByteRange()) {
      return mediaPlaylist.getSegments().stream()
          .map(s -> new MediaSegment(sequenceNumber.getAndIncrement(),
              uri.resolve(s.getURI()), Duration.ofMillis((long) s.getDuration() * 1000)))
          .collect(Collectors.toList());
    } else {
      List<MediaSegment> mediaSegments = mediaPlaylist.getSegments().stream()
          .map(s -> new MediaSegment(sequenceNumber.getAndIncrement(),
              Duration.ofMillis((long) s.getDuration() * 1000))).collect(Collectors.toList());
      for (int i = 0; i < mediaSegments.size(); i++) {
        MediaSegment segment = mediaSegments.get(i);
        ByteRange byteRange = mediaPlaylist.getByteRanges().get(i);
        segment.setByteRangeInfo(uri.resolve(byteRange.getURI()), byteRange.getLength(),
            byteRange.getOffset());

      }

      return mediaSegments;
    }
  }

  public List<String> getSubtitles() {
    Set<String> subtitles = new HashSet<>();
    for (UnparsedTag tag : playlist.getTags()) {
      Map<String, String> att = tag.getAttributes();
      if (!att.isEmpty() && att.get("TYPE") != null && att.get("TYPE").equals("SUBTITLES")) {
        subtitles.add(att.get("LANGUAGE"));
      }
    }

    return new ArrayList<>(subtitles);
  }

  public boolean hasByteRange() {
    return !((MediaPlaylist) playlist).getByteRanges().isEmpty();
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
    long reloadPeriodMillis = Math.round(targetDuration * 1000 * targetDurationMultiplier);
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

  public InitializationSegment getInitializationSegment() {
    MediaPlaylist mediaPlaylist = (MediaPlaylist) playlist;
    ExtMap extMap = mediaPlaylist.getInitializationSegment();
    return new
        InitializationSegment(uri.resolve(extMap.getURI()), extMap.getLength(), extMap.getOffset());
  }

  @Override
  protected String getManifestType() {
    if (playlist == null) {
      return null;
    }

    for (UnparsedTag tag : playlist.getTags()) {
      String tagname = tag.getTagName();
      if ("EXT-X-PLAYLIST-TYPE".equals(tagname)) {
        return tag.getRawTag().split(":", 2)[1];
      }
    }
    return null;
  }
}

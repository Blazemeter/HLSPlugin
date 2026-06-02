package com.blazemeter.jmeter.videostreaming.hls;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.blazemeter.jmeter.videostreaming.core.MediaSegment;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;
import com.comcast.viper.hlsparserj.MediaPlaylist;
import com.comcast.viper.hlsparserj.tags.media.ExtInf;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class PlaylistTest {

  private static final URI MEDIA_PLAYLIST_URI = URI.create("http://test/media/variant.m3u8");

  @Test
  public void shouldResolveSegmentUrisWhenPdtIsBetweenExtInfAndUri() throws Exception {
    Playlist playlist = parseResource("mediaPlaylistPdtBetweenExtInfAndUri.m3u8");
    List<ExtInf> parsedSegments = ((MediaPlaylist) playlist.getPlaylist()).getSegments();

    assertTrue(Playlist.requiresSegmentUriFallback(parsedSegments));

    List<MediaSegment> segments = playlist.getMediaSegments();

    assertEquals(3, segments.size());
    assertEquals(MEDIA_PLAYLIST_URI.resolve("segment_001.ts"), segments.get(0).getUri());
    assertEquals(MEDIA_PLAYLIST_URI.resolve("segment_002.ts"), segments.get(1).getUri());
    assertEquals(MEDIA_PLAYLIST_URI.resolve("segment_003.ts"), segments.get(2).getUri());
    assertEquals(17456L, segments.get(0).getSequenceNumber());
    assertEquals(17457L, segments.get(1).getSequenceNumber());
    assertEquals(17458L, segments.get(2).getSequenceNumber());
  }

  @Test
  public void shouldKeepParserSegmentUrisWhenPdtIsBeforeExtInf() throws Exception {
    Playlist playlist = parseResource("mediaPlaylistPdtBeforeExtInf.m3u8");
    List<ExtInf> parsedSegments = ((MediaPlaylist) playlist.getPlaylist()).getSegments();

    assertFalse(Playlist.requiresSegmentUriFallback(parsedSegments));

    List<MediaSegment> segments = playlist.getMediaSegments();

    assertEquals(2, segments.size());
    assertEquals(MEDIA_PLAYLIST_URI.resolve("segment_001.ts"), segments.get(0).getUri());
    assertEquals(MEDIA_PLAYLIST_URI.resolve("segment_002.ts"), segments.get(1).getUri());
  }

  @Test
  public void shouldUseParserSegmentUrisWithoutFallbackForStandardPlaylist() throws Exception {
    Playlist playlist = parseResource("simpleMediaPlaylist.m3u8");
    List<ExtInf> parsedSegments = ((MediaPlaylist) playlist.getPlaylist()).getSegments();

    assertFalse(Playlist.requiresSegmentUriFallback(parsedSegments));

    List<MediaSegment> segments = playlist.getMediaSegments();

    assertEquals(3, segments.size());
    assertEquals(MEDIA_PLAYLIST_URI.resolve("/media/001.ts"), segments.get(0).getUri());
    assertEquals(MEDIA_PLAYLIST_URI.resolve("/media/002.ts"), segments.get(1).getUri());
    assertEquals(MEDIA_PLAYLIST_URI.resolve("/media/003.ts"), segments.get(2).getUri());
  }

  private static Playlist parseResource(String resourceName)
      throws IOException, PlaylistParsingException {
    String path = "com/blazemeter/jmeter/videostreaming/hls/" + resourceName;
    try (InputStream in = PlaylistTest.class.getClassLoader().getResourceAsStream(path)) {
      String body = IOUtils.toString(in, StandardCharsets.UTF_8);
      return Playlist.fromUriAndBody(MEDIA_PLAYLIST_URI, body, Instant.now());
    }
  }

}

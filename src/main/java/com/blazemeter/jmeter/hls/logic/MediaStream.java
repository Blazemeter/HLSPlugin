package com.blazemeter.jmeter.hls.logic;

import java.net.URI;

public class MediaStream {

  private final URI mediaPlaylistUri;
  private final URI audioUri;
  private final URI subtitleUri;

  public MediaStream(URI mediaPlaylistUri, URI audioUri, URI subtitleUri) {
    this.mediaPlaylistUri = mediaPlaylistUri;
    this.audioUri = audioUri;
    this.subtitleUri = subtitleUri;
  }

  public URI getMediaPlaylistUri() {
    return mediaPlaylistUri;
  }

  public URI getAudioUri() {
    return audioUri;
  }

  public URI getSubtitleUri() {
    return subtitleUri;
  }
}

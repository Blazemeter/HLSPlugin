package com.blazemeter.jmeter.videostreaming.hls;

import java.net.URI;

public class MediaStream {

  private final URI mediaPlaylistUri;
  private final URI audioUri;
  private final URI subtitlesUri;

  public MediaStream(URI mediaPlaylistUri, URI audioUri, URI subtitleUri) {
    this.mediaPlaylistUri = mediaPlaylistUri;
    this.audioUri = audioUri;
    this.subtitlesUri = subtitleUri;
  }

  public URI getMediaPlaylistUri() {
    return mediaPlaylistUri;
  }

  public URI getAudioUri() {
    return audioUri;
  }

  public URI getSubtitlesUri() {
    return subtitlesUri;
  }

}

package com.blazemeter.jmeter.hls.logic;

import java.net.URI;

public class MediaStreamInf {

  private URI mediaPlaylistUri;
  private URI audioUri;
  private URI subtitleUri;

  public MediaStreamInf(URI mediaPlaylistUri, URI audioUri, URI subtitleUri) {
    this.mediaPlaylistUri = mediaPlaylistUri;
    this.audioUri = audioUri;
    this.subtitleUri = subtitleUri;
  }

  public URI getMediaPlaylistUri() {
    return mediaPlaylistUri;
  }

  public void setMediaPlaylistUri(URI mediaPlaylistUri) {
    this.mediaPlaylistUri = mediaPlaylistUri;
  }

  public URI getAudioUri() {
    return audioUri;
  }

  public void setAudioUri(URI audioUri) {
    this.audioUri = audioUri;
  }

  public URI getSubtitleUri() {
    return subtitleUri;
  }

  public void setSubtitleUri(URI subtitleUri) {
    this.subtitleUri = subtitleUri;
  }
}

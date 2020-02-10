package com.blazemeter.jmeter.videostreaming.core.exception;

import java.net.URI;

public class PlaylistDownloadException extends Throwable {

  public PlaylistDownloadException(String playlistName, URI uri) {
    super("Problem downloading " + playlistName + " " + uri);
  }

}

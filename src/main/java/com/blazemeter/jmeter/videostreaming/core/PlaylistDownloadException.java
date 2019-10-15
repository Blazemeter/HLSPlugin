package com.blazemeter.jmeter.videostreaming.core;

import java.net.URI;

public class PlaylistDownloadException extends Throwable {

  public PlaylistDownloadException(String playlistName, URI uri) {
    super("Problem downloading " + playlistName + " " + uri);
  }

}

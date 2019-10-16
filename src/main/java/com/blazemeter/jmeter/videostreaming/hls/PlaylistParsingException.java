package com.blazemeter.jmeter.videostreaming.hls;

import java.net.URI;

public class PlaylistParsingException extends Exception  {

  public PlaylistParsingException(Throwable cause, URI uri) {
    super(buildErrorMessage(uri), cause);
  }

  private static String buildErrorMessage(URI uri) {
    return "Error parsing the play list " + uri;
  }

}

package com.blazemeter.jmeter.hls.logic;

import java.net.URI;

public class PlaylistParsingException extends Exception  {

  public PlaylistParsingException(Throwable cause, URI uri) {
    super(buildErrorMessage(uri), cause);
  }

  public PlaylistParsingException(URI uri) {
    super(buildErrorMessage(uri));
  }

  private static String buildErrorMessage(URI uri) {
    return "Error parsing the play list " + uri;
  }

}

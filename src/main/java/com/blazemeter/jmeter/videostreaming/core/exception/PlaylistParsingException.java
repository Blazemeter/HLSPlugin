package com.blazemeter.jmeter.videostreaming.core.exception;

import java.net.URI;

public class PlaylistParsingException extends Exception  {

  public PlaylistParsingException(URI uri, Throwable cause) {
    super("Error parsing contents from " + uri, cause);
  }

  public PlaylistParsingException(URI uri, String message) {
    super("Error parsing contents from " + uri + ": " + message);
  }

}

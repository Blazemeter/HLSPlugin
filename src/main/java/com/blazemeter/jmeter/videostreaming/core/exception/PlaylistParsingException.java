package com.blazemeter.jmeter.videostreaming.core.exception;

import java.net.URI;

public class PlaylistParsingException extends Exception  {

  public PlaylistParsingException(Throwable cause, URI uri) {
    super("Error parsing contents from " + uri, cause);
  }

}

package com.blazemeter.jmeter.hls.logic;

import java.net.URI;

public class PlaylistParsingException extends Exception  {

  private URI uri;

  public PlaylistParsingException(URI uri) {
    this.uri = uri;
  }

  public String toString() {
    return "PlaylistParsingException: The body for the uri " + uri + " couldn't be parsed.";
  }
}

package com.blazemeter.jmeter.hls.logic;

public class PlaylistParsingException extends Exception  {

  public PlaylistParsingException(Throwable cause, String uri) {
    super("Error parsing the play list: " + uri, cause);
  }
}

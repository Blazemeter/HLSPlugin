package com.blazemeter.jmeter.videostreaming.core;

import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistDownloadException;
import com.blazemeter.jmeter.videostreaming.core.exception.PlaylistParsingException;

public interface VariantsProvider {

  Variants getVariantsFromURL(String url)
      throws IllegalArgumentException, PlaylistParsingException, PlaylistDownloadException;

}

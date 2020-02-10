package com.blazemeter.jmeter.videostreaming.dash;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlTemplateSolver {

  public static final char ESCAPING_CHAR = '$';
  private static final String FORMAT_TAG_PATTERN = "(%0\\dd)?";
  private static final Pattern NUMBER_IDENTIFIER_PATTERN = Pattern
      .compile("^Number" + FORMAT_TAG_PATTERN + "$");
  private static final Pattern BANDWIDTH_IDENTIFIER_PATTERN = Pattern
      .compile("^Bandwidth" + FORMAT_TAG_PATTERN + "$");
  private static final Pattern TIME_IDENTIFIER_PATTERN = Pattern
      .compile("^Time" + FORMAT_TAG_PATTERN + "$");

  private final MediaRepresentation representation;
  private final long segmentNumber;
  private final long segmentTime;

  public UrlTemplateSolver(MediaRepresentation representation, long segmentNumber,
      long segmentTime) {
    this.representation = representation;
    this.segmentNumber = segmentNumber;
    this.segmentTime = segmentTime;
  }

  public URI solveUrlTemplate(String urlTemplate) {
    int searchStart = 0;
    int escapingStart = urlTemplate.indexOf(ESCAPING_CHAR, searchStart);
    StringBuilder ret = new StringBuilder();
    while (searchStart < urlTemplate.length() && escapingStart >= 0) {
      ret.append(urlTemplate, searchStart, escapingStart);
      int escapingEnd = urlTemplate.indexOf(ESCAPING_CHAR, escapingStart + 1);
      if (escapingEnd >= 0) {
        String identifier = urlTemplate.substring(escapingStart + 1, escapingEnd);
        ret.append(solveUrlTemplateIdentifier(identifier));
        searchStart = escapingEnd + 1;
        escapingStart = urlTemplate.indexOf(ESCAPING_CHAR, searchStart);
      } else {
        searchStart = urlTemplate.length();
      }
    }
    if (searchStart < urlTemplate.length()) {
      ret.append(urlTemplate, searchStart, urlTemplate.length());
    }
    return URI.create(ret.toString());
  }

  private String solveUrlTemplateIdentifier(String identifier) {
    if (identifier.isEmpty()) {
      return "$";
    } else if ("RepresentationID".equals(identifier)) {
      return representation.getId();
    }
    Matcher matcher = NUMBER_IDENTIFIER_PATTERN.matcher(identifier);
    if (matcher.matches()) {
      return formatWithMatcher(matcher, segmentNumber);
    }
    matcher = BANDWIDTH_IDENTIFIER_PATTERN.matcher(identifier);
    if (matcher.matches()) {
      return formatWithMatcher(matcher, representation.getBandwidth());
    }
    matcher = TIME_IDENTIFIER_PATTERN.matcher(identifier);
    if (matcher.matches()) {
      return formatWithMatcher(matcher, segmentTime);
    } else {
      return "$" + identifier + "$";
    }
  }

  private String formatWithMatcher(Matcher matcher, long segmentNumber) {
    String format = matcher.group(1);
    return String.format(format != null ? format : "%01d", segmentNumber);
  }

}

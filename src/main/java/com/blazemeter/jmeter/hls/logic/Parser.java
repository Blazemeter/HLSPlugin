package com.blazemeter.jmeter.hls.logic;

import static org.apache.http.protocol.HTTP.USER_AGENT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Parser implements Serializable {

  public static final Pattern STREAM_PATTERN = Pattern
      .compile("(EXT-X-STREAM-INF.*)\\n(.*\\.m3u8.*)");
  public static final Pattern BANDWIDTH_PATTERN = Pattern.compile("[:|,]BANDWIDTH=(\\d+)");
  public static final Pattern RESOLUTION_PATTERN = Pattern.compile("[:|,]RESOLUTION=(\\d+x\\d+)");
  private static final Logger LOG = LoggerFactory.getLogger(Parser.class);

  // HTTP GET request
  public DataRequest getBaseUrl(URL url, SampleResult sampleResult, boolean setRequest)
      throws IOException {

    HttpURLConnection con;
    DataRequest result = new DataRequest();
    boolean first = true;
    long sentBytes = 0;

    con = (HttpURLConnection) url.openConnection();

    sampleResult.connectEnd();

    // By default it is GET request
    con.setRequestMethod("GET");

    // add request header
    con.setRequestProperty("User-Agent", USER_AGENT);

    // Set request header
    result.setRequestHeaders(con.getRequestMethod() + "  " + url.toString() + "\n");

    int responseCode = con.getResponseCode();

    // Reading response from input Stream
    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));

    String inputLine;
    StringBuilder response = new StringBuilder();

    while ((inputLine = in.readLine()) != null) {

      if (setRequest) {
        response.append(inputLine).append("\n");
      }

      sentBytes += inputLine.getBytes().length + 1;

      if (first) {
        sampleResult.latencyEnd();
        first = false;
      }
    }

    in.close();

    // Set response parameters
    result.setHeaders(con.getHeaderFields());
    result.setResponse(response.toString());
    result.setResponseCode(String.valueOf(responseCode));
    result.setResponseMessage(con.getResponseMessage());
    result.setContentType(con.getContentType());
    result.setSuccess(isSuccessCode(responseCode));
    result.setSentBytes(sentBytes);
    result.setContentEncoding(getEncoding(con));

    return result;

  }

  private boolean isSuccessCode(int code) {
    return code >= 200 && code <= 399;
  }

  private String getEncoding(HttpURLConnection connection) {
    String contentType = connection.getContentType();
    String[] values = contentType.split(";"); // values.length should be 2
    String charset = "";

    for (String value : values) {
      String trimmedValue = value.trim();

      if (trimmedValue.toLowerCase().startsWith("charset=")) {
        charset = trimmedValue.substring("charset=".length());
      }
    }

    return charset;
  }

  public List<DataFragment> extractVideoUrl(String playlistUrl) {
    String pattern = "EXTINF:(\\d+\\.?\\d*).*\\n(#.*:.*\\n)*(.*\\.ts(\\?.*\\n*)?)";
    final List<DataFragment> mediaList = new ArrayList<>();
    Pattern r = Pattern.compile(pattern);
    Matcher m = r.matcher(playlistUrl);
    while (m.find()) {
      DataFragment data = new DataFragment(m.group(1), m.group(3));
      LOG.info("index: {} fragment: {}", m.group(1), m.group(3));
      mediaList.add(data);
    }
    return mediaList;
  }

  public String extractMediaUrl(String playlistData, String customResolution,
      Integer customBandwidth, BandwidthOption bwSelected, ResolutionOption resSelected) {
    Integer lastMatchedBandwidth = null;
    String lastMatchedResolution = null;
    String uri = null;
    Matcher streamMatcher = STREAM_PATTERN.matcher(playlistData);
    while (streamMatcher.find()) {
      String stream = streamMatcher.group(1);
      Matcher bandwidthMatcher = BANDWIDTH_PATTERN.matcher(stream);

      if (!bandwidthMatcher.find()) {
        continue;
      }

      int streamBandwidth = Integer.parseInt(bandwidthMatcher.group(1));
      Matcher resolutionMatcher = RESOLUTION_PATTERN.matcher(stream);
      String streamResolution = resolutionMatcher.find() ? resolutionMatcher.group(1) : null;
      String matchedUri = streamMatcher.group(2);
      if (bwSelected.matches(streamBandwidth, lastMatchedBandwidth, customBandwidth)) {
        lastMatchedBandwidth = streamBandwidth;
        LOG.info("resolution match: {}, {}, {}, {}", streamResolution, lastMatchedResolution,
            resSelected, customResolution);
        if (resSelected.matches(streamResolution, lastMatchedResolution, customResolution)) {
          lastMatchedResolution = streamResolution;
          uri = matchedUri;
        }
      }
    }
    return uri;
  }

  public boolean isLive(String playlistUrl) {
    String pattern1 = "EXT-X-ENDLIST";
    Pattern r1 = Pattern.compile(pattern1);
    Matcher m1 = r1.matcher(playlistUrl);
    return !m1.find();
  }

}

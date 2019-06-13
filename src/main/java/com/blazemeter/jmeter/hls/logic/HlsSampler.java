package com.blazemeter.jmeter.hls.logic;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.jmeter.protocol.http.control.CacheManager;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.util.HTTPConstants;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.TestElementProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HlsSampler extends AbstractSampler {

  private static final Logger LOG = LoggerFactory.getLogger(HlsSampler.class);

  private static final String URL_DATA_PROPERTY_NAME = "HLS.URL_DATA";
  private static final String RES_DATA_PROPERTY_NAME = "HLS.RES_DATA";
  private static final String NET_DATA_PROPERTY_NAME = "HLS.NET_DATA";
  private static final String SECONDS_DATA_PROPERTY_NAME = "HLS.SECONDS_DATA";
  private static final String DURATION_PROPERTY_NAME = "HLS.DURATION";
  private static final String VIDEO_TYPE_PROPERTY_NAME = "HLS.VIDEOTYPE";
  private static final String RESOLUTION_TYPE_PROPERTY_NAME = "HLS.RESOLUTION_TYPE";
  private static final String BANDWIDTH_TYPE_PROPERTY_NAME = "HLS.BANDWIDTH_TYPE";
  private static final String PROTOCOL_PROPERTY_NAME = "HLS.PROTOCOL";
  private static final String RESUME_DOWNLOAD_PROPERTY_NAME = "HLS.RESUME.DOWNLOAD";

  private static final String HEADER_MANAGER = "HLSRequest.header_manager";
  private static final String COOKIE_MANAGER = "HLSRequest.cookie_manager";
  private static final String CACHE_MANAGER = "HLSRequest.cache_manager";

  private ArrayList<String> fragmentsDownloaded = new ArrayList<>();
  private Parser parser;
  private String playlist;

  public HlsSampler() {
    setName("HLS Sampler");
    parser = new Parser();
  }

  @VisibleForTesting
  public void setParser(Parser p) {
    parser = p;
  }

  public String getURLData() {
    return this.getPropertyAsString(URL_DATA_PROPERTY_NAME);
  }

  public void setURLData(String url) {
    this.setProperty(URL_DATA_PROPERTY_NAME, url);
  }

  public String getResData() {
    return this.getPropertyAsString(RES_DATA_PROPERTY_NAME);
  }

  public void setResData(String res) {
    this.setProperty(RES_DATA_PROPERTY_NAME, res);
  }

  public String getNetwordData() {
    return this.getPropertyAsString(NET_DATA_PROPERTY_NAME);
  }

  public void setNetworkData(String net) {
    this.setProperty(NET_DATA_PROPERTY_NAME, net);
  }

  public String getPlaySecondsData() {
    return this.getPropertyAsString(SECONDS_DATA_PROPERTY_NAME);
  }

  public void setPlaySecondsData(String seconds) {
    this.setProperty(SECONDS_DATA_PROPERTY_NAME, seconds);
  }

  public boolean getVideoDuration() {
    return this.getPropertyAsBoolean(DURATION_PROPERTY_NAME);
  }

  public void setVideoDuration(boolean res) {
    this.setProperty(DURATION_PROPERTY_NAME, res);
  }

  public VideoType getVideoType() {
    return VideoType.fromString(this.getPropertyAsString(VIDEO_TYPE_PROPERTY_NAME));
  }

  public void setVideoType(VideoType type) {
    this.setProperty(VIDEO_TYPE_PROPERTY_NAME, type.toString());
  }

  public ResolutionOption getResolutionType() {
    return ResolutionOption.fromString(this.getPropertyAsString(RESOLUTION_TYPE_PROPERTY_NAME));
  }

  public void setResolutionType(ResolutionOption type) {
    this.setProperty(RESOLUTION_TYPE_PROPERTY_NAME, type.toString());
  }

  public BandwidthOption getBandwidthType() {
    return BandwidthOption.fromString(this.getPropertyAsString(BANDWIDTH_TYPE_PROPERTY_NAME));
  }

  public void setBandwidthType(BandwidthOption type) {
    this.setProperty(BANDWIDTH_TYPE_PROPERTY_NAME, type.toString());
  }

  public String getProtocol() {
    return this.getPropertyAsString(PROTOCOL_PROPERTY_NAME);
  }

  public void setProtocol(String protocolValue) {
    this.setProperty(PROTOCOL_PROPERTY_NAME, protocolValue);
  }

  public boolean getResumeVideoStatus() {
    return this.getPropertyAsBoolean(RESUME_DOWNLOAD_PROPERTY_NAME);
  }

  public void setResumeVideoStatus(boolean res) {
    this.setProperty(RESUME_DOWNLOAD_PROPERTY_NAME, res);
  }

  @Override
  public SampleResult sample(Entry e) {
    SampleResult masterResult = new SampleResult();
    float currenTimeseconds = 0;
    boolean isVod = getVideoType() == VideoType.VOD;
    boolean out = false;
    boolean firstTime = true;
    boolean containNewFragments = false;
    List<String> list = new ArrayList<>();

    try {

      DataRequest respond = getMasterList(masterResult, parser);
      String auxPath = getPlaylistPath(respond, parser);

      int playSeconds = 0;
      if (!getPlaySecondsData().isEmpty()) {
        playSeconds = Integer.parseInt(getPlaySecondsData());
      }

      if (this.getResumeVideoStatus() != true) {
        this.fragmentsDownloaded.clear();
      }

      while ((playSeconds >= currenTimeseconds) && !out) {

        SampleResult playListResult = new SampleResult();
        DataRequest subRespond = getPlayList(playListResult, parser);

        List<DataFragment> videoUri = parser.extractVideoUrl(subRespond.getResponse());
        List<DataFragment> fragmentToDownload = new ArrayList<>();

        if (firstTime) {
          if ((getVideoType() == VideoType.LIVE && (parser.isLive(subRespond.getResponse())))
              || (isVod && !parser.isLive(subRespond.getResponse()))
              || (getVideoType() == VideoType.EVENT && parser.isLive(subRespond.getResponse()))) {
            firstTime = false;
            out = isVod;
          }
        }

        while ((!videoUri.isEmpty()) && (playSeconds >= currenTimeseconds)) {
          DataFragment frag = videoUri.remove(0);

          boolean isPresent = false;
          int length = fragmentsDownloaded.size();

          if (length != 0) {
            isPresent = fragmentsDownloaded.contains(frag.getTsUri().trim());
          }

          if (!isPresent) {
            fragmentToDownload.add(frag);
            fragmentsDownloaded.add(frag.getTsUri().trim());
            containNewFragments = true;
            if (getVideoDuration()) {
              currenTimeseconds += Float.parseFloat(frag.getDuration());
            }
          }
        }

        List<SampleResult> videoFragment = getFragments(parser, fragmentToDownload, auxPath);
        for (SampleResult sam : videoFragment) {
          playListResult.addSubResult(sam);
        }

        if (!list.contains(playListResult.getSampleLabel()) || containNewFragments) {
          masterResult.addSubResult(playListResult);
          list.add(playListResult.getSampleLabel());
          containNewFragments = false;
        }

      }

    } catch (IOException ex) {
      LOG.error("Problem while getting video from {}", getURLData(), ex);
      masterResult.sampleEnd();
      masterResult.setSuccessful(false);
      masterResult.setResponseMessage("Exception: " + ex);
    }
    return masterResult;
  }

  private DataRequest getMasterList(SampleResult masterResult, Parser parser) throws IOException {

    masterResult.sampleStart();
    DataRequest respond = parser.getBaseUrl(new URL(getURLData()), masterResult, true);
    masterResult.sampleEnd();

    masterResult.setRequestHeaders(
        respond.getRequestHeaders() + "\n\n" + getCookieHeader(getURLData()) + "\n\n"
            + getRequestHeader(this.getHeaderManager()));
    masterResult.setSuccessful(respond.isSuccess());
    masterResult.setResponseMessage(respond.getResponseMessage());
    masterResult.setSampleLabel(this.getName());
    masterResult.setResponseHeaders(respond.getHeadersAsString());
    masterResult.setResponseData(respond.getResponse().getBytes());
    masterResult.setResponseCode(respond.getResponseCode());
    masterResult.setContentType(respond.getContentType());
    masterResult
        .setBytes(masterResult.getBytesAsLong() + (long) masterResult.getRequestHeaders().length());
    masterResult.setHeadersSize(getHeaderBytes(masterResult, respond));
    masterResult.setSentBytes(respond.getSentBytes());
    masterResult.setDataEncoding(respond.getContentEncoding());

    return respond;

  }

  private int getHeaderBytes(SampleResult masterResult, DataRequest respond) {
    return masterResult.getResponseHeaders().length() // condensed length (without \r)
        + respond.getHeaders().size() // Add \r for each header
        + 1 // Add \r for initial header
        + 2; // final \r\n before data
  }

  private String getCookieHeader(String urlData) throws MalformedURLException {
    URL url = new URL(urlData);
    // Extracts all the required cookies for that particular URL request
    if (getCookieManager() != null) {
      String cookieHeader = getCookieManager().getCookieHeaderForURL(url);
      if (cookieHeader != null) {
        return HTTPConstants.HEADER_COOKIE + ": " + cookieHeader + "\n";
      }
    }
    return "";
  }

  private CookieManager getCookieManager() {
    return (CookieManager) getProperty(COOKIE_MANAGER).getObjectValue();
  }

  private HeaderManager getHeaderManager() {
    return (HeaderManager) getProperty(HlsSampler.HEADER_MANAGER).getObjectValue();
  }

  private String getRequestHeader(
      org.apache.jmeter.protocol.http.control.HeaderManager headerManager) {
    StringBuilder headerString = new StringBuilder();

    if (headerManager != null) {
      CollectionProperty headers = headerManager.getHeaders();
      if (headers != null) {
        for (JMeterProperty jMeterProperty : headers) {
          org.apache.jmeter.protocol.http.control.Header header =
              (org.apache.jmeter.protocol.http.control.Header) jMeterProperty.getObjectValue();
          String n = header.getName();
          if (!HTTPConstants.HEADER_CONTENT_LENGTH.equalsIgnoreCase(n)) {
            String v = header.getValue();
            v = v.replaceFirst(":\\d+$", "");
            headerString.append(n).append(": ").append(v).append("\n");
          }
        }
      }
    }

    return headerString.toString();
  }

  private String getPlaylistPath(DataRequest respond, Parser parser) throws MalformedURLException {
    URL masterURL = new URL(getURLData());
    String customBandwidth = this.getNetwordData();
    String playlistUri = parser.extractMediaUrl(respond.getResponse(), this.getResData(),
        customBandwidth != null && !customBandwidth.isEmpty() ? Integer.valueOf(customBandwidth)
            : null,
        this.getBandwidthType(), this.getResolutionType());
    String auxPath = masterURL.getPath().substring(0, masterURL.getPath().lastIndexOf('/') + 1);

    if (playlistUri == null) {
      playlistUri = getURLData();
    }

    if (playlistUri.startsWith("http")) {
      playlist = playlistUri;
    } else if (playlistUri.indexOf('/') == 0) {
      playlist = getBaseUrl(masterURL) + playlistUri; // "https://"
    } else {
      playlist = getBaseUrl(masterURL) + auxPath + playlistUri;
    }

    auxPath = getBaseUrl(masterURL) + auxPath;

    return auxPath;

  }

  private String getBaseUrl(URL masterURL) {
    return getProtocol() + "://" + masterURL.getHost() + (masterURL.getPort() > 0 ? ":" + masterURL
        .getPort() : "");
  }

  private DataRequest getPlayList(SampleResult playListResult, Parser parser) throws IOException {

    String lastPath;
    playListResult.sampleStart();
    DataRequest subRespond = parser.getBaseUrl(new URL(playlist), playListResult, true);
    playListResult.sampleEnd();

    String[] urlArray = playlist.split("/");
    lastPath = urlArray[urlArray.length - 1];

    playListResult.setRequestHeaders(
        subRespond.getRequestHeaders() + "\n\n" + getCookieHeader(playlist) + "\n\n"
            + getRequestHeader(this.getHeaderManager()));
    playListResult.setSuccessful(subRespond.isSuccess());
    playListResult.setResponseMessage(subRespond.getResponseMessage());
    playListResult.setSampleLabel(lastPath);
    playListResult.setResponseHeaders(subRespond.getHeadersAsString());
    playListResult.setResponseData(subRespond.getResponse().getBytes());
    playListResult.setResponseCode(subRespond.getResponseCode());
    playListResult.setContentType(subRespond.getContentType());
    playListResult.setBytes(
        playListResult.getBytesAsLong() + (long) playListResult.getRequestHeaders().length());
    playListResult.setHeadersSize(getHeaderBytes(playListResult, subRespond));
    playListResult.setSentBytes(subRespond.getSentBytes());
    playListResult.setDataEncoding(subRespond.getContentEncoding());

    return subRespond;
  }

  private List<SampleResult> getFragments(Parser parser, List<DataFragment> uris, String url) {
    List<SampleResult> res = new ArrayList<>();
    if (!uris.isEmpty()) {
      SampleResult result = new SampleResult();
      String uriString = uris.get(0).getTsUri();
      if ((url != null) && (!uriString.startsWith("http"))) {
        uriString = url + uriString;
      }

      result.sampleStart();

      try {

        DataRequest respond = parser.getBaseUrl(new URL(uriString), result, false);

        result.sampleEnd();

        String[] urlArray = uriString.split("/");
        String lastPath = urlArray[urlArray.length - 1];

        result.setRequestHeaders(
            respond.getRequestHeaders() + "\n\n" + getCookieHeader(uriString) + "\n\n"
                + getRequestHeader(this.getHeaderManager()));
        result.setSuccessful(respond.isSuccess());
        result.setResponseMessage(respond.getResponseMessage());
        result.setSampleLabel(lastPath);
        result.setResponseHeaders("URL: " + uriString + "\n" + respond.getHeadersAsString());
        result.setResponseCode(respond.getResponseCode());
        result.setContentType(respond.getContentType());
        result.setBytes(result.getBytesAsLong() + (long) result.getRequestHeaders().length());
        result.setHeadersSize(getHeaderBytes(result, respond));
        result.setSentBytes(respond.getSentBytes());
        result.setDataEncoding(respond.getContentEncoding());

        res.add(result);

      } catch (IOException e) {
        LOG.error("Problem while getting fragments from {}", url, e);
        result.sampleEnd();
        result.setSuccessful(false);
        result.setResponseMessage("Exception: " + e);
        res.add(result);
      }

      uris.remove(0);
      List<SampleResult> aux = getFragments(parser, uris, url);
      for (SampleResult s : aux) {
        if (!res.contains(s)) {
          res.add(s);
        }
      }
    }
    return res;
  }

  @Override
  public void addTestElement(TestElement el) {
    if (el instanceof HeaderManager) {
      setHeaderManager((HeaderManager) el);
    } else if (el instanceof CookieManager) {
      setCookieManager((CookieManager) el);
    } else if (el instanceof CacheManager) {
      setCacheManager((CacheManager) el);
    } else {
      super.addTestElement(el);
    }
  }

  private void setHeaderManager(HeaderManager value) {
    HeaderManager mgr = getHeaderManager();
    if (mgr != null) {
      value = mgr.merge(value, true);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Existing HeaderManager '{}' merged with '{}'", mgr.getName(), value.getName());
        for (int i = 0; i < value.getHeaders().size(); i++) {
          LOG.debug("    {}={}", value.getHeader(i).getName(), value.getHeader(i).getValue());
        }
      }
    }
    setProperty(new TestElementProperty(HEADER_MANAGER, value));
  }

  private void setCookieManager(CookieManager value) {
    CookieManager mgr = getCookieManager();
    if (mgr != null) {
      LOG.warn("Existing CookieManager {} superseded by {}", mgr.getName(), value.getName());
    }
    setCookieManagerProperty(value);
  }

  // private method to allow AsyncSample to reset the value without performing
  // checks
  private void setCookieManagerProperty(CookieManager value) {
    setProperty(new TestElementProperty(COOKIE_MANAGER, value));
  }

  private void setCacheManager(CacheManager value) {
    CacheManager mgr = getCacheManager();
    if (mgr != null) {
      LOG.warn("Existing CacheManager {} superseded by {}", mgr.getName(), value.getName());
    }
    setCacheManagerProperty(value);
  }

  private CacheManager getCacheManager() {
    return (CacheManager) getProperty(CACHE_MANAGER).getObjectValue();
  }

  // private method to allow AsyncSample to reset the value without performing
  // checks
  private void setCacheManagerProperty(CacheManager value) {
    setProperty(new TestElementProperty(CACHE_MANAGER, value));
  }

}

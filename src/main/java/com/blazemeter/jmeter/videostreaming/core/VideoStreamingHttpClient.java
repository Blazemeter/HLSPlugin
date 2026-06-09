package com.blazemeter.jmeter.videostreaming.core;

import com.blazemeter.jmeter.videostreaming.core.exception.SamplerInterruptedException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.jmeter.protocol.http.control.CacheManager;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPHC4Impl;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;

/*
We use this class to be able to access some methods on super class and because we can't extend
HTTPProxySampler class
*/
public class VideoStreamingHttpClient extends HTTPHC4Impl {

  private static final String NON_HTTP_RESPONSE_CODE = "Non HTTP response code";
  private static final String NON_HTTP_RESPONSE_MESSAGE = "Non HTTP response message";

  private transient volatile boolean interrupted = false;

  private Map<String, String> headers = new HashMap<>();

  public VideoStreamingHttpClient(HTTPSamplerBase testElement) {
    super(testElement);
  }

  @Override
  public HTTPSampleResult sample(URL url, String method, boolean areFollowingRedirect,
      int frameDepth) {
    return super.sample(url, method, areFollowingRedirect, frameDepth);
  }

  @Override
  public void notifyFirstSampleAfterLoopRestart() {
    super.notifyFirstSampleAfterLoopRestart();
  }

  @Override
  public void threadFinished() {
    super.threadFinished();
  }

  @Override
  public boolean interrupt() {
    interrupted = true;
    return super.interrupt();
  }

  public HTTPSampleResult downloadUri(URI uri) {
    if (interrupted) {
      throw new SamplerInterruptedException();
    }
    if (uri == null || !uri.isAbsolute()) {
      return buildInvalidUriResult(uri, "URI is not absolute");
    }
    try {
      return sample(uri.toURL(), "GET", false, 0);
    } catch (MalformedURLException e) {
      return buildInvalidUriResult(uri, e.getMessage());
    }
  }

  /*
  When the resolved URI can't be turned into an absolute URL (e.g. a dynamic master URL
  variable resolved to an empty value or a relative path), we return a failed sample result
  instead of throwing an uncaught IllegalArgumentException. This way the failure is recorded as
  a measurable sample and handled by the normal download error path, rather than aborting the
  sampler with a raw stack trace in the logs.
  */
  private HTTPSampleResult buildInvalidUriResult(URI uri, String detail) {
    HTTPSampleResult result = new HTTPSampleResult();
    result.sampleStart();
    result.sampleEnd();
    result.setSuccessful(false);
    result.setResponseCode(
        NON_HTTP_RESPONSE_CODE + ": " + IllegalArgumentException.class.getName());
    result.setResponseMessage(NON_HTTP_RESPONSE_MESSAGE + ": "
        + detail + " [" + (uri != null ? uri : "null") + "]");
    return result;
  }

  public void addHeader(String name, String value) {
    headers.put(name, value);
  }

  @Override
  protected void setConnectionHeaders(HttpRequestBase request, URL url,
      HeaderManager headerManager, CacheManager cacheManager) {
    headers.forEach(request::addHeader);
    super.setConnectionHeaders(request, url, headerManager, cacheManager);
  }

}

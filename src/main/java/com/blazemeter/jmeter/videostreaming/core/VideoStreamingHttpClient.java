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
    try {
      return sample(uri.toURL(), "GET", false, 0);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
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

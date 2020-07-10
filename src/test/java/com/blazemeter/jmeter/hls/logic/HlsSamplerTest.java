package com.blazemeter.jmeter.hls.logic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Java6Assertions.assertThat;

import com.blazemeter.jmeter.videostreaming.dash.DashSampler;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.HttpURLConnection;
import org.junit.Before;
import org.junit.Test;

public class HlsSamplerTest {
  private static final String HOST = "localhost";
  private static final String URL_FORMAT = "http://%s:%d/%s";
  private WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
  private HlsSampler sampler;

  @Before
  public void setUp() {
    wireMockServer.start();
    configureFor(HOST, wireMockServer.port());
    sampler = new HlsSampler();
  }

  @Test
  public void shouldRecognizeHlsProtocolWhenUrlWithExtension() {
    String resource = "hls_master_playlist.m3u8";
    prepareMockResponse(resource);
    sampler.setMasterUrl(mockURL("/"+resource));
    assertThat(sampler.protocolPick()).isInstanceOf(com.blazemeter.jmeter.videostreaming.hls.HlsSampler.class);
  }

  private void prepareMockResponse(String resource) {
    wireMockServer.start();
    configureFor(HOST, wireMockServer.port());

    wireMockServer.stubFor(get(urlEqualTo(mockURL(resource))).willReturn(aResponse()
        .withStatus(HttpURLConnection.HTTP_OK)
        .withHeader("Cache-Control", "no-cache")
        .withBody("")
    ));
  }

  private String mockURL(String resourceName) {
    return String.format(URL_FORMAT, HOST, wireMockServer.port(), resourceName);
  }

  @Test
  public void shouldRecognizeDashProtocolWhenUrlWithExtension() {
    String resource = "dash_manifest.mpd";
    prepareMockResponse(resource);
    sampler.setMasterUrl(mockURL("/"+resource));
    assertThat(sampler.protocolPick()).isInstanceOf(DashSampler.class);
  }

  @Test
  public void shouldRecognizeDashProtocolWhenUrlWithNoExtension() {
    String resource = "dash_manifest";
    prepareMockResponse(resource);
    sampler.setMasterUrl(mockURL("/"+resource));
    assertThat(sampler.protocolPick()).isInstanceOf(DashSampler.class);
  }
}
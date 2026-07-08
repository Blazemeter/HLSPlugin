package com.blazemeter.jmeter.videostreaming.dash;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;

public class ManifestTest {

  private static final URI TEST_URI = URI.create("http://test/manifest.mpd");
  private static final int THREAD_COUNT = 32;

  @Test
  public void shouldParseManifestConsistentlyWhenParsingConcurrentlyFromSharedParser()
      throws Exception {
    String defaultBody = loadResource("defaultManifest.mpd");
    String dynamicBody = loadResource("dynamicTimelineManifest.mpd");
    String refreshedBody = loadResource("dynamicTimelineManifestRefreshed.mpd");

    Manifest expectedDefault = Manifest.fromUriAndBody(TEST_URI, defaultBody, Instant.EPOCH);
    Manifest expectedDynamic = Manifest.fromUriAndBody(TEST_URI, dynamicBody, Instant.EPOCH);
    Manifest expectedRefreshed = Manifest.fromUriAndBody(TEST_URI, refreshedBody, Instant.EPOCH);

    List<Callable<Manifest>> tasks = new ArrayList<>();
    for (int i = 0; i < THREAD_COUNT; i++) {
      String body = i % 3 == 0 ? defaultBody : (i % 3 == 1 ? dynamicBody : refreshedBody);
      tasks.add(() -> Manifest.fromUriAndBody(TEST_URI, body, Instant.EPOCH));
    }

    List<Manifest> results = runConcurrently(tasks);
    for (int i = 0; i < results.size(); i++) {
      Manifest parsed = results.get(i);
      Manifest expected = i % 3 == 0 ? expectedDefault : (i % 3 == 1 ? expectedDynamic
          : expectedRefreshed);
      assertManifestEquivalent(parsed, expected);
    }
  }

  private static void assertManifestEquivalent(Manifest parsed, Manifest expected) {
    assertThat(parsed.isDynamic()).isEqualTo(expected.isDynamic());
    assertThat(parsed.getPeriods()).hasSize(expected.getPeriods().size());
    assertThat(parsed.getBandwidths()).isEqualTo(expected.getBandwidths());
    assertThat(parsed.getResolutions()).isEqualTo(expected.getResolutions());
    if (expected.isDynamic()) {
      assertThat(parsed.getMinimumUpdatePeriod()).isEqualTo(expected.getMinimumUpdatePeriod());
    } else {
      assertThat(parsed.getMediaPresentationDuration())
          .isEqualTo(expected.getMediaPresentationDuration());
    }
  }

  private static List<Manifest> runConcurrently(List<Callable<Manifest>> tasks) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    try {
      List<Future<Manifest>> futures = executor.invokeAll(tasks);
      List<Manifest> results = new ArrayList<>(futures.size());
      for (Future<Manifest> future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private static String loadResource(String name) throws IOException {
    return Resources.toString(Resources.getResource(ManifestTest.class, name), Charsets.UTF_8);
  }

  @Test
  public void shouldParseStaticManifestWhenFromUriAndBody() throws Exception {
    String body = loadResource("defaultManifest.mpd");
    Manifest manifest = Manifest.fromUriAndBody(TEST_URI, body, Instant.EPOCH);
    assertThat(manifest.isDynamic()).isFalse();
    assertThat(manifest.getPeriods()).hasSize(1);
  }

  @Test
  public void shouldParseDynamicManifestWhenFromUriAndBody() throws Exception {
    String body = loadResource("dynamicTimelineManifest.mpd");
    Manifest manifest = Manifest.fromUriAndBody(TEST_URI, body, Instant.EPOCH);
    assertThat(manifest.isDynamic()).isTrue();
    assertThat(manifest.getMinimumUpdatePeriod()).isNotNull();
  }

}

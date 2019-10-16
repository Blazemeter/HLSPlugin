package com.blazemeter.jmeter.videostreaming;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.jmeter.samplers.SampleResult;
import org.json.simple.JSONObject;

public class ComparableSampleResult {

  private String json;

  public ComparableSampleResult(SampleResult base) {
    json = sampleResultToJson(base);
  }

  @SuppressWarnings("unchecked")
  private static String sampleResultToJson(SampleResult sample) {
    JSONObject json = new JSONObject();
    json.put("successful", sample.isSuccessful());
    json.put("label", sample.getSampleLabel());
    json.put("requestHeaders", sample.getRequestHeaders());
    json.put("sampleData", sample.getSamplerData());
    json.put("responseCode", sample.getResponseCode());
    json.put("responseMessage", sample.getResponseMessage());
    json.put("responseHeaders", sample.getResponseHeaders());
    json.put("responseData", sample.getResponseDataAsString());
    return json.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ComparableSampleResult that = (ComparableSampleResult) o;
    return Objects.equals(json, that.json);
  }

  @Override
  public int hashCode() {
    return Objects.hash(json);
  }

  @Override
  public String toString() {
    return json;
  }

  public static List<ComparableSampleResult> listFrom(List<SampleResult> results) {
    return results.stream()
        .map(ComparableSampleResult::new)
        .collect(Collectors.toList());
  }

}

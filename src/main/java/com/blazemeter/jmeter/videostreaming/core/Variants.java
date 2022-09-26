package com.blazemeter.jmeter.videostreaming.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Variants {

  private List<String> subtitleList = new ArrayList<>();
  private List<String> audioLanguageList = new ArrayList<>();
  private Map<String, String> bandwidthResolutionMap = new HashMap<>();
  private List<String> resolutionList = new ArrayList<>(bandwidthResolutionMap.values());
  private List<String> bandwidthList = new ArrayList<>(bandwidthResolutionMap.keySet());
  private String defaultValue = "default";

  public List<String> getSubtitleList() {
    subtitleList.add(defaultValue);
    return subtitleList.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toList());
  }

  public void setSubtitleList(List<String> subtitleList) {
    this.subtitleList = subtitleList;
  }

  public List<String> getAudioLanguageList() {
    audioLanguageList.add(defaultValue);
    return audioLanguageList.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toList());
  }

  public void setAudioLanguageList(List<String> audioLanguageList) {
    this.audioLanguageList = audioLanguageList;
  }

  public List<String> getResolutionList() {
    Map<Integer, String> map = new HashMap<>();
    setMinAndMaxForDefault(map);

    for (String value : resolutionList) {
      if (value != null && !value.contains("null")) {
        String[] split = value.split("x", 0);
        int splitKey = Integer.parseInt(split[0]);
        map.put(splitKey, value);
      }
    }

    Map<Integer, String> treeMap = new TreeMap<>(map);

    return new ArrayList<>(treeMap.values());
  }

  public void setResolutionList(List<String> resolutionList) {
    this.resolutionList = resolutionList;
  }

  public List<String> getBandwidthList() {
    Map<Integer, String> map = new HashMap<>();

    setMinAndMaxForDefault(map);
    for (String value : bandwidthList) {
      int convert = Integer.parseInt(value);
      map.put(convert, value);
    }
    Map<Integer, String> treeMap = new TreeMap<>(map);

    return new ArrayList<>(treeMap.values());
  }

  public void setBandwidthList(List<String> bandwidthList) {
    this.bandwidthList = bandwidthList;
  }

  public void setMinAndMaxForDefault(Map<Integer, String> map) {
    map.put(0, "Min");
    map.put(1, "Max");
  }

  public Map<String, String> getBandwidthResolutionMap() {
    return bandwidthResolutionMap;
  }

  public void setBandwidthResolutionMap(Map<String, String> bandwidthResolutionMap) {
    this.bandwidthResolutionMap = bandwidthResolutionMap;
  }
}

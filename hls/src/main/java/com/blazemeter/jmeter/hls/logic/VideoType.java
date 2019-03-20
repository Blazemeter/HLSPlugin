package com.blazemeter.jmeter.hls.logic;

public enum VideoType {
    LIVE, VOD, EVENT;

    public static VideoType fromString(String str) {
        return str != null && !str.isEmpty() ? VideoType.valueOf(str.toUpperCase()) : VideoType.VOD;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

}

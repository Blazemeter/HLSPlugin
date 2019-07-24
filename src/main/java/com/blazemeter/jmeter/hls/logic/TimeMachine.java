package com.blazemeter.jmeter.hls.logic;

import java.time.Instant;

public interface TimeMachine {

  TimeMachine SYSTEM = new TimeMachine() {
    @Override
    public void awaitMillis(long millis) throws InterruptedException {
      Thread.sleep(millis);
    }

    @Override
    public Instant now() {
      return Instant.now();
    }
  };

  void awaitMillis(long millis) throws InterruptedException;

  Instant now();
}

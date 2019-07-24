package com.blazemeter.jmeter.hls.logic;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public interface TimeMachine {

  TimeMachine SYSTEM = new TimeMachine() {

    @Override
    public void countdown() {
      COUNT_DOWN_LATCH.countDown();
    }

    @Override
    public void awaitMillis(long millis) throws InterruptedException {
      COUNT_DOWN_LATCH.await(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public Instant now() {
      return Instant.now();
    }
  };

  CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);

  void awaitMillis(long millis) throws InterruptedException;

  Instant now();

  void countdown();
}

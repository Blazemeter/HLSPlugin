package com.blazemeter.jmeter.videostreaming.core;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public interface TimeMachine {

  TimeMachine SYSTEM = new TimeMachine() {
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    @Override
    public void reset() {
      countDownLatch = new CountDownLatch(1);
    }

    @Override
    public void interrupt() {
      countDownLatch.countDown();
    }

    @Override
    public void awaitMillis(long millis) throws InterruptedException {
      countDownLatch.await(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public Instant now() {
      return Instant.now();
    }
  };

  void reset();

  void awaitMillis(long millis) throws InterruptedException;

  Instant now();

  void interrupt();

}

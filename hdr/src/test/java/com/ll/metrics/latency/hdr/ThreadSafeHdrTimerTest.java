package com.ll.metrics.latency.hdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.snapshot.LatencySnapshot;
import com.ll.metrics.latency.timer.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ThreadSafeHdrTimerTest {
  @Test
  void recordingUpdatesSnapshot() {
    Timer timer = new ThreadSafeHdrTimer();

    timer.record(10);
    timer.record(20);
    timer.record(30);

    LatencySnapshot snapshot = timer.snapshot();
    assertEquals(3, snapshot.count());
    assertEquals(10, snapshot.min());
    assertEquals(30, snapshot.max());
    assertEquals(20.0d, snapshot.mean(), 1.0d);
  }

  @Test
  void percentilesMatchRecordedDistribution() {
    Timer timer = new ThreadSafeHdrTimer();
    for (int value = 1; value <= 100; value++) {
      timer.record(value);
    }

    LatencySnapshot snapshot = timer.snapshot();

    assertEquals(50, snapshot.p50());
    assertEquals(90, snapshot.p90());
    assertEquals(95, snapshot.p95());
    assertEquals(99, snapshot.p99());
    assertEquals(100, snapshot.p999());
  }

  @Test
  void rejectsNegativeDuration() {
    Timer timer = new ThreadSafeHdrTimer();

    assertThrows(IllegalArgumentException.class, () -> timer.record(-1));
  }

  @Test
  void snapshotsAreDefensive() {
    Timer timer = new ThreadSafeHdrTimer();
    timer.record(10);
    LatencySnapshot first = timer.snapshot();

    timer.record(20);
    LatencySnapshot second = timer.snapshot();

    assertEquals(1, first.count());
    assertEquals(2, second.count());
  }

  @Test
  void concurrentRecordCallsDoNotFail() throws InterruptedException {
    Timer timer = new ThreadSafeHdrTimer();
    int threads = 8;
    int recordsPerThread = 1_000;
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      for (int thread = 0; thread < threads; thread++) {
        executor.submit(
            () -> {
              await(start);
              for (int record = 0; record < recordsPerThread; record++) {
                timer.record(record + 1L);
              }
            });
      }
      start.countDown();
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    assertEquals((long) threads * recordsPerThread, timer.snapshot().count());
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}

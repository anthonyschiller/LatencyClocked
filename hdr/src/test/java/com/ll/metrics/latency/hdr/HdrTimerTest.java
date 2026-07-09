package com.ll.metrics.latency.hdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ll.metrics.latency.snapshot.LatencySnapshot;
import com.ll.metrics.latency.timer.Timer;
import org.junit.jupiter.api.Test;

class HdrTimerTest {
  @Test
  void recordingUpdatesSnapshot() {
    Timer timer = new HdrTimer();

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
    Timer timer = new HdrTimer();
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
    Timer timer = new HdrTimer();

    assertThrows(IllegalArgumentException.class, () -> timer.record(-1));
  }

  @Test
  void snapshotsAreDefensive() {
    Timer timer = new HdrTimer();
    timer.record(10);
    LatencySnapshot first = timer.snapshot();

    timer.record(20);
    LatencySnapshot second = timer.snapshot();

    assertEquals(1, first.count());
    assertEquals(2, second.count());
  }
}

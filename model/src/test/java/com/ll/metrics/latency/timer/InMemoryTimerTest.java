package com.ll.metrics.latency.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ll.metrics.latency.snapshot.LatencySnapshot;
import org.junit.jupiter.api.Test;

class InMemoryTimerTest {
  @Test
  void recordingUpdatesSnapshot() {
    Timer timer = new InMemoryTimer();

    for (int value = 1; value <= 100; value++) {
      timer.record(value);
    }

    LatencySnapshot snapshot = timer.snapshot();
    assertEquals(100, snapshot.count());
    assertEquals(1, snapshot.min());
    assertEquals(100, snapshot.max());
    assertEquals(50.5d, snapshot.mean());
    assertEquals(50, snapshot.p50());
    assertEquals(90, snapshot.p90());
    assertEquals(95, snapshot.p95());
    assertEquals(99, snapshot.p99());
    assertEquals(100, snapshot.p999());
  }

  @Test
  void rejectsNegativeDuration() {
    Timer timer = new InMemoryTimer();

    assertThrows(IllegalArgumentException.class, () -> timer.record(-1));
  }
}

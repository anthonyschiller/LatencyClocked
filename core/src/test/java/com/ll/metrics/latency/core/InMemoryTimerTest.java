package com.ll.metrics.latency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InMemoryTimerTest {
  @Test
  void recordingUpdatesSnapshot() {
    Timer timer = new InMemoryTimer();

    timer.record(10);
    timer.record(20);
    timer.record(30);

    LatencySnapshot snapshot = timer.snapshot();
    assertEquals(3, snapshot.count());
    assertEquals(10, snapshot.min());
    assertEquals(30, snapshot.max());
    assertEquals(20.0d, snapshot.mean());
  }

  @Test
  void rejectsNegativeDuration() {
    Timer timer = new InMemoryTimer();

    assertThrows(IllegalArgumentException.class, () -> timer.record(-1));
  }
}

package com.ll.metrics.latency.hdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ll.metrics.latency.snapshot.TimerSnapshot;
import com.ll.metrics.latency.timer.Timer;
import com.ll.metrics.latency.timer.Timers;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class HdrTimersTest {
  @Test
  void createsHdrBackedTimers() {
    Timers timers = HdrTimers.create();

    Timer timer = timers.timer("service.call");

    assertInstanceOf(HdrTimers.class, timers);
    assertInstanceOf(HdrTimer.class, timer);
  }

  @Test
  void createsThreadSafeHdrBackedTimers() {
    Timers timers = HdrTimers.createWithThreadsafeTimers();

    Timer timer = timers.timer("service.call");

    assertInstanceOf(HdrTimers.class, timers);
    assertInstanceOf(ThreadSafeHdrTimer.class, timer);
  }

  @Test
  void returnsSameTimerForSameId() {
    Timers timers = HdrTimers.create();

    Timer first = timers.timer("service.call");
    Timer second = timers.timer("service.call");

    assertSame(first, second);
  }

  @Test
  void returnsDifferentTimersForDifferentIds() {
    Timers timers = HdrTimers.create();

    Timer first = timers.timer("service.first");
    Timer second = timers.timer("service.second");

    assertNotSame(first, second);
  }

  @Test
  void rejectsNullId() {
    Timers timers = HdrTimers.create();

    assertThrows(NullPointerException.class, () -> timers.timer(null));
  }

  @Test
  void rejectsBlankId() {
    Timers timers = HdrTimers.create();

    assertThrows(IllegalArgumentException.class, () -> timers.timer(""));
    assertThrows(IllegalArgumentException.class, () -> timers.timer("  "));
  }

  @Test
  void snapshotsIncludePercentiles() {
    Timers timers = HdrTimers.create();
    Timer timer = timers.timer("service.call");
    for (int value = 1; value <= 100; value++) {
      timer.record(value);
    }

    TimerSnapshot snapshot = timers.snapshots().stream().findFirst().orElseThrow();

    assertEquals("service.call", snapshot.id());
    assertEquals(100, snapshot.count());
    assertEquals(50, snapshot.p50());
    assertEquals(90, snapshot.p90());
    assertEquals(95, snapshot.p95());
    assertEquals(99, snapshot.p99());
    assertEquals(100, snapshot.p999());
  }

  @Test
  void snapshotsReturnsImmutableCollection() {
    Timers timers = HdrTimers.create();
    timers.timer("service.call");

    Collection<TimerSnapshot> snapshots = timers.snapshots();

    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshots.add(new TimerSnapshot("other", 0, 0, 0, 0.0d, 0, 0, 0, 0, 0)));
  }
}

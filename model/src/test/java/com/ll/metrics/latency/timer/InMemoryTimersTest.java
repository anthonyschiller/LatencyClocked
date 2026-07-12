package com.ll.metrics.latency.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.snapshot.TimerSnapshot;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class InMemoryTimersTest {
  @Test
  void returnsSameTimerForSameMethodId() {
    Timers timers = InMemoryTimers.create();

    Timer first = timers.claim("service.call");
    Timer second = timers.claim("service.call");

    assertSame(first, second);
  }

  @Test
  void returnsDifferentTimersForDifferentMethodIds() {
    Timers timers = InMemoryTimers.create();

    Timer first = timers.claim("service.first");
    Timer second = timers.claim("service.second");

    assertNotSame(first, second);
  }

  @Test
  void rejectsNullId() {
    Timers timers = InMemoryTimers.create();

    assertThrows(NullPointerException.class, () -> timers.claim(null));
  }

  @Test
  void rejectsBlankId() {
    Timers timers = InMemoryTimers.create();

    assertThrows(IllegalArgumentException.class, () -> timers.claim(""));
    assertThrows(IllegalArgumentException.class, () -> timers.claim("  "));
  }

  @Test
  void snapshotsIncludeRegisteredTimerIds() {
    Timers timers = InMemoryTimers.create();
    timers.claim("service.first");
    timers.claim("service.second");

    Collection<TimerSnapshot> snapshots = timers.snapshots();

    assertEquals(2, snapshots.size());
    assertTrue(snapshots.stream().anyMatch(snapshot -> snapshot.id().equals("service.first")));
    assertTrue(snapshots.stream().anyMatch(snapshot -> snapshot.id().equals("service.second")));
  }

  @Test
  void snapshotsReflectRecordedValues() {
    Timers timers = InMemoryTimers.create();
    timers.claim("service.call").record(10);
    timers.claim("service.call").record(20);

    TimerSnapshot snapshot =
        timers.snapshots().stream()
            .filter(candidate -> candidate.id().equals("service.call"))
            .findFirst()
            .orElseThrow();

    assertEquals(2, snapshot.count());
    assertEquals(10, snapshot.min());
    assertEquals(20, snapshot.max());
    assertEquals(15.0d, snapshot.mean());
    assertEquals(10, snapshot.p50());
    assertEquals(20, snapshot.p90());
    assertEquals(20, snapshot.p95());
    assertEquals(20, snapshot.p99());
    assertEquals(20, snapshot.p999());
  }

  @Test
  void snapshotsReturnsImmutableCollection() {
    Timers timers = InMemoryTimers.create();
    timers.claim("service.call");

    Collection<TimerSnapshot> snapshots = timers.snapshots();

    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshots.add(new TimerSnapshot("other", 0, 0, 0, 0.0d, 0, 0, 0, 0, 0)));
  }

  @Test
  void previouslyObtainedSnapshotsDoNotChangeAfterRecording() {
    Timers timers = InMemoryTimers.create();
    Timer timer = timers.claim("service.call");
    timer.record(10);
    TimerSnapshot before = timers.snapshots().stream().findFirst().orElseThrow();

    timer.record(20);
    TimerSnapshot after = timers.snapshots().stream().findFirst().orElseThrow();

    assertEquals(1, before.count());
    assertEquals(10, before.max());
    assertEquals(2, after.count());
    assertEquals(20, after.max());
  }

}

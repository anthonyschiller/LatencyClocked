package com.ll.metrics.latency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import org.junit.jupiter.api.Test;

class InMemoryTimersTest {
  @Test
  void returnsSameTimerForSameId() {
    Timers timers = new InMemoryTimers();

    Timer first = timers.timer("service.call");
    Timer second = timers.timer("service.call");

    assertSame(first, second);
  }

  @Test
  void returnsDifferentTimersForDifferentIds() {
    Timers timers = new InMemoryTimers();

    Timer first = timers.timer("service.first");
    Timer second = timers.timer("service.second");

    assertNotSame(first, second);
  }

  @Test
  void rejectsNullId() {
    Timers timers = new InMemoryTimers();

    assertThrows(NullPointerException.class, () -> timers.timer(null));
  }

  @Test
  void rejectsBlankId() {
    Timers timers = new InMemoryTimers();

    assertThrows(IllegalArgumentException.class, () -> timers.timer(""));
    assertThrows(IllegalArgumentException.class, () -> timers.timer("  "));
  }

  @Test
  void snapshotsIncludeRegisteredTimerIds() {
    Timers timers = new InMemoryTimers();
    timers.timer("service.first");
    timers.timer("service.second");

    Collection<TimerSnapshot> snapshots = timers.snapshots();

    assertEquals(2, snapshots.size());
    assertTrue(snapshots.stream().anyMatch(snapshot -> snapshot.id().equals("service.first")));
    assertTrue(snapshots.stream().anyMatch(snapshot -> snapshot.id().equals("service.second")));
  }

  @Test
  void snapshotsReflectRecordedValues() {
    Timers timers = new InMemoryTimers();
    timers.timer("service.call").record(10);
    timers.timer("service.call").record(20);

    TimerSnapshot snapshot =
        timers.snapshots().stream()
            .filter(candidate -> candidate.id().equals("service.call"))
            .findFirst()
            .orElseThrow();

    assertEquals(2, snapshot.count());
    assertEquals(10, snapshot.min());
    assertEquals(20, snapshot.max());
    assertEquals(15.0d, snapshot.mean());
  }

  @Test
  void snapshotsReturnsImmutableCollection() {
    Timers timers = new InMemoryTimers();
    timers.timer("service.call");

    Collection<TimerSnapshot> snapshots = timers.snapshots();

    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshots.add(new TimerSnapshot("other", 0, 0, 0, 0.0d)));
  }
}

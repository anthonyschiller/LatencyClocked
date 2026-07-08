package com.ll.metrics.latency.core;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory timer catalogue that creates timers on first use. */
public final class InMemoryTimers implements Timers {
  private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

  @Override
  public Timer timer(String id) {
    Objects.requireNonNull(id, "id");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    return timers.computeIfAbsent(id, ignored -> new InMemoryTimer());
  }

  @Override
  public Collection<TimerSnapshot> snapshots() {
    return timers.entrySet().stream()
        .map(entry -> snapshot(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static TimerSnapshot snapshot(String id, Timer timer) {
    LatencySnapshot snapshot = timer.snapshot();
    return new TimerSnapshot(id, snapshot.count(), snapshot.min(), snapshot.max(), snapshot.mean());
  }
}

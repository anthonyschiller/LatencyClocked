package com.ll.metrics.latency.timer;

import com.ll.metrics.latency.snapshot.LatencySnapshot;
import com.ll.metrics.latency.snapshot.TimerSnapshot;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory generated method timer catalogue that creates timers on first bind. */
public final class InMemoryTimers implements Timers {
  private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

  public static InMemoryTimers create() {
    return new InMemoryTimers();
  }

  private InMemoryTimers() {}

  @Override
  public Timer claim(String methodId) {
    Objects.requireNonNull(methodId, "methodId");
    if (methodId.isBlank()) {
      throw new IllegalArgumentException("methodId must not be blank");
    }
    return timers.computeIfAbsent(methodId, ignored -> new InMemoryTimer());
  }

  @Override
  public Collection<TimerSnapshot> snapshots() {
    return timers.entrySet().stream()
        .map(entry -> snapshot(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static TimerSnapshot snapshot(String id, Timer timer) {
    LatencySnapshot snapshot = timer.snapshot();
    return new TimerSnapshot(
        id,
        snapshot.count(),
        snapshot.min(),
        snapshot.max(),
        snapshot.mean(),
        snapshot.p50(),
        snapshot.p90(),
        snapshot.p95(),
        snapshot.p99(),
        snapshot.p999());
  }
}

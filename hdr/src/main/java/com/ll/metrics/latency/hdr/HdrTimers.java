package com.ll.metrics.latency.hdr;

import com.ll.metrics.latency.snapshot.LatencySnapshot;
import com.ll.metrics.latency.snapshot.TimerSnapshot;
import com.ll.metrics.latency.timer.Timer;
import com.ll.metrics.latency.timer.Timers;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** HDR Histogram backed timer catalogue that creates timers on first use. */
public final class HdrTimers implements Timers {
  private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();
  private final Supplier<Timer> timerFactory;

  public static HdrTimers create() {
    return new HdrTimers(HdrTimer::new);
  }

  public static HdrTimers createWithThreadsafeTimers() {
    return new HdrTimers(ThreadSafeHdrTimer::new);
  }

  private HdrTimers(Supplier<Timer> timerFactory) {
    this.timerFactory = timerFactory;
  }

  @Override
  public Timer timer(String id) {
    Objects.requireNonNull(id, "id");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    return timers.computeIfAbsent(id, ignored -> timerFactory.get());
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

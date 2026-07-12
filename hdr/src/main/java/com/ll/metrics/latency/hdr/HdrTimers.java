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

/**
 * HDR Histogram backed generated method timer catalogue that creates timers on first bind.
 *
 * <p>This is the production timer factory surface. HDR Histogram remains an implementation
 * detail; public snapshots expose plain nanosecond values and percentiles.
 */
public final class HdrTimers implements Timers {
  private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();
  private final Supplier<Timer> timerFactory;

  /**
   * Creates a timer catalogue backed by single-writer HDR timers.
   *
   * <p>Use this when each generated method timer is recorded by one thread at a time or when the
   * application accepts the lower-overhead non-concurrent implementation.
   *
   * @return HDR-backed timers
   */
  public static HdrTimers create() {
    return new HdrTimers(HdrTimer::new);
  }

  /**
   * Creates a timer catalogue backed by thread-safe HDR timers.
   *
   * @return concurrent HDR-backed timers
   */
  public static HdrTimers createWithThreadsafeTimers() {
    return new HdrTimers(ThreadSafeHdrTimer::new);
  }

  private HdrTimers(Supplier<Timer> timerFactory) {
    this.timerFactory = timerFactory;
  }

  @Override
  public Timer claim(String methodId) {
    Objects.requireNonNull(methodId, "methodId");
    if (methodId.isBlank()) {
      throw new IllegalArgumentException("methodId must not be blank");
    }
    return timers.computeIfAbsent(methodId, ignored -> timerFactory.get());
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

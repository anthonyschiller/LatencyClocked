package com.ll.metrics.latency.timer;

import com.ll.metrics.latency.snapshot.LatencySnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thread-safe in-memory timer implementation backed by summary statistics. */
final class InMemoryTimer implements Timer {
  private final List<Long> durations = new ArrayList<>();

  @Override
  public synchronized void record(long durationNanos) {
    if (durationNanos < 0) {
      throw new IllegalArgumentException("durationNanos must be non-negative");
    }
    durations.add(durationNanos);
  }

  @Override
  public synchronized LatencySnapshot snapshot() {
    if (durations.isEmpty()) {
      return LatencySnapshot.empty();
    }
    List<Long> sorted = new ArrayList<>(durations);
    Collections.sort(sorted);
    long count = sorted.size();
    long min = sorted.getFirst();
    long max = sorted.getLast();
    double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0d);
    return new LatencySnapshot(
        count,
        min,
        max,
        mean,
        percentile(sorted, 50.0d),
        percentile(sorted, 90.0d),
        percentile(sorted, 95.0d),
        percentile(sorted, 99.0d),
        percentile(sorted, 99.9d));
  }

  private static long percentile(List<Long> sorted, double percentile) {
    int index = (int) Math.ceil(percentile / 100.0d * sorted.size()) - 1;
    return sorted.get(clamp(index, 0, sorted.size() - 1));
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}

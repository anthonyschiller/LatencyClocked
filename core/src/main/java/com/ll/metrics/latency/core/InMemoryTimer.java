package com.ll.metrics.latency.core;

import java.util.LongSummaryStatistics;

/** Thread-safe in-memory timer implementation backed by summary statistics. */
public final class InMemoryTimer implements Timer {
  private final LongSummaryStatistics statistics = new LongSummaryStatistics();

  @Override
  public synchronized void record(long durationNanos) {
    if (durationNanos < 0) {
      throw new IllegalArgumentException("durationNanos must be non-negative");
    }
    statistics.accept(durationNanos);
  }

  @Override
  public synchronized LatencySnapshot snapshot() {
    if (statistics.getCount() == 0) {
      return new LatencySnapshot(0, 0, 0, 0.0d);
    }
    return new LatencySnapshot(
        statistics.getCount(), statistics.getMin(), statistics.getMax(), statistics.getAverage());
  }
}

package com.ll.metrics.latency.core;

/** Records latency durations and exposes aggregate snapshots. */
public interface Timer {
  void record(long durationNanos);

  LatencySnapshot snapshot();
}

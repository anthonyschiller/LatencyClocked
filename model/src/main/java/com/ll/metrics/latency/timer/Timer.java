package com.ll.metrics.latency.timer;

import com.ll.metrics.latency.snapshot.LatencySnapshot;

/** Records latency durations and exposes aggregate snapshots. */
public interface Timer {
  void record(long durationNanos);

  LatencySnapshot snapshot();
}

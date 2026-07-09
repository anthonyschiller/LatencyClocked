package com.ll.metrics.latency.snapshot;

/** Immutable summary of the durations recorded by a timer. */
public record LatencySnapshot(
    long count,
    long min,
    long max,
    double mean,
    long p50,
    long p90,
    long p95,
    long p99,
    long p999) {
  public static LatencySnapshot empty() {
    return new LatencySnapshot(0, 0, 0, 0.0d, 0, 0, 0, 0, 0);
  }
}

package com.ll.metrics.latency.snapshot;

/**
 * Immutable point-in-time summary of durations recorded by a timer.
 *
 * <p>All latency values are nanoseconds. This snapshot is detached from the mutable timer state and
 * remains unchanged after subsequent recordings.
 *
 * @param count recorded duration count
 * @param min minimum recorded duration in nanoseconds
 * @param max maximum recorded duration in nanoseconds
 * @param mean mean recorded duration in nanoseconds
 * @param p50 50th percentile duration in nanoseconds
 * @param p90 90th percentile duration in nanoseconds
 * @param p95 95th percentile duration in nanoseconds
 * @param p99 99th percentile duration in nanoseconds
 * @param p999 99.9th percentile duration in nanoseconds
 */
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
  /**
   * Returns an empty nanosecond snapshot.
   *
   * @return empty snapshot
   */
  public static LatencySnapshot empty() {
    return new LatencySnapshot(0, 0, 0, 0.0d, 0, 0, 0, 0, 0);
  }
}

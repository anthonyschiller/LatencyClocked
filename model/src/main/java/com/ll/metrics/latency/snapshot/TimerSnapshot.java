package com.ll.metrics.latency.snapshot;

/**
 * Immutable point-in-time summary of one generated method timer.
 *
 * <p>{@link #id()} is the canonical generated method identity. All latency values are
 * nanoseconds. A snapshot is detached from the underlying mutable timer and will not change after
 * later method executions are recorded.
 *
 * @param id canonical generated method identity
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
public record TimerSnapshot(
    String id,
    long count,
    long min,
    long max,
    double mean,
    long p50,
    long p90,
    long p95,
    long p99,
    long p999) {}

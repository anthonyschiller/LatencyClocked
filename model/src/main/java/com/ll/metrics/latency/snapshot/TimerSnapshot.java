package com.ll.metrics.latency.snapshot;

/** Immutable summary of a named timer. */
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

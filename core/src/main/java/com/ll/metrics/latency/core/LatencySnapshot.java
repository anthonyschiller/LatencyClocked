package com.ll.metrics.latency.core;

/** Immutable summary of the durations recorded by a timer. */
public record LatencySnapshot(long count, long min, long max, double mean) {}

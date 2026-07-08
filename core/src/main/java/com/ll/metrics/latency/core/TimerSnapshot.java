package com.ll.metrics.latency.core;

/** Immutable summary of a named timer. */
public record TimerSnapshot(String id, long count, long min, long max, double mean) {}

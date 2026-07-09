package com.ll.metrics.latency.hdr;

import com.ll.metrics.latency.snapshot.LatencySnapshot;
import com.ll.metrics.latency.timer.Timer;
import org.HdrHistogram.Histogram;

/** HDR Histogram backed timer implementation. */
public final class HdrTimer implements Timer {
  private static final long HIGHEST_TRACKABLE_VALUE_NANOS = 3_600_000_000_000L;
  private static final int SIGNIFICANT_VALUE_DIGITS = 3;

  private final Histogram histogram =
      new Histogram(HIGHEST_TRACKABLE_VALUE_NANOS, SIGNIFICANT_VALUE_DIGITS);

  @Override
  public void record(long durationNanos) {
    if (durationNanos < 0) {
      throw new IllegalArgumentException("durationNanos must be non-negative");
    }
    histogram.recordValue(durationNanos);
  }

  @Override
  public LatencySnapshot snapshot() {
    return HdrSnapshots.snapshot(histogram.copy());
  }
}

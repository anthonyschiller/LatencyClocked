package com.ll.metrics.latency.hdr;

import com.ll.metrics.latency.snapshot.LatencySnapshot;
import org.HdrHistogram.Histogram;

final class HdrSnapshots {
  private HdrSnapshots() {}

  static LatencySnapshot snapshot(Histogram snapshot) {
    if (snapshot.getTotalCount() == 0) {
      return LatencySnapshot.empty();
    }
    return new LatencySnapshot(
        snapshot.getTotalCount(),
        snapshot.getMinValue(),
        snapshot.getMaxValue(),
        snapshot.getMean(),
        snapshot.getValueAtPercentile(50.0d),
        snapshot.getValueAtPercentile(90.0d),
        snapshot.getValueAtPercentile(95.0d),
        snapshot.getValueAtPercentile(99.0d),
        snapshot.getValueAtPercentile(99.9d));
  }
}

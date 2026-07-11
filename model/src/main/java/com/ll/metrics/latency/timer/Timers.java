package com.ll.metrics.latency.timer;

import com.ll.metrics.latency.snapshot.TimerSnapshot;
import java.util.Collection;

/** Owns generated method latency timers and exposes immutable reporting snapshots. */
public interface Timers {
  /**
   * Claims the canonical timer for one generated method identity.
   *
   * <p>This is an instrumentation SPI used by generated {@code __latency_clocked$bind} methods.
   * Application code should not use it for programmatic timing or direct recording.
   */
  Timer claim(String methodId);

  Collection<TimerSnapshot> snapshots();
}

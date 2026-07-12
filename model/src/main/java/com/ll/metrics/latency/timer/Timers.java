package com.ll.metrics.latency.timer;

import com.ll.metrics.latency.snapshot.TimerSnapshot;
import java.util.Collection;

/**
 * Owns generated method latency timers and exposes immutable reporting snapshots.
 *
 * <p>This is an instrumentation SPI used by generated {@code __latency_clocked$bind} methods.
 * Application code should not use it for programmatic timing or direct recording. Mutable
 * {@link Timer} instances are claimed by generated bind methods, not by ordinary application code.
 */
public interface Timers {
  /**
   * Claims the canonical timer for one generated method identity.
   *
   * <p>This is an instrumentation SPI used by generated {@code __latency_clocked$bind} methods.
   * Application code must not use it for unrelated, ad hoc, or programmatic measurements.
   *
   * <p>Implementations must reject null or blank method identities, be thread-safe, return the
   * same timer when the same identity is claimed again, and never allow two different method
   * identities to share one timer.
   *
   * @param methodId deterministic identity generated for one annotated method
   * @return canonical timer owned by that method identity
   */
  Timer claim(String methodId);

  /**
   * Returns immutable point-in-time snapshots for every claimed method timer.
   *
   * <p>The returned collection must not expose mutable timer internals. Snapshot latency values are
   * nanoseconds and remain unchanged after subsequent recordings.
   *
   * @return immutable or defensive-copy collection of timer snapshots
   */
  Collection<TimerSnapshot> snapshots();
}

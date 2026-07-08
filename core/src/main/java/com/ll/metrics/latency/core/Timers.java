package com.ll.metrics.latency.core;

import java.util.Collection;

/** Owns the application's named latency timers. */
public interface Timers {
  Timer timer(String id);

  Collection<TimerSnapshot> snapshots();
}

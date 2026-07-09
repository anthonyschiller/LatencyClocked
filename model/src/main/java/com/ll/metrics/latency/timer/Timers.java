package com.ll.metrics.latency.timer;

import com.ll.metrics.latency.snapshot.TimerSnapshot;
import java.util.Collection;

/** Owns the application's named latency timers. */
public interface Timers {
  Timer timer(String id);

  Collection<TimerSnapshot> snapshots();
}

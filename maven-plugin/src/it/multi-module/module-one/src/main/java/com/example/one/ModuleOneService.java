package com.example.one;

import com.ll.metrics.latency.annotations.Timed;

public final class ModuleOneService {
  @Timed
  public int one() {
    return 1;
  }
}

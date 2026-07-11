package com.example.two;

import com.ll.metrics.latency.annotations.Timed;

public final class ModuleTwoService {
  @Timed("it.module.two")
  public static int two() {
    return 2;
  }
}

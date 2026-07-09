package com.example;

import com.ll.metrics.latency.annotations.Timed;

public final class InvokerTimedSample {
  @Timed("it.sample")
  public void timed() {}
}

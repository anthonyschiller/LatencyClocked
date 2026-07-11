package com.ll.metrics.latency.maven.samples;

import com.ll.metrics.latency.annotations.Timed;

/** Test fixture with timed and untimed methods for descriptor generation tests. */
public final class SampleTimedClass {
  @Timed
  public void timedMethod() {}

  public void normalMethod() {}

  @Timed
  public void overloaded(int value) {}

  @Timed
  public void overloaded(String value) {}

  @Timed
  public void timedMethodWithGeneratedId() {}

  @Timed
  public static void staticTimedMethod() {}
}

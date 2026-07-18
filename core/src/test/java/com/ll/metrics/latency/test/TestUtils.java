package com.ll.metrics.latency.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Shared helpers for LatencyClocked test modules. */
public final class TestUtils {
  private TestUtils() {}

  /** Resets LatencyClocked static lifecycle state through its private test hook. */
  public static void resetLatencyClocked() {
    try {
      Class<?> latencyClocked = Class.forName("com.ll.metrics.latency.core.LatencyClocked");
      Method reset = latencyClocked.getDeclaredMethod("resetForTests");
      reset.setAccessible(true);
      reset.invoke(null);
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Unable to reset LatencyClocked test state", e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException("LatencyClocked test reset failed", e.getCause());
    }
  }
}

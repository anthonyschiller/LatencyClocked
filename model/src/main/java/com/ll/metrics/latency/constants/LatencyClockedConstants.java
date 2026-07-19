package com.ll.metrics.latency.constants;

import com.ll.metrics.latency.timer.Timer;

/** Shared generated names used by runtime startup and build-time instrumentation. */
public final class LatencyClockedConstants {
  /** Path separator used in generated classpath resource names. */
  public static final String RESOURCE_PATH_SEPARATOR = "/";
  /** Separator used in Java class names. */
  public static final String CLASS_NAME_SEPARATOR = ".";
  /** Separator between generated timer class name and method signature. */
  public static final String METHOD_ID_SEPARATOR = "#";
  /** JVM class file extension. */
  public static final String CLASS_FILE_EXTENSION = ".class";
  /** Root directory for the generated instrumented-class index. */
  public static final String INSTRUMENTED_CLASS_INDEX_ROOT = "META-INF";
  /** Directory below {@link #INSTRUMENTED_CLASS_INDEX_ROOT} for generated indexes. */
  public static final String INSTRUMENTED_CLASS_INDEX_DIRECTORY = "latency-clocked";
  /** Generated instrumented-class index file name. */
  public static final String INSTRUMENTED_CLASS_INDEX_FILE = "index";
  /** Complete generated instrumented-class index resource path. */
  public static final String INSTRUMENTED_CLASS_INDEX_RESOURCE =
      INSTRUMENTED_CLASS_INDEX_ROOT
          + RESOURCE_PATH_SEPARATOR
          + INSTRUMENTED_CLASS_INDEX_DIRECTORY
          + RESOURCE_PATH_SEPARATOR
          + INSTRUMENTED_CLASS_INDEX_FILE;
  /** System property used to disable startup binding and generated timing. */
  public static final String ENABLED_PROPERTY = "latency-clocked.enabled";
  /** Stable JMX object name for the global LatencyClocked runtime control. */
  public static final String MBEAN_NAME = "com.ll.metrics.latency:type=LatencyClocked";
  /** Generated bind method name. */
  public static final String BIND_METHOD = "__latency_clocked$bind";
  /** Prefix for generated timer field names. */
  public static final String TIMER_FIELD_PREFIX = "__latency_clocked_timer_";
  /** Name of Timer record method {@link Timer#record(long)}. */
  public static final String TIMER_RECORD_METHOD_NAME = "record";

  private LatencyClockedConstants() {}
}

package com.ll.metrics.latency.constants;

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
  /** Root directory for generated classpath indexes. */
  public static final String DESCRIPTOR_ROOT = "META-INF";
  /** Directory below {@link #DESCRIPTOR_ROOT} for generated indexes. */
  public static final String DESCRIPTOR_DIRECTORY = "latency-clocked";
  /** Generated index file name. */
  public static final String DESCRIPTOR_FILE = "index";
  /** Complete generated classpath index resource path. */
  public static final String DESCRIPTOR_RESOURCE =
      DESCRIPTOR_ROOT
          + RESOURCE_PATH_SEPARATOR
          + DESCRIPTOR_DIRECTORY
          + RESOURCE_PATH_SEPARATOR
          + DESCRIPTOR_FILE;
  /** System property used to disable startup binding and generated timing. */
  public static final String ENABLED_PROPERTY = "latency-clocked.enabled";
  /** Generated bind method name. */
  public static final String BIND_METHOD = "__latency_clocked$bind";
  /** Prefix for generated timer field names. */
  public static final String TIMER_FIELD_PREFIX = "__latency_clocked_timer_";

  private LatencyClockedConstants() {}
}

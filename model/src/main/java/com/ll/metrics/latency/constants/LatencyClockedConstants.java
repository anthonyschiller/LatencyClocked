package com.ll.metrics.latency.constants;

/** Shared generated names used by runtime startup and build-time instrumentation. */
public final class LatencyClockedConstants {
  public static final String RESOURCE_PATH_SEPARATOR = "/";
  public static final String CLASS_NAME_SEPARATOR = ".";
  public static final String METHOD_ID_SEPARATOR = "#";
  public static final String CLASS_FILE_EXTENSION = ".class";
  public static final String DESCRIPTOR_ROOT = "META-INF";
  public static final String DESCRIPTOR_DIRECTORY = "latency-clocked";
  public static final String DESCRIPTOR_FILE = "index";
  public static final String DESCRIPTOR_RESOURCE =
      DESCRIPTOR_ROOT
          + RESOURCE_PATH_SEPARATOR
          + DESCRIPTOR_DIRECTORY
          + RESOURCE_PATH_SEPARATOR
          + DESCRIPTOR_FILE;
  public static final String ENABLED_PROPERTY = "latency-clocked.enabled";
  public static final String BIND_METHOD = "__latency_clocked$bind";
  public static final String TIMER_FIELD_PREFIX = "__latency_clocked_timer_";

  private LatencyClockedConstants() {}
}

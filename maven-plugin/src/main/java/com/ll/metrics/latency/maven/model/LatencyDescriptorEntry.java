package com.ll.metrics.latency.maven.model;

import java.util.Objects;

/** Timer metadata for one timed method discovered during scanning. */
public record LatencyDescriptorEntry(
    String className,
    String methodName,
    String methodDescriptor,
    String fieldName,
    String timerId) {
  /** Creates a descriptor entry. */
  public LatencyDescriptorEntry {
    Objects.requireNonNull(className, "className");
    Objects.requireNonNull(methodName, "methodName");
    Objects.requireNonNull(methodDescriptor, "methodDescriptor");
    Objects.requireNonNull(fieldName, "fieldName");
    Objects.requireNonNull(timerId, "timerId");
  }
}

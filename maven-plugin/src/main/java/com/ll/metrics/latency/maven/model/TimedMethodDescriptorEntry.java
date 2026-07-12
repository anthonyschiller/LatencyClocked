package com.ll.metrics.latency.maven.model;

import java.util.Objects;

/** Timer metadata for one timed method discovered during scanning. */
public record TimedMethodDescriptorEntry(
    String className,
    String methodName,
    String methodDescriptor,
    String fieldName,
    String timerId) {
  /** Creates a timed method descriptor entry. */
  public TimedMethodDescriptorEntry {
    Objects.requireNonNull(className, "className");
    Objects.requireNonNull(methodName, "methodName");
    Objects.requireNonNull(methodDescriptor, "methodDescriptor");
    Objects.requireNonNull(fieldName, "fieldName");
    Objects.requireNonNull(timerId, "timerId");
  }
}

package com.ll.metrics.latency.maven.model;

import java.util.Objects;

/** Resolved metadata for one timed method. */
public record TimedMethodMetadata(
    String methodName,
    String methodDescriptor,
    boolean isStatic,
    String generatedTimerFieldName,
    String resolvedTimerId) {
  /** Creates resolved timed method metadata. */
  public TimedMethodMetadata {
    Objects.requireNonNull(methodName, "methodName");
    Objects.requireNonNull(methodDescriptor, "methodDescriptor");
    Objects.requireNonNull(generatedTimerFieldName, "generatedTimerFieldName");
    Objects.requireNonNull(resolvedTimerId, "resolvedTimerId");
  }
}

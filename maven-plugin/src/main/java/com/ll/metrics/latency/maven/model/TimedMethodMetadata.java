package com.ll.metrics.latency.maven.model;

import java.util.Objects;

/** Resolved metadata for one timed method. */
public record TimedMethodMetadata(
    String methodName,
    String methodDescriptor,
    boolean isStatic,
    String explicitTimerId,
    String generatedFieldName,
    String resolvedTimerId) {
  /** Creates resolved timed method metadata. */
  public TimedMethodMetadata {
    Objects.requireNonNull(methodName, "methodName");
    Objects.requireNonNull(methodDescriptor, "methodDescriptor");
    explicitTimerId = explicitTimerId == null ? "" : explicitTimerId;
    Objects.requireNonNull(generatedFieldName, "generatedFieldName");
    Objects.requireNonNull(resolvedTimerId, "resolvedTimerId");
  }
}

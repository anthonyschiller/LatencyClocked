package com.ll.metrics.latency.maven.model;

import java.util.Objects;

/** Raw timed method data collected directly from bytecode. */
public record TimedMethodCandidate(String methodName, String methodDescriptor, boolean isStatic) {
  /** Creates a timed method candidate. */
  public TimedMethodCandidate {
    Objects.requireNonNull(methodName, "methodName");
    Objects.requireNonNull(methodDescriptor, "methodDescriptor");
  }
}

package com.ll.metrics.latency.maven.model;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Metadata discovered for one class containing zero or more timed methods. */
public record TimedClassMetadata(String className, Collection<TimedMethodMetadata> timedMethods) {
  public TimedClassMetadata {
    Objects.requireNonNull(className, "className");
    timedMethods = List.copyOf(timedMethods);
  }
}

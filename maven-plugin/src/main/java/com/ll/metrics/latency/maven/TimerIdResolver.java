package com.ll.metrics.latency.maven;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.model.TimedMethodCandidate;
import com.ll.metrics.latency.maven.model.TimedMethodMetadata;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Resolves generated method timer ids and generated field names for timed method candidates. */
public final class TimerIdResolver {
  /** Resolves timed method metadata for one class. */
  public List<TimedMethodMetadata> resolve(
      String className, Collection<TimedMethodCandidate> candidates) {
    Objects.requireNonNull(className, "className");
    Objects.requireNonNull(candidates, "candidates");

    List<TimedMethodMetadata> metadata = new ArrayList<>(candidates.size());
    int fieldIndex = 0;
    for (TimedMethodCandidate candidate : candidates) {
      metadata.add(
          new TimedMethodMetadata(
              candidate.methodName(),
              candidate.methodDescriptor(),
              candidate.isStatic(),
              LatencyClockedConstants.TIMER_FIELD_PREFIX + fieldIndex++,
              methodTimerId(className, candidate)));
    }
    return metadata;
  }

  private static String methodTimerId(String className, TimedMethodCandidate candidate) {
    return className
        + LatencyClockedConstants.METHOD_ID_SEPARATOR
        + candidate.methodName()
        + candidate.methodDescriptor();
  }
}

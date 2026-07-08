package com.ll.metrics.latency.maven;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.model.TimedMethodCandidate;
import com.ll.metrics.latency.maven.model.TimedMethodMetadata;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.Type;

/** Resolves timer ids and generated field names for timed method candidates. */
public final class TimerIdResolver {
  /** Resolves timed method metadata for one class. */
  public List<TimedMethodMetadata> resolve(
      String className, Collection<TimedMethodCandidate> candidates) {
    Objects.requireNonNull(className, "className");
    Objects.requireNonNull(candidates, "candidates");

    Set<TimedMethodCandidate> overloadedMethods = overloadedMethodsIn(candidates);
    List<TimedMethodMetadata> metadata = new ArrayList<>(candidates.size());
    int fieldIndex = 0;
    for (TimedMethodCandidate candidate : candidates) {
      metadata.add(
          new TimedMethodMetadata(
              candidate.methodName(),
              candidate.methodDescriptor(),
              candidate.isStatic(),
              candidate.explicitTimerId(),
              LatencyClockedConstants.TIMER_FIELD_PREFIX + fieldIndex++,
              timerId(className, candidate, overloadedMethods)));
    }
    return metadata;
  }

  private static String timerId(
      String className,
      TimedMethodCandidate candidate,
      Set<TimedMethodCandidate> overloadedMethods) {
    if (!candidate.explicitTimerId().isBlank()) {
      return candidate.explicitTimerId();
    }
    String prefix =
        className + LatencyClockedConstants.CLASS_NAME_SEPARATOR + candidate.methodName();
    if (overloadedMethods.contains(candidate)) {
      return prefix
          + LatencyClockedConstants.CLASS_NAME_SEPARATOR
          + parameterKey(candidate.methodDescriptor());
    }
    return prefix;
  }

  private static Set<TimedMethodCandidate> overloadedMethodsIn(
      Collection<TimedMethodCandidate> candidates) {
    Map<String, TimedMethodCandidate> workingSet = new LinkedHashMap<>();
    Set<TimedMethodCandidate> overloadedMethods = new LinkedHashSet<>();
    for (TimedMethodCandidate candidate : candidates) {
      TimedMethodCandidate previous = workingSet.putIfAbsent(candidate.methodName(), candidate);
      if (previous != null) {
        overloadedMethods.add(previous);
        overloadedMethods.add(candidate);
      }
    }
    return overloadedMethods;
  }

  private static String parameterKey(String methodDescriptor) {
    Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
    List<String> names = new ArrayList<>(argumentTypes.length);
    for (Type argumentType : argumentTypes) {
      names.add(javaTypeName(argumentType));
    }
    return String.join(LatencyClockedConstants.CLASS_NAME_SEPARATOR, names);
  }

  private static String javaTypeName(Type type) {
    return switch (type.getSort()) {
      case Type.BOOLEAN -> "boolean";
      case Type.CHAR -> "char";
      case Type.BYTE -> "byte";
      case Type.SHORT -> "short";
      case Type.INT -> "int";
      case Type.FLOAT -> "float";
      case Type.LONG -> "long";
      case Type.DOUBLE -> "double";
      case Type.ARRAY -> javaTypeName(type.getElementType()) + "[]".repeat(type.getDimensions());
      case Type.OBJECT -> type.getClassName();
      default -> type.getClassName();
    };
  }
}

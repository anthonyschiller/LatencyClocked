package com.ll.metrics.latency.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ll.metrics.latency.maven.model.TimedMethodCandidate;
import com.ll.metrics.latency.maven.model.TimedMethodMetadata;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TimerIdResolverTest {
  private final TimerIdResolver resolver = new TimerIdResolver();

  @Test
  void defaultTimerIdUsesClassAndMethodName() {
    List<TimedMethodMetadata> methods =
        resolver.resolve(
            "com.example.Service", List.of(new TimedMethodCandidate("call", "()V", false)));

    assertEquals("com.example.Service#call()V", methods.getFirst().resolvedTimerId());
  }

  @Test
  void overloadedMethodsIncludeParameterTypes() {
    List<TimedMethodMetadata> methods =
        resolver.resolve(
            "com.example.Service",
            List.of(
                new TimedMethodCandidate("call", "(I)V", false),
                new TimedMethodCandidate("call", "(Ljava/lang/String;)V", false)));

    Map<String, String> idsByDescriptor =
        methods.stream()
            .collect(
                Collectors.toMap(
                    TimedMethodMetadata::methodDescriptor, TimedMethodMetadata::resolvedTimerId));

    assertEquals("com.example.Service#call(I)V", idsByDescriptor.get("(I)V"));
    assertEquals(
        "com.example.Service#call(Ljava/lang/String;)V",
        idsByDescriptor.get("(Ljava/lang/String;)V"));
  }

  @Test
  void descriptorBackedMethodIdsAvoidDotDelimitedParameterCollisions() {
    TimedMethodCandidate twoParameters =
        new TimedMethodCandidate("call", "(Lcollision/a;Lcollision/b;)V", false);
    TimedMethodCandidate oneParameter =
        new TimedMethodCandidate("call", "(Lcollision/a/collision/b;)V", false);

    List<TimedMethodMetadata> methods =
        resolver.resolve("com.example.Service", List.of(twoParameters, oneParameter));
    Map<String, String> idsByDescriptor =
        methods.stream()
            .collect(
                Collectors.toMap(
                    TimedMethodMetadata::methodDescriptor, TimedMethodMetadata::resolvedTimerId));

    assertEquals(
        oldDotDelimitedId("com.example.Service", twoParameters),
        oldDotDelimitedId("com.example.Service", oneParameter));
    assertEquals(
        "com.example.Service#call(Lcollision/a;Lcollision/b;)V",
        idsByDescriptor.get("(Lcollision/a;Lcollision/b;)V"));
    assertEquals(
        "com.example.Service#call(Lcollision/a/collision/b;)V",
        idsByDescriptor.get("(Lcollision/a/collision/b;)V"));
  }

  @Test
  void generatedTimerFieldNamesAreAllocatedInMethodOrder() {
    List<TimedMethodMetadata> methods =
        resolver.resolve(
            "com.example.Service",
            List.of(
                new TimedMethodCandidate("first", "()V", false),
                new TimedMethodCandidate("second", "()V", false)));

    assertEquals("__latency_clocked_timer_0", methods.get(0).generatedTimerFieldName());
    assertEquals("__latency_clocked_timer_1", methods.get(1).generatedTimerFieldName());
  }

  private static String oldDotDelimitedId(String className, TimedMethodCandidate candidate) {
    return className + "." + candidate.methodName() + "." + oldParameterKey(candidate);
  }

  private static String oldParameterKey(TimedMethodCandidate candidate) {
    return switch (candidate.methodDescriptor()) {
      case "(Lcollision/a;Lcollision/b;)V" -> "collision.a.collision.b";
      case "(Lcollision/a/collision/b;)V" -> "collision.a.collision.b";
      default -> throw new IllegalArgumentException(candidate.methodDescriptor());
    };
  }
}

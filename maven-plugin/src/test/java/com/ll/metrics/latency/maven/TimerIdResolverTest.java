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
  void explicitTimerIdWins() {
    List<TimedMethodMetadata> methods =
        resolver.resolve(
            "com.example.Service",
            List.of(new TimedMethodCandidate("call", "()V", false, "custom.id")));

    assertEquals("custom.id", methods.getFirst().resolvedTimerId());
  }

  @Test
  void defaultTimerIdUsesClassAndMethodName() {
    List<TimedMethodMetadata> methods =
        resolver.resolve(
            "com.example.Service", List.of(new TimedMethodCandidate("call", "()V", false, "")));

    assertEquals("com.example.Service.call", methods.getFirst().resolvedTimerId());
  }

  @Test
  void overloadedMethodsIncludeParameterTypes() {
    List<TimedMethodMetadata> methods =
        resolver.resolve(
            "com.example.Service",
            List.of(
                new TimedMethodCandidate("call", "(I)V", false, ""),
                new TimedMethodCandidate("call", "(Ljava/lang/String;)V", false, "")));

    Map<String, String> idsByDescriptor =
        methods.stream()
            .collect(
                Collectors.toMap(
                    TimedMethodMetadata::methodDescriptor, TimedMethodMetadata::resolvedTimerId));

    assertEquals("com.example.Service.call.int", idsByDescriptor.get("(I)V"));
    assertEquals(
        "com.example.Service.call.java.lang.String", idsByDescriptor.get("(Ljava/lang/String;)V"));
  }

  @Test
  void generatedFieldNamesAreAllocatedInMethodOrder() {
    List<TimedMethodMetadata> methods =
        resolver.resolve(
            "com.example.Service",
            List.of(
                new TimedMethodCandidate("first", "()V", false, ""),
                new TimedMethodCandidate("second", "()V", false, "")));

    assertEquals("__latency_clocked_timer_0", methods.get(0).generatedFieldName());
    assertEquals("__latency_clocked_timer_1", methods.get(1).generatedFieldName());
  }
}

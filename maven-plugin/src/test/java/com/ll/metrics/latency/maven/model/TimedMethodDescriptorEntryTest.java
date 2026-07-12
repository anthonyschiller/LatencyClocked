package com.ll.metrics.latency.maven.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TimedMethodDescriptorEntryTest {
  @Test
  void rejectsNullClassName() {
    assertThrows(
        NullPointerException.class,
        () ->
            new TimedMethodDescriptorEntry(
                null, "call", "()V", "__latency_clocked_timer_0", "id"));
  }

  @Test
  void rejectsNullMethodName() {
    assertThrows(
        NullPointerException.class,
        () ->
            new TimedMethodDescriptorEntry(
                "com.example.Service", null, "()V", "__latency_clocked_timer_0", "id"));
  }

  @Test
  void rejectsNullMethodDescriptor() {
    assertThrows(
        NullPointerException.class,
        () ->
            new TimedMethodDescriptorEntry(
                "com.example.Service", "call", null, "__latency_clocked_timer_0", "id"));
  }

  @Test
  void rejectsNullFieldName() {
    assertThrows(
        NullPointerException.class,
        () -> new TimedMethodDescriptorEntry("com.example.Service", "call", "()V", null, "id"));
  }

  @Test
  void rejectsNullTimerId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new TimedMethodDescriptorEntry(
                "com.example.Service", "call", "()V", "__latency_clocked_timer_0", null));
  }
}

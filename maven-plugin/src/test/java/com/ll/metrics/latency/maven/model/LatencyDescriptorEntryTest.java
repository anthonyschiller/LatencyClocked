package com.ll.metrics.latency.maven.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LatencyDescriptorEntryTest {
  @Test
  void rejectsNullClassName() {
    assertThrows(
        NullPointerException.class,
        () -> new LatencyDescriptorEntry(null, "call", "()V", "__latency_clocked_timer_0", "id"));
  }

  @Test
  void rejectsNullMethodName() {
    assertThrows(
        NullPointerException.class,
        () ->
            new LatencyDescriptorEntry(
                "com.example.Service", null, "()V", "__latency_clocked_timer_0", "id"));
  }

  @Test
  void rejectsNullMethodDescriptor() {
    assertThrows(
        NullPointerException.class,
        () ->
            new LatencyDescriptorEntry(
                "com.example.Service", "call", null, "__latency_clocked_timer_0", "id"));
  }

  @Test
  void rejectsNullFieldName() {
    assertThrows(
        NullPointerException.class,
        () -> new LatencyDescriptorEntry("com.example.Service", "call", "()V", null, "id"));
  }

  @Test
  void rejectsNullTimerId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new LatencyDescriptorEntry(
                "com.example.Service", "call", "()V", "__latency_clocked_timer_0", null));
  }
}

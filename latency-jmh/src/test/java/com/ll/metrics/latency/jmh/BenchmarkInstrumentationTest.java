package com.ll.metrics.latency.jmh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.core.LatencyClocked;
import com.ll.metrics.latency.timer.InMemoryTimers;
import com.ll.metrics.latency.timer.Timer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BenchmarkInstrumentationTest {
  private final List<io.micrometer.core.instrument.MeterRegistry> previousRegistries =
      new ArrayList<>();
  private SimpleMeterRegistry micrometerRegistry;

  @BeforeEach
  void setUp() {
    previousRegistries.addAll(Metrics.globalRegistry.getRegistries());
    previousRegistries.forEach(Metrics::removeRegistry);
    Metrics.globalRegistry.clear();
    micrometerRegistry = new SimpleMeterRegistry();
    Metrics.addRegistry(micrometerRegistry);
  }

  @AfterEach
  void tearDown() {
    Metrics.removeRegistry(micrometerRegistry);
    micrometerRegistry.close();
    Metrics.globalRegistry.clear();
    previousRegistries.forEach(Metrics::addRegistry);
    previousRegistries.clear();
  }

  @Test
  void micrometerTimedFixtureIsAspectjWoven() throws IOException {
    byte[] classBytes = fixtureClassBytes();

    String classFile = new String(classBytes, StandardCharsets.ISO_8859_1);

    assertTrue(
        classFile.contains("TimedAspect") && classFile.contains("aspectOf"),
        "Micrometer benchmark fixture must contain compile-time woven TimedAspect calls");
  }

  @Test
  void micrometerTimedFixturesIncrementsExpectedTimers() {
    LatencyClockedBenchmark.MicrometerTarget target =
        new LatencyClockedBenchmark.MicrometerTarget();

    target.voidCall();
    target.primitiveReturn();
    target.objectReturn();

    assertMicrometerTimerCount("benchmark.micrometer.void", 1L);
    assertMicrometerTimerCount("benchmark.micrometer.primitive", 1L);
    assertMicrometerTimerCount("benchmark.micrometer.object", 1L);
  }

  @Test
  void micrometerTimedStaticFixtureHasBeenCompiledInstrumentedWithTimingCapture() {
    LatencyClockedBenchmark.MicrometerTarget.staticCall();

    assertMicrometerTimerCount("benchmark.micrometer.static", 1L);
  }

  private void assertMicrometerTimerCount(String timerName, long expectedCount) {
    io.micrometer.core.instrument.Timer timer =
        micrometerRegistry.find(timerName).timer();
    assertNotNull(timer, "Expected Micrometer timer was not registered: " + timerName);
    assertEquals(expectedCount, timer.count());
  }

  @Test
  void latencyClockedRecordsTimingCapture() {
    InMemoryTimers timers = InMemoryTimers.create();
    LatencyClocked.initialise(timers);
    LatencyClockedBenchmark.LatencyClockedTarget target =
        new LatencyClockedBenchmark.LatencyClockedTarget();

    target.voidCall();

    assertEquals(1L, timers.timer("benchmark.latency-clocked.void").snapshot().count());
  }

  @Test
  void whenLatencyClockedDisabledThenTimingCaptureIsNoOp() throws Exception {
    clearGeneratedTimerFields(LatencyClockedBenchmark.LatencyClockedDisabledTarget.class);
    String previous = System.getProperty(LatencyClockedConstants.ENABLED_PROPERTY);
    InMemoryTimers timers = InMemoryTimers.create();
    try {
      System.setProperty(LatencyClockedConstants.ENABLED_PROPERTY, "false");
      LatencyClocked.initialise(timers);
      LatencyClockedBenchmark.LatencyClockedDisabledTarget target =
          new LatencyClockedBenchmark.LatencyClockedDisabledTarget();

      target.voidCall();
      target.primitiveReturn();
      target.objectReturn();
      LatencyClockedBenchmark.LatencyClockedDisabledTarget.staticCall();

      assertTrue(timers.snapshots().isEmpty());
      assertGeneratedTimerFieldsAreNull(LatencyClockedBenchmark.LatencyClockedDisabledTarget.class);
    } finally {
      restoreEnabledProperty(previous);
      clearGeneratedTimerFields(LatencyClockedBenchmark.LatencyClockedDisabledTarget.class);
    }
  }

  private static byte[] fixtureClassBytes() throws IOException {
    String resource = "LatencyClockedBenchmark$MicrometerTarget.class";
    try (InputStream input =
        LatencyClockedBenchmark.MicrometerTarget.class.getResourceAsStream(resource)) {
      assertNotNull(input, "Unable to load Micrometer fixture class bytes");
      return input.readAllBytes();
    }
  }

  private static void clearGeneratedTimerFields(Class<?> targetClass) throws Exception {
    for (Field field : targetClass.getDeclaredFields()) {
      if (field.getName().startsWith(LatencyClockedConstants.TIMER_FIELD_PREFIX)
          && Timer.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        field.set(null, null);
      }
    }
  }

  private static void assertGeneratedTimerFieldsAreNull(Class<?> targetClass) throws Exception {
    for (Field field : targetClass.getDeclaredFields()) {
      if (field.getName().startsWith(LatencyClockedConstants.TIMER_FIELD_PREFIX)
          && Timer.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        assertNull(field.get(null), "Expected disabled timer field to remain null: " + field);
      }
    }
  }

  private static void restoreEnabledProperty(String previous) {
    if (previous == null) {
      System.clearProperty(LatencyClockedConstants.ENABLED_PROPERTY);
    } else {
      System.setProperty(LatencyClockedConstants.ENABLED_PROPERTY, previous);
    }
    LatencyClocked.initialise(InMemoryTimers.create());
  }
}

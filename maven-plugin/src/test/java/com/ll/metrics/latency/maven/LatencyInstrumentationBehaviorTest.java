package com.ll.metrics.latency.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.core.LatencyClocked;
import com.ll.metrics.latency.maven.model.LatencyDescriptorEntry;
import com.ll.metrics.latency.snapshot.LatencySnapshot;
import com.ll.metrics.latency.timer.InMemoryTimers;
import com.ll.metrics.latency.timer.Timers;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LatencyInstrumentationBehaviorTest {
  private static final String SAMPLE_CLASS_NAME = "golden.GoldenTimedSamples";

  @Test
  void successfulVoidMethodRecordsExactlyOnce(@TempDir Path outputDirectory) throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      invoke(instance, "successfulVoid");

      assertEquals(1, snapshot(fixture.timers(), "successfulVoid").count());
      assertTrue(snapshot(fixture.timers(), "successfulVoid").min() >= 0);
      assertEquals(1, fieldValue(instance, "sideEffect"));
    }
  }

  @Test
  void primitiveReturnRecordsOnceAndPreservesReturnValue(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      Object result = invoke(instance, "primitiveReturn", int.class, 41);

      assertEquals(42, result);
      assertEquals(1, snapshot(fixture.timers(), "primitiveReturn").count());
    }
  }

  @Test
  void objectReturnRecordsOnceAndPreservesReturnValue(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      Object result = invoke(instance, "objectReturn", String.class, "value");

      assertEquals("object:value", result);
      assertEquals(1, snapshot(fixture.timers(), "objectReturn").count());
    }
  }

  @Test
  void staticTimedMethodRecordsOnce(@TempDir Path outputDirectory) throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {

      Object result = invokeStatic(fixture.sampleClass(), "staticPrimitive", int.class, 21);

      assertEquals(42, result);
      assertEquals(1, snapshot(fixture.timers(), "staticPrimitive").count());
    }
  }

  @Test
  void multipleReturnPathsRecordOncePerSuccessfulExit(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      assertEquals(-1, invoke(instance, "multipleReturnPaths", int.class, -5));
      assertEquals(0, invoke(instance, "multipleReturnPaths", int.class, 0));
      assertEquals(1, invoke(instance, "multipleReturnPaths", int.class, 5));

      assertEquals(3, snapshot(fixture.timers(), "multipleReturnPaths").count());
    }
  }

  @Test
  void nestedBranchingRecordsOncePerSuccessfulPath(@TempDir Path outputDirectory) throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      assertEquals(-100, invoke(instance, "nestedBranching", int.class, -20));
      assertEquals(-10, invoke(instance, "nestedBranching", int.class, -5));
      assertEquals(0, invoke(instance, "nestedBranching", int.class, 0));
      assertEquals(10, invoke(instance, "nestedBranching", int.class, 5));

      assertEquals(4, snapshot(fixture.timers(), "nestedBranching").count());
      assertEquals(113, fieldValue(instance, "sideEffect"));
    }
  }

  @Test
  void switchStatementRecordsOnceAndPreservesReturnValue(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      assertEquals("zero", invoke(instance, "switchStatement", int.class, 0));
      assertEquals("small", invoke(instance, "switchStatement", int.class, 2));
      assertEquals("large", invoke(instance, "switchStatement", int.class, 10));

      assertEquals(3, snapshot(fixture.timers(), "switchStatement").count());
    }
  }

  @Test
  void loopWithEarlyReturnRecordsOncePerSuccessfulPath(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      assertEquals(-1, invoke(instance, "loopWithEarlyReturn", int.class, 2));
      assertEquals(2, invoke(instance, "loopWithEarlyReturn", int.class, 5));

      assertEquals(2, snapshot(fixture.timers(), "loopWithEarlyReturn").count());
      assertEquals(5, fieldValue(instance, "sideEffect"));
    }
  }

  @Test
  void tryCatchReturningFromCatchRecordsSuccessfulCatchReturn(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      assertEquals(10, invoke(instance, "tryCatchReturningFromCatch", boolean.class, false));
      assertEquals(20, invoke(instance, "tryCatchReturningFromCatch", boolean.class, true));

      assertEquals(2, snapshot(fixture.timers(), "tryCatchReturningFromCatch").count());
      assertEquals(3, fieldValue(instance, "sideEffect"));
    }
  }

  @Test
  void tryFinallyReturningNormallyRecordsAfterFinallySideEffects(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      assertEquals(42, invoke(instance, "tryFinallyReturningNormally", int.class, 41));

      assertEquals(1, snapshot(fixture.timers(), "tryFinallyReturningNormally").count());
      assertEquals(4, fieldValue(instance, "sideEffect"));
    }
  }

  @Test
  void throwingMethodDoesNotRecordLatency(@TempDir Path outputDirectory) throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      InvocationTargetException exception =
          assertThrows(InvocationTargetException.class, () -> invoke(instance, "throwingMethod"));

      assertTrue(exception.getCause() instanceof IllegalStateException);
      assertEquals(0, snapshot(fixture.timers(), "throwingMethod").count());
    }
  }

  @Test
  void exceptionsBeforeReturnAndInsideCatchDoNotRecordLatency(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      InvocationTargetException beforeReturn =
          assertThrows(
              InvocationTargetException.class,
              () -> invoke(instance, "exceptionThrownBeforeReturn"));
      InvocationTargetException insideCatch =
          assertThrows(
              InvocationTargetException.class,
              () -> invoke(instance, "exceptionThrownInsideCatch"));

      assertTrue(beforeReturn.getCause() instanceof IllegalStateException);
      assertEquals("before return", beforeReturn.getCause().getMessage());
      assertTrue(insideCatch.getCause() instanceof IllegalStateException);
      assertTrue(insideCatch.getCause().getCause() instanceof IllegalArgumentException);
      assertEquals(0, snapshot(fixture.timers(), "exceptionThrownBeforeReturn").count());
      assertEquals(0, snapshot(fixture.timers(), "exceptionThrownInsideCatch").count());
      assertEquals(11, fieldValue(instance, "sideEffect"));
    }
  }

  @Test
  void privateFinalAndSynchronizedTimedMethodsRecord(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      assertEquals(12, invoke(instance, "callPrivateTimedMethod", int.class, 5));
      assertEquals(13, invoke(instance, "finalTimedMethod", int.class, 5));
      assertEquals(14, invoke(instance, "synchronizedTimedMethod", int.class, 5));

      assertEquals(1, snapshot(fixture.timers(), "privateTimedMethod").count());
      assertEquals(1, snapshot(fixture.timers(), "finalTimedMethod").count());
      assertEquals(1, snapshot(fixture.timers(), "synchronizedTimedMethod").count());
      assertEquals(16, fieldValue(instance, "sideEffect"));
    }
  }

  @Test
  void nonAnnotatedMethodIsUnchangedAndUnregistered(@TempDir Path outputDirectory)
      throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      invoke(instance, "normalMethod");

      assertEquals(10, fieldValue(instance, "sideEffect"));
      assertTrue(
          fixture.timers().snapshots().stream()
              .noneMatch(snapshot -> snapshot.id().equals(SAMPLE_CLASS_NAME + ".normalMethod")));
    }
  }

  @Test
  void overloadedTimedMethodsRecordSeparateTimers(@TempDir Path outputDirectory) throws Exception {
    try (RuntimeFixture fixture = instrumentAndLoad(outputDirectory)) {
      Object instance = fixture.newInstance();

      assertEquals(15, invoke(instance, "overloaded", int.class, 5));
      assertEquals("overloaded:value", invoke(instance, "overloaded", String.class, "value"));

      assertEquals(1, snapshot(fixture.timers(), "overloaded.int").count());
      assertEquals(1, snapshot(fixture.timers(), "overloaded.java.lang.String").count());
    }
  }

  @Test
  void runningPluginTwiceDoesNotDoubleInstrument(@TempDir Path outputDirectory) throws Exception {
    compileGolden(outputDirectory);
    InstrumentationResult first = instrument(outputDirectory);
    InstrumentationResult second = instrument(outputDirectory);

    assertEquals(18, first.injectionResult().instrumentedMethods());
    assertEquals(0, first.injectionResult().skippedInstrumentedMethods());
    assertEquals(0, second.injectionResult().instrumentedMethods());
    assertEquals(18, second.injectionResult().skippedInstrumentedMethods());

    try (RuntimeFixture fixture = load(outputDirectory)) {
      Object instance = fixture.newInstance();

      invoke(instance, "successfulVoid");

      assertEquals(1, snapshot(fixture.timers(), "successfulVoid").count());
    }
  }

  @Test
  void disabledLatencyClockedSkipsInstrumentedCodeAndTimeCapture(@TempDir Path outputDirectory)
      throws Exception {
    compileGolden(outputDirectory);
    instrument(outputDirectory);
    String previousEnabledProperty = System.getProperty(LatencyClockedConstants.ENABLED_PROPERTY);
    try (URLClassLoader classLoader =
        new URLClassLoader(
            new URL[] {outputDirectory.toUri().toURL()},
            LatencyInstrumentationBehaviorTest.class.getClassLoader())) {
      Thread currentThread = Thread.currentThread();
      ClassLoader previousClassLoader = currentThread.getContextClassLoader();
      currentThread.setContextClassLoader(classLoader);
      try {
        System.setProperty(LatencyClockedConstants.ENABLED_PROPERTY, "false");
        Timers timers = InMemoryTimers.create();
        LatencyClocked.initialise(timers);
        Class<?> sampleClass = Class.forName(SAMPLE_CLASS_NAME, true, classLoader);
        Object instance = sampleClass.getDeclaredConstructor().newInstance();

        invoke(instance, "successfulVoid");

        assertTrue(timers.snapshots().isEmpty());
      } finally {
        restoreEnabledProperty(previousEnabledProperty);
        currentThread.setContextClassLoader(previousClassLoader);
      }
    }
  }

  private static RuntimeFixture instrumentAndLoad(Path outputDirectory) throws Exception {
    compileGolden(outputDirectory);
    instrument(outputDirectory);
    return load(outputDirectory);
  }

  private static InstrumentationResult instrument(Path outputDirectory) throws IOException {
    Map<Path, List<LatencyDescriptorEntry>> entries =
        LatencyDescriptorGenerator.scan(outputDirectory);
    LatencyDescriptorGenerator.InjectionResult injectionResult =
        LatencyDescriptorGenerator.injectTimerFields(entries);
    LatencyDescriptorGenerator.writeDescriptor(
        outputDirectory, entries.values().stream().flatMap(List::stream).toList());
    return new InstrumentationResult(entries, injectionResult);
  }

  private static RuntimeFixture load(Path outputDirectory) throws Exception {
    URLClassLoader classLoader =
        new URLClassLoader(
            new URL[] {outputDirectory.toUri().toURL()},
            LatencyInstrumentationBehaviorTest.class.getClassLoader());
    Thread currentThread = Thread.currentThread();
    ClassLoader previousClassLoader = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(classLoader);
    try {
      Timers timers = InMemoryTimers.create();
      LatencyClocked latencyClocked = LatencyClocked.initialise(timers);
      Class<?> sampleClass = Class.forName(SAMPLE_CLASS_NAME, true, classLoader);
      return new RuntimeFixture(classLoader, sampleClass, timers, latencyClocked);
    } finally {
      currentThread.setContextClassLoader(previousClassLoader);
    }
  }

  private static void compileGolden(Path outputDirectory) throws IOException {
    GoldenFixtureCompiler.compile(outputDirectory, "GoldenTimedSamples.java");
  }

  private static Object invoke(Object target, String methodName)
      throws ReflectiveOperationException {
    Method method = target.getClass().getDeclaredMethod(methodName);
    return method.invoke(target);
  }

  private static Object invoke(
      Object target, String methodName, Class<?> parameterType, Object argument)
      throws ReflectiveOperationException {
    Method method = target.getClass().getDeclaredMethod(methodName, parameterType);
    return method.invoke(target, argument);
  }

  private static Object invokeStatic(
      Class<?> target, String methodName, Class<?> parameterType, Object argument)
      throws ReflectiveOperationException {
    Method method = target.getDeclaredMethod(methodName, parameterType);
    return method.invoke(null, argument);
  }

  private static Object fieldValue(Object target, String fieldName)
      throws ReflectiveOperationException {
    java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  private static LatencySnapshot snapshot(Timers timers, String timerName) {
    return timers
        .timer(SAMPLE_CLASS_NAME + LatencyClockedConstants.CLASS_NAME_SEPARATOR + timerName)
        .snapshot();
  }

  private static void restoreEnabledProperty(String previousEnabledProperty) {
    if (previousEnabledProperty == null) {
      System.clearProperty(LatencyClockedConstants.ENABLED_PROPERTY);
    } else {
      System.setProperty(
          LatencyClockedConstants.ENABLED_PROPERTY, previousEnabledProperty);
    }
    LatencyClocked.initialise(InMemoryTimers.create());
  }

  private record InstrumentationResult(
      Map<Path, List<LatencyDescriptorEntry>> entries,
      LatencyDescriptorGenerator.InjectionResult injectionResult) {}

  private record RuntimeFixture(
      URLClassLoader classLoader,
      Class<?> sampleClass,
      Timers timers,
      LatencyClocked latencyClocked)
      implements AutoCloseable {
    private Object newInstance() throws ReflectiveOperationException {
      Constructor<?> constructor = sampleClass.getDeclaredConstructor();
      return constructor.newInstance();
    }

    @Override
    public void close() throws IOException {
      classLoader.close();
    }
  }
}

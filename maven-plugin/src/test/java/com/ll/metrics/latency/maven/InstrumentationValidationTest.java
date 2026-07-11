package com.ll.metrics.latency.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.maven.model.LatencyDescriptorEntry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstrumentationValidationTest {
  @Test
  void scanRejectsAbstractTimedMethods(@TempDir Path outputDirectory) throws Exception {
    GoldenFixtureCompiler.compile(outputDirectory, "UnsupportedAbstractTimedSample.java");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> LatencyDescriptorGenerator.scan(outputDirectory));

    assertUnsupportedMessage(
        exception,
        "golden.UnsupportedAbstractTimedSample.abstractTimedMethod()V",
        "@Timed cannot be applied to abstract methods because there is no method body to "
            + "instrument. Annotate the concrete implementation instead.");
  }

  @Test
  void scanRejectsNativeTimedMethods(@TempDir Path outputDirectory) throws Exception {
    GoldenFixtureCompiler.compile(outputDirectory, "UnsupportedNativeTimedSample.java");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> LatencyDescriptorGenerator.scan(outputDirectory));

    assertUnsupportedMessage(
        exception,
        "golden.UnsupportedNativeTimedSample.nativeTimedMethod()V",
        "@Timed cannot be applied to native methods because there is no bytecode body to "
            + "instrument.");
  }

  @Test
  void scanRejectsAbstractInterfaceTimedMethods(@TempDir Path outputDirectory) throws Exception {
    GoldenFixtureCompiler.compile(outputDirectory, "UnsupportedInterfaceAbstractTimedSample.java");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> LatencyDescriptorGenerator.scan(outputDirectory));

    assertUnsupportedMessage(
        exception,
        "golden.UnsupportedInterfaceAbstractTimedSample.abstractInterfaceTimedMethod()V",
        "@Timed cannot be applied to abstract methods because there is no method body to "
            + "instrument. Annotate the concrete implementation instead.");
  }

  @Test
  void scanRejectsDefaultInterfaceTimedMethods(@TempDir Path outputDirectory) throws Exception {
    GoldenFixtureCompiler.compile(outputDirectory, "UnsupportedDefaultInterfaceTimedSample.java");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> LatencyDescriptorGenerator.scan(outputDirectory));

    assertUnsupportedMessage(
        exception,
        "golden.UnsupportedDefaultInterfaceTimedSample.defaultTimedMethod(I)I",
        "@Timed default interface methods are not supported yet. Annotate a concrete class "
            + "method instead.");
  }

  @Test
  void scanSkipsCompilerGeneratedBridgeMethods(@TempDir Path outputDirectory) throws Exception {
    GoldenFixtureCompiler.compile(outputDirectory, "GenericBridgeTimedSamples.java");

    List<LatencyDescriptorEntry> entries =
        LatencyDescriptorGenerator.scan(outputDirectory).values().stream()
            .flatMap(List::stream)
            .toList();

    assertEquals(1, entries.size());
    assertEquals(
        "golden.GenericBridgeTimedSamples$StringHandler#handle"
            + "(Ljava/lang/String;)Ljava/lang/String;",
        entries.getFirst().timerId());
    assertEquals("(Ljava/lang/String;)Ljava/lang/String;", entries.getFirst().methodDescriptor());
  }

  private static void assertUnsupportedMessage(
      IllegalArgumentException exception, String methodDetails, String reason) {
    assertTrue(exception.getMessage().contains("Instrumentation unsupported for " + methodDetails));
    assertTrue(exception.getMessage().contains(reason));
  }
}

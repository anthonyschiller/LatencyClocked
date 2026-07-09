package com.ll.metrics.latency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LatencyClockedTest {
  private final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

  @AfterEach
  void reset() {
    Thread.currentThread().setContextClassLoader(originalClassLoader);
    System.clearProperty(LatencyClockedConstants.ENABLED_PROPERTY);
  }

  @Test
  void oneIndexFileInvokesGeneratedBindMethod(@TempDir Path tempDir) throws Exception {
    compileFixture(
        tempDir,
        "DescriptorTarget",
        """
        import com.ll.metrics.latency.timer.Timer;
        import com.ll.metrics.latency.timer.Timers;

        public final class DescriptorTarget {
          public static Timer firstTimer;
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            bindInvocations++;
            firstTimer = timers.timer("service.first");
          }
        }
        """);
    writeDescriptor(tempDir, "DescriptorTarget");

    withDescriptorClasspath(
        tempDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          Class<?> target = load("DescriptorTarget");
          assertSame(latencyClocked.timer("service.first"), fieldValue(target, "firstTimer"));
          assertEquals(1, fieldValue(target, "bindInvocations"));
        });
  }

  @Test
  void multipleIndexResourcesAreAllLoaded(@TempDir Path firstDir, @TempDir Path secondDir)
      throws Exception {
    compileBindingFixture(firstDir, "FirstDescriptorTarget", "firstTimer", "service.first");
    compileBindingFixture(secondDir, "SecondDescriptorTarget", "secondTimer", "service.second");
    writeDescriptor(firstDir, "FirstDescriptorTarget");
    writeDescriptor(secondDir, "SecondDescriptorTarget");

    withDescriptorClasspath(
        firstDir,
        secondDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          assertSame(
              latencyClocked.timer("service.first"),
              fieldValue(load("FirstDescriptorTarget"), "firstTimer"));
          assertSame(
              latencyClocked.timer("service.second"),
              fieldValue(load("SecondDescriptorTarget"), "secondTimer"));
        });
  }

  @Test
  void duplicateClassNamesAreBoundOnce(@TempDir Path firstDir, @TempDir Path secondDir)
      throws Exception {
    compileFixture(
        firstDir,
        "DuplicateDescriptorTarget",
        """
        import com.ll.metrics.latency.timer.Timers;

        public final class DuplicateDescriptorTarget {
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            timers.timer("service.duplicate");
            bindInvocations++;
          }
        }
        """);
    writeDescriptor(firstDir, "DuplicateDescriptorTarget");
    writeDescriptor(secondDir, "DuplicateDescriptorTarget");

    withDescriptorClasspath(
        firstDir,
        secondDir,
        () -> {
          LatencyClocked.initialise();
          assertEquals(1, fieldValue(load("DuplicateDescriptorTarget"), "bindInvocations"));
        });
  }

  @Test
  void blankAndCommentLinesAreIgnored(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "DescriptorTarget", "privateTimer", "service.private");
    writeDescriptor(tempDir, "\n# comment\n  \nDescriptorTarget\n");

    withDescriptorClasspath(
        tempDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          assertSame(
              latencyClocked.timer("service.private"),
              fieldValue(load("DescriptorTarget"), "privateTimer"));
        });
  }

  @Test
  void malformedClassNameFailsFast(@TempDir Path tempDir) throws IOException {
    writeDescriptor(tempDir, "not|a|class");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withDescriptorClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

    assertTrue(exception.getMessage().contains("Malformed latency descriptor line"));
  }

  @Test
  void missingClassFailsFast(@TempDir Path tempDir) throws IOException {
    writeDescriptor(tempDir, "com.example.DoesNotExist");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withDescriptorClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

    assertTrue(exception.getMessage().contains("references missing class"));
  }

  @Test
  void missingBindMethodFailsFast(@TempDir Path tempDir) throws IOException {
    compileFixture(
        tempDir,
        "MissingBindTarget",
        """
        public final class MissingBindTarget {}
        """);
    writeDescriptor(tempDir, "MissingBindTarget");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withDescriptorClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

    assertTrue(exception.getMessage().contains("missing generated bind method"));
  }

  @Test
  void nonStaticBindMethodFailsFast(@TempDir Path tempDir) throws IOException {
    compileFixture(
        tempDir,
        "NonStaticBindTarget",
        """
        import com.ll.metrics.latency.timer.Timers;

        public final class NonStaticBindTarget {
          void __latency_clocked$bind(Timers timers) {
            timers.timer("service.nonstatic");
          }
        }
        """);
    writeDescriptor(tempDir, "NonStaticBindTarget");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withDescriptorClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

    assertTrue(exception.getMessage().contains("must be static"));
  }

  @Test
  void bindInvocationFailureFailsFast(@TempDir Path tempDir) throws IOException {
    compileFixture(
        tempDir,
        "ThrowingBindTarget",
        """
        import com.ll.metrics.latency.timer.Timers;

        public final class ThrowingBindTarget {
          static void __latency_clocked$bind(Timers timers) {
            throw new IllegalStateException("bind failed");
          }
        }
        """);
    writeDescriptor(tempDir, "ThrowingBindTarget");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withDescriptorClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

    assertTrue(exception.getMessage().contains("Invocation failure"));
  }

  @Test
  void initialiseDoesNotLoadDescriptorsWhenDisabled(@TempDir Path tempDir) throws Exception {
    compileFixture(
        tempDir,
        "DisabledDescriptorTarget",
        """
        import com.ll.metrics.latency.timer.Timers;

        public final class DisabledDescriptorTarget {
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            bindInvocations++;
          }
        }
        """);
    writeDescriptor(tempDir, "not|a|class\nDisabledDescriptorTarget");
    System.setProperty(LatencyClockedConstants.ENABLED_PROPERTY, "false");

    withDescriptorClasspath(
        tempDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          assertNotNull(latencyClocked.timer("manual.timer"));
          assertEquals(0, fieldValue(load("DisabledDescriptorTarget"), "bindInvocations"));
        });
  }

  @Test
  void timerLookupStillUsesActiveTimers(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "DescriptorTarget", "firstTimer", "service.first");
    writeDescriptor(tempDir, "DescriptorTarget");
    LatencyClocked latencyClocked =
        withDescriptorClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise);

    assertNotNull(latencyClocked.timer("service.first"));
    assertSame(
        latencyClocked.timer("service.first"), fieldValue(load("DescriptorTarget"), "firstTimer"));
  }

  @Test
  void threadSafeFactoryBindsGeneratedTimerFields(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "ThreadSafeDescriptorTarget", "timer", "service.thread.safe");
    writeDescriptor(tempDir, "ThreadSafeDescriptorTarget");
    LatencyClocked latencyClocked =
        withDescriptorClasspath(
            tempDir, (ThrowingInitialise) LatencyClocked::initialisedThreadSafe);

    assertSame(
        latencyClocked.timer("service.thread.safe"),
        fieldValue(load("ThreadSafeDescriptorTarget"), "timer"));
  }

  private static void compileBindingFixture(
      Path root, String className, String fieldName, String timerId) throws IOException {
    compileFixture(
        root,
        className,
        """
        import com.ll.metrics.latency.timer.Timer;
        import com.ll.metrics.latency.timer.Timers;

        public final class %s {
          public static Timer %s;

          static void __latency_clocked$bind(Timers timers) {
            %s = timers.timer("%s");
          }
        }
        """
            .formatted(className, fieldName, fieldName, timerId));
  }

  private static void compileFixture(Path root, String className, String source)
      throws IOException {
    Path sourceFile = root.resolve(className + ".java");
    Files.writeString(sourceFile, source);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("JDK compiler is required to compile test fixtures");
    }
    int result =
        compiler.run(
            null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            root.toString(),
            sourceFile.toString());
    if (result != 0) {
      throw new IllegalStateException("Failed to compile fixture " + className);
    }
  }

  private static void writeDescriptor(Path root, String content) throws IOException {
    Path descriptor =
        root.resolve(LatencyClockedConstants.DESCRIPTOR_ROOT)
            .resolve(LatencyClockedConstants.DESCRIPTOR_DIRECTORY)
            .resolve(LatencyClockedConstants.DESCRIPTOR_FILE);
    Files.createDirectories(descriptor.getParent());
    Files.writeString(descriptor, content);
  }

  private void withDescriptorClasspath(Path firstRoot, ThrowingRunnable runnable)
      throws IOException {
    withDescriptorClasspath(new Path[] {firstRoot}, runnable);
  }

  private LatencyClocked withDescriptorClasspath(Path firstRoot, ThrowingInitialise initialise)
      throws IOException {
    return withDescriptorClasspath(new Path[] {firstRoot}, initialise);
  }

  private void withDescriptorClasspath(Path firstRoot, Path secondRoot, ThrowingRunnable runnable)
      throws IOException {
    withDescriptorClasspath(new Path[] {firstRoot, secondRoot}, runnable);
  }

  private LatencyClocked withDescriptorClasspath(
      Path firstRoot, Path secondRoot, ThrowingInitialise initialise) throws IOException {
    return withDescriptorClasspath(new Path[] {firstRoot, secondRoot}, initialise);
  }

  private void withDescriptorClasspath(Path[] roots, ThrowingRunnable runnable) throws IOException {
    withDescriptorClasspath(
        roots,
        () -> {
          runnable.run();
          return null;
        });
  }

  private LatencyClocked withDescriptorClasspath(Path[] roots, ThrowingInitialise initialise)
      throws IOException {
    URL[] urls = new URL[roots.length];
    for (int i = 0; i < roots.length; i++) {
      urls[i] = roots[i].toUri().toURL();
    }

    try (URLClassLoader classLoader = new URLClassLoader(urls, originalClassLoader)) {
      Thread.currentThread().setContextClassLoader(classLoader);
      return initialise.run();
    }
  }

  private static Class<?> load(String className) {
    try {
      return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Object fieldValue(Class<?> target, String fieldName) {
    try {
      Field field = target.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run();
  }

  @FunctionalInterface
  private interface ThrowingInitialise {
    LatencyClocked run();
  }
}

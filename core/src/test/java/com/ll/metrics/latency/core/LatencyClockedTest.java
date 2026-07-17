package com.ll.metrics.latency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.timer.Timer;
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
        "IndexTarget",
        """
        import com.ll.metrics.latency.timer.Timer;
        import com.ll.metrics.latency.timer.Timers;

        public final class IndexTarget {
          public static Timer firstTimer;
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            bindInvocations++;
            firstTimer = timers.claim("service.first");
          }
        }
        """);
    writeIndex(tempDir, "IndexTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          Class<?> target = load("IndexTarget");
          assertNotNull(fieldValue(target, "firstTimer"));
          assertTrue(
              latencyClocked.snapshots().stream()
                  .anyMatch(snapshot -> snapshot.id().equals("service.first")));
          assertEquals(1, fieldValue(target, "bindInvocations"));
        });
  }

  @Test
  void multipleIndexResourcesAreAllLoaded(@TempDir Path firstDir, @TempDir Path secondDir)
      throws Exception {
    compileBindingFixture(firstDir, "FirstIndexTarget", "firstTimer", "service.first");
    compileBindingFixture(secondDir, "SecondIndexTarget", "secondTimer", "service.second");
    writeIndex(firstDir, "FirstIndexTarget");
    writeIndex(secondDir, "SecondIndexTarget");

    withIndexClasspath(
        firstDir,
        secondDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          assertNotNull(fieldValue(load("FirstIndexTarget"), "firstTimer"));
          assertNotNull(fieldValue(load("SecondIndexTarget"), "secondTimer"));
          assertTrue(
              latencyClocked.snapshots().stream()
                  .anyMatch(snapshot -> snapshot.id().equals("service.first")));
          assertTrue(
              latencyClocked.snapshots().stream()
                  .anyMatch(snapshot -> snapshot.id().equals("service.second")));
        });
  }

  @Test
  void duplicateClassNamesAreBoundOnce(@TempDir Path firstDir, @TempDir Path secondDir)
      throws Exception {
    compileFixture(
        firstDir,
        "DuplicateIndexTarget",
        """
        import com.ll.metrics.latency.timer.Timers;

        public final class DuplicateIndexTarget {
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            timers.claim("service.duplicate");
            bindInvocations++;
          }
        }
        """);
    writeIndex(firstDir, "DuplicateIndexTarget");
    writeIndex(secondDir, "DuplicateIndexTarget");

    withIndexClasspath(
        firstDir,
        secondDir,
        () -> {
          LatencyClocked.initialise();
          assertEquals(1, fieldValue(load("DuplicateIndexTarget"), "bindInvocations"));
        });
  }

  @Test
  void blankAndCommentLinesAreIgnored(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "IndexTarget", "privateTimer", "service.private");
    writeIndex(tempDir, "\n# comment\n  \nIndexTarget\n");

    withIndexClasspath(
        tempDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          assertNotNull(fieldValue(load("IndexTarget"), "privateTimer"));
          assertTrue(
              latencyClocked.snapshots().stream()
                  .anyMatch(snapshot -> snapshot.id().equals("service.private")));
        });
  }

  @Test
  void malformedClassNameFailsFast(@TempDir Path tempDir) throws IOException {
    writeIndex(tempDir, "not|a|class");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withIndexClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

    assertTrue(
        exception
            .getMessage()
            .contains("Malformed instrumented class-index line"));
  }

  @Test
  void missingClassFailsFast(@TempDir Path tempDir) throws IOException {
    writeIndex(tempDir, "com.example.DoesNotExist");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withIndexClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

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
    writeIndex(tempDir, "MissingBindTarget");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withIndexClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

    assertTrue(exception.getMessage().contains("missing generated bind method"));
    assertTrue(
        exception
            .getMessage()
            .contains(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_RESOURCE));
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
            timers.claim("service.nonstatic");
          }
        }
        """);
    writeIndex(tempDir, "NonStaticBindTarget");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withIndexClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

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
    writeIndex(tempDir, "ThrowingBindTarget");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                withIndexClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise));

    assertTrue(exception.getMessage().contains("Invocation failure"));
  }

  @Test
  void initialiseDoesNotLoadIndexesWhenDisabled(@TempDir Path tempDir) throws Exception {
    compileFixture(
        tempDir,
        "DisabledIndexTarget",
        """
        import com.ll.metrics.latency.timer.Timers;

        public final class DisabledIndexTarget {
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            bindInvocations++;
          }
        }
        """);
    writeIndex(tempDir, "not|a|class\nDisabledIndexTarget");
    System.setProperty(LatencyClockedConstants.ENABLED_PROPERTY, "false");

    withIndexClasspath(
        tempDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          assertTrue(latencyClocked.snapshots().isEmpty());
          assertEquals(0, fieldValue(load("DisabledIndexTarget"), "bindInvocations"));
        });
  }

  @Test
  void snapshotsRemainAvailableForBoundTimers(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "IndexTarget", "firstTimer", "service.first");
    writeIndex(tempDir, "IndexTarget");
    LatencyClocked latencyClocked =
        withIndexClasspath(tempDir, (ThrowingInitialise) LatencyClocked::initialise);

    Timer timer = (Timer) fieldValue(load("IndexTarget"), "firstTimer");
    timer.record(10);

    assertTrue(
        latencyClocked.snapshots().stream()
            .anyMatch(snapshot -> snapshot.id().equals("service.first") && snapshot.count() == 1));
  }

  @Test
  void threadSafeFactoryBindsGeneratedTimerFields(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "ThreadSafeIndexTarget", "timer", "service.thread.safe");
    writeIndex(tempDir, "ThreadSafeIndexTarget");
    LatencyClocked latencyClocked =
        withIndexClasspath(
            tempDir, (ThrowingInitialise) LatencyClocked::initialisedThreadSafe);

    assertNotNull(fieldValue(load("ThreadSafeIndexTarget"), "timer"));
    assertTrue(
        latencyClocked.snapshots().stream()
            .anyMatch(snapshot -> snapshot.id().equals("service.thread.safe")));
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
            %s = timers.claim("%s");
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

  private static void writeIndex(Path root, String content) throws IOException {
    Path index =
        root.resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_ROOT)
            .resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_DIRECTORY)
            .resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_FILE);
    Files.createDirectories(index.getParent());
    Files.writeString(index, content);
  }

  private void withIndexClasspath(Path firstRoot, ThrowingRunnable runnable)
      throws IOException {
    withIndexClasspath(new Path[] {firstRoot}, runnable);
  }

  private LatencyClocked withIndexClasspath(Path firstRoot, ThrowingInitialise initialise)
      throws IOException {
    return withIndexClasspath(new Path[] {firstRoot}, initialise);
  }

  private void withIndexClasspath(Path firstRoot, Path secondRoot, ThrowingRunnable runnable)
      throws IOException {
    withIndexClasspath(new Path[] {firstRoot, secondRoot}, runnable);
  }

  private void withIndexClasspath(Path[] roots, ThrowingRunnable runnable) throws IOException {
    withIndexClasspath(
        roots,
        () -> {
          runnable.run();
          return null;
        });
  }

  private LatencyClocked withIndexClasspath(Path[] roots, ThrowingInitialise initialise)
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

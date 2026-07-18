package com.ll.metrics.latency.core;

import static com.ll.metrics.latency.test.TestUtils.resetLatencyClocked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.snapshot.TimerSnapshot;
import com.ll.metrics.latency.timer.InMemoryTimers;
import com.ll.metrics.latency.timer.Timer;
import com.ll.metrics.latency.timer.Timers;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class LatencyClockedTest {
  private final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

  @BeforeEach
  void prepare() {
    resetLatencyClocked();
  }

  @AfterEach
  void reset() {
    Thread.currentThread().setContextClassLoader(originalClassLoader);
    System.clearProperty(LatencyClockedConstants.ENABLED_PROPERTY);
    resetLatencyClocked();
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
  void nullTimersAreRejectedWithoutChangingInitialisationState(@TempDir Path tempDir)
      throws Exception {
    compileBindingFixture(tempDir, "NullInputIndexTarget", "timer", "service.null.input");
    writeIndex(tempDir, "NullInputIndexTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          assertThrows(NullPointerException.class, () -> LatencyClocked.initialise(null));

          Timers timers = InMemoryTimers.create();
          LatencyClocked.initialise(timers);

          Class<?> target = load("NullInputIndexTarget");
          assertNotNull(fieldValue(target, "timer"));
          assertEquals(1, fieldValue(target, "bindInvocations"));
        });
  }

  @Test
  void managementBeanExposesEnabledAttribute(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "ManagedIndexTarget", "timer", "service.managed");
    writeIndex(tempDir, "ManagedIndexTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          LatencyClocked.initialise(InMemoryTimers.create());
          MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
          ObjectName objectName = new ObjectName(LatencyClockedConstants.MBEAN_NAME);

          assertEquals(LatencyClocked.enabled(), mbeanServer.getAttribute(objectName, "Enabled"));

          mbeanServer.setAttribute(objectName, new Attribute("Enabled", false));
          assertFalse(LatencyClocked.enabled());
          assertEquals(false, mbeanServer.getAttribute(objectName, "Enabled"));

          mbeanServer.setAttribute(objectName, new Attribute("Enabled", true));
          assertTrue(LatencyClocked.enabled());
          assertEquals(true, mbeanServer.getAttribute(objectName, "Enabled"));
        });
  }

  @Test
  void repeatedInitialiseWithSameTimersIsNoOp(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "IdempotentIndexTarget", "timer", "service.idempotent");
    writeIndex(tempDir, "IdempotentIndexTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          Timers timers = InMemoryTimers.create();
          LatencyClocked.initialise(timers);
          Timer firstTimer = (Timer) fieldValue(load("IdempotentIndexTarget"), "timer");

          LatencyClocked.initialise(timers);
          LatencyClocked.initialise(timers);

          Class<?> target = load("IdempotentIndexTarget");
          assertSame(firstTimer, fieldValue(target, "timer"));
          assertEquals(1, fieldValue(target, "bindInvocations"));
        });
  }

  @Test
  void repeatedInitialiseWithDifferentTimersFails(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "DifferentOwnerIndexTarget", "timer", "service.owner");
    writeIndex(tempDir, "DifferentOwnerIndexTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          Timers firstTimers = InMemoryTimers.create();
          Timers secondTimers = InMemoryTimers.create();
          LatencyClocked.initialise(firstTimers);
          Timer firstTimer = (Timer) fieldValue(load("DifferentOwnerIndexTarget"), "timer");

          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class, () -> LatencyClocked.initialise(secondTimers));

          assertTrue(exception.getMessage().contains("different Timers instance"));
          assertSame(firstTimer, fieldValue(load("DifferentOwnerIndexTarget"), "timer"));
          assertEquals(1, fieldValue(load("DifferentOwnerIndexTarget"), "bindInvocations"));
        });
  }

  @Test
  void ownerChecksUseInstanceIdentityInsteadOfEquality(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "EqualOwnerIndexTarget", "timer", "service.equal");
    writeIndex(tempDir, "EqualOwnerIndexTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          Timers firstTimers = new EqualTimers();
          Timers secondTimers = new EqualTimers();
          assertEquals(firstTimers, secondTimers);

          LatencyClocked.initialise(firstTimers);
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class, () -> LatencyClocked.initialise(secondTimers));

          assertTrue(exception.getMessage().contains("different Timers instance"));
        });
  }

  @Test
  void failedInitialiseCanBeRetriedWithSameTimers(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "FirstRetryTarget", "timer", "service.retry.first");
    compileFixture(
        tempDir,
        "FailingRetryTarget",
        """
        import com.ll.metrics.latency.timer.Timer;
        import com.ll.metrics.latency.timer.Timers;

        public final class FailingRetryTarget {
          public static Timer timer;
          public static boolean fail = true;
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            bindInvocations++;
            if (fail) {
              throw new IllegalStateException("controlled failure");
            }
            timer = timers.claim("service.retry.failing");
          }
        }
        """);
    compileBindingFixture(tempDir, "ThirdRetryTarget", "timer", "service.retry.third");
    writeIndex(tempDir, "FirstRetryTarget\nFailingRetryTarget\nThirdRetryTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          Timers timers = InMemoryTimers.create();
          assertThrows(IllegalStateException.class, () -> LatencyClocked.initialise(timers));
          Timer firstTimer = (Timer) fieldValue(load("FirstRetryTarget"), "timer");
          assertNotNull(firstTimer);
          assertEquals(1, fieldValue(load("FirstRetryTarget"), "bindInvocations"));
          assertEquals(1, fieldValue(load("FailingRetryTarget"), "bindInvocations"));

          setStaticField(load("FailingRetryTarget"), "fail", false);
          LatencyClocked.initialise(timers);

          assertSame(firstTimer, fieldValue(load("FirstRetryTarget"), "timer"));
          assertNotNull(fieldValue(load("FailingRetryTarget"), "timer"));
          assertNotNull(fieldValue(load("ThirdRetryTarget"), "timer"));
          assertEquals(2, fieldValue(load("FirstRetryTarget"), "bindInvocations"));
          assertEquals(2, fieldValue(load("FailingRetryTarget"), "bindInvocations"));
          assertEquals(1, fieldValue(load("ThirdRetryTarget"), "bindInvocations"));
        });
  }

  @Test
  void failedInitialiseCannotBeRetriedWithDifferentTimers(@TempDir Path tempDir)
      throws Exception {
    compileBindingFixture(tempDir, "FirstFailedOwnerTarget", "timer", "service.failed.first");
    compileFixture(
        tempDir,
        "FailingOwnerTarget",
        """
        import com.ll.metrics.latency.timer.Timers;

        public final class FailingOwnerTarget {
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            bindInvocations++;
            throw new IllegalStateException("controlled failure");
          }
        }
        """);
    writeIndex(tempDir, "FirstFailedOwnerTarget\nFailingOwnerTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          Timers firstTimers = InMemoryTimers.create();
          Timers secondTimers = InMemoryTimers.create();
          assertThrows(IllegalStateException.class, () -> LatencyClocked.initialise(firstTimers));

          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class, () -> LatencyClocked.initialise(secondTimers));

          assertTrue(exception.getMessage().contains("different Timers instance"));
          assertEquals(1, fieldValue(load("FirstFailedOwnerTarget"), "bindInvocations"));
          assertEquals(1, fieldValue(load("FailingOwnerTarget"), "bindInvocations"));
        });
  }

  @Test
  void concurrentInitialiseWithSameTimersBindsOnce(@TempDir Path tempDir) throws Exception {
    compileBlockingBindingFixture(tempDir, "ConcurrentSameOwnerTarget", "service.concurrent");
    writeIndex(tempDir, "ConcurrentSameOwnerTarget");

    try (URLClassLoader classLoader =
            new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, originalClassLoader);
        ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Class<?> target = Class.forName("ConcurrentSameOwnerTarget", true, classLoader);
      CountDownLatch entered = (CountDownLatch) fieldValue(target, "entered");
      CountDownLatch release = (CountDownLatch) fieldValue(target, "release");
      Timers timers = InMemoryTimers.create();

      final Future<LatencyClocked> first =
          executor.submit(
              () -> withContextClassLoader(classLoader, () -> LatencyClocked.initialise(timers)));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      final Future<LatencyClocked> second =
          executor.submit(
              () -> withContextClassLoader(classLoader, () -> LatencyClocked.initialise(timers)));

      assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
      assertEquals(1, fieldValue(target, "bindInvocations"));
      assertEquals(1, timers.snapshots().size());
    }
  }

  @Test
  void concurrentInitialiseWithDifferentTimersRejectsLosingOwner(@TempDir Path tempDir)
      throws Exception {
    compileBlockingBindingFixture(tempDir, "ConcurrentDifferentOwnerTarget", "service.race");
    writeIndex(tempDir, "ConcurrentDifferentOwnerTarget");

    try (URLClassLoader classLoader =
            new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, originalClassLoader);
        ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Class<?> target = Class.forName("ConcurrentDifferentOwnerTarget", true, classLoader);
      CountDownLatch entered = (CountDownLatch) fieldValue(target, "entered");
      CountDownLatch release = (CountDownLatch) fieldValue(target, "release");
      Timers firstTimers = InMemoryTimers.create();
      Timers secondTimers = InMemoryTimers.create();

      Future<LatencyClocked> first =
          executor.submit(
              () ->
                  withContextClassLoader(
                      classLoader, () -> LatencyClocked.initialise(firstTimers)));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      Future<LatencyClocked> second =
          executor.submit(
              () ->
                  withContextClassLoader(
                      classLoader, () -> LatencyClocked.initialise(secondTimers)));

      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> unwrap(second));

      assertTrue(exception.getMessage().contains("different Timers instance"));
      assertEquals(1, fieldValue(target, "bindInvocations"));
      assertEquals(1, firstTimers.snapshots().size());
      assertTrue(secondTimers.snapshots().isEmpty());
    }
  }

  @Timeout(2)
  @Test
  void initialiseFailsOnSameThreadRecursiveEntry(@TempDir Path tempDir) throws Exception {
    compileBindingFixture(tempDir, "RecursiveClaimIndexTarget", "timer", "service.recursive");
    writeIndex(tempDir, "RecursiveClaimIndexTarget");

    withIndexClasspath(
        tempDir,
        () -> {
          RecursingTimers timers = new RecursingTimers(Thread.currentThread());
          IllegalStateException exception =
              assertThrows(IllegalStateException.class, () -> LatencyClocked.initialise(timers));
          assertTrue(exception.getMessage().contains("Invocation failure"));
          assertTrue(exception.getMessage().contains("Recursive LatencyClocked initialisation"));
          assertTrue(LatencyClocked.snapshots().isEmpty());

          LatencyClocked.initialise(timers);
          assertNotNull(fieldValue(load("RecursiveClaimIndexTarget"), "timer"));
          assertEquals(1, LatencyClocked.snapshots().size());
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
  void initialiseBindsIndexesWhenRecordingIsDisabled(@TempDir Path tempDir) throws Exception {
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
    writeIndex(tempDir, "DisabledIndexTarget");
    System.setProperty(LatencyClockedConstants.ENABLED_PROPERTY, "false");

    withIndexClasspath(
        tempDir,
        () -> {
          LatencyClocked latencyClocked = LatencyClocked.initialise();
          assertTrue(latencyClocked.snapshots().isEmpty());
          assertEquals(1, fieldValue(load("DisabledIndexTarget"), "bindInvocations"));
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
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            bindInvocations++;
            %s = timers.claim("%s");
          }
        }
        """
            .formatted(className, fieldName, fieldName, timerId));
  }

  private static void compileBlockingBindingFixture(Path root, String className, String timerId)
      throws IOException {
    compileFixture(
        root,
        className,
        """
        import com.ll.metrics.latency.timer.Timer;
        import com.ll.metrics.latency.timer.Timers;
        import java.util.concurrent.CountDownLatch;

        public final class %s {
          public static final CountDownLatch entered = new CountDownLatch(1);
          public static final CountDownLatch release = new CountDownLatch(1);
          public static Timer timer;
          public static int bindInvocations;

          static void __latency_clocked$bind(Timers timers) {
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
            bindInvocations++;
            timer = timers.claim("%s");
          }
        }
        """
            .formatted(className, timerId));
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

  private void withIndexClasspath(Path firstRoot, ThrowingRunnable runnable) throws Exception {
    withIndexClasspath(new Path[] {firstRoot}, runnable);
  }

  private LatencyClocked withIndexClasspath(Path firstRoot, ThrowingInitialise initialise)
      throws Exception {
    return withIndexClasspath(new Path[] {firstRoot}, initialise);
  }

  private void withIndexClasspath(Path firstRoot, Path secondRoot, ThrowingRunnable runnable)
      throws Exception {
    withIndexClasspath(new Path[] {firstRoot, secondRoot}, runnable);
  }

  private void withIndexClasspath(Path[] roots, ThrowingRunnable runnable) throws Exception {
    withIndexClasspath(
        roots,
        () -> {
          runnable.run();
          return null;
        });
  }

  private LatencyClocked withIndexClasspath(Path[] roots, ThrowingInitialise initialise)
      throws Exception {
    URL[] urls = new URL[roots.length];
    for (int i = 0; i < roots.length; i++) {
      urls[i] = roots[i].toUri().toURL();
    }

    try (URLClassLoader classLoader = new URLClassLoader(urls, originalClassLoader)) {
      Thread.currentThread().setContextClassLoader(classLoader);
      return initialise.run();
    }
  }

  private LatencyClocked withContextClassLoader(
      ClassLoader classLoader, ThrowingInitialise initialise) throws Exception {
    Thread currentThread = Thread.currentThread();
    ClassLoader previousClassLoader = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(classLoader);
    try {
      return initialise.run();
    } finally {
      currentThread.setContextClassLoader(previousClassLoader);
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

  private static void setStaticField(Class<?> target, String fieldName, Object value) {
    try {
      Field field = target.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(null, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static LatencyClocked unwrap(Future<LatencyClocked> future) throws Exception {
    try {
      return future.get(5, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof Exception exception) {
        throw exception;
      }
      throw e;
    }
  }

  private static final class EqualTimers implements Timers {
    private final Timers delegate = InMemoryTimers.create();

    @Override
    public Timer claim(String methodId) {
      return delegate.claim(methodId);
    }

    @Override
    public Collection<TimerSnapshot> snapshots() {
      return delegate.snapshots();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof EqualTimers;
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }

  private static final class RecursingTimers implements Timers {
    private final Timers delegate = InMemoryTimers.create();
    private final Thread expectedThread;
    private boolean recurse = true;
    private boolean recursedOnExpectedThread;

    private RecursingTimers(Thread expectedThread) {
      this.expectedThread = expectedThread;
    }

    @Override
    public Timer claim(String methodId) {
      if (recurse) {
        recurse = false;
        recursedOnExpectedThread = Thread.currentThread() == expectedThread;
        LatencyClocked.initialise(this);
      }
      return delegate.claim(methodId);
    }

    @Override
    public Collection<TimerSnapshot> snapshots() {
      return delegate.snapshots();
    }

    private boolean recursedOnExpectedThread() {
      return recursedOnExpectedThread;
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingInitialise {
    LatencyClocked run() throws Exception;
  }
}

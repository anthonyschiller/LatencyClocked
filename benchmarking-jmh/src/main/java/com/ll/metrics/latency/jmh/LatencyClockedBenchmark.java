package com.ll.metrics.latency.jmh;

import com.ll.metrics.latency.annotations.Timed;
import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.core.LatencyClocked;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** JMH benchmarks for plain calls, instrumented calls, and direct timer recording. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2)
public class LatencyClockedBenchmark {

  @Benchmark
  public long baselineNanoTime() {
    return System.nanoTime();
  }

  @Benchmark
  public void plainVoidCall(PlainBenchmarkState state) {
    state.target.voidCall();
  }

  @Benchmark
  public int plainPrimitiveReturn(PlainBenchmarkState state) {
    return state.target.primitiveReturn();
  }

  @Benchmark
  public Object plainObjectReturn(PlainBenchmarkState state) {
    return state.target.objectReturn();
  }

  @Benchmark
  public void plainStaticCall() {
    PlainTarget.staticCall();
  }

  @Benchmark
  public void latencyClockedVoidCall(
      LatencyClockedBindingState bindingState, LatencyClockedThreadState state) {
    state.target.voidCall();
  }

  @Benchmark
  public int latencyClockedPrimitiveReturn(
      LatencyClockedBindingState bindingState, LatencyClockedThreadState state) {
    return state.target.primitiveReturn();
  }

  @Benchmark
  public Object latencyClockedObjectReturn(
      LatencyClockedBindingState bindingState, LatencyClockedThreadState state) {
    return state.target.objectReturn();
  }

  @Benchmark
  public void latencyClockedStaticCall(LatencyClockedBindingState bindingState) {
    LatencyClockedTarget.staticCall();
  }

  @Benchmark
  public void latencyClockedDisabledVoidCall(
      LatencyClockedRecordingDisabledState bindingState,
      LatencyClockedDisabledThreadState state) {
    state.target.voidCall();
  }

  @Benchmark
  public int latencyClockedDisabledPrimitiveReturn(
      LatencyClockedRecordingDisabledState bindingState,
      LatencyClockedDisabledThreadState state) {
    return state.target.primitiveReturn();
  }

  @Benchmark
  public Object latencyClockedDisabledObjectReturn(
      LatencyClockedRecordingDisabledState bindingState,
      LatencyClockedDisabledThreadState state) {
    return state.target.objectReturn();
  }

  @Benchmark
  public void latencyClockedDisabledStaticCall(LatencyClockedRecordingDisabledState bindingState) {
    LatencyClockedDisabledTarget.staticCall();
  }

  @Benchmark
  public void latencyClockedThreadSafeVoidCall(
      LatencyClockedThreadSafeBindingState bindingState, LatencyClockedThreadState state) {
    state.target.voidCall();
  }

  @Benchmark
  public int latencyClockedThreadSafePrimitiveReturn(
      LatencyClockedThreadSafeBindingState bindingState, LatencyClockedThreadState state) {
    return state.target.primitiveReturn();
  }

  @Benchmark
  public Object latencyClockedThreadSafeObjectReturn(
      LatencyClockedThreadSafeBindingState bindingState, LatencyClockedThreadState state) {
    return state.target.objectReturn();
  }

  @Benchmark
  public void latencyClockedThreadSafeStaticCall(
      LatencyClockedThreadSafeBindingState bindingState) {
    LatencyClockedTarget.staticCall();
  }

  @Benchmark
  public void micrometerTimedVoidCall(
      MicrometerRegistryState registryState, MicrometerThreadState state) {
    state.target.voidCall();
  }

  @Benchmark
  public int micrometerTimedPrimitiveReturn(
      MicrometerRegistryState registryState, MicrometerThreadState state) {
    return state.target.primitiveReturn();
  }

  @Benchmark
  public Object micrometerTimedObjectReturn(
      MicrometerRegistryState registryState, MicrometerThreadState state) {
    return state.target.objectReturn();
  }

  @Benchmark
  public void micrometerTimedStaticCall(MicrometerRegistryState registryState) {
    MicrometerTarget.staticCall();
  }

  /** Per-thread state for plain baseline calls. */
  @State(Scope.Thread)
  public static class PlainBenchmarkState {
    private PlainTarget target;

    @Setup(Level.Trial)
    public void setup() {
      target = new PlainTarget();
    }
  }

  /** Benchmark-scoped startup binding for single-writer latency-clocked timers. */
  @State(Scope.Benchmark)
  public static class LatencyClockedBindingState {
    @Setup(Level.Trial)
    public void setup() {
      LatencyClocked.initialise();
    }
  }

  /** Benchmark-scoped startup binding for thread-safe latency-clocked timers. */
  @State(Scope.Benchmark)
  public static class LatencyClockedThreadSafeBindingState {
    @Setup(Level.Trial)
    public void setup() {
      LatencyClocked.initialisedThreadSafe();
    }
  }

  /** Benchmark-scoped disabled recording path for latency-clocked timers. */
  @State(Scope.Benchmark)
  public static class LatencyClockedRecordingDisabledState {
    private String previousEnabledProperty;

    /** Starts latency-clocked normally with recording disabled. */
    @Setup(Level.Trial)
    public void setup() {
      previousEnabledProperty = System.getProperty(LatencyClockedConstants.ENABLED_PROPERTY);
      System.setProperty(LatencyClockedConstants.ENABLED_PROPERTY, "false");
      LatencyClocked.initialise();
    }

    /** Restores the previous enabled property value after the benchmark trial. */
    @TearDown(Level.Trial)
    public void tearDown() {
      if (previousEnabledProperty == null) {
        System.clearProperty(LatencyClockedConstants.ENABLED_PROPERTY);
      } else {
        System.setProperty(LatencyClockedConstants.ENABLED_PROPERTY, previousEnabledProperty);
      }
      LatencyClocked.setEnabled(
          !"false".equalsIgnoreCase(System.getProperty(LatencyClockedConstants.ENABLED_PROPERTY)));
    }
  }

  /** Per-thread latency-clocked invocation target. */
  @State(Scope.Thread)
  public static class LatencyClockedThreadState {
    private LatencyClockedTarget target;

    @Setup(Level.Trial)
    public void setup() {
      target = new LatencyClockedTarget();
    }
  }

  /** Per-thread latency-clocked invocation target used with disabled recording. */
  @State(Scope.Thread)
  public static class LatencyClockedDisabledThreadState {
    private LatencyClockedDisabledTarget target;

    @Setup(Level.Trial)
    public void setup() {
      target = new LatencyClockedDisabledTarget();
    }
  }

  /** Benchmark-scoped Micrometer registry lifecycle. */
  @State(Scope.Benchmark)
  public static class MicrometerRegistryState {
    private final List<MeterRegistry> previousRegistries = new ArrayList<>();
    private SimpleMeterRegistry micrometerRegistry;

    /** Installs a single Micrometer registry and pre-registers benchmark meters. */
    @Setup(Level.Trial)
    public void setup() {
      previousRegistries.addAll(Metrics.globalRegistry.getRegistries());
      previousRegistries.forEach(Metrics::removeRegistry);
      Metrics.globalRegistry.clear();
      micrometerRegistry = new SimpleMeterRegistry();
      Metrics.addRegistry(micrometerRegistry);
      preRegisterMeters();
    }

    /** Removes the benchmark registry and restores any registry present before the trial. */
    @TearDown(Level.Trial)
    public void tearDown() {
      Metrics.removeRegistry(micrometerRegistry);
      micrometerRegistry.close();
      Metrics.globalRegistry.clear();
      previousRegistries.forEach(Metrics::addRegistry);
      previousRegistries.clear();
    }

    private static void preRegisterMeters() {
      MicrometerTarget target = new MicrometerTarget();
      target.voidCall();
      target.primitiveReturn();
      target.objectReturn();
      MicrometerTarget.staticCall();
    }
  }

  /** Per-thread Micrometer invocation target. */
  @State(Scope.Thread)
  public static class MicrometerThreadState {
    private MicrometerTarget target;

    @Setup(Level.Trial)
    public void setup() {
      target = new MicrometerTarget();
    }
  }

  static final class PlainTarget {
    private int value = 41;
    private final Object object = new Object();

    void voidCall() {
      value++;
    }

    int primitiveReturn() {
      return value + 1;
    }

    Object objectReturn() {
      return object;
    }

    static void staticCall() {
      Blackhole.consumeCPU(1);
    }
  }

  static final class LatencyClockedTarget {
    private int value = 41;
    private final Object object = new Object();

    @Timed
    void voidCall() {
      value++;
    }

    @Timed
    int primitiveReturn() {
      return value + 1;
    }

    @Timed
    Object objectReturn() {
      return object;
    }

    @Timed
    static void staticCall() {
      Blackhole.consumeCPU(1);
    }
  }

  static final class LatencyClockedDisabledTarget {
    private int value = 41;
    private final Object object = new Object();

    @Timed
    void voidCall() {
      value++;
    }

    @Timed
    int primitiveReturn() {
      return value + 1;
    }

    @Timed
    Object objectReturn() {
      return object;
    }

    @Timed
    static void staticCall() {
      Blackhole.consumeCPU(1);
    }
  }

  static final class MicrometerTarget {
    private int value = 41;
    private final Object object = new Object();

    @io.micrometer.core.annotation.Timed(value = "benchmark.micrometer.void")
    void voidCall() {
      value++;
    }

    @io.micrometer.core.annotation.Timed(value = "benchmark.micrometer.primitive")
    int primitiveReturn() {
      return value + 1;
    }

    @io.micrometer.core.annotation.Timed(value = "benchmark.micrometer.object")
    Object objectReturn() {
      return object;
    }

    @io.micrometer.core.annotation.Timed(value = "benchmark.micrometer.static")
    static void staticCall() {
      Blackhole.consumeCPU(1);
    }
  }
}

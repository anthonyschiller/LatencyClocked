package com.ll.metrics.latency.jmh;

import com.ll.metrics.latency.annotations.Timed;
import com.ll.metrics.latency.core.LatencyClocked;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** JMH benchmarks for baseline calls, instrumented calls, and direct timer recording. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2)
public class LatencyClockedBenchmark {

  @Benchmark
  public void plainVoidMethod(BaselineState state) {
    state.target.voidMethod();
  }

  @Benchmark
  public int plainPrimitiveReturnMethod(BaselineState state) {
    return state.target.primitiveReturnMethod();
  }

  @Benchmark
  public Object plainObjectReturnMethod(BaselineState state) {
    return state.target.objectReturnMethod();
  }

  @Benchmark
  public void plainStaticMethod() {
    BaselineTarget.staticMethod();
  }

  @Benchmark
  public void timedVoidMethodWithSingleWriterTimer(SingleWriterInstrumentedState state) {
    state.target.voidMethod();
  }

  @Benchmark
  public int timedPrimitiveReturnMethodWithSingleWriterTimer(
      SingleWriterInstrumentedState state) {
    return state.target.primitiveReturnMethod();
  }

  @Benchmark
  public Object timedObjectReturnMethodWithSingleWriterTimer(SingleWriterInstrumentedState state) {
    return state.target.objectReturnMethod();
  }

  @Benchmark
  public void timedStaticMethodWithSingleWriterTimer(SingleWriterInstrumentedState state) {
    InstrumentedTarget.staticMethod();
  }

  @Benchmark
  public void timedVoidMethodWithThreadSafeTimer(ThreadSafeInstrumentedState state) {
    state.target.voidMethod();
  }

  @Benchmark
  public int timedPrimitiveReturnMethodWithThreadSafeTimer(ThreadSafeInstrumentedState state) {
    return state.target.primitiveReturnMethod();
  }

  @Benchmark
  public Object timedObjectReturnMethodWithThreadSafeTimer(ThreadSafeInstrumentedState state) {
    return state.target.objectReturnMethod();
  }

  @Benchmark
  public void timedStaticMethodWithThreadSafeTimer(ThreadSafeInstrumentedState state) {
    InstrumentedTarget.staticMethod();
  }

  /** Per-thread state for plain baseline calls. */
  @State(Scope.Thread)
  public static class BaselineState {
    private BaselineTarget target;

    @Setup(Level.Trial)
    public void setup() {
      target = new BaselineTarget();
    }
  }

  /** Per-thread state for instrumented calls bound to single-writer HDR timers. */
  @State(Scope.Thread)
  public static class SingleWriterInstrumentedState {
    private InstrumentedTarget target;

    @Setup(Level.Trial)
    public void setup() {
      LatencyClocked.initialise();
      target = new InstrumentedTarget();
    }
  }

  /** Per-thread state for instrumented calls bound to thread-safe HDR timers. */
  @State(Scope.Thread)
  public static class ThreadSafeInstrumentedState {
    private InstrumentedTarget target;

    @Setup(Level.Trial)
    public void setup() {
      LatencyClocked.initialisedThreadSafe();
      target = new InstrumentedTarget();
    }
  }

  private static final class BaselineTarget {
    private int value = 41;
    private final Object object = new Object();

    private void voidMethod() {
      value++;
    }

    private int primitiveReturnMethod() {
      return value + 1;
    }

    private Object objectReturnMethod() {
      return object;
    }

    private static void staticMethod() {
      Blackhole.consumeCPU(1);
    }
  }

  private static final class InstrumentedTarget {
    private int value = 41;
    private final Object object = new Object();

    @Timed("jmh.timed.void")
    private void voidMethod() {
      value++;
    }

    @Timed("jmh.timed.primitive")
    private int primitiveReturnMethod() {
      return value + 1;
    }

    @Timed("jmh.timed.object")
    private Object objectReturnMethod() {
      return object;
    }

    @Timed("jmh.timed.static")
    private static void staticMethod() {
      Blackhole.consumeCPU(1);
    }
  }
}

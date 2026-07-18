# Benchmarking

LatencyClocked benchmark results are hardware, JVM, operating system, and configuration
dependent. This project provides JMH benchmarks so performance discussions can be based on
reproducible measurements rather than fixed claims.

Build and run the benchmark jar with:

```shell
mvn -pl benchmarking-jmh -am clean package
java -jar benchmarking-jmh/target/benchmarks.jar
```

The `-am` flag builds the local latency-clocked Maven plugin first so the benchmark classes
can be instrumented during `process-classes`.

Run only the benchmark class with:

```shell
java -jar benchmarking-jmh/target/benchmarks.jar \
  "com.ll.metrics.latency.jmh.LatencyClockedBenchmark.*"
```

Run the same benchmarks with JMH's GC profiler with:

```shell
java -jar benchmarking-jmh/target/benchmarks.jar \
  "com.ll.metrics.latency.jmh.LatencyClockedBenchmark.*" \
  -prof gc
```

On supported Linux setups with `perf` and `hsdis` available, `-prof perfasm` can be used
for assembly-level inspection. Treat profiler output as environment-specific evidence, not
as a portable performance claim.

The benchmark module covers:

- Plain method call baselines.
- Latency-clocked void, primitive-return, object-return, and static methods.
- Latency-clocked disabled-recording void, primitive-return, object-return, and static
  methods using `-Dlatency-clocked.enabled=false` semantics.
- Micrometer `@Timed` void, primitive-return, object-return, and static methods woven by
  AspectJ compile-time weaving.
- Latency-clocked methods bound through `LatencyClocked.initialise()`.
- Latency-clocked methods bound through `LatencyClocked.initialisedThreadSafe()`.

AspectJ compile-time weaving is configured only in `benchmarking-jmh`. The module first compiles
normally with javac so JMH annotation processing can generate benchmark harnesses. The
latency-clocked Maven plugin then instruments latency-clocked fixtures. Finally, the
AspectJ Maven plugin binary-weaves `target/classes` using `micrometer-core` as an aspect
library. The benchmark jar runs without `-javaagent`, load-time weaving, Spring, or Spring
AOP.

Weaving is verified by `BenchmarkInstrumentationTest`. The tests fail the
build if:

- The Micrometer fixture class does not contain woven `TimedAspect.aspectOf` calls.
- Invoking Micrometer `@Timed` fixture methods does not increment the expected timers.
- Invoking the Micrometer static fixture does not increment the expected timer.
- Latency-clocked startup does not bind the generated timer field and record a call.

Micrometer uses a `SimpleMeterRegistry` added to `Metrics.globalRegistry` once per JMH
trial and removed after the trial. The benchmark clears the global composite around that
lifecycle so only the benchmark registry is active. Meters are pre-registered during setup;
the benchmark invocation methods do not create registries or timers explicitly.

The comparison is intentionally semantic, not product-equivalence based:

- Latency-clocked currently records successful executions only.
- Micrometer `TimedAspect` records exceptional completion with an exception tag.
- Micrometer records through `MeterRegistry` and its broader meter abstraction.
- LatencyClocked records directly into a generated, startup-bound static timer field.
- When `latency-clocked.enabled=false`, startup binding still occurs and generated methods
  bypass their injected timing code through `LatencyClocked.enabled()`.
- Micrometer `TimedAspect` performs its normal steady-state meter resolution inside advice;
  the source-level benchmark method does not perform registry lookup itself.
- The primary benchmark set covers successful synchronous calls only.

When sharing results, include at least:

- JVM vendor and version.
- CPU model and core/thread count.
- Operating system and version.
- GC and relevant JVM flags.
- Warmup and measurement iterations.
- Fork count.
- Full command line used to run JMH.

Do not publish fixed performance claims without reproducible numbers and the environment
details needed to interpret them.

## Sample Report Format

The following sample is included only to show the kind of environment and output detail
that should accompany benchmark results. Treat the numbers as non-portable.

```text
java -version
java version "25.0.2" 2026-01-20 LTS
Java(TM) SE Runtime Environment (build 25.0.2+10-LTS-69)
Java HotSpot(TM) 64-Bit Server VM (build 25.0.2+10-LTS-69, mixed mode, sharing)

ver
Microsoft Windows [Version 10.0.26200.8737]

wmic cpu get Name, NumberOfCores, NumberOfLogicalProcessors
Name                                      NumberOfCores  NumberOfLogicalProcessors
Intel(R) Core(TM) i5-8250U CPU @ 1.60GHz  4              8

wmic memorychip get Capacity, Speed, DeviceLocator, Manufacturer
Capacity    DeviceLocator  Manufacturer  Speed
8589934592  DIMM A         859B0000802C  2400
8589934592  DIMM B         80AD000080AD  2400

plainObjectReturn                        avgt   10    0.870 +/-  0.072  ns/op
plainPrimitiveReturn                     avgt   10    0.849 +/-  0.012  ns/op
plainStaticCall                          avgt   10    5.451 +/-  0.075  ns/op
plainVoidCall                            avgt   10    1.593 +/-  0.033  ns/op

latencyClockedDisabledObjectReturn       avgt   10    1.281 +/-  0.214  ns/op
latencyClockedDisabledPrimitiveReturn    avgt   10    1.294 +/-  0.026  ns/op
latencyClockedDisabledStaticCall         avgt   10    6.173 +/-  0.125  ns/op
latencyClockedDisabledVoidCall           avgt   10    1.784 +/-  0.151  ns/op

baselineNanoTime                         avgt   10   22.199 +/-  2.083  ns/op

latencyClockedObjectReturn               avgt   10   50.321 +/-  0.682  ns/op
latencyClockedPrimitiveReturn            avgt   10   51.658 +/-  2.590  ns/op
latencyClockedStaticCall                 avgt   10   55.237 +/-  0.569  ns/op
latencyClockedVoidCall                   avgt   10   50.278 +/-  0.485  ns/op

latencyClockedThreadSafeObjectReturn     avgt   10   69.958 +/-  1.025  ns/op
latencyClockedThreadSafePrimitiveReturn  avgt   10   70.247 +/-  1.768  ns/op
latencyClockedThreadSafeStaticCall       avgt   10   75.320 +/-  6.178  ns/op
latencyClockedThreadSafeVoidCall         avgt   10   71.167 +/-  1.949  ns/op

micrometerTimedObjectReturn              avgt   10  370.990 +/- 40.544  ns/op
micrometerTimedPrimitiveReturn           avgt   10  361.648 +/- 15.381  ns/op
micrometerTimedStaticCall                avgt   10  367.560 +/- 19.636  ns/op
micrometerTimedVoidCall                  avgt   10  372.569 +/- 15.188  ns/op
```

In this sample, time attributed to LatencyClocked is dominated by two calls to
`System.nanoTime()`.

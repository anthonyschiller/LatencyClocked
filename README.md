# LatencyClocked

LatencyClocked is a small Java latency measurement toolkit.

This repository currently contains an early skeleton: a multi-module Maven project, a `@Timed` annotation, and small core timer APIs backed by an in-memory implementation.

Generated descriptor and bytecode member names are shared through a small Java 21
`latency-clocked-constants` module so runtime and Maven plugin code use the same constants.

Timer fields are registered at startup from index files named `META-INF/latency-clocked/index`.
Each non-comment line is an instrumented class name:

```text
<fully.qualified.ClassName>
```

`Timers` owns the application's timer catalogue. `LatencyClocked.initialise(timers)` loads
every index resource from the classpath, loads each class, invokes its generated
`__latency_clocked$bind(Timers)` method once at startup, and returns a `LatencyClocked`
runtime handle.

Generated bind methods assign fields with embedded timer ids:

```java
__latency_clocked_timer_0 = timers.timer("some.id");
```

Application code can keep the returned handle for explicit timer lookup:

```java
LatencyClocked latencyClocked = LatencyClocked.initialise(timers);
Timer timer = latencyClocked.timer("some.id");
```

Set `-Dlatency-clocked.enabled=false` to disable generated timer binding at startup.
When disabled, `LatencyClocked.initialise(timers)` skips descriptor loading and logs that
LatencyClocked is disabled. Explicit timer lookup through the returned handle still uses
the supplied `Timers` instance.

The Maven plugin provides `latency-clocked:scan`, bound by default to `process-classes`.
It scans compiled classes for `@Timed` methods, injects matching private static synthetic
`Timer` fields, injects `__latency_clocked$bind(Timers)`, instruments timed method bodies,
and writes class names to `target/classes/META-INF/latency-clocked/index`.
It also writes `target/latency-clocked/instrumentation-report.txt` with the instrumented
class, method, descriptor, generated field, and timer id. Use
`-Dlatency-clocked.verbose=true` to promote detailed plugin diagnostics from Maven debug
output to normal info output.

Generated field names such as `__latency_clocked_timer_0` now correspond to real
bytecode fields. Runtime startup no longer parses field names or timer ids from the index.
Timed methods call `System.nanoTime()` at method entry and record elapsed nanoseconds on
every normal return path. Exception exits intentionally do not record latency. Timing is
inserted into the original method body, so instrumentation adds no wrapper stack frames and
uses no runtime proxies. HDR histogram integration and benchmarks are intentionally not
implemented yet.

Supported `@Timed` targets include concrete instance methods, static methods, private
methods, final methods, and synchronized methods. Unsupported targets fail during build-time
scanning with class, method, descriptor, and reason: abstract methods, native methods,
constructors, class initialisers, and default interface methods. Compiler-generated synthetic
and bridge methods are skipped.

Runtime logging uses SLF4J and is intentionally limited to startup diagnostics around
descriptor loading and generated bind invocation. Applications that want asynchronous Log4j2
logging can use the SLF4J binding plus Disruptor:

```xml
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-slf4j2-impl</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-core</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>com.lmax</groupId>
  <artifactId>disruptor</artifactId>
  <scope>runtime</scope>
</dependency>
```

Configure Log4j2 with async loggers, for example by setting
`-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector`
and providing a normal `log4j2.xml`.

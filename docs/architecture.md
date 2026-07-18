# Architecture

LatencyClocked is split into small Maven modules:

- `model`: shared timer APIs, snapshot values, and generated instrumented class-index/bytecode constants,
  compiled to Java 21 for Maven plugin compatibility.
- `hdr`: HDR Histogram backed timer implementation.
- `core`: startup registration API and runtime handle, using HDR timers by default.
- `maven-plugin`: build-time instrumented class-index generation and timer field injection.
- `benchmarking-jmh`: isolated JMH benchmark module.

The `model` module contains shared API contracts under `com.ll.metrics.latency.timer`,
snapshot records under `com.ll.metrics.latency.snapshot`, `@Timed` under
`com.ll.metrics.latency.annotations`, and generated name constants under
`com.ll.metrics.latency.constants`. `Timers` is the central owner and catalogue for generated
method timers. `claim(methodId)` is an instrumentation SPI used only by generated
`__latency_clocked$bind` methods; it returns the canonical timer for one generated method id.
Application code should use snapshots for reporting instead of obtaining mutable timers.
`snapshots()` returns immutable snapshot values for the registered timers, including count,
min, max, mean, p50, p90, p95, p99, and p999.

Package ownership is intentionally narrow:

| Package | Module | Purpose |
| --- | --- | --- |
| `com.ll.metrics.latency.annotations` | `model` | Public source annotations such as `@Timed`. |
| `com.ll.metrics.latency.constants` | `model` | Shared class-index paths and generated member names. |
| `com.ll.metrics.latency.snapshot` | `model` | Immutable latency snapshot records. |
| `com.ll.metrics.latency.timer` | `model` | Public timer contracts and lightweight in-memory support implementation. |
| `com.ll.metrics.latency.hdr` | `hdr` | HDR Histogram backed production timer implementation. |
| `com.ll.metrics.latency.core` | `core` | Runtime startup and generated timer binding. |
| `com.ll.metrics.latency.maven` | `maven-plugin` | Maven goal orchestration and instrumentation reporting. |
| `com.ll.metrics.latency.maven.asm` | `maven-plugin` | ASM-specific scanning and bytecode rewriting. |
| `com.ll.metrics.latency.maven.model` | `maven-plugin` | Build-time metadata records used by the plugin. |

The `hdr` module provides two HDR-backed timer variants. `HdrTimers.create()` creates
`HdrTimer` instances backed by a regular HDR Histogram for lower-overhead single-writer
recording. `HdrTimers.createWithThreadsafeTimers()` creates `ThreadSafeHdrTimer` instances
backed by HDR Histogram's `ConcurrentHistogram` for concurrent recording. Core APIs remain
histogram-agnostic: neither `Timer`, `Timers`, `LatencySnapshot`, nor `TimerSnapshot` expose
HDR Histogram types.

The public `LatencyClocked` runtime handle is HDR-first. Production startup should normally call
`LatencyClocked.initialise()` for single-writer timers or
`LatencyClocked.initialisedThreadSafe()` when timers may be recorded by multiple threads.
Both create an HDR-backed timer catalogue internally and then bind generated fields. HDR is
the default because latency telemetry needs percentile snapshots and bounded memory usage;
each timer keeps a compact histogram rather than retaining every observed duration.
`LatencyClocked.initialise(timers)` remains available for tests and custom timer catalogues.
The in-memory timer implementation in `model` is retained as a lightweight support/testing
implementation rather than the production default.

Startup timer registration is driven-off the instrumented class-index. Classpath modules may 
publish files named `META-INF/latency-clocked/index`. `LatencyClocked.initialise()` or 
`LatencyClocked.initialise(timers)` loads all matching resources from the context classloader
and returns a `LatencyClocked` runtime handle. Each active line has this format:

```text
<fully.qualified.ClassName>
```

Blank lines and lines starting with `#` are ignored. Duplicate class names are ignored.
For each class entry, core loads the class, finds generated static method
`__latency_clocked$bind(Timers)`, makes it accessible if needed, and invokes it with the active
`Timers` instance. Reflection is limited to startup registration; recording remains on the
`Timer` hot path. Reporting is exposed through immutable snapshots on the returned handle or
the supplied `Timers` instance.

`LatencyClocked.initialise(timers)` has explicit owner semantics. The first successful call
establishes the supplied `Timers` instance as the owner for generated static timer fields. Later
calls with that same instance are no-ops. Later calls with another instance fail because rebinding
would split generated static fields from the snapshot owner observed by application code. Identity
comparison is used deliberately; two `Timers` instances that compare equal are still different
owners for generated `Timer` references.

If initialization fails after binding some classes, those partial bindings are retained. The same
owner can retry because generated bind methods are safe to invoke repeatedly and
`Timers.claim(methodId)` returns the same canonical timer for the exact same generated method
identity. Partial bindings are not rolled back because timed methods may already have recorded
data into those timers. Retrying with a different owner is rejected.

Concurrent initialization calls are serialized. A recursive initialization call from code already
executing during startup fails clearly rather than recursing. If a timed method is invoked while
LatencyClocked is enabled but before binding has occurred, generated bytecode fails fast with an
actionable `IllegalStateException` instead of a raw null timer failure.

Recording can be disabled at startup with system property `latency-clocked.enabled=false`.
Startup still reads the instrumented class-index resource and invokes generated bind methods,
so generated fields are ready if recording is later enabled dynamically. This is an operational
escape hatch for disabling generated timing without changing the application classpath.

The enabled state is globally managed by `LatencyClocked.enabled()` and exposed through JMX at
`com.ll.metrics.latency:type=LatencyClocked`, attribute `Enabled`. Instrumented methods read the
volatile enabled flag directly; they do not perform registry lookup, reflection, allocation, or
JMX calls on the hot path.

In multi-module applications, each module may contribute its own `META-INF/latency-clocked/index`
resource. Runtime startup loads all matching resources from the classpath and wires all
generated timer fields once.

The Maven plugin currently implements `latency-clocked:instrument`, bound to `process-classes`.
It scans `${project.build.outputDirectory}` with ASM, finds methods annotated with
`@Timed`, injects one private static synthetic `Timer` field per timed method, injects
`__latency_clocked$bind(Timers)`, instruments timed method bodies, and writes class names
to `target/classes/META-INF/latency-clocked/index`.
The goal emits a deterministic report at
`target/latency-clocked/instrumentation-report.txt` containing one line per instrumented
method with class name, method name, descriptor, generated field, timer id, and source
class file. Detailed diagnostics are logged at Maven debug level, or at info level when
`-Dlatency-clocked.verbose=true` is supplied.

Field names generated by the scanner, such as `__latency_clocked_timer_0`, now
correspond to real bytecode fields. Timer ids are embedded into the generated bind method,
which assigns fields during startup with the equivalent of:

```java
__latency_clocked_timer_0 = timers.claim("com.example.Service#call()V");
```

Field and bind  method injection are idempotent: if the plugin sees generated members that already
exist, it skips injecting them again. Re-running the plugin keeps the instrumented class-index 
stable, does not duplicate timer fields or bind methods, and does not double-instrument method 
bodies.

A method timer represents only complete successful executions of one `@Timed` method.
Each generated timer field is private, static, synthetic, and associated with exactly one
annotated method. `@Timed` is intentionally a marker annotation; generated method ids are the
only timer ownership keys. Generated ids use JVM method descriptors, for example
`com.example.Service#call()V`, so overloads and unusual package/type names remain
collision-resistant.

Timed method bodies are instrumented in place. At method entry, generated bytecode reads
`LatencyClocked.enabled()` once, stores that invocation-local value, and captures
`System.nanoTime()` only when enabled. Before each normal return, it records
`System.nanoTime() - start` through the generated static `Timer` field only when the invocation
started enabled and recording is still enabled at exit. Void, primitive, object, static,
concrete instance, private, final, synchronized, overloaded, and multi-return methods are
supported. Common control-flow shapes are supported, including nested branches, switch
statements, loops with early returns, try/catch blocks that return normally, and try/finally
blocks that return normally. If a method exits by throwing an exception, latency is intentionally
not recorded.

Instrumentation preserves the original method contract and adds no wrapper stack frames,
runtime proxies, delegate objects, or hot-path reflection. Reflection remains limited to
startup binding through `LatencyClocked.initialise()` and `LatencyClocked.initialise(timers)`.

Runtime startup logging uses SLF4J. The core module depends only on `slf4j-api`; applications
choose the binding. For production deployments that want asynchronous logging, use Log4j2's
SLF4J 2 binding with `log4j-core` and LMAX Disruptor, then enable async loggers through
Log4j2 configuration. Instrumented method bodies do not log.

The `benchmarking-jmh` module contains reproducible JMH benchmarks for plain calls, instrumented
calls, and direct timer recording. It depends on JMH and shades a standalone
`benchmarks.jar`, but those dependencies are isolated to the benchmark module and do not
affect normal library consumers.

Unsupported annotated targets fail during build-time scanning with clear messages:
constructors, class initialisers, native methods, abstract methods, and default interface
methods. Diagnostics include class name, method name, method descriptor, and the unsupported
reason. Synthetic and bridge methods are skipped.

The `model` and Maven plugin modules intentionally compile to Java 21 bytecode even though
the rest of the project targets Java 25. Maven Plugin Tools currently cannot process Java 25
plugin class files while generating plugin descriptors, so shared API/constants used by the
plugin must live in the Java 21-compatible `model` module.

Release packaging attaches main, source, and Javadoc jars for publishable modules. API
compatibility checking is configured behind the `api-compat` Maven profile; after the first
release, pass `-Dapi.previous.version=<released-version>` to compare the current build against a
released baseline.

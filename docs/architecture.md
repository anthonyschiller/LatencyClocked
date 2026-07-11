# Architecture

LatencyClocked is split into small Maven modules:

- `model`: shared timer APIs, snapshot values, and generated descriptor/bytecode constants,
  compiled to Java 21 for Maven plugin compatibility.
- `hdr`: HDR Histogram backed timer implementation.
- `core`: startup registration API and runtime handle, using HDR timers by default.
- `maven-plugin`: build-time descriptor generation and timer field injection.
- `latency-jmh`: isolated JMH benchmark module.

The `model` module contains shared API contracts under `com.ll.metrics.latency.timer`,
snapshot records under `com.ll.metrics.latency.snapshot`, `@Timed` under
`com.ll.metrics.latency.annotations`, and generated name constants under
`com.ll.metrics.latency.constants`. `Timers` is the central owner and catalogue for all
application timers. `timer(id)` returns the existing timer for an id or creates and
registers one if it does not already exist. `snapshots()` returns immutable snapshot values
for the registered timers, including count, min, max, mean, p50, p90, p95, p99, and p999.

Package ownership is intentionally narrow:

| Package | Module | Purpose |
| --- | --- | --- |
| `com.ll.metrics.latency.annotations` | `model` | Public source annotations such as `@Timed`. |
| `com.ll.metrics.latency.constants` | `model` | Shared descriptor paths and generated member names. |
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

`LatencyClocked` is HDR-first. Production startup should normally call
`LatencyClocked.initialise()` for single-writer timers or
`LatencyClocked.initialisedThreadSafe()` when timers may be recorded by multiple threads.
Both create an HDR-backed timer catalogue internally and then bind generated fields. HDR is
the default because latency telemetry needs percentile snapshots and bounded memory usage;
each timer keeps a compact histogram rather than retaining every observed duration.
`LatencyClocked.initialise(timers)` remains available for tests and custom timer catalogues.
The in-memory timer implementation in `model` is retained as a lightweight support/testing
implementation rather than the production default.

Startup timer registration is descriptor-based. Classpath modules may publish files named
`META-INF/latency-clocked/index`; `LatencyClocked.initialise()` or
`LatencyClocked.initialise(timers)` loads all matching resources from the context classloader
and returns a `LatencyClocked` runtime handle. Each active line has this format:

```text
<fully.qualified.ClassName>
```

Blank lines and lines starting with `#` are ignored. Duplicate class names are ignored.
For each class entry, core loads the class, finds generated static method
`__latency_clocked$bind(Timers)`, makes it accessible if needed, and invokes it with the active
`Timers` instance. Reflection is limited to startup registration; recording remains on the
`Timer` hot path. Explicit timer lookup is available through the returned handle with
`latencyClocked.timer(id)`.

Startup binding can be disabled with system property `latency-clocked.enabled=false`.
When disabled, startup does not read descriptor resources or invoke generated bind methods;
it only returns the runtime handle and logs that binding is disabled. This is an operational
escape hatch for disabling generated timing without changing the application classpath.

In multi-module applications, each module may contribute its own `META-INF/latency-clocked/index`
resource. Runtime startup loads all matching resources from the classpath and wires all
generated timer fields once.

The Maven plugin currently implements `latency-clocked:scan`, bound to `process-classes`.
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
__latency_clocked_timer_0 = timers.timer("some.id");
```

Runtime startup no longer parses field names or timer ids from the index. Field and bind
method injection are idempotent: if the plugin sees generated members that already exist,
it skips injecting them again.

Timed method bodies are instrumented in place. At method entry, the generated bytecode
captures `System.nanoTime()` into a new local variable. Before each normal return, it
records `System.nanoTime() - start` through the generated static `Timer` field. Void,
primitive, object, static, concrete instance, private, final, synchronized, overloaded, and
multi-return methods are supported. Common control-flow shapes are supported, including
nested branches, switch statements, loops with early returns, try/catch blocks that return
normally, and try/finally blocks that return normally. If a method exits by throwing an
exception, latency is intentionally not recorded.

Instrumentation preserves the original method contract and adds no wrapper stack frames,
runtime proxies, delegate objects, or hot-path reflection. Reflection remains limited to
startup binding through `LatencyClocked.initialise()` and `LatencyClocked.initialise(timers)`.

Runtime startup logging uses SLF4J. The core module depends only on `slf4j-api`; applications
choose the binding. For production deployments that want asynchronous logging, use Log4j2's
SLF4J 2 binding with `log4j-core` and LMAX Disruptor, then enable async loggers through
Log4j2 configuration. Instrumented method bodies do not log.

The `latency-jmh` module contains reproducible JMH benchmarks for plain calls, instrumented
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

# LatencyClocked

LatencyClocked is a small Java latency measurement toolkit for build-time bytecode instrumentation
of `@Timed` annotated methods. Designed to keep the overhead of profiling the execution time of 
`@Timed` annotated methods to a minimum - allocation-conscious, no runtime proxies, no wrapper 
stack frames or hot-path reflection.

## Quick Start

Add the runtime module:

```xml
<dependency>
  <groupId>com.ll.metrics</groupId>
  <artifactId>latency-clocked-core</artifactId>
  <version>{released-version}</version>
</dependency>
```

For multi-module projects, those modules that only declare `@Timed` methods and do not initialize
or read latency snapshots may depend purely on:

```xml
<dependency>
  <groupId>com.ll.metrics</groupId>
  <artifactId>latency-clocked-model</artifactId>
  <version>{released-version}</version>
</dependency>
```

Configure the Maven plugin in every module that contains `@Timed` methods:

```xml
<plugin>
  <groupId>com.ll.metrics</groupId>
  <artifactId>latency-clocked-maven-plugin</artifactId>
  <version>{project.version}</version>
  <executions>
    <execution>
      <goals>
        <goal>scan</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Annotate concrete methods:

```java
import com.ll.metrics.latency.annotations.Timed;

final class PricingService {
  @Timed
  long price(Order order) {
    return 100L * order.quantity();
  }
}
```

Initialize once during startup and read snapshots:

```java
import com.ll.metrics.latency.core.LatencyClocked;

LatencyClocked latencyClocked = LatencyClocked.initialise();

// invoke application code

latencyClocked.snapshots().forEach(System.out::println);
```

Application code should not call `Timers.claim(...)`. That method is an instrumentation SPI used
only by generated `__latency_clocked$bind(Timers)` methods.

## Runtime Model

The plugin provides `latency-clocked:scan`, bound by default to `process-classes`. It scans
compiled classes for `@Timed`, injects private static synthetic timer fields, injects a generated
`__latency_clocked$bind(Timers)` method, instruments timed method bodies, and writes class names to
`target/classes/META-INF/latency-clocked/index`.

Each module packages its own index in its jar. At startup, `LatencyClocked.initialise(...)` loads all
`META-INF/latency-clocked/index` resources visible on the classpath and invokes generated bind
methods. Multi-module applications therefore work by configuring the plugin in every module with
timed methods and calling `LatencyClocked.initialise(...)` once in the application module.

`@Timed` is intentionally a marker annotation. It has no custom value, display name, or alias.
Every annotated method owns exactly one timer, and a method timer records only complete successful
executions of that method. Exceptions are intentionally excluded.

Public snapshots expose count, min, max, mean, p50, p90, p95, p99, and p999 in nanoseconds. HDR
Histogram is used internally by `latency-clocked-hdr`; histogram types are not part of the public
API. `LatencyClocked.initialise()` uses lower-overhead single-writer HDR timers, and
API. `LatencyClocked.initialise()` uses lower-overhead single-writer HDR timers, and
`LatencyClocked.initialisedThreadSafe()` uses HDR `ConcurrentHistogram` timers.

Set `-Dlatency-clocked.enabled=false` to disable generated timer binding at startup. Instrumented
methods bypass generated timing while `LatencyClocked.enabled()` is false.

## Supported Methods

Supported `@Timed` targets include concrete instance methods, static methods, private methods,
final methods, synchronized methods, overloaded methods, multiple normal return paths, loops,
switches, and normal returns from try/catch/finally control flow.

Unsupported targets fail during build-time scanning with class, method, descriptor, and reason:
abstract methods, native methods, constructors, class initialisers, and default interface methods.
Compiler-generated synthetic and bridge methods are skipped.

## Examples And Docs

The `example-application` module is a complete framework-independent example. It demonstrates plugin
configuration, instance/static/overloaded timed methods, explicit startup, and snapshot reporting.

Further documentation:

- `docs/architecture.md`
- `docs/design-principles.md`
- `docs/instrumentation.md`
- `docs/runtime.md`
- `docs/timer-identity.md`
- `docs/edge-cases.md`
- `docs/benchmarking.md`
- `docs/troubleshooting.md`

Useful build commands:

```shell
mvn verify
mvn -pl maven-plugin -am -Pplugin-it verify
mvn -Pdependency-updates validate
mvn -Papi-compat -Dapi.previous.version=<released-version> verify
```

Benchmarks:

```shell
mvn -pl benchmarking-jmh -am clean package
java -jar benchmarking-jmh/target/benchmarks.jar \
  "com.ll.metrics.latency.jmh.LatencyClockedBenchmark.*"
java -jar benchmarking-jmh/target/benchmarks.jar \
  "com.ll.metrics.latency.jmh.LatencyClockedBenchmark.*" \
  -prof gc
```

Benchmark results are hardware, JVM, OS, and configuration dependent. No fixed performance claims
are made without reproducible numbers.

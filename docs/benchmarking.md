# Benchmarking

LatencyClocked benchmark results are hardware, JVM, operating system, and configuration
dependent. This project provides JMH benchmarks so performance discussions can be based on
reproducible measurements rather than fixed claims.

Build and run the benchmark jar with:

```shell
mvn -pl latency-jmh -am clean package
java -jar latency-jmh/target/benchmarks.jar
```

The `-am` flag builds the local latency-clocked Maven plugin first so the benchmark classes
can be instrumented during `process-classes`.

The benchmark module covers:

- Plain method call baselines.
- Instrumented void, primitive-return, object-return, and static methods.
- Instrumented methods bound through `LatencyClocked.initialise()`.
- Instrumented methods bound through `LatencyClocked.initialisedThreadSafe()`.

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

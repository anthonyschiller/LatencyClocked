# latency-jmh

This module contains the JMH benchmark jar for LatencyClocked. It is intentionally isolated
from the runtime modules so JMH, Micrometer, and AspectJ dependencies do not affect library
consumers.

Build:

```shell
mvn -pl latency-jmh -am clean package
```

Run only the benchmark class:

```shell
java -jar latency-jmh/target/benchmarks.jar \
  "com.ll.metrics.latency.jmh.LatencyClockedBenchmark.*"
```

Run with allocation/GC profiling:

```shell
java -jar latency-jmh/target/benchmarks.jar \
  "com.ll.metrics.latency.jmh.LatencyClockedBenchmark.*" \
  -prof gc
```

The module uses AspectJ compile-time weaving for Micrometer `@Timed` benchmarks. No
Spring, Spring AOP, Java agent, or load-time weaving is used. The build verifies weaving by
checking woven bytecode and by asserting that Micrometer timers are incremented by the
fixture calls.
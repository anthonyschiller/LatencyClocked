# Runtime

Applications initialize generated timer binding once during startup:

```java
LatencyClocked latencyClocked = LatencyClocked.initialise();
```

`LatencyClocked.initialise(...)` reads every `META-INF/latency-clocked/index` resource visible to
the context class loader. For each class name, it loads the class and invokes generated
`__latency_clocked$bind(Timers)`.

Repeated initialisation is safe. Generated fields are rebound to timers from the latest supplied
catalogue. Invoking an instrumented method before startup binding fails clearly while latency
recording is enabled.

`latency-clocked.enabled=false` disables descriptor loading and generated timing. Instrumented
methods branch around their injected timing code while disabled.

Snapshots are read through the returned `LatencyClocked` handle. Advanced tests or custom timer
catalogues may also read through `Timers.snapshots()`. Snapshot latency values are nanoseconds
and are detached from mutable timer state.

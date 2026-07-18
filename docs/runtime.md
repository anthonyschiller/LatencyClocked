# Runtime

Applications initialize generated timer binding once during startup:

```java
LatencyClocked latencyClocked = LatencyClocked.initialise();
```

`LatencyClocked.initialise(...)` reads every `META-INF/latency-clocked/index` resource visible to
the context class loader. For each class name, it loads the class and invokes generated
`__latency_clocked$bind(Timers)`.

Initialization is thread-safe and serialized. The first successful call establishes one `Timers`
owner by object identity and binds generated static timer fields to timers claimed from that
owner. Repeated calls with that exact same `Timers` instance are no-ops. Calls with any different
`Timers` instance fail, even if the instances compare equal or have identical configuration.

If initialization fails partway through, successful partial bindings are not rolled back. The same
owner may retry because generated bind methods are repeatable and `claim(methodId)` returns the
same canonical `Timer` for repeated claims. A different owner is rejected after failure so partial
bindings cannot silently move from one timer catalogue to another.

Recursive initialization from code executed during discovery, class loading, or generated binding
is rejected with a clear failure. Concurrent calls from other threads wait for the active startup
attempt to finish and then re-evaluate the resulting state.

Invoking an instrumented method before startup binding fails clearly while latency recording is
enabled.

`latency-clocked.enabled=false` starts the runtime with latency recording disabled. Startup still
loads instrumented class indexes and binds generated timer fields, which lets operators enable
recording later without restarting the application.

The enabled state is exposed through JMX as object name
`com.ll.metrics.latency:type=LatencyClocked`, attribute `Enabled`. Updating that attribute changes
the same volatile flag read by generated instrumentation; generated code does not call through JMX.

An individual invocation records only when recording was enabled at method entry and remains
enabled at successful method exit. If recording is disabled at either boundary, the invocation is
discarded. The entry check avoids `System.nanoTime()` while disabled; the exit check avoids timer
access, binding checks, `System.nanoTime()`, and `Timer.record(...)` after recording has been
disabled.

Snapshots are read through the returned `LatencyClocked` handle. Advanced tests or custom timer
catalogues may also read through `Timers.snapshots()`. Snapshot latency values are nanoseconds
and are detached from mutable timer state.

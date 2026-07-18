# Troubleshooting

## Missing Startup Initialisation

Symptom: invoking an instrumented `@Timed` method throws an `IllegalStateException` saying a
LatencyClocked timer field is not bound.

Cause: generated timer fields are assigned during startup by `LatencyClocked.initialise(...)`.

Fix: call `LatencyClocked.initialise()` or `LatencyClocked.initialise(timers)` before timed methods
can run. For concurrent applications, use `LatencyClocked.initialisedThreadSafe()`.

## Rebinding To Another Timers Instance

Symptom: startup fails with a message saying latency has already associated instrumented method
timers with a different `Timers` instance.

Cause: generated timer fields were already bound, or a previous failed startup attempt partially
bound fields, using another `Timers` owner. Runtime ownership is based on instance identity, not
`equals`, because generated fields hold concrete `Timer` references.

Fix: initialize once during startup and keep using the returned `LatencyClocked` handle for
snapshots. If startup failed and must be retried, retry with the same `Timers` instance.

## Recursive Initialisation

Symptom: startup fails with a recursive initialization error.

Cause: code executed during class loading or generated binding called `LatencyClocked.initialise`
again on the same thread.

Fix: move startup initialization earlier in application bootstrapping so it is not triggered from
instrumented class initialization or generated bind code.

## Plugin Not Configured In A Module

Symptom: a module contains `@Timed` methods but no timers are registered for those methods.

Cause: each Maven module that contains `@Timed` methods must run `latency-clocked:instrument`.
Multi-module applications may produce one `META-INF/latency-clocked/index` per module.

Fix: configure `latency-clocked-maven-plugin` in every module with timed methods and run at
least through `process-classes`.

## Missing Generated Index

Symptom: startup does not bind any timers for a module.

Cause: `target/classes/META-INF/latency-clocked/index` was not generated or was not packaged
into the module jar.

Fix: check the plugin output and `target/latency-clocked/instrumentation-report.txt`. The
index should contain class names only, one per line, with no field names or timer ids.

## Unsupported Timed Methods

Symptom: the Maven build fails during `latency-clocked:instrument`.

Cause: unsupported `@Timed` usage. Abstract methods, native methods, constructors, class
initialisers, and default interface methods are unsupported. Synthetic and bridge methods are
skipped.

Fix: move `@Timed` to a concrete class method with a bytecode body. Error messages include
the class name, method name, descriptor, and reason.

## Duplicate Instrumentation

Symptom: concern that running the plugin twice might inject duplicate fields or double-record.

Behaviour: the plugin is idempotent. Existing generated timer fields, bind methods, and timed
method instrumentation are detected and skipped. The class index is written deterministically.

## Disabled Runtime

Symptom: no generated timings are recorded even though methods are instrumented.

Cause: `latency-clocked.enabled=false` disables generated recording, or the JMX `Enabled`
attribute has been set to `false`. Startup still binds generated timer fields while disabled so
recording can be enabled later.

Fix: remove the property, set it to `true`, or set JMX attribute
`com.ll.metrics.latency:type=LatencyClocked` / `Enabled` to `true`.

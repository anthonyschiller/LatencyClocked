# Troubleshooting

## Missing Startup Initialisation

Symptom: invoking an instrumented `@Timed` method throws an `IllegalStateException` saying a
LatencyClocked timer field is not bound.

Cause: generated timer fields are assigned during startup by `LatencyClocked.initialise(...)`.

Fix: call `LatencyClocked.initialise()` or `LatencyClocked.initialise(timers)` before timed
methods can run. For concurrent applications, use `LatencyClocked.initialisedThreadSafe()`.

## Plugin Not Configured In A Module

Symptom: a module contains `@Timed` methods but no timers are registered for those methods.

Cause: each Maven module that contains `@Timed` methods must run `latency-clocked:scan`.
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

Symptom: the Maven build fails during `latency-clocked:scan`.

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

Cause: `latency-clocked.enabled=false` disables descriptor loading and generated timing code.

Fix: remove the property or set it to `true`, then call `LatencyClocked.initialise(...)`
during startup.

# Instrumentation Edge Cases

LatencyClocked instruments `@Timed` methods in place at build time. There are no wrapper
methods, runtime proxies, or hot-path reflection calls.

Successful method executions record exactly once before the normal return instruction.
Methods that exit by throwing an exception record zero times, including exceptions thrown
directly, rethrown from catch blocks, or thrown from catch blocks after handling another
exception.

Supported method shapes:

- Concrete instance methods
- Static methods
- Private methods
- Final methods
- Synchronized methods
- Void return methods
- Primitive return methods
- Object return methods
- Overloaded methods
- Multiple normal return paths
- Nested if/else branches
- Switch statements and expressions
- Loops with early return
- Try/catch blocks where the try or catch returns normally
- Try/finally blocks where the method returns normally

Unsupported annotated targets fail during build-time scanning:

- Abstract methods, including abstract interface methods, because there is no method body to
  instrument
- Native methods because there is no bytecode body to instrument
- Constructors because constructor bytecode has special initialization rules
- Class initialisers
- Default interface methods; annotate a concrete class method instead

Errors include the class name, method name, method descriptor, and the reason instrumentation
is unsupported. Synthetic and bridge methods are skipped by the scanner, so compiler-generated
bridge methods do not receive their own timers. The plugin continues to use ASM frame and
max-stack computation so instrumented classes must load normally; tests fail if a class cannot
be verified.

Startup lifecycle cases:

- Calling `LatencyClocked.initialise(timers)` once binds generated timer fields.
- Calling `LatencyClocked.initialise(timers)` again with the same `Timers` instance is a no-op.
- Calling `LatencyClocked.initialise(otherTimers)` after success or partial failure is rejected.
- A failed startup attempt may be retried with the original `Timers` instance.
- Concurrent startup calls are serialized; same-thread recursive startup is rejected.
- Calling an instrumented method before startup binding fails with an actionable
  `IllegalStateException` when LatencyClocked is enabled.
- Calling an instrumented method while `latency-clocked.enabled=false` bypasses generated
  timing code.
- A method timer represents only complete successful executions of one `@Timed` method.
  `@Timed` is intentionally a marker annotation, so timer ownership is always method-scoped.

Build idempotency cases:

- Running the plugin more than once keeps the index content stable.
- Generated timer fields are not duplicated.
- The generated `__latency_clocked$bind(Timers)` method is not duplicated.
- Timed method bodies are not instrumented twice, so successful calls do not double-record.

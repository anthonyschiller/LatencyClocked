# Timer Identity

A method Timer records only complete successful executions of one `@Timed` method.

`@Timed` has no configurable value, alias, display name, or custom name. Every timer identity is
generated from the concrete method. Alias support is deferred because a shared or user-chosen name
can blur ownership between methods and programmatic measurements.

Generated canonical identities currently use JVM method descriptors:

```text
<fully.qualified.ClassName>#<methodName><methodDescriptor>
```

Examples:

```text
com.acme.pricing.PricingService#calculate()J
com.acme.pricing.PricingService#calculate(Lcom/acme/orders/Order;)J
```

This format is deterministic across equivalent builds, does not depend on discovery order or
generated field sequence, distinguishes overloaded methods, and avoids collisions caused by
dot-delimited parameter names.

Generated bind methods call `Timers.claim(methodId)` with these identities. `claim` is an
instrumentation SPI:

- it is called by generated `__latency_clocked$bind(Timers)` methods
- it returns the canonical timer for exactly one generated method identity
- repeated claiming of the same identity returns the same timer for safe repeated startup
- application code must not use it for ad hoc or programmatic timing

Application code should report through immutable `TimerSnapshot` values. Method timers are not
reused for programmatic measurement because that would break the one-to-one method ownership
invariant.

# Instrumentation

The Maven goal is `latency-clocked:scan`, bound by default to `process-classes`.

For every compiled class with supported `@Timed` methods, the plugin:

- injects one private static synthetic `Timer` field per annotated method
- injects one static synthetic `__latency_clocked$bind(Timers)` method
- instruments each timed method body with `System.nanoTime()` on entry and `Timer.record(long)`
  before every normal return
- writes the class name to `META-INF/latency-clocked/index`

The index contains class names only. Timer identities are embedded in generated bind methods, not
parsed by runtime startup.

Exception exits intentionally do not record latency. Constructors, class initialisers, abstract
methods, native methods, and default interface methods are unsupported.

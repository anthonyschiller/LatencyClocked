# Design Principles

LatencyClocked keeps the runtime model narrow:

- Instrumentation happens at build time, not through runtime proxies or Java agents.
- Timed method bodies record directly into generated static timer fields.
- Startup reflection is allowed for binding generated fields; hot-path reflection is not.
- A method timer represents only complete successful executions of one `@Timed` method.
- Application code reads immutable snapshots and does not obtain mutable timers through a lookup API.
- `@Timed` is a marker annotation; custom names and aliases are deferred design topics.

The public API remains histogram-agnostic but by default runtime is HDR-first.

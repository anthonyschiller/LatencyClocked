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

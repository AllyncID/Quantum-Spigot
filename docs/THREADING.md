# Threading

Bukkit events, synchronous scheduler execution, world mutation, inventories,
entity updates and network packet ordering retain the upstream model.
The new instrumentation hooks execute on the authoritative server thread.

Only diagnostic report IO is offloaded. The payload crossing the boundary is
an immutable string plus an integer retention limit. The worker does not
retain a world, entity, plugin task or mutable configuration object.

The diagnostics executor has one daemon thread named `Quantum Diagnostics`,
a bounded queue and abort/rejection handling. Rejection increments a metric;
it never invokes work on the caller. Exceptions are logged and counted.
Shutdown allows five seconds to drain, then interrupts remaining work and
reports abandonment. Diagnostic failure does not alter upstream world saving.

The worker allocator reserves processors globally for future Quantum domains.
Unallocated capacity remains unused. It does not create five copies of the
processor count or attempt to reconfigure upstream workers. Low-core machines
retain at least one diagnostic slot. Explicit maxima are ceilings, not a
request to oversubscribe processors.

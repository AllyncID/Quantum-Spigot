# Threading

Bukkit events, synchronous scheduler execution, world mutation, inventories,
entity updates and network packet ordering retain the upstream model.
The new instrumentation hooks execute on the authoritative server thread.

Diagnostic report IO is offloaded. The payload crossing the boundary is
an immutable string plus an integer retention limit. The worker does not
retain a world, entity, plugin task or mutable configuration object.

The diagnostics executor has one daemon thread named `Quantum Diagnostics`,
a bounded queue and abort/rejection handling. Rejection increments a metric;
it never invokes work on the caller. Exceptions are logged and counted.
Shutdown allows five seconds to drain, then interrupts remaining work and
reports abandonment. Diagnostic failure does not alter upstream world saving.

The optional chunk serializer copies section data on the owner thread, after
reserving a bounded admission slot. A worker encodes only those copies and
publishes the existing packet-ready barrier. Heightmaps, block entities, light,
visibility and plugin events remain on the owner; Anti-Xray modification falls
back to native serialization. Queue overflow also uses the native path before
copying. Disconnected queued jobs are discarded; a serialization failure closes
the affected connection through an owner-thread task rather than publishing a
partial packet. Shutdown drains for five seconds and reports abandoned jobs.
This pipeline does not parallelize world access or client chunk application.

Optional per-world timing and plugin attribution are observations on the owner
thread. Event trees retain inclusive and exclusive durations with bounded
groups/recent samples; sampled output is diagnostic evidence, not a total-time
estimate. Async plugin work, direct NMS calls and native Brigadier commands are
outside that hook coverage. Storage counters read Moonrise's existing atomic
in-flight task counters and autosave queue; they do not add a save worker.

Tracked inventories and block-entity sleep/wake hooks run on the owner thread.
Sleeping retains native ticker order, removal checks and mid-tick task cadence.
Workers never mutate an inventory, wake a machine or dispatch a plugin event.

The worker allocator reserves processors globally for Quantum domains.
Chunk workers are capped by the capacity left after the diagnostic slot;
remaining capacity is unused. It does not create five copies of the
processor count or attempt to reconfigure upstream workers. Low-core machines
retain at least one diagnostic slot. Explicit maxima are ceilings, not a
request to oversubscribe processors.

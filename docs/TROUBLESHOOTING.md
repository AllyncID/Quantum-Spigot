# Troubleshooting

* Java version errors: run `java -version`; both Gradle and the server should
  use JDK 25. On Windows, JAVA_HOME and PATH may point to different versions.
* Patch failure: confirm the pinned upstream and a clean generated tree, then
  use `applyAllPatches`. Do not hide a conflict by removing a patch.
* Dependency download failure: retry the wrapper after connectivity recovers.
  Preserve checksum verification; do not substitute an unverified jar.
* Configuration startup failure: inspect the named file/key and correct the
  value. Quantum intentionally leaves malformed files intact.
* Empty MSPT: wait for ticks, or check `diagnostics.enabled`. A no-sample state
  is not reported as a healthy zero-ms server.
* High p95: record Spark/JFR with workload details. `/quantum plugins` only
  covers synchronous scheduled tasks, not all plugin event/command handlers.
* Missing lag report: inspect writer rejection/error counters and log messages.
  Reports are bounded and may be dropped under IO overload.
* No async speedup: Phase 1 adds instrumentation only. Compute offload and
  adaptive simulation remain unimplemented.

Share sanitized reports and exact versions when investigating a problem.
Reports should not include authentication data, addresses or player identities.

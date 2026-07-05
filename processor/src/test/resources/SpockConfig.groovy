// Project-owned Spock configuration (change type-query-seam, task 5.3), auto-discovered from the test classpath —
// no jvmArg needed, applies uniformly to `test` and `pitest`. Exists so the build never depends on a developer's
// machine-global ~/.spock/SpockConfig.groovy, whose defaults (optimizeRunOrder on, in-JVM parallel execution on, a
// short global timeout) are outside the repo's control and were observed to corrupt stale per-spec run-history
// entries (ClassCastException in OptimizeRunOrderExtension) and to interact badly with a shared JVM running the
// whole suite (as pitest's coverage pass does), causing spurious multi-minute stalls unrelated to the tests
// themselves. Restoring threaded pitest (task 5.3: `threads = availableProcessors()`) is about pitest's OWN
// minion-level parallelism — separate JVMs, so no shared-state race — not Spock's in-JVM `runner.parallel`, which
// stays off here regardless of substrate: deterministic single-threaded-per-JVM execution is simply the safer
// default for a mutation-testing oracle.
runner {
    optimizeRunOrder false
    parallel {
        enabled false
    }
}
mockMaker {
    // Some specs mock/spy a final class (e.g. a package-private *Stage); the inline maker is required for that.
    preferredMockMaker spock.mock.MockMakers.mockito
}

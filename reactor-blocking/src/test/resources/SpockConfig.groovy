// Project-owned Spock configuration, mirroring processor/src/test/resources/SpockConfig.groovy (change
// type-query-seam, task 5.3) — auto-discovered from the test classpath, applies uniformly to `test` and `pitest`.
// Exists so the build never depends on a developer's machine-global ~/.spock/SpockConfig.groovy, whose defaults
// (optimizeRunOrder on) persist per-spec run-history to a per-user-home file (~/.spock/RunHistory/<SpecName>)
// shared across every concurrent JVM on the machine; under pitest's own minion-level parallelism this produces
// racy concurrent writes that corrupt a spec's history entry, surfacing as
// "IllegalArgumentException: Comparison method violates its general contract!" during Spock's test-discovery sort
// — intermittently failing the whole spec rather than any single mutant, which pitest then misreports as
// widespread survived mutants unrelated to the actual code under test. Root-caused for `spi` (change
// clean-up-test-coverage-tooling, task 6) and propagated here for the same reason: pitest was newly enrolled in
// this module by that change — this is very likely also the explanation for task 8.3's "mutation kill/test
// strength plateau at ~54-68%" finding in reactor-blocking, previously attributed to pitest's own attribution
// nondeterminism.
runner {
    optimizeRunOrder false
    parallel {
        enabled false
    }
}

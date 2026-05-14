# processor-test-support

Test support module for the Percolate annotation processor. Provides a fluent
seed-graph DSL, an execution harness with two modes (SPI / explicit), AssertJ-style
expansion assertions, jqwik generator base class, and a long-lived `TypeUniverse`
fixture backed by the public `javax.tools` / `com.sun.source.util.JavacTask` API.

## Isolation rules

- **No SPI registrations.** No file under `META-INF/services/io.github.joke.percolate.processor.spi.*`
  is allowed in this module's artifact. A test-only `Bridge`, `SourceStep`, or `GroupTarget`
  would otherwise pollute the production SPI surface of every consumer.
- **No internal javac imports.** Sources under `src/main/java` must not import any class
  from `com.sun.tools.javac.*`. All javac access goes through `javax.tools`,
  `javax.lang.model`, and `com.sun.source.util` — the public API. The Gradle task
  `checkNoInternalJavacImports` enforces this at build time; there are no `--add-exports`
  flags in `build.gradle`.

## Harness modes

### SPI mode (default)

```java
ExpansionResult result = ExpansionHarness.expand(seedGraph);
```

Loads `Bridge`, `SourceStep`, and `GroupTarget` lists via `ServiceLoader` from
`ExpansionHarness.class.getClassLoader()`. Used by Spock specs and production-parity
scenarios.

### Explicit mode

```java
ExpansionResult result = ExpansionHarness.expand(seedGraph, bridges, sourceSteps, groupTargets);
```

Bypasses `ServiceLoader`. Used by jqwik property tests (which vary the strategy set)
and by tests that need to isolate a specific subset.

Both modes go through `ProcessorModule.assembleExpansionPipeline(...)` so test and
production wiring cannot drift.

## Post-expansion checks on the result

`ExpansionResult` exposes the expanded graph and a handful of checks the test can
call explicitly:

- `result.expandedGraph()` — the expanded `MapperGraph`
- `result.diagnostics()` — captured `ERROR`-kind messager output (includes any
  `Expansion did not converge…` or `Cycle detected…` message emitted by `ExpandStage`)
- `result.converged()` — soft flag derived from the diagnostics
- `result.hasIdentityCollisions()` — `true` if two nodes share `(scope, location, type)`
- `result.hasOrphanRealisedNodes()` — `true` if any `REALISED` edge endpoint is
  unreachable from any `SEED` endpoint via `REALISED`/`MARKER`/`SUB_SEED` edges
- `result.dotRender()` — DOT rendering of the expanded graph for failure messages

Tests opt into the checks they care about; there is no global "all-or-nothing"
invariant assertion in the harness today.

## `TypeUniverse`

Static holder backed by a single JVM-lifetime `com.sun.source.util.JavacTask`
(`ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, null)`).
The task is never `parse()`d, `analyze()`d, or `call()`ed — bootstrap class
resolution is lazy via `Elements.getTypeElement(...)`.

```java
TypeUniverse.INT             // primitive
TypeUniverse.INTEGER         // boxed
TypeUniverse.STRING
TypeUniverse.DAY_OF_WEEK     // user-defined enum stand-in
TypeUniverse.LIST_OF_INT     // List<Integer>
TypeUniverse.pool()          // the full list, used by jqwik generators
```

## `SeedDsl`

Fluent builder for `MapperGraph` instances containing only `SEED` (and `SUB_SEED`
when explicitly declared) edges:

```java
MapperGraph seed = SeedDsl.seed()
        .method("map")
        .arg("input", TypeUniverse.STRING)
        .returns(TypeUniverse.INT);
seed.directive(seed.target("output"), seed.source("input"));
MapperGraph graph = SeedDsl.seed().build(); // ... or chained
```

Node identity follows the production `(scope, location, type)` rule.

## `GraphFixtures`

Static builders for manually constructed graphs — useful when the seed DSL is too
high-level (e.g., when you need to inject `REALISED`/`SUB_SEED` edges directly):

- `graphWithSeedAndRealisedPath()` — a seed + a single realised edge between its endpoints
- `graphWithSubSeedCycle()` — two `SUB_SEED` edges forming a cycle (drives
  `ExpandStage`'s cycle diagnostic)
- `graphWithOrphanRealisedEdge()` — a realised edge unreachable from any seed

## `ExpansionAssertions`

AssertJ-compatible fluent assertions:

```java
assertThat(result).reachable("input", "output");
assertThat(result).reportedError(DiagnosticKind.NO_PATH).forSeedEdge("input", "output");
```

Failure messages include the DOT rendering of the expanded graph. No assertion
method takes a raw `String` for diagnostic-text matching — the contract is the
diagnostic *kind* and the seed edge it was attached to.

## `PropertyTestBase`

Base class for jqwik property tests. Annotated with
`@PropertyDefaults(tries = 500)` and exposes two `@Provide` methods:

- `mapperSpecs()` — produces `MapperSpec` values constrained to `TypeUniverse`
- `strategyBundles()` — produces `StrategyBundle` values that are random subsets
  of the SPI-discovered strategies

Property classes extend this base and use `@ForAll("mapperSpecs")` /
`@ForAll("strategyBundles")` parameters.

## Adding a capability row

To document a new supported transform, add a `where:` row to
`processor/src/test/groovy/.../ExpansionCapabilitiesSpec.groovy`:

```groovy
where:
scenario        | seed
'identity Foo'  | identitySeed(TypeUniverse.FOO)
```

Use `ExpansionHarness.expand(seed)` (SPI mode), assert on the *kind* of behavior
(reachable, converged, diagnostic kind) — never on diagnostic message text.

## @Tag convention

Spock specs use `@spock.lang.Tag('unit')`. jqwik property classes use
`@net.jqwik.api.Tag("unit")`. JUnit Jupiter's `@org.junit.jupiter.api.Tag`
does **not** propagate through either engine in this project — Spock silently
drops Spec discovery and jqwik logs a warning. Match the framework to the tag
package.

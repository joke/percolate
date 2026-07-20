# pitest mutation-attribution ceiling — reproduction notes (RESOLVED)

**Resolution:** not a pitest limitation. Root cause was Spock's `optimizeRunOrder` (on by
default) persisting per-spec run-history to `~/.spock/RunHistory/<SpecName>` — a file
shared across every concurrent JVM on the machine, not scoped to a build or even a
project. Under pitest's own minion-level parallelism, concurrent JVMs race to read/write
that file, corrupting a spec's history entry and causing
`IllegalArgumentException: Comparison method violates its general contract!` during
Spock's test-discovery sort (`SpecRunHistory.sortFeatures` /
`OptimizeRunOrderExtension`) — intermittently crashing the *entire* spec class rather
than any specific mutant, which pitest then reports as a wave of unrelated survived
mutants. `processor/src/test/resources/SpockConfig.groovy` already disabled this
(`runner { optimizeRunOrder false }`) as part of the `type-query-seam` change, but that
fix was never propagated to `spi`/`strategies-builtin`/`reactor`/`reactor-blocking` when
this change newly enrolled them in pitest. Adding the same `SpockConfig.groovy` to `spi`
took it from an unstable 47-60% mutation kill to a stable 96% (3/3 cleared-history runs)
with **zero new tests**. See `tasks.md` section 6 for the final write-up; the notes below
are the investigation log kept for reference (the `threads=1` and
`enableDefaultIncrementalAnalysis=false` diagnostics documented below were both red
herrings — the `ContainerSpec` failure they surfaced was this same pre-existing race,
triggered by rerunning pitest repeatedly in the same investigation session, not by either
setting).

---


Investigation notes for the suspected systemic pitest test-to-mutant attribution issue
hit while working tasks 6 (`spi`) and already documented for task 8.3 (`reactor-blocking`)
in `tasks.md`. Not a spec artifact — a working log so this can be picked up again later
without re-deriving it.

## Symptom

A module's unit suite has high, stable line coverage and specs that directly assert the
exact behavior a given mutant breaks — yet pitest reports mutation-kill / test-strength
scores far below the 85/95/90 floor, and the scores **drift between cleared-history runs**
even though the module has zero javac-backed substrate in its unit test path (confirmed
repo-wide by the already-shipped `dissolve-private-type-universe` change).

First conclusively identified in `reactor-blocking` (see `tasks.md` task 8.3): a
hand-mutated `Blockings.declared()`, run directly via `BlockingsSpec`, correctly failed
with `TooFewInvocationsError` — proving the test *would* kill the mutant — while pitest's
own HTML report only ever attributed ~15-19 of the 34 written feature methods as
"covering" anything, with no positional pattern.

## Reproducing the spi baseline (coverage stable, mutation kill/strength unstable and low)

```bash
cd /home/joke/Projects/joke/percolate
rm -f spi/build/pitHistory.txt
./gradlew :spi:pitest --no-configuration-cache 2>&1 | grep -E "Line Coverage|Generated .* mutations|Test strength|Ran .* tests"
```

Run this 3 times (each preceded by clearing `spi/build/pitHistory.txt`, per task 6.1) and
compare. Results actually observed (single machine, one sitting):

| run | line coverage | mutation kill | test strength | tests run |
|-----|---------------|----------------|-----------------|-----------|
| 1   | 298/307 (97%) | 382/811 (47%)  | 48%             | 1917      |
| 2   | 296/307 (96%) | 436/811 (54%)  | 55%             | 1787      |
| 3   | 296/307 (96%) | 475/811 (59%)  | 60%             | 1649      |

Coverage is essentially flat; mutation kill/strength both vary by 10+ points run to run
and (in this sample) trended upward while the number of tests actually run **decreased**
— the opposite of what you'd expect if more mutants were being genuinely killed.

Per-class breakdown (from run 3's HTML report, `spi/build/reports/pitest/io.github.joke.percolate.spi/index.html`):

```
ResolveCtx.java:    98% line / 41% mutation kill / 42% strength  (259 mutations — biggest single class)
Container.java:     98% line / 67% mutation kill / 71% strength  (156 mutations)
Containers.java:    90% line / 27% mutation kill / 29% strength  (66 mutations)
LiteralCoercion.java: 98% line / 83% mutation kill / 83% strength (205 mutations)
Accessor.java:      100% line / 28% mutation kill / 28% strength (25 mutations)
TypeProbe.java:     80% line / 10% mutation kill / 10% strength  (10 mutations)
OperationSpec.java: 94% line / 47% mutation kill / 52% strength  (30 mutations)
```

Raw per-mutant data lives in `spi/build/reports/pitest/mutations.xml` after any run; parse
with e.g.:

```bash
python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('spi/build/reports/pitest/mutations.xml')
from collections import Counter
c = Counter()
for m in tree.getroot().findall('mutation'):
    if 'ResolveCtx' in m.find('mutatedClass').text:
        c[m.get('status')] += 1
print(c)
"
```

## A specific inspected "survived" mutant that looks wrong

`ResolveCtx.arrayComponent` (`spi/src/main/java/io/github/joke/percolate/spi/ResolveCtx.java:100-105`):

```java
default TypeMirror arrayComponent(final TypeMirror type) {
    if (!isArray(type)) {
        throw new IllegalArgumentException("Not an array type: " + type);
    }
    return ((ArrayType) type).getComponentType();
}
```

pitest reports several SURVIVED mutants on lines 101-104 (negated conditional, removed
`IllegalArgumentException` constructor call, removed `isArray` call, removed
`getComponentType` call). `ResolveCtxSpec` already has:

```groovy
def 'arrayComponent reads the element type of an array, and rejects non-arrays'() {
    expect:
    ctx.isSameType(ctx.arrayComponent(stringArray), STRING)

    when:
    ctx.arrayComponent(STRING)
    then:
    thrown(IllegalArgumentException)
}
```

This exercises both branches with a value assertion and an exception-type assertion — it
should logically kill every one of those mutants. Yet pitest reports them surviving.

## Diagnostic attempts and their outcomes

**1. Direct source-level hand-mutation + `./gradlew :spi:test --tests ResolveCtxSpec`.**
Blocked: `spi`'s `compileTestJava` runs the `percolate`/`reactor` annotation processors
over the module's own integration-tagged test fixtures (`testAnnotationProcessor
project(':percolate')` / `project(':reactor')` in `spi/build.gradle`), and those fixtures
compile real `@Mapper` classes that end up calling the real (now-mutated)
`ResolveCtx.arrayComponent` during annotation processing itself — so the build fails at
**compile time**, before any Spock spec runs. This does confirm the method is live,
reachable production code, but it doesn't cleanly answer "does `ResolveCtxSpec` kill this
mutant" because pitest's own execution model never re-invokes the annotation processor —
it mutates already-compiled bytecode and reruns the already-compiled `test` task classes.
Reverted; not a usable reproduction path as-is.

**2. Bytecode-level reproduction (compile only `:spi:compileJava` with the mutation,
reuse pre-existing compiled test classes, run directly via `java`/JUnit console launcher,
bypassing `compileTestJava`).** Attempted via a Gradle init script to extract
`sourceSets.test.runtimeClasspath` from the `spi` project outside the normal task graph;
hit repeated init-script/composite-build resolution issues (`buildSrc` project name
collisions, `Project with path ':spi' could not be found`, then `task not found`).
Abandoned without a result — the `reactor-blocking` precedent already used a clean,
successful version of this exact experiment (see task 8.3 write-up in `tasks.md`), so this
was not pursued further for `spi` specifically.

**3. `threads=1` diagnostic on the shared pitest config.** Temporarily edited
`buildSrc/src/main/groovy/percolate.conventions.gradle`:

```diff
-        threads = Runtime.runtime.availableProcessors()
+        threads = 1
```

then `rm -f spi/build/pitHistory.txt && ./gradlew :spi:pitest --no-configuration-cache`.
Result: **not a clean signal.** PIT's single-minion coverage pre-pass hung on
`ContainerSpec` for exactly 120000ms and then failed with:

```
PIT >> SEVERE : Description [testClass=io.github.joke.percolate.spi.ContainerSpec, ...] did not pass without mutation.
Exception in thread "main" org.pitest.help.PitHelpError: 1 tests did not pass without mutation when calculating line coverage. Mutation testing requires a green suite.
```

i.e. under `threads=1`, PIT's own coverage pass treats `ContainerSpec` as failing/timing
out even with **no mutation active** — a different failure mode, not evidence for or
against the attribution hypothesis. Reverted (`threads =
Runtime.runtime.availableProcessors()` restored) without further investigation of the
hang itself.

## Where things stand

- `reactor-blocking`: attribution failure **confirmed** via a working direct-run
  experiment (task 8.3).
- `spi`: same symptom shape (stable high coverage, unstable/low mutation kill despite
  specs that appear to directly assert the broken behavior) but **not yet confirmed** via
  a clean direct-run experiment — attempts 1 and 2 above were blocked by unrelated
  plumbing issues, not ruled out on the merits.
- `strategies-builtin`: baseline only (92% line / 36% mutation kill / 41% strength, task
  7.1 not yet run) — never actually investigated for this pattern, just noted as
  structurally similar (coverage far ahead of mutation kill).
- The `threads=1` diagnostic (attempt 3) is unfinished — the `ContainerSpec` hang under
  single-threaded PIT coverage is itself unexplained and worth a look before drawing any
  conclusion about threading as the root cause.

## Likely next steps if picked back up

- Get attempt 2 (bytecode-level, no-annotation-processor reproduction) working for `spi`
  the way it already worked for `reactor-blocking` — that's the cleanest confirming
  experiment.
- Separately investigate the `ContainerSpec` 120s hang under `threads=1` — could itself be
  a real bug (e.g. a blocking wait/latch in test setup that only manifests without
  parallelism) unrelated to the attribution question.
- Consider a `pitestVersion`/`junit5PluginVersion` bump (currently `1.25.1` /
  `1.2.3` in `percolate.conventions.gradle`) as a candidate fix if attribution is
  confirmed as a known upstream limitation with threaded JUnit5/Spock coverage capture.
- Run the same experiment shape against `strategies-builtin` once its own baseline
  (task 7.1) is captured.

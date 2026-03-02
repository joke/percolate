# DOT Export Debug Output — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Export each mapper method's binding and wiring graphs as DOT files to `/tmp/` after their respective pipeline stages.

**Architecture:** A private static `exportDot` helper is added to `Pipeline`. It is called twice inside `process()` — once after `bindingStage.execute` and once after `wiringStage.execute`. It iterates non-opaque registry entries and writes one DOT file per method per stage using JGraphT's `DOTExporter`. Node labels use `MappingNode.toString()`; edge labels use `FlowEdge.toString()`.

**Tech Stack:** Java 11, JGraphT `jgrapht-io:1.5.2` (`DOTExporter`, `DefaultAttribute`), Spock + Google Compile Testing, Gradle, ErrorProne + NullAway (`-Werror`).

---

## Background

`jgrapht-io` is already an `implementation` dependency in `processor/build.gradle`. No dependency changes needed.

The `processor` package is `@NullMarked`. NullAway does not narrow nullability through filter predicates, so `Objects.requireNonNull` must be used inside the helper after the opaque/null guard — exactly as done in `WiringStage.wireMethod`.

Output filename pattern: `/tmp/{MapperSimpleName}-{methodName}-{phase}.dot` where `phase` is `"binding"` or `"wiring"`.

Known passing tests before this change: `WiringStageSpec`, `BindingStageSpec`, `ParseMapperStageSpec`, `MapperDiscoveryStageSpec`, `RegistrationStageSpec`, `PercolateProcessorSpec`. These must remain passing.

---

## Task 1: Add `exportDot` to `Pipeline` and test it

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java`
- Modify: `processor/src/test/groovy/io/github/joke/percolate/processor/PercolateProcessorSpec.groovy`

---

**Step 1: Write the failing test**

Add a new feature to `PercolateProcessorSpec.groovy`. The test compiles a one-method mapper and then asserts that both DOT files exist in `/tmp/` and contain the word `digraph` (the DOT format preamble).

```groovy
def "exportDot writes binding and wiring dot files to /tmp"() {
    given:
    def src = JavaFileObjects.forSourceLines('test.Src',
        'package test;',
        'public class Src { public String getName() { return ""; } }')
    def tgt = JavaFileObjects.forSourceLines('test.Tgt',
        'package test;',
        'public class Tgt { private final String name;',
        '    public Tgt(String name) { this.name = name; }',
        '    public String getName() { return name; } }')
    def mapper = JavaFileObjects.forSourceLines('test.DotMapper',
        'package test;',
        'import io.github.joke.percolate.Mapper;',
        '@Mapper public interface DotMapper { Tgt map(Src src); }')

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(src, tgt, mapper)

    then:
    assertThat(compilation).succeeded()
    new File('/tmp/DotMapper-map-binding.dot').text.contains('digraph')
    new File('/tmp/DotMapper-map-wiring.dot').text.contains('digraph')
}
```

---

**Step 2: Run the test to confirm it fails**

```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec'
```

Expected: the new test fails — the DOT files don't exist yet.

---

**Step 3: Implement `exportDot` in `Pipeline`**

Replace the current `Pipeline.java` with:

```java
package io.github.joke.percolate.processor;

import static java.util.Collections.singletonMap;

import static org.jgrapht.nio.DefaultAttribute.createAttribute;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.ParseMapperStage;
import io.github.joke.percolate.stage.RegistrationStage;
import io.github.joke.percolate.stage.RegistryEntry;
import io.github.joke.percolate.stage.WiringStage;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import org.jgrapht.nio.dot.DOTExporter;

@RoundScoped
public class Pipeline {

    private final ParseMapperStage parseMapperStage;
    private final RegistrationStage registrationStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;

    @Inject
    Pipeline(
            ParseMapperStage parseMapperStage,
            RegistrationStage registrationStage,
            BindingStage bindingStage,
            WiringStage wiringStage) {
        this.parseMapperStage = parseMapperStage;
        this.registrationStage = registrationStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
    }

    public void process(TypeElement typeElement) {
        MapperDefinition mapper = parseMapperStage.execute(typeElement);
        MethodRegistry registry = registrationStage.execute(mapper);
        bindingStage.execute(registry);
        exportDot(mapper.getSimpleName(), registry, "binding");
        wiringStage.execute(registry);
        exportDot(mapper.getSimpleName(), registry, "wiring");
        // ValidateStage, OptimizeStage, CodeGenStage reconnected in future redesign
    }

    private static void exportDot(String mapperName, MethodRegistry registry, String phase) {
        registry.entries().forEach((typePair, entry) -> writeEntryDot(mapperName, phase, entry));
    }

    private static void writeEntryDot(String mapperName, String phase, RegistryEntry entry) {
        if (entry.isOpaque() || entry.getSignature() == null || entry.getGraph() == null) {
            return;
        }
        String methodName = Objects.requireNonNull(entry.getSignature()).getName();
        String path = "/tmp/" + mapperName + "-" + methodName + "-" + phase + ".dot";
        DOTExporter<MappingNode, FlowEdge> exporter = new DOTExporter<>();
        exporter.setVertexIdProvider(v -> String.valueOf(System.identityHashCode(v)));
        exporter.setVertexAttributeProvider(v -> singletonMap("label", createAttribute(v.toString())));
        exporter.setEdgeAttributeProvider(e -> singletonMap("label", createAttribute(e.toString())));
        try (Writer w = new FileWriter(path)) {
            exporter.exportGraph(Objects.requireNonNull(entry.getGraph()), w);
        } catch (IOException ignored) {
            // debug aid — processor must not fail if /tmp/ is unwritable
        }
    }
}
```

**Key points:**
- The `exportDot` loop body is extracted to `writeEntryDot` — keeps the lambda one expression (no statement lambda).
- `entry.getSignature()` and `entry.getGraph()` are `@Nullable`. The early-return guard satisfies the human reader; `Objects.requireNonNull` satisfies NullAway (which does not narrow through the `== null` check in the guard).
- `System.identityHashCode(v)` produces a unique integer ID per object instance — no collision risk.
- Node and edge labels delegate to existing `toString()` implementations on `MappingNode` and `FlowEdge`.

---

**Step 4: Compile**

```bash
./gradlew :processor:compileJava
```

Expected: clean. Fix any ErrorProne/NullAway errors before continuing.

---

**Step 5: Run tests**

```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec'
```

Expected: both tests in `PercolateProcessorSpec` pass. Then run the full suite:

```bash
./gradlew :processor:test
```

Expected: same passing tests as before the change, same 7 known failures, no new failures.

---

**Step 6: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java
git add processor/src/test/groovy/io/github/joke/percolate/processor/PercolateProcessorSpec.groovy
git commit -m "feat: export binding and wiring graphs as DOT files after each pipeline stage"
```

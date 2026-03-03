# Wiring Validation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Detect unresolvable type conversions in the wiring graph and report them as compile errors.

**Architecture:** Add `priority()` to `ConversionProvider` so `MapperMethodProvider` (direct methods) always wins before `ListProvider` (element-wise expansion). Fix `WiringStage.expandEdge` to sever (remove) edges it cannot resolve instead of re-inserting them. Rewrite `ValidateStage` to run dead-end detection (forward + backward DFS) on each wired graph and report broken `PropertyAccessNode`s as errors. Also check that every constructor parameter has an incoming edge. Wire `ValidateStage` into `Pipeline`.

**Tech Stack:** Java 11, JGraphT (`DepthFirstIterator`, `EdgeReversedGraph`), Google Auto Service, Spock + Google Compile Testing.

---

### Task 1: Add `priority()` to `ConversionProvider` and sort providers in `WiringStage`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`

This is a pure refactor — no behavior change for existing tests. It ensures that when a developer supplies a direct `List<TicketActor> myMap(List<Actor> a)` method, `MapperMethodProvider` picks it up before `ListProvider` tries element-wise expansion.

**Step 1: Add `default int priority()` to `ConversionProvider`**

In `ConversionProvider.java`, add after the `provide(...)` method:

```java
/** Lower number = higher priority. Default is 100. */
default int priority() {
    return 100;
}
```

**Step 2: Override in `MapperMethodProvider`**

Add inside `MapperMethodProvider`:

```java
@Override
public int priority() {
    return 10;
}
```

**Step 3: Override in `ListProvider`**

Add inside `ListProvider`:

```java
@Override
public int priority() {
    return 50;
}
```

**Step 4: Sort providers in `WiringStage` constructor**

In `WiringStage`, after the `ServiceLoader.load(ConversionProvider.class, ...)` forEach, add:

```java
conversionProviders.sort(Comparator.comparingInt(ConversionProvider::priority));
```

Add the import:
```java
import java.util.Comparator;
```

**Step 5: Build to verify**

```bash
./gradlew :processor:compileGroovy
```

Expected: `BUILD SUCCESSFUL`. No behavior change yet.

**Step 6: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java
git add processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java
git commit -m "refactor: add ConversionProvider.priority(), sort providers in WiringStage"
```

---

### Task 2: Write failing test — missing element mapper causes compile error

**Files:**
- Modify: `processor/src/test/groovy/io/github/joke/percolate/stage/ValidateStageSpec.groovy`

Add a new test to `ValidateStageSpec` that compiles a mapper with a `List<Actor> → List<TicketActor>` mapping but no `mapActor` method. The test expects a compile error mentioning `actors`.

**Step 1: Add the failing test**

In `ValidateStageSpec.groovy`, add after the existing tests:

```groovy
def "emits error when element mapper is missing for List-to-List mapping"() {
    given:
    def actor = JavaFileObjects.forSourceLines('test.Actor',
        'package test;',
        'public class Actor { public String getName() { return ""; } }')
    def ticketActor = JavaFileObjects.forSourceLines('test.TicketActor',
        'package test;',
        'public class TicketActor { private final String name;',
        '    public TicketActor(String name) { this.name = name; } }')
    def container = JavaFileObjects.forSourceLines('test.Container',
        'package test; import java.util.List;',
        'public class Container { public List<Actor> getActors() { return null; } }')
    def result = JavaFileObjects.forSourceLines('test.Result',
        'package test; import java.util.List;',
        'public class Result { private final List<TicketActor> actors;',
        '    public Result(List<TicketActor> actors) { this.actors = actors; } }')
    def mapper = JavaFileObjects.forSourceLines('test.ListMapper',
        'package test; import java.util.List;',
        'import io.github.joke.percolate.Mapper;',
        '@Mapper public interface ListMapper {',
        '    Result map(Container container);',
        '    // mapActor is intentionally missing',
        '}')

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(actor, ticketActor, container, result, mapper)

    then:
    assertThat(compilation).failed()
    assertThat(compilation).hadErrorContaining('actors')
}
```

**Step 2: Run to verify it fails**

```bash
./gradlew :processor:test --tests 'ValidateStageSpec.emits error when element mapper is missing for List-to-List mapping'
```

Expected: FAIL — the processor does not currently emit an error for this case.

---

### Task 3: Sever unresolvable edges in `WiringStage.expandEdge`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`

Currently `expandEdge` re-inserts an edge when no fragment is found (`graph.addEdge(src, tgt, edge)`). With this change, unresolvable edges are simply removed. The wiring graph is left with dead-end nodes — exactly what `ValidateStage` will detect.

**Step 1: Fix `expandEdge`**

In `WiringStage.expandEdge`, replace:

```java
if (fragment.isPresent() && !fragment.get().isEmpty()) {
    spliceFragment(graph, src, tgt, edge, fragment.get());
} else {
    graph.addEdge(src, tgt, edge);
}
```

With:

```java
if (fragment.isPresent() && !fragment.get().isEmpty()) {
    spliceFragment(graph, src, tgt, edge, fragment.get());
}
// If no fragment found, the edge is severed — the source node becomes a dead-end,
// which ValidateStage will detect via forward/backward reachability analysis.
```

**Step 2: Build**

```bash
./gradlew :processor:compileGroovy
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Rewrite `ValidateStage` with dead-end detection and param coverage

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/ValidateStage.java`

The new `ValidateStage` operates directly on the wired graphs in `MethodRegistry`. It uses two checks:

1. **Dead-end detection** — forward DFS from all `SourceNode`s, backward DFS from `ConstructorAssignmentNode`. Any `PropertyAccessNode` reachable from a source but unable to reach the constructor is a dead-end.
2. **Constructor param coverage** — every parameter in the `ConstructorAssignmentNode`'s descriptor must have an incoming slot edge.

**Step 1: Replace `ValidateStage.java` entirely**

```java
package io.github.joke.percolate.stage;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.ConstructorAssignmentNode;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.graph.node.SourceNode;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import org.jgrapht.Graph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.DepthFirstIterator;

@RoundScoped
public final class ValidateStage {

    private final Messager messager;

    @Inject
    ValidateStage(Messager messager) {
        this.messager = messager;
    }

    public boolean execute(MethodRegistry registry) {
        return registry.entries().values().stream()
                .filter(entry -> !entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null)
                .map(this::validateEntry)
                .reduce(false, Boolean::logicalOr);
    }

    private boolean validateEntry(RegistryEntry entry) {
        Graph<MappingNode, FlowEdge> graph = requireNonNull(entry.getGraph());
        MethodDefinition signature = requireNonNull(entry.getSignature());

        List<SourceNode> sources = graph.vertexSet().stream()
                .filter(SourceNode.class::isInstance)
                .map(SourceNode.class::cast)
                .collect(toList());

        Optional<ConstructorAssignmentNode> sinkOpt = graph.vertexSet().stream()
                .filter(ConstructorAssignmentNode.class::isInstance)
                .map(ConstructorAssignmentNode.class::cast)
                .findFirst();

        if (sources.isEmpty() || !sinkOpt.isPresent()) {
            return false;
        }
        ConstructorAssignmentNode sink = sinkOpt.get();

        boolean deadEndErrors = reportDeadEnds(graph, sources, sink, signature);
        boolean paramErrors = reportMissingParams(graph, sink, signature);
        return deadEndErrors || paramErrors;
    }

    private boolean reportDeadEnds(
            Graph<MappingNode, FlowEdge> graph,
            List<SourceNode> sources,
            ConstructorAssignmentNode sink,
            MethodDefinition signature) {
        Set<MappingNode> forwardReachable = forwardReachable(graph, sources);
        Set<MappingNode> canReachSink = backwardReachable(graph, sink);

        Set<MappingNode> broken = new LinkedHashSet<>(forwardReachable);
        broken.removeAll(canReachSink);

        boolean hasErrors = false;
        for (MappingNode node : broken) {
            if (node instanceof PropertyAccessNode) {
                PropertyAccessNode prop = (PropertyAccessNode) node;
                messager.printMessage(ERROR,
                        "Property '" + prop.getPropertyName()
                                + "' (type " + prop.getOutType()
                                + ") has no conversion path to "
                                + sink.getTargetType().getSimpleName()
                                + " in " + signature.getName());
                hasErrors = true;
            }
        }
        return hasErrors;
    }

    private boolean reportMissingParams(
            Graph<MappingNode, FlowEdge> graph,
            ConstructorAssignmentNode sink,
            MethodDefinition signature) {
        Set<String> mappedSlots = graph.incomingEdgesOf(sink).stream()
                .map(FlowEdge::getSlotName)
                .filter(Objects::nonNull)
                .collect(toSet());

        boolean hasErrors = false;
        for (Property param : sink.getDescriptor().getParameters()) {
            if (!mappedSlots.contains(param.getName())) {
                messager.printMessage(ERROR,
                        "No source mapping for constructor parameter '"
                                + param.getName()
                                + "' of " + sink.getTargetType().getSimpleName()
                                + " in " + signature.getName());
                hasErrors = true;
            }
        }
        return hasErrors;
    }

    private static Set<MappingNode> forwardReachable(
            Graph<MappingNode, FlowEdge> graph, List<SourceNode> sources) {
        Set<MappingNode> reachable = new LinkedHashSet<>();
        for (SourceNode source : sources) {
            new DepthFirstIterator<>(graph, source).forEachRemaining(reachable::add);
        }
        return reachable;
    }

    private static Set<MappingNode> backwardReachable(
            Graph<MappingNode, FlowEdge> graph, ConstructorAssignmentNode sink) {
        Set<MappingNode> reachable = new LinkedHashSet<>();
        new DepthFirstIterator<>(new EdgeReversedGraph<>(graph), sink).forEachRemaining(reachable::add);
        return reachable;
    }
}
```

**Step 2: Build to verify**

```bash
./gradlew :processor:compileGroovy
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Wire `ValidateStage` into `Pipeline`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java`

**Step 1: Add `ValidateStage` to `Pipeline`**

Add `ValidateStage` as a constructor parameter and call it after `wiringStage`:

```java
import io.github.joke.percolate.stage.ValidateStage;

@RoundScoped
public class Pipeline {

    private final ParseMapperStage parseMapperStage;
    private final RegistrationStage registrationStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;
    private final ValidateStage validateStage;

    @Inject
    Pipeline(
            ParseMapperStage parseMapperStage,
            RegistrationStage registrationStage,
            BindingStage bindingStage,
            WiringStage wiringStage,
            ValidateStage validateStage) {
        this.parseMapperStage = parseMapperStage;
        this.registrationStage = registrationStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
        this.validateStage = validateStage;
    }

    public void process(TypeElement typeElement) {
        MapperDefinition mapper = parseMapperStage.execute(typeElement);
        MethodRegistry registry = registrationStage.execute(mapper);
        bindingStage.execute(registry);
        exportDot(mapper.getSimpleName(), registry, "binding");
        wiringStage.execute(registry);
        exportDot(mapper.getSimpleName(), registry, "wiring");
        validateStage.execute(registry);
    }

    // exportDot and writeEntryDot unchanged
```

**Step 2: Run the failing test from Task 2**

```bash
./gradlew :processor:test --tests 'ValidateStageSpec.emits error when element mapper is missing for List-to-List mapping'
```

Expected: PASS — processor now emits an error for missing element mapper.

**Step 3: Run all `ValidateStageSpec` tests**

```bash
./gradlew :processor:test --tests 'ValidateStageSpec'
```

Expected: all 3 tests pass (the 2 pre-existing + the new one).

**Step 4: Run all `WiringStageSpec` tests**

```bash
./gradlew :processor:test --tests 'WiringStageSpec'
```

Expected: all WiringStageSpec tests continue to pass. Note: the existing `List<T> to List<U>` test still passes because it includes a `mapActor` method — severing only affects cases where no provider can handle the edge.

**Step 5: Run full check**

```bash
./gradlew :processor:check
```

Expected: no new errors. Pre-existing failure count unchanged.

**Step 6: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java
git add processor/src/main/java/io/github/joke/percolate/stage/ValidateStage.java
git add processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java
git add processor/src/test/groovy/io/github/joke/percolate/stage/ValidateStageSpec.groovy
git commit -m "feat: sever unresolvable wiring edges; rewrite ValidateStage with dead-end detection"
```

---

### Task 6: Verify final state

**Step 1: Run full build**

```bash
./gradlew build
```

**Step 2: Confirm all new tests pass**

The following must pass:
- `ValidateStageSpec` — all 3 tests (missing param, missing converter, missing element mapper)
- `WiringStageSpec` — all 4 tests (compatible, multi-property, List-to-List with mapActor, nested)

**Step 3: Confirm DOT output**

With `mapActor` missing from `TicketMapper`, inspect `/tmp/TicketMapper-mapPerson-wiring.dot`. The direct `List<Actor> -[actors]-> List<TicketActor>` edge must be absent (severed). The processor must also emit an ERROR naming property `actors`.

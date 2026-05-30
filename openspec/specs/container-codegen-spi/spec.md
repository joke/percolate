# Container Codegen SPI Spec

## Purpose

The container-codegen SPI is the strategy-supplied seam through which a developer adds container support (`List`/`Set`/array/`Optional`, and developer `Flux`/`Mono`/custom) by writing **one class per container** — both a `Bridge` (candidacy) and a codegen-handle provider (per-paradigm snippets). The composer owns only structure (the recursive `PlanView` walk + a one-bit `isStream` flag) and obtains every container `CodeBlock` from a handle, so adding a container needs no engine or composer change. Code generation is a pure function of the solved graph.

## Requirements

### Requirement: Container-codegen handle family

The percolate-spi module SHALL define a `Codegen` marker interface and a `StreamOps` base of paradigm-generic stream operations, with a sequence handle (`ContainerCodegen`) and a presence handle (`WrapperCodegen`) extending it. The composer SHALL obtain every container-touching `CodeBlock` from one of these handles and SHALL contain no literal container syntax.

```java
public interface Codegen { }

public interface StreamOps extends Codegen {                   // shared by every container kind
    CodeBlock iterate(CodeBlock container);                    // open an element stream
    CodeBlock mapElements(CodeBlock stream, String var, CodeBlock body);
    CodeBlock flatMapElements(CodeBlock stream, String var, CodeBlock inner);
}

public interface ContainerCodegen extends StreamOps {          // List, Set, array, Flux
    CodeBlock collect(CodeBlock stream);                       // close the stream into this container
}

public interface WrapperCodegen extends StreamOps {            // Optional, Mono — NO collect
    CodeBlock mapPresence(CodeBlock wrapper, String var, CodeBlock body);
    CodeBlock wrap(CodeBlock scalar);
    CodeBlock unwrap(CodeBlock wrapper, Nullability targetNullability);
}
```

`collect` (close a stream back into a container) is a **sequence** terminal and SHALL live only on `ContainerCodegen`. A presence wrapper SHALL NOT expose or emit a `collect`: closing a stream into a 0-or-1 container is meaningless for the presence axis (only sequences collect). A wrapper still participates in a stream via the shared `iterate` (its 0-or-1 element stream is how the composer drops empties with a flat-map).

`unwrap` SHALL render the empty-collapse chosen by `targetNullability`: a non-null target collapses by throwing (`orElseThrow`-equivalent); a `@Nullable` target collapses to `null` (`orElse(null)`-equivalent). A `WrapperCodegen` MAY leave `unwrap` unsupported when its container cannot collapse to a synchronous scalar (e.g. `Mono`), in which case the framework SHALL NOT offer a wrapper-to-scalar mapping for that container.

#### Scenario: A presence wrapper has no collect
- **WHEN** `WrapperCodegen` is inspected
- **THEN** it extends `StreamOps` (not `ContainerCodegen`) and declares no `collect`
- **AND** a `WrapperContainer` base emits no `EXITING` collect step, so the framework never renders a stream-collapse (`findFirst`-style) into a wrapper

#### Scenario: A sequence handle supplies stream snippets
- **WHEN** the composer renders a sequence container hop
- **THEN** the open/map/collect `CodeBlock`s originate from that container's `ContainerCodegen`, not from the composer

#### Scenario: A wrapper handle supplies presence snippets
- **WHEN** the composer renders a top-level wrapper hop
- **THEN** the `map`/`wrap`/`unwrap` `CodeBlock`s originate from that container's `WrapperCodegen`
- **AND** `unwrap` renders a throwing collapse for a non-null target and a `null` collapse for a `@Nullable` target

### Requirement: SequenceContainer and WrapperContainer bases

The percolate-spi module SHALL define abstract bases `SequenceContainer` and `WrapperContainer` that `implement Bridge` and let a developer declare a container in **one class** by supplying only its type predicate, its element extractor, and its codegen snippets. `SequenceContainer` SHALL also implement `ContainerCodegen`; `WrapperContainer` SHALL also implement `WrapperCodegen`.

```java
public abstract class SequenceContainer implements Bridge, ContainerCodegen {
    protected abstract boolean matches(TypeMirror t, ResolveCtx ctx);
    protected abstract TypeMirror element(TypeMirror t);
    protected Optional<EdgeCodegen> singleElementWrap() { return Optional.empty(); }
    public ContainerCodegen streamCodegen() { return this; }
    public Optional<LoopContainerCodegen> loopCodegen() { return Optional.empty(); }
    // bridge(from, to, ctx) is supplied by the base
}

public abstract class WrapperContainer implements Bridge, WrapperCodegen {
    protected abstract boolean matches(TypeMirror t, ResolveCtx ctx);
    protected abstract TypeMirror element(TypeMirror t);
    protected abstract Optional<TypeMirror> wrapped(TypeMirror element, ResolveCtx ctx); // e.g. Optional<element>
    public WrapperCodegen streamCodegen() { return this; }
    public Optional<LoopContainerCodegen> loopCodegen() { return Optional.empty(); }
    // bridge(from, to, ctx) is supplied by the base
}
```

The base's `bridge(from, to, ctx)` SHALL derive candidacy from `matches`/`element`. A **sequence** and a **wrapper** are asymmetric, because a sequence iterates an *existing* source and closes a stream into itself, while a wrapper only wraps/unwraps a scalar and never collects:

- A `SequenceContainer`, when `matches(to)`, emits the collect (`EXITING`, carrying the container as provider) step and, when supported, the single-element wrap (`PRESERVING`, scalar `EdgeCodegen` from `singleElementWrap()`, e.g. `List.of`/`Set.of`; arrays omit it); when `matches(from)`, it emits the iterate (`ENTERING`, provider) step with input `from` and output `element(from)`.
- A `WrapperContainer`, when `matches(to)`, emits **only** the single-element wrap (`PRESERVING`, scalar `EdgeCodegen` from `wrap`, e.g. `ofNullable`) — **no collect step** (a wrapper is not a sequence); when `to` is **not** itself the wrapper, it emits the unwrap (`ENTERING`, provider) step with input `wrapped(to)` (e.g. `Optional<to>`) and output `to`.

Each scope-entering / scope-exiting step SHALL carry the container as its codegen provider; the single-element wrap step SHALL carry a scalar `EdgeCodegen` (see the `expansion-strategy-spi` BridgeStep modification). The developer SHALL NOT write graph or `BridgeStep` logic by hand. Registration SHALL be via `@AutoService(Bridge.class)` / `ServiceLoader`, identical to existing bridges.

#### Scenario: A developer adds a container with one class
- **WHEN** a developer writes a class extending `SequenceContainer` (or `WrapperContainer`) implementing `matches`, `element`, and the snippet methods, annotated `@AutoService`
- **THEN** the processor discovers it via `ServiceLoader` and uses it for matching types during expansion and codegen
- **AND** no change to the composer, the expansion driver, or any built-in is required

#### Scenario: A sequence derives candidacy from matches and element
- **WHEN** the driver queries a `SequenceContainer` for `(from, to)` where `matches(to)` holds
- **THEN** the base emits the collect `BridgeStep` (and, when supported, a scalar single-element wrap) with input `element(to)` and output `to`
- **AND** when `matches(from)` holds it emits the iterate `BridgeStep` with input `from` and output `element(from)`

#### Scenario: A wrapper synthesises its unwrap input from a scalar target
- **WHEN** the driver queries a `WrapperContainer` for `(from, to)` where `to` is not the wrapper type
- **THEN** the base emits an `ENTERING` unwrap `BridgeStep` with input `wrapped(to)` (e.g. `Optional<to>`) and output `to`, carrying the container as provider
- **AND** when `matches(to)` holds it emits the collect step (and a scalar `ofNullable` wrap) instead

### Requirement: Built-in containers are the first customers of the SPI

The built-in List, Set, array, and Optional containers SHALL be implemented as `ListContainer`, `SetContainer`, `ArrayContainer` (extending `SequenceContainer`) and `OptionalContainer` (extending `WrapperContainer`) in the strategies-builtin module, on the **same** bases a developer uses, with no privileged internal path. The nine per-operation container bridges SHALL be removed (see the `expansion-strategy-spi` removal).

#### Scenario: List is expressed on the developer SPI
- **WHEN** `ListContainer` is inspected
- **THEN** it extends `SequenceContainer`, implements `matches` (is-a `List`), `element` (type argument 0), and the `iterate`/`mapElements`/`flatMapElements`/`collect` snippets
- **AND** it is indistinguishable in shape from a developer-authored `FluxContainer`

### Requirement: Composer container weaving

`BuildMethodBodies` SHALL weave container hops as an extension of its recursive `PlanView` walk, threading a single boolean — whether the rendered child is already an element stream — **up** the recursion, alongside the `StreamOps` handle that owns the open stream (a `ContainerCodegen` or a `WrapperCodegen`). There SHALL be no intermediate representation, mutable plan graph, or lowering pass. Each container hop SHALL render per the child-stream state:

- An `ENTERING` sequence hop whose child is **not** a stream SHALL open a stream (`iterate`) and mark the result a stream.
- An `ENTERING` wrapper hop whose child **is** a stream SHALL drop empties by `flatMapElements(child, v, iterate(v))` and stay a stream (the `FilterPresent` behaviour, emergent — not a distinct operation).
- An `ENTERING` wrapper hop whose child is **not** a stream SHALL render a top-level `unwrap` under the target's nullability.
- A `PRESERVING` element-map hop (scalar `EdgeCodegen`) whose child **is** a stream SHALL render `mapElements(child, v, edge(v))` via the threaded stream handle; otherwise it renders the scalar `EdgeCodegen` inline (unchanged). The single-element `wrap` is a `PRESERVING` scalar edge rendered the same way.
- An `EXITING` hop SHALL close the stream (`collect`) and mark the result not a stream.

#### Scenario: Wrapper-in-sequence weaves to a flat-map
- **WHEN** the plan threads a source `List<Optional<A>>` through iterate, an `Optional` element, an element map, and a `Set` collect
- **THEN** the emitted body opens the list stream, `flatMap`s the optional element stream (dropping empties), maps the element, and collects into the set — all snippets from the respective container handles

#### Scenario: The same wrapper hop renders differently by context
- **WHEN** an `Optional` unwrap hop's child is a stream
- **THEN** it renders as a flat-map of the optional's element stream
- **WHEN** the same hop's child is not a stream (top-level)
- **THEN** it renders as `unwrap` (collapse to scalar) under the target's nullability

### Requirement: Three orthogonal axes

Code generation SHALL treat reference-nullability, wrapper-presence, and sequence iteration as independent, composable concerns and SHALL NOT merge them. Reference-nullability SHALL be resolved by the existing nullability machinery, applied **per reference level** (the container reference and each element reference independently), and SHALL only be **read** at container boundaries. Wrapper-presence SHALL NOT be modelled as nullability.

#### Scenario: Null reference, empty wrapper, and present value are distinct
- **WHEN** a source is typed `@Nullable Optional<@Nullable Long>`
- **THEN** the emitted code distinguishes the null-reference case (reference-nullability), the empty case (presence), and the inner null (reference-nullability of the element)
- **AND** the presence handling comes from the wrapper handle, not from the nullability machinery

#### Scenario: Per-level nullability around a sequence
- **WHEN** a source is typed `@Nullable List<@Nullable Long>`
- **THEN** the container reference is null-guarded before iteration and each element is null-handled within the element map, independently of the sequence iterate/collect

# Container Codegen SPI Spec

## Purpose

The container-codegen SPI is the strategy-supplied seam through which a developer adds container support (`List`/`Set`/array/`Optional`, and developer `Flux`/`Mono`/custom) by writing **one class per container** — both a `ContainerMatch` candidacy and a codegen-handle provider (per-paradigm snippets). The composer owns only structure (the recursive extracted-plan walk) and obtains every container `CodeBlock` from a handle attached to the container `Operation` — a scope-owning Operation's handle weaves around the rendered child-plan lambda, a wrap/unwrap handle renders inline — so adding a container needs no engine or composer change. Code generation is a pure function of the solved graph.

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

The percolate-spi module SHALL define abstract bases `SequenceContainer` and `WrapperContainer` that implement `ContainerMatch` and let a developer declare a container in **one class** by supplying only its type predicate, its element extractor, and its codegen snippets. `SequenceContainer` SHALL also implement `ContainerCodegen`; `WrapperContainer` SHALL also implement `WrapperCodegen`.

```java
public abstract class SequenceContainer implements ContainerMatch, ContainerCodegen {
    protected abstract boolean matches(TypeMirror t, ResolveCtx ctx);
    protected abstract TypeMirror element(TypeMirror t);
    protected Optional<EdgeCodegen> singleElementWrap() { return Optional.empty(); }
    public ContainerCodegen streamCodegen() { return this; }
    public Optional<LoopContainerCodegen> loopCodegen() { return Optional.empty(); }
    // bridge(from, to, ctx) is supplied by the base
}

public abstract class WrapperContainer implements ContainerMatch, WrapperCodegen {
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

Each scope-entering / scope-exiting step SHALL carry the container as its codegen provider; the single-element wrap step SHALL carry a scalar `EdgeCodegen` (see the `expansion-strategy-spi` `ExpansionStep` surface). The developer SHALL NOT write graph or `ExpansionStep` logic by hand. Registration SHALL be via `@AutoService(ExpansionStrategy.class)` / `ServiceLoader`, identical to every other strategy.

#### Scenario: A developer adds a container with one class
- **WHEN** a developer writes a class extending `SequenceContainer` (or `WrapperContainer`) implementing `matches`, `element`, and the snippet methods, annotated `@AutoService`
- **THEN** the processor discovers it via `ServiceLoader` and uses it for matching types during expansion and codegen
- **AND** no change to the composer, the expansion driver, or any built-in is required

#### Scenario: A sequence derives candidacy from matches and element
- **WHEN** the driver queries a `SequenceContainer` for `(from, to)` where `matches(to)` holds
- **THEN** the base emits the collect `ExpansionStep` (and, when supported, a scalar single-element wrap) with input `element(to)` and output `to`
- **AND** when `matches(from)` holds it emits the iterate `ExpansionStep` with input `from` and output `element(from)`

#### Scenario: A wrapper synthesises its unwrap input from a scalar target
- **WHEN** the driver queries a `WrapperContainer` for `(from, to)` where `to` is not the wrapper type
- **THEN** the base emits an `ENTERING` unwrap `ExpansionStep` with input `wrapped(to)` (e.g. `Optional<to>`) and output `to`, carrying the container as provider
- **AND** when `matches(to)` holds it emits the collect step (and a scalar `ofNullable` wrap) instead

### Requirement: Built-in containers are the first customers of the SPI

The built-in List, Set, array, and Optional containers SHALL be implemented as `ListContainer`, `SetContainer`, `ArrayContainer` (extending `SequenceContainer`) and `OptionalContainer` (extending `WrapperContainer`) in the strategies-builtin module, on the **same** bases a developer uses, with no privileged internal path. The nine per-operation container bridges SHALL be removed (see the `expansion-strategy-spi` removal).

#### Scenario: List is expressed on the developer SPI
- **WHEN** `ListContainer` is inspected
- **THEN** it extends `SequenceContainer`, implements `matches` (is-a `List`), `element` (type argument 0), and the `iterate`/`mapElements`/`flatMapElements`/`collect` snippets
- **AND** it is indistinguishable in shape from a developer-authored `FluxContainer`

### Requirement: Container codegen handles attach to Operations

Container codegen handles (per-paradigm snippet providers) SHALL attach to the container
`Operation`: the scope-owning Operation's handle weaves the container operation around the rendered
child-scope lambda; a wrap/unwrap Operation's handle renders inline like any scalar codegen. The
composer obtains every container `CodeBlock` from a handle and owns only the recursive plan walk —
adding a container still requires no engine or composer change.

#### Scenario: Handle weaves around the child lambda
- **WHEN** the composer renders a scope-owning container Operation
- **THEN** it obtains the iterate/collect (or map/orElse) snippets from the Operation's handle and
  inserts the rendered child plan as the lambda body

#### Scenario: One class per container still suffices
- **WHEN** a developer adds support for a new container type
- **THEN** one class provides both candidacy and the codegen handle, with no composer modification

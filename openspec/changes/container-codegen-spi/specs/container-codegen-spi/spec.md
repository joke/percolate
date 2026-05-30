## ADDED Requirements

### Requirement: Container-codegen handle family

The percolate-spi module SHALL define a `Codegen` marker interface and two container-codegen handles extending it, providing the per-operation snippets the composer weaves. The composer SHALL obtain every container-touching `CodeBlock` from one of these handles and SHALL contain no literal container syntax.

```java
public interface Codegen { }

public interface ContainerCodegen extends Codegen {            // List, Set, array, Flux
    CodeBlock iterate(CodeBlock container);                    // open an element stream
    CodeBlock mapElements(CodeBlock stream, String var, CodeBlock body);
    CodeBlock flatMapElements(CodeBlock stream, String var, CodeBlock inner);
    CodeBlock collect(CodeBlock stream);                       // close the stream into this container
}

public interface WrapperCodegen extends ContainerCodegen {    // Optional, Mono
    CodeBlock mapPresence(CodeBlock wrapper, String var, CodeBlock body);
    CodeBlock wrap(CodeBlock scalar);
    CodeBlock unwrap(CodeBlock wrapper, Nullability targetNullability);
}
```

`unwrap` SHALL render the empty-collapse chosen by `targetNullability`: a non-null target collapses by throwing (`orElseThrow`-equivalent); a `@Nullable` target collapses to `null` (`orElse(null)`-equivalent). A `WrapperCodegen` MAY leave `unwrap` unsupported when its container cannot collapse to a synchronous scalar (e.g. `Mono`), in which case the framework SHALL NOT offer a wrapper-to-scalar mapping for that container.

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
    public ContainerCodegen streamCodegen() { return this; }
    public Optional<LoopContainerCodegen> loopCodegen() { return Optional.empty(); }
    // bridge(from, to, ctx) is supplied by the base
}
```

The base's `bridge(from, to, ctx)` SHALL derive candidacy from `matches`/`element`: when `matches(to)` it emits the collect (`EXITING`) and single-element wrap (`PRESERVING`) steps; when `matches(from)` it emits the iterate (`ENTERING`) step. Each emitted `BridgeStep` SHALL carry the container as its codegen provider (see the `expansion-strategy-spi` BridgeStep modification). The developer SHALL NOT write graph or `BridgeStep` logic by hand. Registration SHALL be via `@AutoService(Bridge.class)` / `ServiceLoader`, identical to existing bridges.

#### Scenario: A developer adds a container with one class
- **WHEN** a developer writes a class extending `SequenceContainer` (or `WrapperContainer`) implementing `matches`, `element`, and the snippet methods, annotated `@AutoService`
- **THEN** the processor discovers it via `ServiceLoader` and uses it for matching types during expansion and codegen
- **AND** no change to the composer, the expansion driver, or any built-in is required

#### Scenario: The base derives candidacy from matches and element
- **WHEN** the driver queries a `SequenceContainer` for `(from, to)` where `matches(to)` holds
- **THEN** the base emits the collect and wrap `BridgeStep`s with input type `element(to)` and output type `to`
- **AND** when `matches(from)` holds it emits the iterate `BridgeStep` with input `from` and output `element(from)`

### Requirement: Built-in containers are the first customers of the SPI

The built-in List, Set, array, and Optional containers SHALL be implemented as `ListContainer`, `SetContainer`, `ArrayContainer` (extending `SequenceContainer`) and `OptionalContainer` (extending `WrapperContainer`) in the strategies-builtin module, on the **same** bases a developer uses, with no privileged internal path. The nine per-operation container bridges SHALL be removed (see the `expansion-strategy-spi` removal).

#### Scenario: List is expressed on the developer SPI
- **WHEN** `ListContainer` is inspected
- **THEN** it extends `SequenceContainer`, implements `matches` (is-a `List`), `element` (type argument 0), and the `iterate`/`mapElements`/`flatMapElements`/`collect` snippets
- **AND** it is indistinguishable in shape from a developer-authored `FluxContainer`

### Requirement: Composer container weaving

`BuildMethodBodies` SHALL weave container hops as an extension of its recursive `PlanView` walk, threading a single boolean — whether the rendered child is already an element stream — **up** the recursion. There SHALL be no intermediate representation, mutable plan graph, or lowering pass. Each container hop SHALL render per the child-stream state:

- An `ENTERING` sequence hop whose child is **not** a stream SHALL open a stream (`iterate`) and mark the result a stream.
- An `ENTERING` wrapper hop whose child **is** a stream SHALL drop empties by `flatMapElements(child, v, iterate(v))` and stay a stream (the `FilterPresent` behaviour, emergent — not a distinct operation).
- An `ENTERING` wrapper hop whose child is **not** a stream SHALL render a top-level `unwrap`/`mapPresence`.
- A `PRESERVING` element-map hop whose child **is** a stream SHALL render `mapElements(child, v, edge(v))`; otherwise it renders the scalar `EdgeCodegen` inline (unchanged).
- An `EXITING` hop SHALL close the stream (`collect` for a sequence, the wrapper close for a wrapper) and mark the result not a stream.

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

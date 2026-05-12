## Why

The processor's expansion engine can build chains of typed conversions
between user types via `MethodCallBridge`, but it cannot reach into
containers. A user authoring a `@Mapper` with a sibling
`Pet map(GoldenRetriever)` cannot have a directive of shape
`Optional<Pet> getPet(List<Optional<GoldenRetriever>> xs)` resolve —
the strategy machinery cannot peel an `Optional` off a value, cannot
iterate a `List` to convert its elements, and cannot wrap a value
into a target container. Containers are the next layer of vocabulary
the expansion engine needs.

The infrastructure shipped by `add-method-call-bridge` (iterative
fixed-point loop, intermediate-node materialisation, SUB_SEED
iteration) was designed with this in mind. The graph vocabulary
already names `ElementLocation` and parent-linked phantom nodes for
this purpose. This change is its first user: it turns the latent
graph primitives into a working container layer, and it generalises
the model so future container kinds (Map, custom user containers,
key/value-shaped containers in general) drop in as additive
strategies without further driver work.

## What Changes

- **BREAKING** `processor.spi.BridgeStep` gains a `List<ElementSeed>`
  field. Same-location bridge steps (existing strategies) emit an
  empty list. Container "map" steps emit one entry per element role
  the inner sub-conversion must traverse.
- **BREAKING** New value type `processor.spi.ElementSeed(String role,
  TypeMirror inputType, TypeMirror outputType)` describing one
  inner-scope conversion request paired with an outer container edge.
- **BREAKING** `Weights.CONTAINER` joins the existing weight bands.
  Single weight band for v1; all seven new container strategies use
  the same constant.
- **BREAKING** `processor.graph.ElementLocation` gains a `String role`
  field (default `"element"`). The role discriminator is the
  forward-compat hook for `Map<K,V>`'s key/value element scopes; it
  enables zero-SPI-churn extension to multi-element-scope containers.
- **BREAKING** `Node.id()` for element nodes now includes both the
  location segment (with role) AND the type encoding. Today's rule
  drops type for element nodes, which makes element-scope chain
  intermediates collide on id. The fix is required for correctness;
  the previous form was unused by working code.
- New built-in `Bridge` strategies under
  `processor.spi.builtins`: `OptionalWrap`, `OptionalUnwrap`,
  `OptionalMap`, `ListWrap`, `ListMap`, `SetWrap`, `SetMap`. All seven
  register via `@AutoService(Bridge.class)`. `ListMap` / `SetMap`
  accept multiple input shapes (`List`, `Set`, `Collection`,
  `Iterable`, arrays, `Optional` via `Optional.stream()`); cross-
  container conversions fall out for free.
- New `processor.spi.Containers` helper exposing `isOptional` /
  `isList` / `isSet` / `isCollection` / `isIterable` / `isArray` /
  `typeArgument` / `arrayComponentType`. Public so external custom-
  container strategies can reuse it.
- `BridgeSourceToTargetPhase` widens in three rules:
  - Element-scope SEEDs (both endpoints at `ElementLocation`) are
    added to the phase's work-list alongside source→target SEEDs and
    SUB_SEEDs.
  - Output-side SUB_SEED emission: when an emitted step's `outputType`
    differs from `T.type`, the driver emits a SUB_SEED from the
    allocated output node to `T` (mirror of the existing input-side
    rule). This is required for container "unwrap" cases to close.
  - Parent inheritance for intermediate allocation: when allocating a
    new node at `loc instanceof ElementLocation`, the driver inherits
    `parent` from the anchor node (F for input, T for output).
  - Element-seed emission: for each `ElementSeed` carried on a
    `BridgeStep`, the driver allocates a pair of element nodes
    (parented by the input and output nodes respectively) and emits
    one SEED edge between them. Inner expansion uses the same
    fixed-point loop.
- Out of scope (named explicitly so the boundary is clear): `Map<K,V>`
  and other key/value containers, `Stream<T>` / `T[]` as target types
  (input-shape acceptance only), raw-container or wildcard-element
  diagnostics, null/default policy on `OptionalUnwrap` (today: emits
  `.orElse(null)`; future change controlled by `@Nullable` /
  `@Default` enrichment), codegen rendering of element-scope inlining.
  The `EdgeCodegen` SPI is NOT widened here; container "map"
  strategies emit a codegen lambda that throws
  `UnsupportedOperationException`. The renderer change that ships
  alongside the codegen capability will widen `EdgeCodegen` to accept
  inner-path resolution.

Note on the **BREAKING** marks: the processor module is pre-publication.
No external implementors of `Bridge` exist; the only internal
implementors (`DirectAssign`, `MethodCallBridge`) are updated in this
same change. Breaking changes are accepted freely.

## Capabilities

### New Capabilities

- `container-expansion`: the family of container-shaped `Bridge`
  built-ins (Optional/List/Set Wrap/Unwrap/Map) and the `Containers`
  helper they share. Covers each strategy's pattern-matching rule,
  the codegen lambda each emits, the element-seed emission for "map"
  strategies, and the input-shape acceptance for `ListMap` / `SetMap`
  cross-container conversions.

### Modified Capabilities

- `expansion-strategy-spi`: `BridgeStep` gains a `List<ElementSeed>`
  field; new `ElementSeed` value type joins the SPI; `Weights.CONTAINER`
  constant added.
- `graph-expansion`: `BridgeSourceToTargetPhase` extends its work-list
  to include element-scope SEEDs (third bucket alongside source→target
  SEEDs and SUB_SEEDs); the unified edge-emission rule widens with
  output-side SUB_SEED emission, parent inheritance for element-
  location intermediate allocation, and element-seed handling that
  spawns parent-linked element nodes and inner SEEDs.
- `graph-model`: `ElementLocation` widens with a `String role` field
  (default `"element"`); its `segment()` encodes `"elem(<role>)"`;
  `Node.id()` for element nodes now appends both the location segment
  and the type encoding so element-scope chain intermediates do not
  collide on id.

## Impact

- Processor module: new files under `processor.spi` (`ElementSeed`,
  `Containers`) and `processor.spi.builtins` (seven new strategy
  classes). Existing files touched: `processor.spi.BridgeStep` (added
  field), `processor.spi.Weights` (added constant),
  `processor.graph.ElementLocation` (added role field),
  `processor.graph.Node` (id rule for element nodes),
  `processor.graph.Location` (doc only),
  `processor.stages.expand.BridgeSourceToTargetPhase` (three rule
  additions), `processor.spi.builtins.DirectAssign` and
  `processor.spi.builtins.MethodCallBridge` (one-line constructor
  update for new `BridgeStep` field).
- Tests touched: `ElementLocationSpec` (segment text changes;
  equality semantics widen with role); `NodeSpec` (element-node id
  string asserted at line 180); `DotRendererSpec` (no-arg
  `new ElementLocation()` is preserved as a default-role convenience
  ctor so existing instantiations remain valid).
- No new third-party dependencies. Google `auto-service` already on
  the classpath.
- No changes to `@Map`, `@Mapper`, or any other public annotation
  surface.
- Affected stakeholders: processor maintainers (this change); future
  authors of `Map<K,V>` and custom multi-role container strategies
  (whose infrastructure prerequisites are unblocked here); strategy
  authors generally (who see a richer `BridgeStep` and a `Containers`
  helper that did not exist before).

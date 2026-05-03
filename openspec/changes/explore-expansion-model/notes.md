# Expansion model — exploration notes

**Status:** exploration record. Not a change proposal. Captures the joint
mental model reached during a thinking session on 2026-05-02 between Joke
and the assistant. Future Phase 2 (expansion) work should start here, not
from a blank page.

**Predecessor doc:** `openspec/changes/archive/2026-05-02-add-seed-graph-and-debug-dump/design.md`.
That doc sketched expansion in its "Future Plans" section. This doc supersedes
that sketch in several places — the divergences are called out below.

## Reading order

1. **Where seed-graph left us** — recap of what's already shipped.
2. **The five tensions** — open questions from the seed-graph design and how
   we resolved them (T1–T5).
3. **Container model unification** — Optional / List / Set / Stream all share
   one graph shape via phantom element nodes.
4. **Method discovery model** — manual default methods, generated mapper
   methods, and cross-mapper composition all via one `MethodCallStrategy`
   with concentric scopes.
5. **Constructor and builder grouping** — hint-driven exact-match emission;
   why this collapses the multi-group Dijkstra problem.
6. **Strategy SPI shape** — the interface that falls out of all of the above.
7. **Weight scale convention** — agreed scale + sentinel for unrealised seeds.
8. **Open questions / deferred** — what we explicitly chose not to decide.
9. **Glossary** — terms used here that don't appear in the codebase yet.

---

## 1. Where seed-graph left us

The Phase 1 change (`add-seed-graph-and-debug-dump`) shipped:

- A per-mapper graph data model (`Node`, `Edge`, `Location`, `MapperGraph`,
  `Scope`, `AccessPath`, `TargetPath`).
- A `SeedGraph` stage that turns `MapperMappings` into a directive-seeded
  graph: parameter roots, return-type roots, and chains of `?`-typed
  intermediate nodes connected by directive-seed edges (weight 1, carrying
  the originating `@Map` `AnnotationMirror`).
- A gated DOT renderer (`-Apercolate.debug.graphs=true`) writing
  `<MapperFQN>.seed.dot` to `SOURCE_OUTPUT`.
- A typed `ProcessorOptions` carrier and a `ValidateSourceParameters` validator.

The seed graph for the example mapper

```java
@Mapper
interface PersonMapper {
  @Map(target = "firstName", source = "person.first")
  @Map(target = "lastName",  source = "person.lastName")
  Human mapHuman(Person person);

  @Map(target = "street", source = "address.street")
  Human.Address mapAddress(Person.Address address);
}
```

looks like this (taken verbatim from the actual generated DOT, with two
clusters, one per method):

```
mapHuman cluster:
  src[person]:Person ──seed,w=1──▶ src[person.first]:?     ──seed──▶ tgt[.firstName]:? ──seed──▶ tgt[]:Human
                     ──seed,w=1──▶ src[person.lastName]:? ──seed──▶ tgt[.lastName]:?  ──seed──▶ tgt[]:Human

mapAddress cluster:
  src[address]:Person.Address ──seed──▶ src[address.street]:? ──seed──▶ tgt[.street]:? ──seed──▶ tgt[]:Human.Address
```

Three flavors of directive seed edge are visible:

- **flavor ① source-side step:** `src[person]:Person → src[person.first]:?`
  (start typed, end `?`, hint = path-tail of the goal)
- **flavor ② bridge:** `src[person.first]:? → tgt[.firstName]:?`
  (both `?`, hint = pair the source path with the target path)
- **flavor ③ target-side step:** `tgt[.firstName]:? → tgt[]:Human`
  (start `?`, end typed, hint = path-tail of the source)

These three flavors recur throughout this doc. Each flavor needs a different
strategy *invocation* shape, even though the SPI surface is uniform.

---

## 2. The five tensions

### T1 — directive seed handling: coexist + dual-graph view

**Question.** What happens to `?`-typed seed nodes when expansion produces
typed realised counterparts?

**Decision.** Seed nodes **coexist** with realised nodes. Seeds are not
deleted, not refined, not type-mutated. They stay in the graph as
**error-attribution carriers** — each one holds the originating `@Map`
`AnnotationMirror`, which is exactly what Tier-2 needs to underline the
right annotation in the user's source when emitting "this directive could
not be realised" diagnostics.

**Implementation.** Two views over the same `DirectedMultigraph`:

```
   ┌─ Full graph (seed + realised + realises markers) ─┐
   │                                                   │
   │   Used for: diagnostics, debug DOT output,        │
   │             ranking partial realisations,         │
   │             "what's missing" Tier-2 errors.       │
   │                                                   │
   └───────────────────────────────────────────────────┘

   ┌─ Realised subgraph (excludes seed + realises) ────┐
   │                                                   │
   │   Used for: codegen, Dijkstra path selection,     │
   │             Tier-3 "is there a finite path?"      │
   │                                                   │
   │   Implemented via JGraphT MaskSubgraph filtering  │
   │   on edge.kind. No graph copy.                    │
   └───────────────────────────────────────────────────┘
```

**Weight handling.** Directive seed edges carry sentinel weight `∞`
(implementation: `Integer.MAX_VALUE / 2` or a named constant). Realised
edges use the documented weight scale (see §7). With seeds at `∞`, Dijkstra
on the *full* graph naturally prefers any realised path; on the *realised
subgraph* seeds are absent entirely.

**Tier-2 check.** For each directive seed node `n`, walk its outgoing
"realises" marker edges (see T3 below). Empty set ⇒ unrealised ⇒ emit error
referencing `n`'s incoming directive edge's `AnnotationMirror`.

**Why not Bellman-Ford with negative weights?** Considered. JGraphT's
`DijkstraShortestPath` requires non-negative weights, so negative-weight
seeds force a slower algorithm. Sentinel `∞` + dual-graph view keeps Dijkstra
and is conceptually simpler.

**Rejected alternatives:**
- *Replace seed nodes with typed nodes.* Requires removal API on `MapperGraph`
  (which Phase 1 explicitly forbade) and loses the `AnnotationMirror`
  attribution.
- *Refine seed nodes in place by mutating their type.* `Node` is `@Value` /
  immutable; mutation is not on the table.

### T2 — node identity is derivation, not location

**Question.** What is `Node.id()` for nodes that have no `Location`
(intermediate values like `Optional<String>` produced mid-expansion)? The
seed-graph design (D5) only defined two id forms — source-located and
target-located.

**Decision.** Node identity is **content-addressable on derivation**.
A node's id is a deterministic function of how it was produced:

```
   roots:                    encode(scope, parameter-name-or-return, type)
   directive-seeded chain:   parent.id ⊕ "::" ⊕ pathSegment
   strategy-derived:         parent.id ⊕ "::" ⊕ edge-kind ⊕ edge-discriminator
   phantom container elem:   parent.id ⊕ "::elem"
```

**Consequences.**

- Same derivation ⇒ same id ⇒ same node. `addNode` idempotency is automatic;
  no separate canonicalisation pass.
- Different derivations producing the "same" type stay distinct. Two
  `Optional<String>` intermediates from two different paths get distinct ids
  and don't collide.
- Seed nodes and realised nodes are distinct nodes by id (T1) — they sit
  side-by-side in the graph, linked only by "realises" marker edges (T3).
- Phantom container element nodes (§3) get well-defined ids derived from
  their parent collection node, so two `Set<X>` collections at different
  points produce distinct elements.

**The seed-graph ids fit this model as a special case.** The Phase 1 encoding

```
   v::<methodSig>::<accessPath>      →   parameter root + walk segments
   r::<methodSig>::<targetPath>      →   return root + walk segments
```

is just "root + path" — exactly the derivation pattern. No change needed to
existing seed-time encoding; expansion extends the same shape.

**The directive-seed path-string isn't a method call.** The seed encodes the
*directive-stated path* (e.g., `person.address.street`), which is not a
realised access. Realised expansion-time ids encode the actual access (e.g.,
`person→getAddress()→getStreet()`), which is a different string and a
different node.

**Random ids considered, rejected.** Random ids would require a separate
canonicalisation pass on every insert (find existing equivalent, dedup).
Deterministic derivation does the same work for free, so there's no benefit
to randomness.

### T3 — strategy emission: atomic edges + sub-directives

**Question.** What does a strategy invocation produce? The seed-graph design
sketched `Iterable<Proposal>` where a proposal bundled new nodes and new
edges. Is that the right shape?

**Decision.** A strategy invocation produces a **stream of atomic edges**.
Each edge is self-contained:

```java
interface ExpansionStrategy {
    Stream<Edge> proposeFor(Edge seedEdge, GraphContext ctx);
    CodeBlock emit(Edge realisedEdge, VarNames vars);   // codegen-time
}
```

- The strategy reads `seedEdge.from`, `seedEdge.to`, `seedEdge.directive`
  (and optionally walks `ctx`) to decide what to emit.
- Each emitted `Edge` may implicitly create its `to` node (the new node's
  id is derivable from the edge — see T2).
- No separate `Proposal` type; no `(newNodes, newEdges)` bundle.

**Three invocation flavors map to the three seed-edge flavors** from §1:

| Flavor | Shape | Strategies |
|---|---|---|
| ① source-side step (typed → ?) | hinted by goal's path-tail | GetterRead, FieldRead |
| ② bridge (? → ?) | needs realised counterparts of both ends | DirectAssign, OptionalWrap, Conversions |
| ③ target-side step (? → typed) | hinted by source's path-tail | SetterWrite, ConstructorCall (group), BuilderWrite (group) |

A strategy declares which flavor(s) it handles. The expansion driver dispatches
seed edges to applicable strategies.

**Sub-directives are the universal escape hatch.** When a strategy can do
*one step* but not the rest, it emits:

- One real edge (with weight from the scale).
- Optionally, one new untyped intermediate node.
- Optionally, one new **sub-directive** — a fresh `seed`-like edge marking
  "this still needs realising".

Sub-directives re-enter the work queue. Fixed-point continues until no
directives remain open. Termination is guaranteed if every sub-directive is
strictly closer to a known type than its parent (true for the strategy set
under consideration).

**Bridge strategies depend on prior elaboration.** A bridge can only fire
once both its endpoints have realised counterparts. The driver must run
flavor ① and ③ strategies first, then flavor ② — or run a fixed-point loop
where bridges retry until their endpoints are typed.

**Realisation tracking via marker edges.** When a strategy realises a `?`-typed
seed node (by producing a typed counterpart), it also emits a `realises`
edge from the seed to the realised node:

```
   src[person.first]:?  ──realises,w=0──▶  src[person→getFirst()]:String
```

Marker edges are excluded from the realised subgraph (§T1) and from codegen.
They serve two purposes:

1. Bridge strategies use `ctx.realisationsOf(seedNode)` to find the realised
   counterparts of seed endpoints.
2. Tier-2 walks the markers: any seed node with no outgoing `realises` edge
   is unrealised ⇒ emit error.

**Multi-edge groups (constructor / builder).** §5 covers this. A group strategy
emits a coordinated *set* of edges sharing a group identity. The "single
atomic edge per call" rule has a documented exception for group strategies:
they emit `Stream<Edge>` where all edges share the same `groupId` field.

### T4 — scope is encoded in id, not a node field

**Question.** Does every `Node` need an explicit `Scope` field? The
seed-graph implementation has it; is it load-bearing?

**Decision.** Scope is **encoded in node ids** (as the method-signature
prefix in the existing encoding). The `Scope` field on `Node` is **redundant
with id** — kept only as a rendering hint for the DOT cluster grouping (D7).

**Why this works.** Strategies operate locally on existing nodes. They don't
do global type queries like `findNodesByType(String)` across the whole
mapper. Scope discrimination is therefore **implicit in the graph topology**:
two same-typed nodes in different methods have different parent-derivation
chains, hence different ids, hence don't collide on dedup, hence aren't
accidentally bridged.

**Where scope still surfaces:**

- **Cluster rendering** in DOT files (D7). Field stays for this.
- **Cross-method composition via `MethodCallStrategy`** (§4). When a routable
  method `mapAddress(Address): AddressDto` exists, the strategy emits an
  edge whose `from` is in one method's chain and `to` is in another. This
  is the *only* edge type that crosses cluster boundaries, and it does so
  deliberately. `MapperScope` value type is the natural label for those
  edges' originating context.

**Implementation note.** The `Scope` field can remain on `Node` for now.
A future cleanup may demote it to a denormalisation (re-derived from id at
render time) or drop it. Not blocking Phase 2.

### T5 — expansion direction: demand-driven from seed edges

**Question.** Does expansion run forward (start from typed roots, fan out
through every strategy on every node, fixed-point) or backward (start from
goals, pull strategies in to fill them)?

**Decision.** **Demand-driven from seed edges.** The work queue is the set
of unrealised directive seed edges (initial: from `SeedGraph`; growing: from
sub-directives emitted during expansion).

```
   queue ← all directive seed edges from SeedGraph
   while queue not empty:
     seed ← queue.pop()
     for each strategy declaring seed.flavor():
       newEdges ← strategy.proposeFor(seed, ctx)
       for each Edge e in newEdges:
         graph.addIfAbsent(e)
         if e is a sub-directive: queue.push(e)
   if any seed remains unrealised: emit Tier-2 errors
```

**Contrast with the doc's earlier sketch** (forward-mode fixed point):

```
   Doc sketch (forward, O(strategies × nodes × passes)):
     for each pass:
       for each strategy:
         for each node:
           strategy.proposeFor(node)

   Demand-driven (O(seeds × strategies × paths)):
     work bound is tied to seed-graph size, not type-system size.
     strategies fire only when a seed edge requests them.
     no enumeration; strategies are verifiers.
```

**Strategies are verifiers, not enumerators.** GetterRead doesn't enumerate
all of `Person`'s getters when invoked on `src[person]:Person`. It reads the
hint (`goal.pathTail = "first"`) and asks Person for *that one* getter. If
found, emit one edge. If not, emit nothing.

This is the key efficiency property: strategies don't fan out unless asked.

---

## 3. Container model unification

`Optional<T>`, `List<T>`, `Set<T>`, `Stream<T>`, `Iterable<T>`, `Collection<T>`
are **structurally identical in the graph**. They differ only in codegen
template.

### The phantom-element-node pattern

Every container-typed node has implicit `extract` and `collect` edges to
its **phantom element node**:

```
   container<X>  ──extract,w=0──▶  ⌊container<X>⌋:X
                                          │
                                          │ (whatever element-wise
                                          │  transformation, resolved
                                          │  by ordinary strategies)
                                          ▼
                                   ⌊container<Y>⌋:Y  ──collect,w=2──▶  container<Y>
```

The element-wise edge is just another edge in the graph, fired by ordinary
strategies (e.g., `LocalDateTimeToInstantStrategy`). Codegen for the surrounding
extract/collect pair walks the inner edge and wraps it in the appropriate
container template:

| Container | Codegen template (sketch) |
|---|---|
| `Optional<X>` | `opt.map(elem -> ${innerCodegen}).orElse(null)` |
| `List<X>` | `list.stream().map(elem -> ${innerCodegen}).toList()` |
| `Set<X>` | `set.stream().map(elem -> ${innerCodegen}).collect(toSet())` |
| `Stream<X>` | `stream.map(elem -> ${innerCodegen})` |
| `Collection<X>` | `coll.stream().map(elem -> ${innerCodegen}).toList()` (defaults to List) |

**Phantom node ids** derive from their parent: `⌊parent⌋:X` has id =
`parent.id ⊕ "::elem"`. Two `Set<LDT>` collections at different points get
distinct phantom elements.

### Worked example: `Set<LocalDateTime>` → `Optional<List<Instant>>`

Strategies on the table: `OptionalWrap`, `LDTtoInstant`, `CollectionToList`,
`SetToCollection`, plus the implicit container `extract`/`collect` machinery.

Final realised path (one of several Dijkstra-equivalent options, this one
costed at 7):

```
   src[input]:Set<LDT>
      │
      │ extract, w=0
      ▼
   ⌊Set<LDT>⌋:LDT
      │
      │ ldtToInstant, w=1
      ▼
   ⌊Set<Instant>⌋:Instant
      │
      │ collectAsSet, w=2
      ▼
   i_a:Set<Instant>
      │
      │ setToCollection, w=0
      ▼
   i_b:Collection<Instant>
      │
      │ collectionToList, w=2
      ▼
   i_c:List<Instant>
      │
      │ optionalWrap, w=1
      ▼
   tgt[.timestamps]:Optional<List<Instant>>
      │
      │ setter, w=1
      ▼
   tgt[]:Foo
```

Total: 0 + 1 + 2 + 0 + 2 + 1 + 1 = **7**.

Alternative paths exist (e.g., `setToCollection` first, then `collectionMap`)
and may have different costs. Dijkstra on the realised subgraph picks the
cheapest.

### Why the unification matters

- **No special "lift" strategy machinery.** The `CollectionMapStrategy` /
  `OptionalMapStrategy` from the seed-graph design's Phase 3 list collapse
  into the phantom-element pattern. There's only one element-wise edge,
  fired by an ordinary strategy on phantom element nodes.
- **Nested generics work by recursion.** `Optional<List<Set<LDT>>>` to
  `Optional<List<Set<Instant>>>` produces three extract/collect pairs in the
  path, with the innermost edge being the LDT→Instant transformation. No
  special handling.
- **Cross-container conversion (`Set<X>` → `List<X>`) is a single direct
  edge** when X doesn't change. When X changes (`Set<LDT>` → `List<Instant>`),
  the path goes through phantom elements naturally.
- **Tier-3 reduces to "path-exists?"** No separate handling for containers.

### `ContainerKind` SPI

A small SPI declares container handlers:

```java
interface ContainerKind {
    boolean matches(TypeMirror type);                                        // is `type` a Foo<?> ?
    TypeMirror elementType(TypeMirror containerType);                        // element T from Foo<T>
    CodeBlock extractCodegen(VarNames vars);                                 // how to iterate
    CodeBlock collectCodegen(TypeMirror containerType, VarNames vars);       // how to rebuild
}
```

Built-ins live in the processor module (Optional, List, Set, Collection,
Stream, Iterable). User can ship a `ContainerKind` for their own type via
`META-INF/services` (same SPI mechanism as `ExpansionStrategy`).

### What's outside the unification

`Map<K,V>` and `Either<L,R>` have **two type parameters** and don't fit the
single-type-parameter container model. They probably need a different SPI
shape (multi-arity entry pattern with `keyExtract`, `valueExtract`,
`entryConstruct` edges). Deferred — cross that bridge when needed.

---

## 4. Method discovery model

A **single `MethodCallStrategy`** unifies three previously-separate
features from the seed-graph design:

```
   ┌───────────────────────────────────────────────────────┐
   │ Cross-mapper (injected dependency mapper)             │
   │ ┌─────────────────────────────────────────────────┐   │
   │ │ Same mapper, other generated abstract methods   │   │
   │ │ ┌───────────────────────────────────────────┐   │   │
   │ │ │ Same mapper, default methods (manual)     │   │   │
   │ │ └───────────────────────────────────────────┘   │   │
   │ └─────────────────────────────────────────────────┘   │
   └───────────────────────────────────────────────────────┘
```

**One strategy, three concentric scopes.** All three are "the strategy walks
a `TypeElement`'s methods and proposes each as an edge". The differences are
purely "which `TypeElement`(s) does it walk" and "what does codegen call".

| Scope | Source of methods | Codegen call site |
|---|---|---|
| Same mapper, default methods | `mapper.getEnclosedElements()` filtered to `default`-modifier | `this.userMethod(...)` |
| Same mapper, other generated methods | `mapper.getEnclosedElements()` filtered to abstract `@Mapper`-style methods | `this.otherGeneratedMethod(...)` |
| Cross-mapper composition | another mapper's `TypeElement` (injected as field) | `this.injectedMapper.method(...)` |

A method `Instant ldtToInstant(LocalDateTime)` becomes an edge:

```
   X:LocalDateTime  ──methodCall("ldtToInstant"),w=1──▶  Y:Instant
```

where `X` and `Y` are typed nodes (could be roots, intermediate values,
or phantom container elements).

**Routable mapper methods become first-class.** `mapAddress(Address): AddressDto`
on the same mapper is just another method-call edge:

```
   X:Address  ──methodCall("mapAddress"),w=1──▶  Y:AddressDto
```

When `mapHuman` needs to fill a slot of type `AddressDto`, expansion finds
this edge and uses it. No separate "routable methods" machinery needed —
this collapses Phase 6 of the seed-graph design.md into Phase 2.

**Cross-mapper composition is the same shape** with a different `TypeElement`
source. Out of scope for the first expansion change but the SPI is ready
for it.

**Discovery scope can be enabled per-mapper or globally** via processor
options or an annotation parameter. Default: same-mapper only (the two
innermost scopes); cross-mapper requires opt-in.

---

## 5. Constructor and builder grouping

### Hint-driven exact-match emission

Constructor and builder strategies fire on **the typed return root**
(`tgt[]:Foo`). They use the **set of directive targets sharing that return
root** as their hint:

```
   ConstructorStrategy.proposeFor(seedEdge: tgt[.firstName]:? → tgt[]:Foo, ctx):
       hint ← ctx.directiveTargetsOf(tgt[]:Foo) = {firstName, lastName}

       for each constructor of Foo:
         if constructor's parameter names == hint:
           emit a group of edges (one per param) sharing groupId

       if no constructor matches: emit nothing (this route is closed;
                                                 setter or builder may still cover)
```

Foo has constructors:

```
   Foo()                                          → too few params, skip
   Foo(String firstName)                          → too few params, skip
   Foo(String firstName, String lastName)         → EXACT match — emit group
   Foo(String firstName, String lastName, Address)→ too many params, skip
```

### What the strategy emits when a constructor matches

```
   ctor_arg[firstName]:String  ──ctorArg(groupId="Foo(S,S)" idx=0),w=1──▶  tgt[]:Foo
   ctor_arg[lastName]:String   ──ctorArg(groupId="Foo(S,S)" idx=1),w=1──▶  tgt[]:Foo

   plus realises edges from the seed targets to the new typed slots:
   tgt[.firstName]:?  ──realises──▶  ctor_arg[firstName]:String
   tgt[.lastName]:?   ──realises──▶  ctor_arg[lastName]:String
```

The new typed slot nodes (`ctor_arg[X]:T`) are now ordinary target slots
that source-side bridges fill normally.

### Why this collapses the multi-group Dijkstra problem

In an enumerate-all-constructors model, `Foo` with 4 constructors produces
4 parallel groups in the multigraph, and Dijkstra has to be group-aware
(super-nodes or per-group runs). Hint-driven exact match means usually
**0 or 1 constructor matches**:

- 0 matches ⇒ no constructor edges; setter/builder may cover.
- 1 match ⇒ one group; trivial.
- Rare: overloaded constructors with same param names but different types
  (e.g., `Foo(String, int)` vs `Foo(String, long)`) ⇒ 2 groups; super-node
  encoding handles it.

### Builder strategy is the same shape

`BuilderStrategy` fires on `tgt[]:Foo`, gets the same hint (directive
targets), looks for a builder pattern (`Foo.builder()` returning some
builder type with `firstName(...)`, `lastName(...)` chainable methods and
a `build()` terminator). If found, emits a chained-call group:

```
   builder_step[firstName]:String  ──builderCall(groupId, idx=0),w=1──▶  tgt[]:Foo
   builder_step[lastName]:String   ──builderCall(groupId, idx=1),w=1──▶  tgt[]:Foo
```

Codegen walks the group and emits `Foo.builder().firstName(...).lastName(...).build()`.

### Setter strategy stays per-edge

`SetterStrategy` fires on each `tgt[.X]:? → tgt[]:Foo` directive edge
independently. No grouping — setters compose freely.

```
   ┌────────────────────┬─────────────────────────────────────────────┐
   │ Strategy           │ Invocation trigger                          │
   ├────────────────────┼─────────────────────────────────────────────┤
   │ SetterStrategy     │ flavor ③ seed edge (per-edge)               │
   │ ConstructorStrategy│ flavor ③ seed edge, hint = ALL targets       │
   │                    │   sharing the return root (group emission)  │
   │ BuilderStrategy    │ flavor ③ seed edge, hint = ALL targets       │
   │                    │   sharing the return root (group emission)  │
   │ GetterReadStrategy │ flavor ① seed edge (per-edge)               │
   │ FieldReadStrategy  │ flavor ① seed edge (per-edge)               │
   │ DirectAssign       │ flavor ② seed edge, needs both ends typed   │
   │ OptionalWrap       │ flavor ② seed edge, T = Optional<X>         │
   │ Conversion (LDT→I) │ flavor ② seed edge, type-pair specific      │
   │ MethodCallStrategy │ flavor ② seed edge, type-pair via method    │
   │ ContainerKind      │ implicit extract/collect edges per container│
   └────────────────────┴─────────────────────────────────────────────┘
```

### Hybrid constructor + setter is unsupported in the first cut

If `Foo` has only `Foo(String firstName)` and `setLastName(String)`, and
the user requests both `firstName` and `lastName`, neither pure-constructor
nor pure-setter route works alone:

- ConstructorStrategy: no exact match.
- SetterStrategy: no no-arg constructor to start from.

To support this, a `HybridConstructorSetterStrategy` would emit a constructor
group for the matching subset and fall back to setters for the rest. This
mirrors MapStruct's behaviour. **Flagged and deferred** — known gap, not
blocking Phase 2.

---

## 6. Strategy SPI shape

The interface that falls out of T1–T5 + §3–§5:

```java
interface ExpansionStrategy {
    /**
     * Declares which seed-edge flavor(s) this strategy handles.
     * Driver dispatches seed edges to applicable strategies.
     */
    Set<SeedEdgeFlavor> handles();   // ① SOURCE_STEP, ② BRIDGE, ③ TARGET_STEP

    /**
     * Inspect the seed edge and graph context, emit zero or more atomic
     * edges. May also emit sub-directives (edges with seed-like role) that
     * re-enter the work queue.
     */
    Stream<Edge> proposeFor(Edge seedEdge, GraphContext ctx);

    /**
     * Codegen-time. Given an edge this strategy emitted, render the
     * corresponding code fragment.
     */
    CodeBlock emit(Edge realisedEdge, VarNames vars);
}

interface GraphContext {
    /** For bridge strategies: find typed counterparts of a seed node. */
    Stream<Node> realisationsOf(Node seedNode);

    /** For ctor/builder strategies: find directive targets sharing a return root. */
    Stream<Node> directiveTargetsOf(Node returnRoot);
}
```

Group emission (constructor / builder) is expressed by giving multiple
returned `Edge`s a shared `groupId` field. The driver and codegen treat
group members as all-or-nothing.

`SeedEdgeFlavor` is an enum with three members corresponding to the three
flavors from §1. Strategies declare their flavors statically; the driver
uses the declarations to filter dispatch.

### Strategy registration

Two registration mechanisms (mirroring container kinds):

- **Built-in strategies** wired via Dagger in the processor module.
- **User strategies** discovered via `ServiceLoader<ExpansionStrategy>`
  (`META-INF/services` entries in user JARs).

Discovery order is deterministic (lexicographic by FQN) and stable across
runs. Tiebreak in path selection: strategy priority (declared on the
interface as a `default int priority() { return 0; }`) → strategy class FQN
→ source position.

---

## 7. Weight scale convention

All strategy edges use weights from this scale:

| Weight | Meaning | Examples |
|---|---|---|
| 0 | reference / view / no-op | identity, `Set→Collection` widening, `extract` from container, `realises` marker |
| 1 | single Java operation | getter call, setter call, method call, primitive boxing, optional wrap, conversion (LDT→Instant) |
| 2 | full structural copy / O(n) op | collect into a new collection, materialise stream, `Collection→List` |
| 3 | expensive op | DB lookup, file read — unusual in mappers but reserved |
| `∞` | sentinel: directive seed / unrealised | `Integer.MAX_VALUE / 2` constant |

**Tier-1 invariant:** every strategy edge has weight in `{0, 1, 2, 3}`.
Sentinel `∞` is only used by directive seed edges and sub-directives.

**Why a small fixed scale.** Forces strategy authors to think in terms of
a shared cost language. Drift across strategies (one author uses 1, another
uses 7 for "the same kind of op") would make Dijkstra path selection
incoherent. The scale is documented and reviewed.

**Optimization phase deferred.** A future pass may "tune" the graph by
collapsing or rewriting edges (e.g., merging consecutive container
operations into a single stream pipeline). Not needed for correctness;
Dijkstra on the un-optimized graph already produces correct codegen.

---

## 8. Open questions / explicitly deferred

- **Hybrid constructor + setter** (§5). MapStruct supports this. Percolate's
  first cut does not. Deferred to a future strategy.
- **`Map<K,V>` and `Either<L,R>`** (§3). Multi-type-parameter containers
  need a different SPI shape than the single-type-parameter `ContainerKind`.
  Deferred.
- **Auto-mapping of unmentioned slots.** MapStruct fills any setter whose
  name matches a getter even without `@Map`. Percolate currently requires
  explicit `@Map` (the directive seeds set the scope). Whether to add an
  auto-discovery mode is a future product decision.
- **Empty-container codegen.** `null`-source handling, empty-Optional
  handling, empty-collection handling. Codegen templates need to be
  defensive but the rules aren't pinned yet.
- **Weight optimization phase.** Worth doing eventually (collapse
  consecutive container ops, deduplicate redundant copies). Not blocking
  correctness.
- **`Scope` field cleanup.** Currently a denormalisation of the id encoding.
  Could be derived at render time and dropped from `Node`. Cosmetic, deferred.
- **Diagnostic buffering for Tier-3.** When one missing strategy causes
  fan-out errors, the existing direct-write `Diagnostics` channel will
  produce duplicates. Buffered/deduplicated diagnostics are sketched in the
  seed-graph design doc's Phase 7. Still future.

---

## 9. Glossary

Terms used here that don't appear (or appear with different meanings) in
the current codebase.

- **directive seed edge** — an edge emitted by `SeedGraph` from the user's
  `@Map` directives. Carries the originating `AnnotationMirror`. Weight is
  the sentinel `∞` post-Phase-2 (currently `1` in the seed-graph
  implementation; this changes when expansion lands).
- **flavor (① / ② / ③)** — classification of seed edges by which endpoints
  are typed: source-step (typed → ?), bridge (? → ?), target-step (? → typed).
  Drives strategy dispatch.
- **realised edge** — an edge emitted by an expansion strategy from real
  Java machinery (getter, setter, method call, conversion, container
  extract/collect). Weight in `{0, 1, 2, 3}`.
- **realises marker edge** — a weight-0 edge linking a seed node to its
  realised typed counterpart. Excluded from codegen subgraph; used by
  bridge strategies and Tier-2.
- **sub-directive** — a fresh seed-like edge emitted by a strategy mid-
  expansion when it can do one step but not the rest. Re-enters the work
  queue.
- **phantom element node** — a derived node representing the element type
  of a container node. Id = `parent.id ⊕ "::elem"`. Connected to its parent
  via implicit `extract` and `collect` edges.
- **group emission** — a strategy emits multiple edges sharing a `groupId`
  (constructor parameters, builder chain steps). Codegen treats group
  members as all-or-nothing.
- **realised subgraph** — view over the full graph excluding seed and
  marker edges. Used for codegen Dijkstra and Tier-3 path-existence checks.
- **full graph** — the complete `DirectedMultigraph` including seed,
  realised, and marker edges. Used for diagnostics and debug DOT.
- **hint-driven exact match** — the way constructor/builder strategies use
  the set of directive targets sharing a return root to find a matching
  shape (constructor parameters, builder methods). No enumeration; either
  exactly matches or doesn't fire.
- **GraphContext** — the small read-only query API provided to strategies.
  Two methods: `realisationsOf(seedNode)` and `directiveTargetsOf(returnRoot)`.
- **`SeedEdgeFlavor`** — enum classifying directive seed edges so the driver
  can dispatch to applicable strategies.
- **container kind** — a unit of SPI declaring how to handle one
  single-type-parameter container (Optional, List, Set, etc.). Provides
  type detection, element extraction, and codegen templates for `extract`
  and `collect` edges.

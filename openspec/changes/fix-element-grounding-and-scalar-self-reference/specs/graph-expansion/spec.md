## REMOVED Requirements

### Requirement: A method never satisfies demands in its own method scope

**Reason**: The scope-wide candidate-visibility exclusion is too coarse. By hiding the method-under-generation `M` throughout its entire `MethodScope`, it also forbids the *legitimate* self-call on a **sub-part** of the parameter (`this.mapNode(src.getNext())`), which lives in the same scope as the degenerate `this.mapNode(src)`. A scope/visibility filter runs before any argument is bound, so it cannot tell the two apart — but the distinction is exactly the bound argument. Replaced by the per-binding rule below.

**Migration**: Strategies are unaffected (still myopic, still call `producing(type)`). The driver's filtered `CallableMethods` view (`SelfCallGuard`) is removed; the self-call exclusion now keys on the **bound argument `Value`** at land time, recognising the self-call structurally via the neutral call-target on `OperationSpec` (see `expansion-strategy-spi`), not via candidate visibility.

## ADDED Requirements

### Requirement: A method never calls itself on its own whole parameter

The driver SHALL refuse to land a self-call operation whose argument port binds to the calling method's **own parameter-root `Value`** — the whole, unchanged parameter (`this.m(src)`), which is always degenerate (runtime infinite recursion). A self-call on a **strict sub-part** of the parameter (an accessor result such as `src.getNext()`, or a container element) SHALL remain available, because structural recursion over a shrinking input terminates. The decision SHALL be made **per binding** at land time, not per scope or per site: at one target site the engine over-emits both bindings and the degenerate one is *strictly cheaper* (the parameter-root costs nothing, an accessor costs `ACCESS`), so over-emit + cost-prune alone cannot choose correctly — the binding must be refused outright.

The driver SHALL recognise a self-call structurally by comparing the operation's **call target** (carried on its `OperationSpec`, see `expansion-strategy-spi`) against the current `MethodScope`'s method, matched by signature (name + parameter types); it SHALL NOT infer identity from the spec's `label`. The refusal SHALL apply only when the call target is the scope's own method **and** the bound argument is that scope's parameter-root `Value`; it SHALL NOT apply to a container's per-element transform (a separate child scope), and delegation to a *different* method returning the same type SHALL remain available. There SHALL be no change to the `CallableMethods` / `ResolveCtx` SPI and no loss of strategy myopia.

#### Scenario: A container-return method does not self-bridge

- **WHEN** `List<DAO> mapMany(Set<DTO>)` is expanded and the mapper also declares `DAO mapOne(DTO)`
- **THEN** `mapMany` called on its own parameter is refused, so the selected plan is `src.stream().map(this::mapOne).collect(...)`, never `return this.mapMany(src)` nor an `iterate`/`collect` round-trip over `this.mapMany(src)`

#### Scenario: Legitimate self-recursion through a container element is preserved

- **WHEN** a self-similar mapper `Cat mapCat(CatDto)` maps a `List<Cat> children` field from `List<CatDto>` element-wise
- **THEN** the element transform (a child scope) calls `mapCat` recursively — `src.getChildren().stream().map(e -> mapCat(e))` — while the method's own scope never binds `mapCat` to the whole parameter

#### Scenario: A scalar self-referential field generates structural recursion

- **WHEN** a mapper `Node mapNode(NodeSrc)` maps a scalar `Node next` field from `src.getNext()` (the recursion lives in the method's own scope, not a child scope)
- **THEN** the self-call `this.mapNode(src.getNext())` is kept (its argument is a sub-part of the parameter), so the mapper generates the terminating `next`-walk and the degenerate `this.mapNode(src)` binding is the only one refused

#### Scenario: The whole-parameter self-call remains an honest no plan

- **WHEN** a mapper would only be satisfiable by calling itself on its whole, unchanged parameter (no smaller argument exists)
- **THEN** that binding is refused and the demand reports a clean "no plan", never an infinite `return this.m(src)` recursion

#### Scenario: Delegation to a different method returning the same type is available

- **WHEN** `M` and a different method `N` both return the demanded type and `N` consumes the parameter (`return n(p)`)
- **THEN** the call to `N` is landed (only `M`'s self-call on its own parameter-root is refused)

## MODIFIED Requirements

### Requirement: Unit tests mock the ResolveCtx seam

Every `processor` and `spi` unit spec (`@Tag('unit')`) SHALL exercise its subject against a **mocked `ResolveCtx`** — a Spock `Mock()` stubbing only the seam questions its subject asks — with **mocked collaborators** for the subject's injected seams, and a **`Spy()` on the subject stubbing only a genuinely self-recursive call** where a method recurses into its own type structure (unification, substitution). `processor` SHALL contain **no `FakeResolveCtx`/`FakeType`**: once `Grounding` and `ExpandStage.Driver` are decomposed into single-method collaborators, no `processor` unit spec drives a whole-pipeline pass, so none needs a structural fake substrate. A hand-written, javac-free `FakeResolveCtx`/`FakeType` MAY remain only for the small number of **`spi`** specs pinning the seam's own default-method composition (`ResolveCtxSpec`, `ContainerSpec`), pending `spi`'s own decomposition. Either way, any `javax.lang.model` value is passed through as a **never-stubbed opaque token** — the `ValidateNoDuplicateTargetsStageSpec` pattern. There SHALL be **no javac in the unit path**: a unit spec SHALL NOT construct a `JavacTask`, compile a `@Mapper`, or stand up any shared type substrate, so the suite is parallel-safe by construction. Specs SHALL be **example-based Spock** (`where:` tables for algebra/laws); **jqwik SHALL NOT be used**.

Most specs stub only the 1-2 seam questions their subject asks (e.g. `ListContainerSeamSpec`, `SourceCandidatesSpec`, `AccessorSpec`). A decomposed algorithm such as `Grounding`'s unification is tested by mocking its extracted collaborators (`Unifier`, `BindingEnumerator`, `SpecInstantiator`) and spying only its irreducible self-recursion — not through a `FakeResolveCtx`.

#### Scenario: A processor unit spec mocks the seam and collaborators, adds no javac, and uses no fake
- **WHEN** a `processor` unit spec drives its subject
- **THEN** it constructs `ResolveCtx ctx = Mock()` (stubbing only the seam questions the subject asks) and mocks the subject's injected collaborators, never constructs a `JavacTask` or compiles a `@Mapper`, and imports no `FakeResolveCtx`/`FakeType`

#### Scenario: Self-recursion is isolated with a spy
- **WHEN** a decomposed engine method recurses into its own type structure (unification or substitution)
- **THEN** its spec spies the subject and stubs the recursive call, rather than exercising the full recursion or standing up a fake type-world

#### Scenario: Unit specs are example-based Spock
- **WHEN** the unit specs are inspected
- **THEN** no spec imports `net.jqwik`; property-shaped cases are covered by `where:` tables

## ADDED Requirements

### Requirement: Codegen unit tests need no shared javac substrate

Once the codegen stages (`AssembleMapperType`, `BuildMethodBodies`) are decomposed, their pure assembly logic SHALL be unit-tested against mocked seams, and the only residue requiring a genuinely compiler-backed `TypeMirror` — a JavaPoet `TypeName.get(mirror)` rendering leaf — SHALL be covered by the compile-based feature-e2e layer, not by a shared unit-test javac substrate. `PrivateTypeUniverse` SHALL be reduced to that leaf's needs or removed outright; it SHALL NOT back any `processor` engine-logic unit spec.

#### Scenario: PrivateTypeUniverse no longer backs engine-logic unit specs
- **WHEN** the `processor` unit specs for decomposed stages are inspected
- **THEN** none of them constructs a `PrivateTypeUniverse` for engine-logic assertions; any surviving use is confined to a compile-tested codegen `TypeName.get` leaf

#### Scenario: Codegen assembly logic is mock-tested
- **WHEN** a decomposed codegen unit (an extracted method-signature or method-body assembler) is unit-tested
- **THEN** it is exercised against mocked seams, with real-mirror rendering left to the feature-e2e compile tests

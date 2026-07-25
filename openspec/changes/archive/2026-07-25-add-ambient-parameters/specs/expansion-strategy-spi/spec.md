## MODIFIED Requirements

### Requirement: Port declares an explicit sourcing mode

Each `Port` of an `OperationSpec` SHALL declare an explicit **sourcing mode** stating how the engine binds the feeding `Value`, so the driver dispatches on a declared fact and never reconstructs a port's intent from a name-match or a boolean. The mode SHALL be one of a closed set:

- `SUBTARGET` — a structural sub-target: the engine mints a fresh `FREE` demand at the child location (the parent target path extended by the port's name) and re-demands it. Assembly strategies (e.g. `ConstructorCall`) stamp their parameter ports `SUBTARGET`.
- `REUSE` — the feeding `Value` must already exist in scope: the engine binds a matching in-scope source, or the Operation does not apply (it is **never** minted). This is exactly the **reuse-only** port the built-in identity, nullness-crossing, and container-`unwrap` requirements describe — the bound mode for a consuming Operation whose input is structurally larger than its output.
- `REUSE_OR_MINT` — the default: the engine binds a matching in-scope source, or mints a fresh `FREE` intermediate of the port's type and nullness at the output location and re-demands it (a multi-hop conversion). An ordinary concrete conversion or accessor input port uses this mode.
- `AMBIENT` — the feeding `Value` is the ambient binding registered under the port's **key** in the enclosing scope's ambient environment (see `ambient-parameters`). The engine resolves the key, verifies the binding's type against the port's declared type, and binds it. Unlike `REUSE`, an unresolved key SHALL NOT cause the Operation to quietly not apply — it is reported as an error naming the key. A port in this mode SHALL carry a non-empty key.

A `Port` SHALL therefore carry an optional **key**, meaningful only in `AMBIENT` mode and empty in the other three.

A strategy SHALL choose a port's mode as a purely local decision; the mode carries **no** graph or candidate access, and the engine — not the strategy — owns the child location, the ambient environment, and every graph mutation. A strategy declaring an `AMBIENT` port SHALL stamp the mode and key only, and SHALL NOT itself consult the ambient environment. The mode set SHALL remain **extensible**: a further binding mode SHALL be addable beside these four without changing the existing four or the strategies that declare them. `REUSE_OR_MINT` SHALL be the default of a plainly-constructed concrete port, so existing concrete-port construction is source-unaffected.

#### Scenario: An assembly port is a sub-target
- **WHEN** `ConstructorCall` emits a constructor parameter port
- **THEN** the port's sourcing mode is `SUBTARGET`, and the engine mints a child-target demand at the parent path extended by the port name

#### Scenario: A reuse-only port is REUSE
- **WHEN** `DirectAssign`, a nullness crossing, or a container `unwrap` emits its consuming input port
- **THEN** the port's sourcing mode is `REUSE`, and the engine binds an in-scope source or the Operation does not apply (never minted)

#### Scenario: A default conversion port is REUSE_OR_MINT
- **WHEN** a unary conversion (e.g. `int→long`) emits its value port without specifying a mode
- **THEN** the port's sourcing mode is `REUSE_OR_MINT`, and the engine binds an in-scope source or mints a fresh intermediate at the output location

#### Scenario: An ambient port is AMBIENT and carries a key
- **WHEN** `MethodCallBridge` emits the port for an `@Ambient Order order` parameter
- **THEN** the port's sourcing mode is `AMBIENT` and its key is `"order"`
- **AND** the engine, not the strategy, resolves that key against the enclosing scope's ambient environment

#### Scenario: A non-ambient port carries no key
- **WHEN** a `SUBTARGET`, `REUSE`, or `REUSE_OR_MINT` port is inspected
- **THEN** its key is empty

#### Scenario: The mode set is closed but extensible
- **WHEN** the `Port` sourcing modes are enumerated
- **THEN** exactly `SUBTARGET`, `REUSE`, `REUSE_OR_MINT`, and `AMBIENT` are defined, and a new mode could be added beside them without altering these four or their declaring strategies

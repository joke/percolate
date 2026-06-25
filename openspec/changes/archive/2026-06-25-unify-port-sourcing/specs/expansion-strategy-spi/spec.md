## ADDED Requirements

### Requirement: Port declares an explicit sourcing mode

Each `Port` of an `OperationSpec` SHALL declare an explicit **sourcing mode** stating how the engine binds the feeding `Value`, so the driver dispatches on a declared fact and never reconstructs a port's intent from a name-match or a boolean. The mode SHALL be one of a closed set:

- `SUBTARGET` — a structural sub-target: the engine mints a fresh `FREE` demand at the child location (the parent target path extended by the port's name) and re-demands it. Assembly strategies (e.g. `ConstructorCall`) stamp their parameter ports `SUBTARGET`.
- `REUSE` — the feeding `Value` must already exist in scope: the engine binds a matching in-scope source, or the Operation does not apply (it is **never** minted). This is exactly the **reuse-only** port the built-in identity, nullness-crossing, and container-`unwrap` requirements describe — the bound mode for a consuming Operation whose input is structurally larger than its output.
- `REUSE_OR_MINT` — the default: the engine binds a matching in-scope source, or mints a fresh `FREE` intermediate of the port's type and nullness at the output location and re-demands it (a multi-hop conversion). An ordinary concrete conversion or accessor input port uses this mode.

A strategy SHALL choose a port's mode as a purely local decision; the mode carries **no** graph or candidate access, and the engine — not the strategy — owns the child location and every graph mutation. The mode set SHALL be **extensible**: a future binding mode (e.g. binding a port by name to an ambient captured source) SHALL be addable beside these three without changing the existing three or the strategies that declare them. `REUSE_OR_MINT` SHALL be the default of a plainly-constructed concrete port, so existing concrete-port construction is source-unaffected.

#### Scenario: An assembly port is a sub-target
- **WHEN** `ConstructorCall` emits a constructor parameter port
- **THEN** the port's sourcing mode is `SUBTARGET`, and the engine mints a child-target demand at the parent path extended by the port name

#### Scenario: A reuse-only port is REUSE
- **WHEN** `DirectAssign`, a nullness crossing, or a container `unwrap` emits its consuming input port
- **THEN** the port's sourcing mode is `REUSE`, and the engine binds an in-scope source or the Operation does not apply (never minted)

#### Scenario: A default conversion port is REUSE_OR_MINT
- **WHEN** a unary conversion (e.g. `int→long`) emits its value port without specifying a mode
- **THEN** the port's sourcing mode is `REUSE_OR_MINT`, and the engine binds an in-scope source or mints a fresh intermediate at the output location

#### Scenario: The mode set is closed but extensible
- **WHEN** the `Port` sourcing modes are enumerated
- **THEN** exactly `SUBTARGET`, `REUSE`, and `REUSE_OR_MINT` are defined, and a new mode could be added beside them without altering these three or their declaring strategies

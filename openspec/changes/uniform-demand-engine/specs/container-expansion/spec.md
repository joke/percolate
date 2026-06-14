## MODIFIED Requirements

### Requirement: Containers iterate and collect through a Stream intermediate

A container SHALL expose its element sequence as an explicit `Stream<E>` `Value`. Each container emits
a plain `iterate` Operation `Cont<E> → Stream<E>` for **its own kind only** (a sequence via
`Collection.stream()`/`Arrays.stream`; a presence wrapper via `Optional.stream()` — the 0-or-1 stream
that realises drop-empties). A container that supplies `collect` (i.e. **a sequence** — kind is
emergent from the presence of `collect`, not a separate base type) emits a plain `collect` Operation
`Stream<E> → Seq<E>` for its own kind; a presence wrapper supplies no `collect`. No container
Operation SHALL reference a container kind other than its own.

#### Scenario: A list iterates and a set collects
- **WHEN** a `Stream<E>` is demanded from a `List<E>` candidate, and a `Set<E>` from a `Stream<E>`
  candidate
- **THEN** the `List` container emits the `iterate` (`.stream()`) and the `Set` container emits the
  `collect` (`Collectors.toSet()`), each unaware of the other kind

#### Scenario: A presence wrapper iterates but does not collect
- **WHEN** a `Stream<E>` is demanded from an `Optional<E>` candidate
- **THEN** the `Optional` container emits an `iterate` rendering `Optional.stream()`, and supplies no
  `collect` (it is a wrapper by the absence of `collect`)

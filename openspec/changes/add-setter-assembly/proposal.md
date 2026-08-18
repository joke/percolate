## Why

Percolate assembles a target through a constructor or through a builder. A plain JavaBean — a no-argument
constructor plus `setX` methods — is the most common target shape in Java, and percolate cannot map to one at
all. `add-builder-assembly` excluded setter assembly on purpose and named it as its own change, because the
setter sequence is a statement list and every operation renders one expression.

That gap closes without a new codegen shape. The statement list moves into a generated helper method, and the
operation renders one call to it. The result keeps the single n-ary operation that carries totality, so the
engine gains no setter vocabulary.

## What Changes

- A new built-in strategy `SetterAssembly` produces a target through its no-argument constructor and its
  JavaBean setters. It emits **one** n-ary `OperationSpec` with one sub-target port per declared child, exactly
  as `BuilderAssembly` does, and it requests a generated helper method that runs the setter sequence.
- **BREAKING (third-party strategy authors)** `MemberRequest` grows a second kind. Today it is a `@Value` class
  describing a field. It becomes an interface with two implementations, so it also describes a **method** — a
  return type, an ordered parameter list, and a body `CodeBlock`. The processor still allocates the name, and
  the requesting operation still references the member by its dedup key.
- The generate stage stops hard-coding `private static final` on every requested member. Two new compile-time
  options set the visibility and the `static` modifier of every generated helper member.
- **BREAKING (third-party strategy authors)** `percolate.construction.preference` accepts an **ordered list** of
  form tokens instead of one token. Each assembly strategy reads its own rank and prices itself from it.
  `ConstructionPreference.from` is replaced by a rank query. Both current values stay valid as one-element
  lists, so no mapper author sees a behaviour change.
- The manual gains a setter-assembly page in `strategies-builtin/src/docs/`, and the compile-time switches
  reference documents the two new options and the list grammar.

Explicitly **out of scope**: updating an existing bean instance in place, the MapStruct `@MappingTarget` shape
(`void updateCarFromDto(CarDto dto, @MappingTarget Car car)`). It shares the setter sequence and nothing else.
Its cost sits in the engine — a parameter that is not a source, a constraint that excludes creation, and a root
demand for a `void` method. `design.md` records what this change fixes now so that feature stays cheap later.

## Capabilities

### New Capabilities

- `setter-assembly`: the JavaBean setter assembly form — its gate (a non-private no-argument constructor plus
  containment of the declared children in the inherited setter set), the single n-ary operation it emits, the
  generated helper method that carries the setter statements, its dedup identity, and its self-pricing.

### Modified Capabilities

- `builder-assembly`: the arbitration requirement covers three forms instead of two. The preference becomes a
  ranked list, each strategy reads only its own token and its own default rank, and the unpreferred forms carry
  distinct weights so two forms can never tie.
- `expansion-strategy-spi`: `MemberRequest` describes either a field or a method, and the requirement that named
  a field type and an initializer now covers both kinds.
- `code-generation`: the member hoisting requirement emits methods as well as fields, and the modifiers of a
  generated member come from options rather than from a constant.
- `processor-options`: `percolate.helpers.visibility` and `percolate.helpers.static` join the declared options
  as typed `ProcessorOptions` fields, because the generate stage reads them. The
  `percolate.construction.preference` declaration records the list grammar.
- `builtin-strategy-unit-tests`: `SetterAssembly` joins the enumerated per-strategy unit specifications, with
  scenarios for its gate, its helper request, and its rank pricing.
- `user-manual`: a setter-assembly page backed by a compiled end-to-end fixture, and the compile-time switches
  reference for the two new options.

## Impact

**Modules.** `percolate-spi` (`MemberRequest`), `percolate-processor` (`ProcessorOptions`,
`ProcessorOptionsReader`, `PercolateProcessor.getSupportedOptions`, `MemberPlan`, `BuildMethodBodies`),
`percolate-strategies-builtin` (`SetterAssembly`, `ConstructionPreference`, `ConstructorCall`,
`BuilderAssembly`), `docs` (navigation entry only).

**Mapper authors.** One new target shape becomes mappable. Two new compile-time options exist. No current
mapping changes behaviour, because the new form ranks last by default and every existing option value stays
valid.

**Third-party strategy authors.** Two source-breaking changes. `ConstructionPreference.from` is removed, and an
author who prices against the construction preference reads a rank instead. `MemberRequest` becomes an
interface, so `new MemberRequest(type, initializer, key)` becomes `MemberRequest.field(type, initializer,
key)`.

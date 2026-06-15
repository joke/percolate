## REMOVED Requirements

### Requirement: Seeding produces roots and goal specs, not edges

**Reason**: `SeedStage` is deleted. The graph starts empty and is grown entirely by the demand
work-list; root demands are enqueued at expansion entry and parameter leaves materialise lazily on
first reference.
**Migration**: See `graph-expansion` ADDED "Expansion self-seeds root demands from an empty graph".
Goal-spec derivation moves to `mapping-discovery` ADDED "Declared-bindings goal spec derived during
discovery".

### Requirement: Declared bindings derived per target level

**Reason**: This derivation is unchanged in substance but no longer belongs to a seed stage; it is a
pure reshaping of discovered `@Map` directives.
**Migration**: Now owned by `mapping-discovery` ADDED "Declared-bindings goal spec derived during
discovery" (same per-level grouping rule).

### Requirement: Constant and default directives participate as bindings

**Reason**: Goal-spec membership of constant/default directives is unchanged but is no longer a
seeding concern.
**Migration**: Covered by the discovery-owned goal spec (`mapping-discovery`); constant/default
supply and crossings remain expansion-time strategies (`constant-values`, `default-values`).

### Requirement: Seed-time validation preconditions

**Reason**: There is no seed stage to state preconditions for; the validation stages
(`ValidateSourceParameters`, `ValidateMappingShape`) run before expansion and are unchanged.
**Migration**: The "no directive dropped silently" guarantee is preserved by discovery + validation
ahead of expansion; no seeding step is involved.

### Requirement: Stage classes follow the *Stage naming convention

**Reason**: This is a cross-cutting convention about every `Stage` implementation, mis-homed in the
removed `seed-graph` capability; with `SeedStage` deleted its seed-specific clause is moot.
**Migration**: Re-homed to the `processor` capability as ADDED "Stage classes follow the *Stage
naming convention" (general rule retained; `*Phase` exemption retained; the SeedStage-specific clause
dropped).

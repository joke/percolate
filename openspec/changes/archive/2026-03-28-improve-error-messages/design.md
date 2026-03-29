## Context

The annotation processor pipeline produces error diagnostics when `@Map` directives reference unknown properties or when the mapping graph has structural issues. Current error messages are terse — e.g., `Unknown source property: userName` — providing no context about what properties exist, which type was inspected, or whether a typo was made.

The error construction is currently inline string concatenation in `BuildGraphStage` and `ValidateStage`. Both stages have access to all the information needed for rich messages (discovered property maps, type mirrors, method elements, graph structure).

## Goals / Non-Goals

**Goals:**
- Provide actionable error messages that include available properties, type context, and fuzzy-match suggestions
- Centralize error message formatting in a dedicated `ErrorMessages` utility
- Keep `BuildGraphStage` and `ValidateStage` focused on their graph logic

**Non-Goals:**
- Changing the pipeline architecture or stage execution order
- Adding new error detection (only improving existing error messages)
- Internationalization or configurable message formats

## Decisions

### Decision 1: Dedicated `ErrorMessages` utility class

Static utility class with one method per error type. Stages call these methods instead of concatenating strings inline.

**Why over inline construction:** Keeps stage logic clean, makes message formatting independently testable, and centralizes the "did you mean?" logic in one place.

**Why over an injected service:** No state or dependencies needed — pure functions from inputs to strings. A static utility is the simplest fit.

### Decision 2: Levenshtein distance for fuzzy matching

Implement a private Levenshtein distance method inside `ErrorMessages` (~15 lines). Suggest candidates with edit distance ≤ 3 and ≤ half the input name length, sorted by distance, limited to top 3.

**Why Levenshtein:** Well-understood, handles typos (insertions, deletions, substitutions). Good enough for property names which are typically short identifiers.

**Why not an external library:** The algorithm is trivial to implement. Adding a dependency for one function is not worth it.

### Decision 3: Multi-line error message format

```
Unknown source property 'userName' on method 'toTarget(PersonDto)'.
  Source type: com.example.PersonDto
  Available source properties: [name, age, email, address]
  Did you mean: name?
```

Each message includes:
1. Primary error line with property name and method context
2. Indented detail lines with type, available properties, and suggestions

**Why multi-line:** Compiler error output preserves newlines. Indentation groups related info and keeps the primary message scannable.

### Decision 4: ValidateStage collects additional graph context

For "unmapped target property" errors: collect source nodes with out-degree 0 (unmapped sources) and suggest the closest match.

For "conflicting mappings" errors: walk incoming edges to name the conflicting source properties.

**Why:** These are the natural follow-up questions a user asks. "What source *could* I map?" and "Which sources are conflicting?" Answering them in the error saves a round-trip.

## Risks / Trade-offs

- **Verbose output with many properties** — If a type has 50+ properties, listing all of them is noisy. → Mitigation: cap the displayed list (e.g., first 10 + "and N more").
- **Levenshtein on short names** — Very short property names (1-2 chars) may produce spurious suggestions. → Mitigation: the distance threshold (≤ half name length) naturally filters these.
- **TypeMirror.toString() format** — The type name format depends on the compiler implementation. In practice, `javac` produces fully-qualified names which is what we want.

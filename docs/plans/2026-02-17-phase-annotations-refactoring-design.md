# Phase Annotations Refactoring Design

**Date:** 2026-02-17

## Problem

`AnalysisPhase`, `GenerationPhase`, and `ValidationPhase` are Dagger `@Qualifier`
annotations that live in the `io.github.joke.caffeinate.immutable` package. They have
no logical connection to immutables — they are shared infrastructure used by both the
`mutable` and `immutable` modules. This creates a confusing cross-package dependency
where `mutable` code imports from `immutable`.

## Decision

Move all three phase qualifier annotations to a new dedicated package:
`io.github.joke.caffeinate.phase`.

## Approach

**Straight move — no backwards-compatibility stubs.** The annotations are internal to
the processor and have no external callers, so there is no need to keep forwarding
declarations in the old location.

## Files Affected

### New files

| File | Description |
|------|-------------|
| `processor/src/main/java/io/github/joke/caffeinate/phase/AnalysisPhase.java` | Moved from `immutable` |
| `processor/src/main/java/io/github/joke/caffeinate/phase/GenerationPhase.java` | Moved from `immutable` |
| `processor/src/main/java/io/github/joke/caffeinate/phase/ValidationPhase.java` | Moved from `immutable` |
| `processor/src/main/java/io/github/joke/caffeinate/phase/package-info.java` | New, consistent with other packages |

### Deleted files

- `processor/src/main/java/io/github/joke/caffeinate/immutable/AnalysisPhase.java`
- `processor/src/main/java/io/github/joke/caffeinate/immutable/GenerationPhase.java`
- `processor/src/main/java/io/github/joke/caffeinate/immutable/ValidationPhase.java`

### Updated imports

| File | Changes |
|------|---------|
| `immutable/ImmutableModule.java` | Update imports for `AnalysisPhase`, `GenerationPhase` |
| `immutable/ImmutableGenerator.java` | Update imports for `AnalysisPhase`, `GenerationPhase` |
| `mutable/MutableModule.java` | Update imports for all three (was importing from `immutable`) |
| `mutable/MutableGenerator.java` | Update imports for all three (was importing from `immutable`) |

## No Logic Changes

This is a pure structural refactoring. No behaviour changes, no API changes.
All existing tests pass unchanged.

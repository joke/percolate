// Deliberately @NullUnmarked (not @NullMarked): this example demonstrates the requireNonNullElse fallback for
// an absent-capable (unannotated, non-JSpecify-tracked) reference source, which requires it resolve as UNKNOWN
// nullness rather than the NON_NULL a @NullMarked package would default unannotated types to.
@org.jspecify.annotations.NullUnmarked
package io.github.joke.percolate.docs.mapannotation;

package io.github.joke.percolate.processor.internal.graph;

// A vertex of the bipartite resolution graph. Exactly two implementations exist — Value (a typed variable; an
// OR over its producer Operations) and Operation (an n-ary production; an AND over its ports) — both final with
// package-private constructors, so the hierarchy is closed by the package boundary (Java 11; no sealed types).
// Both use instance identity for equals/hashCode.
public interface GraphVertex {

    // The scope this vertex lives in. No Dep edge ever connects vertices of different scopes.
    Scope getScope();

    // A deterministic identifier used for stable ordering and rendering, never for equality.
    String id();
}

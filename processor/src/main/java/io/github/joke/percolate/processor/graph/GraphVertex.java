package io.github.joke.percolate.processor.graph;

/**
 * A vertex of the bipartite resolution graph. Exactly two implementations exist — {@link Value} (a typed
 * variable; an OR over its producer Operations) and {@link Operation} (an n-ary production; an AND over its
 * ports) — both final with package-private constructors, so the hierarchy is closed by the package boundary
 * (Java 11; no sealed types). Both use instance identity for {@code equals}/{@code hashCode}.
 */
public interface GraphVertex {

    /** The scope this vertex lives in. No {@link Dep} edge ever connects vertices of different scopes. */
    Scope getScope();

    /** A deterministic identifier used for stable ordering and rendering, never for equality. */
    String id();
}

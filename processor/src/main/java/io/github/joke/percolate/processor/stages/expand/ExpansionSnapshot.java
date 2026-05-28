package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.Graph;
import org.jspecify.annotations.Nullable;

/**
 * Read-only view of expansion state, handed to {@link GroupExpander}s so they can inspect the graph without
 * being able to mutate it. Within an outer pass every expander sees the same snapshot; bundles applied at the
 * end of a pass are reflected only in the next pass's snapshot (batched-end-of-pass semantics). The group
 * views returned by {@link #viewOf} are {@code Graphs.unmodifiableGraph} wrappers: mutating them throws
 * {@code UnsupportedOperationException}.
 */
public interface ExpansionSnapshot {

    Stream<ExpansionGroup> groups();

    Graph<Node, Edge> viewOf(ExpansionGroup group);

    Optional<TypeMirror> typeOf(Node node);

    boolean isSat(ExpansionGroup group);

    /** The producer-stamped type if present, else the group's recorded expected type, else {@code null}. */
    @Nullable
    TypeMirror effectiveTypeFor(Node node, ExpansionGroup group);

    /** The {@link Element} scope that drove {@code node}'s typing, or a source-parameter fallback; may be {@code null}. */
    @Nullable
    Element producerScopeOf(Node node);

    @Nullable
    ExecutableElement currentMethod();
}

package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.spi.Directive;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * Adds a {@link Node} as a vertex of the underlying graph. When {@link #inheritedDirective} is non-null the node
 * was synthesized as the input of a {@code CONVERSION} step: the {@link Applier} stamps that {@code @Map}
 * {@link Directive} onto the node so a downstream strategy reads its config from local context (design D5).
 * Boundary slots carry a {@code null} directive and inherit nothing.
 */
@Value
public class AddNode implements Delta {
    Node node;

    @Nullable
    Directive inheritedDirective;

    public AddNode(final Node node) {
        this(node, null);
    }

    public AddNode(final Node node, final @Nullable Directive inheritedDirective) {
        this.node = node;
        this.inheritedDirective = inheritedDirective;
    }

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitAddNode(this);
    }
}

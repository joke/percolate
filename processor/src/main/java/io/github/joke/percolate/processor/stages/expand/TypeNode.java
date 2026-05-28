package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Node;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * Types a previously-untyped {@link Node} via {@code Node.setTyping}. The {@link #scope} is the producer
 * {@link Element} that drove the typing; the {@link Applier} re-invokes the nullability resolver with it and
 * records the {@code (node -> scope)} association so propagation sites can recover it later. A {@code null}
 * scope yields {@code Nullability.UNKNOWN}. Applied at most once per node — the applier skips already-typed
 * nodes, making sibling re-typing within a pass idempotent.
 */
@Value
public class TypeNode implements Delta {
    Node node;
    TypeMirror type;

    @Nullable
    Element scope;

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitTypeNode(this);
    }
}

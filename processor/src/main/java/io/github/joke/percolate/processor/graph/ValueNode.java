package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;
import java.util.Map;
import javax.lang.model.type.TypeMirror;

/**
 * Typed vertex in a per-method {@link org.jgrapht.graph.DefaultDirectedGraph ValueGraph}.
 *
 * <p>Subtypes are restricted to this package via a package-private constructor, approximating
 * Java 17 {@code sealed} semantics for Java 11. The four permitted subtypes are:
 * {@link SourceParamNode}, {@link PropertyNode}, {@link TypedValueNode}, {@link TargetSlotNode}.
 *
 * <p>Each node participates in code generation by implementing {@link #compose(Map, ComposeKind)}
 * — given the rendered {@link CodeBlock} contributed by each incoming edge, it returns the value
 * (or statement list) this vertex represents. This replaces the previous {@code instanceof} ladder
 * in {@code GenerateStage}.
 */
public abstract class ValueNode {

    /** Package-private — prevents subclassing from outside this package. */
    ValueNode() {}

    /** The type carried by this node; used by strategies to propose transform edges. */
    public abstract TypeMirror getType();

    /**
     * Assemble this node's value from the rendered inputs contributed by its incoming edges.
     *
     * @param inputs mapping from incoming {@link ValueEdge} to the already-rendered {@link
     *     CodeBlock} produced by applying that edge's template to its source vertex's value
     * @param kind expected shape of the result; all built-in nodes support {@link
     *     ComposeKind#EXPRESSION}
     * @return the {@link CodeBlock} representing this vertex
     * @throws IllegalStateException if the node cannot satisfy the given {@code kind} or the
     *     arity of {@code inputs} does not match the node's contract
     */
    public abstract CodeBlock compose(Map<ValueEdge, CodeBlock> inputs, ComposeKind kind);
}

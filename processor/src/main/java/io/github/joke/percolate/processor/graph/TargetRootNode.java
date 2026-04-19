package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Fifth permitted {@link ValueNode} subtype: the compositional root representing the constructed
 * target value of a mapping method.
 *
 * <p>One {@code TargetRootNode} is created per abstract method of a mapper and serves as the sink
 * of the method's {@code VertexPartition}. Its ordered list of {@link TargetSlotNode} slots is
 * populated by {@code ConstructorCallStrategy} when it satisfies a {@code ROOT_CONSTRUCTION}
 * demand.
 *
 * <p>{@link #compose(Map, ComposeKind) compose(EXPRESSION)} emits a {@code new T(slot0, slot1,
 * ...)} {@link CodeBlock} assembled from the cached values at each slot. Input iteration order
 * MUST match {@link #getSlots()} declaration order; {@code GenerateStage} inserts inputs in
 * slot-order when invoking compose.
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public final class TargetRootNode extends ValueNode {

    @EqualsAndHashCode.Include
    private final String typeString;

    @ToString.Include
    private final TypeMirror type;

    private final List<TargetSlotNode> slots = new ArrayList<>();

    public TargetRootNode(final TypeMirror type) {
        this.type = type;
        this.typeString = type.toString();
    }

    /**
     * Register the ordered slot list discovered by {@code ConstructorCallStrategy}. The order
     * SHALL match the constructor's declaration order so that {@link #compose(Map, ComposeKind)}
     * can pass slot values positionally.
     */
    public void setSlots(final List<TargetSlotNode> orderedSlots) {
        slots.clear();
        slots.addAll(orderedSlots);
    }

    @Override
    public CodeBlock compose(final Map<ValueEdge, CodeBlock> inputs, final ComposeKind kind) {
        if (kind != ComposeKind.EXPRESSION) {
            throw new IllegalStateException("TargetRootNode supports EXPRESSION only, got: " + kind);
        }
        if (slots.isEmpty()) {
            return CodeBlock.of("new $T()", type);
        }
        if (inputs.size() != slots.size()) {
            throw new IllegalStateException(
                    "TargetRootNode expects one incoming edge per slot ("
                            + slots.size()
                            + "), got: "
                            + inputs.size());
        }
        final var args = new ArrayList<>(inputs.values());
        return CodeBlock.of("new $T($L)", type, CodeBlock.join(args, ", "));
    }
}

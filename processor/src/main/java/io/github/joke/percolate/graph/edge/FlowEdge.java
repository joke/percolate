package io.github.joke.percolate.graph.edge;

import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * Directed edge in the data-flow graph. Represents "output of source node becomes input of target node".
 * slotName is non-null only for edges flowing into a TargetAssignmentNode,
 * identifying which constructor parameter / builder setter this value feeds.
 */
public final class FlowEdge {
    private final TypeMirror sourceType;
    private final TypeMirror targetType;
    private final @Nullable String slotName;

    public FlowEdge(TypeMirror sourceType, TypeMirror targetType, @Nullable String slotName) {
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.slotName = slotName;
    }

    /** Convenience factory for edges that carry no slot binding. */
    public static FlowEdge of(TypeMirror sourceType, TypeMirror targetType) {
        return new FlowEdge(sourceType, targetType, null);
    }

    /** Convenience factory for edges into TargetAssignmentNode. */
    public static FlowEdge forSlot(TypeMirror sourceType, TypeMirror targetType, String slotName) {
        return new FlowEdge(sourceType, targetType, slotName);
    }

    public TypeMirror getSourceType() {
        return sourceType;
    }

    public TypeMirror getTargetType() {
        return targetType;
    }

    public @Nullable String getSlotName() {
        return slotName;
    }

    @Override
    public String toString() {
        return slotName != null
                ? sourceType + " -[" + slotName + "]-> " + targetType
                : sourceType + " -> " + targetType;
    }
}

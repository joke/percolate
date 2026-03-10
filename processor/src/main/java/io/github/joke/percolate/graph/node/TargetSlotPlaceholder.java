package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Terminal node created by BindingStage. Replaced by WiringStage with a ConstructorAssignmentNode. */
public final class TargetSlotPlaceholder implements MappingNode {
    private final TypeMirror targetType;

    public TargetSlotPlaceholder(TypeMirror targetType) {
        this.targetType = targetType;
    }

    public TypeMirror getTargetType() {
        return targetType;
    }

    @Override
    public TypeMirror inType() {
        return targetType;
    }

    @Override
    public TypeMirror outType() {
        return targetType;
    }

    @Override
    public String toString() {
        return "Slot(" + targetType + ")";
    }
}

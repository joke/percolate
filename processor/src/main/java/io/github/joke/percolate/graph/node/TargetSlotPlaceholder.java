package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Terminal node created by BindingStage. Replaced by WiringStage with a TargetAssignmentNode. */
public final class TargetSlotPlaceholder implements MappingNode {
    private final TypeMirror targetType;
    private final String slotName;

    public TargetSlotPlaceholder(TypeMirror targetType, String slotName) {
        this.targetType = targetType;
        this.slotName = slotName;
    }

    public TypeMirror getTargetType() {
        return targetType;
    }

    public String getSlotName() {
        return slotName;
    }

    @Override
    public String toString() {
        return "Slot(" + slotName + " on " + targetType + ")";
    }
}

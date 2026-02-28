package io.github.joke.percolate.graph.node;

import javax.lang.model.element.TypeElement;

/** Represents how the target object is created. Strategy determined by WiringStage. */
public interface TargetAssignmentNode extends MappingNode {
    TypeElement getTargetType();
}

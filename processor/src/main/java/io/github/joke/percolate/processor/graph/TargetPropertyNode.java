package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.model.WriteAccessor;
import javax.lang.model.type.TypeMirror;

public final class TargetPropertyNode extends PropertyNode {

    private final WriteAccessor accessor;

    public TargetPropertyNode(final String name, final TypeMirror type, final WriteAccessor accessor) {
        super(name, type);
        this.accessor = accessor;
    }

    public WriteAccessor accessor() {
        return accessor;
    }
}

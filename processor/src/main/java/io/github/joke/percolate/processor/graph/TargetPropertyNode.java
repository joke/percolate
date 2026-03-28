package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.model.WriteAccessor;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;

@Getter
public final class TargetPropertyNode extends PropertyNode {

    private final WriteAccessor accessor;

    public TargetPropertyNode(final String name, final TypeMirror type, final WriteAccessor accessor) {
        super(name, type);
        this.accessor = accessor;
    }
}

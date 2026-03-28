package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.model.ReadAccessor;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;

@Getter
public final class SourcePropertyNode extends PropertyNode {

    private final ReadAccessor accessor;

    public SourcePropertyNode(final String name, final TypeMirror type, final ReadAccessor accessor) {
        super(name, type);
        this.accessor = accessor;
    }
}

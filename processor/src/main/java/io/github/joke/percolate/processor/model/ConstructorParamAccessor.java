package io.github.joke.percolate.processor.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;

@Getter
public final class ConstructorParamAccessor extends WriteAccessor {

    private final ExecutableElement constructor;
    private final int paramIndex;

    public ConstructorParamAccessor(
            final String name, final TypeMirror type, final ExecutableElement constructor, final int paramIndex) {
        super(name, type);
        this.constructor = constructor;
        this.paramIndex = paramIndex;
    }
}

package io.github.joke.percolate.processor.model;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;

@Getter
public final class FieldWriteAccessor extends WriteAccessor {

    private final VariableElement field;

    public FieldWriteAccessor(final String name, final TypeMirror type, final VariableElement field) {
        super(name, type);
        this.field = field;
    }
}

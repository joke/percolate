package io.github.joke.percolate.processor.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public final class GetterAccessor extends ReadAccessor {

    private final ExecutableElement method;

    public GetterAccessor(final String name, final TypeMirror type, final ExecutableElement method) {
        super(name, type);
        this.method = method;
    }

    public ExecutableElement method() {
        return method;
    }
}

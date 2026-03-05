package io.github.joke.percolate.model;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public final class Property {

    private final String name;
    private final TypeMirror type;
    private final Element accessor;

    public Property(final String name, final TypeMirror type, final Element accessor) {
        this.name = name;
        this.type = type;
        this.accessor = accessor;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getType() {
        return type;
    }

    public Element getAccessor() {
        return accessor;
    }
}

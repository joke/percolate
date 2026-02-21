package io.github.joke.caffeinate.analysis.property;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public final class Property {
    private final String name;
    private final TypeMirror type;
    private final Element accessor;

    public Property(String name, TypeMirror type, Element accessor) {
        this.name = name;
        this.type = type;
        this.accessor = accessor;
    }

    public String getName() { return name; }
    public TypeMirror getType() { return type; }
    public Element getAccessor() { return accessor; }
}

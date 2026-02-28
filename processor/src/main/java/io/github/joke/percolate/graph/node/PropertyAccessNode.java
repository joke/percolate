package io.github.joke.percolate.graph.node;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public final class PropertyAccessNode implements MappingNode {
    private final String propertyName;
    private final TypeMirror inType;
    private final TypeMirror outType;
    private final Element accessor;

    public PropertyAccessNode(String propertyName, TypeMirror inType, TypeMirror outType, Element accessor) {
        this.propertyName = propertyName;
        this.inType = inType;
        this.outType = outType;
        this.accessor = accessor;
    }

    public String getPropertyName() { return propertyName; }
    public TypeMirror getInType() { return inType; }
    public TypeMirror getOutType() { return outType; }
    public Element getAccessor() { return accessor; }

    @Override
    public String toString() { return "Property(" + propertyName + ":" + inType + "->" + outType + ")"; }
}

package io.github.joke.percolate.model;

import java.util.List;
import javax.lang.model.element.TypeElement;

public final class MapperDefinition {

    private final TypeElement element;
    private final String packageName;
    private final String simpleName;
    private final List<MethodDefinition> methods;

    public MapperDefinition(
            final TypeElement element,
            final String packageName,
            final String simpleName,
            final List<MethodDefinition> methods) {
        this.element = element;
        this.packageName = packageName;
        this.simpleName = simpleName;
        this.methods = List.copyOf(methods);
    }

    public TypeElement getElement() {
        return element;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public List<MethodDefinition> getMethods() {
        return methods;
    }

    public MapperDefinition withMethods(final List<MethodDefinition> methods) {
        return new MapperDefinition(element, packageName, simpleName, methods);
    }
}

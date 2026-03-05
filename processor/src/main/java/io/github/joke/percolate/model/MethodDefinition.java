package io.github.joke.percolate.model;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public final class MethodDefinition {

    private final ExecutableElement element;
    private final String name;
    private final TypeMirror returnType;
    private final List<ParameterDefinition> parameters;
    private final boolean isAbstract;
    private final List<MapDirective> directives;

    public MethodDefinition(
            final ExecutableElement element,
            final String name,
            final TypeMirror returnType,
            final List<ParameterDefinition> parameters,
            final boolean isAbstract,
            final List<MapDirective> directives) {
        this.element = element;
        this.name = name;
        this.returnType = returnType;
        this.parameters = List.copyOf(parameters);
        this.isAbstract = isAbstract;
        this.directives = List.copyOf(directives);
    }

    public ExecutableElement getElement() {
        return element;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getReturnType() {
        return returnType;
    }

    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public List<MapDirective> getDirectives() {
        return directives;
    }

    public MethodDefinition withDirectives(final List<MapDirective> directives) {
        return new MethodDefinition(element, name, returnType, parameters, isAbstract, directives);
    }
}

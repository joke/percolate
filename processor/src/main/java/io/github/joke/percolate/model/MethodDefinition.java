package io.github.joke.percolate.model;

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
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
            ExecutableElement element,
            String name,
            TypeMirror returnType,
            List<ParameterDefinition> parameters,
            boolean isAbstract,
            List<MapDirective> directives) {
        this.element = element;
        this.name = name;
        this.returnType = returnType;
        this.parameters = unmodifiableList(new ArrayList<>(parameters));
        this.isAbstract = isAbstract;
        this.directives = unmodifiableList(new ArrayList<>(directives));
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

    public MethodDefinition withDirectives(List<MapDirective> directives) {
        return new MethodDefinition(element, name, returnType, parameters, isAbstract, directives);
    }
}

package io.github.joke.caffeinate.analysis;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public final class MappingMethod {
    private final ExecutableElement method;
    private final TypeElement targetType;
    private final List<? extends VariableElement> parameters;
    private final List<MapAnnotation> mapAnnotations;
    private final List<ExecutableElement> converterCandidates;

    public MappingMethod(
            ExecutableElement method,
            TypeElement targetType,
            List<? extends VariableElement> parameters,
            List<MapAnnotation> mapAnnotations,
            List<ExecutableElement> converterCandidates) {
        this.method = method;
        this.targetType = targetType;
        this.parameters = parameters; // safe — javax.lang.model returns unmodifiable list
        this.mapAnnotations = List.copyOf(mapAnnotations);
        this.converterCandidates = List.copyOf(converterCandidates);
    }

    public ExecutableElement getMethod() {
        return method;
    }

    public TypeElement getTargetType() {
        return targetType;
    }

    public List<? extends VariableElement> getParameters() {
        return parameters;
    }

    public List<MapAnnotation> getMapAnnotations() {
        return mapAnnotations;
    }

    public List<ExecutableElement> getConverterCandidates() {
        return converterCandidates;
    }
}

package io.github.joke.caffeinate.resolution;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public final class ResolvedMappingMethod {
    private final ExecutableElement method;
    private final TypeElement targetType;
    private final List<? extends VariableElement> parameters;
    /** True when the method has >1 parameter — auto name-matching is disabled. */
    private final boolean requiresExplicitMappings;

    private final List<ResolvedMapAnnotation> resolvedMappings;

    public ResolvedMappingMethod(
            ExecutableElement method,
            TypeElement targetType,
            List<? extends VariableElement> parameters,
            boolean requiresExplicitMappings,
            List<ResolvedMapAnnotation> resolvedMappings) {
        this.method = method;
        this.targetType = targetType;
        this.parameters = parameters;
        this.requiresExplicitMappings = requiresExplicitMappings;
        this.resolvedMappings = List.copyOf(resolvedMappings);
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

    public boolean isRequiresExplicitMappings() {
        return requiresExplicitMappings;
    }

    public List<ResolvedMapAnnotation> getResolvedMappings() {
        return resolvedMappings;
    }
}

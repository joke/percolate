package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

public class ValidationStage {

    private final ProcessingEnvironment env;
    private final Set<PropertyDiscoveryStrategy> strategies;

    @Inject
    public ValidationStage(ProcessingEnvironment env, Set<PropertyDiscoveryStrategy> strategies) {
        this.env = env;
        this.strategies = strategies;
    }

    public ValidationResult validate(AnalysisResult analysis) {
        boolean hasFatalErrors = false;
        for (MapperDescriptor descriptor : analysis.getMappers()) {
            for (MappingMethod method : descriptor.getMethods()) {
                if (!validateMethod(method, descriptor)) {
                    hasFatalErrors = true;
                }
            }
        }
        return new ValidationResult(analysis, hasFatalErrors);
    }

    private boolean validateMethod(MappingMethod method, MapperDescriptor descriptor) {
        List<Property> targetProperties = PropertyMerger.merge(strategies, method.getTargetType(), env);

        List<Property> uncovered = new ArrayList<>();
        for (Property targetProp : targetProperties) {
            if (!isCovered(targetProp, method)) {
                uncovered.add(targetProp);
            }
        }

        if (uncovered.isEmpty()) return true;

        String graph = PartialGraphRenderer.render(method, strategies, env);
        Property firstUncovered = uncovered.get(0);

        env.getMessager()
                .printMessage(
                        Diagnostic.Kind.ERROR,
                        String.format(
                                "[Percolate] %s.%s: validation failed.\n%s\nConsider adding: %s map%s(%s source)",
                                descriptor.getMapperInterface().getSimpleName(),
                                method.getMethod().getSimpleName(),
                                graph,
                                firstUncovered.getType(),
                                capitalize(firstUncovered.getName()),
                                firstUncovered.getType()),
                        method.getMethod());

        return false;
    }

    private boolean isCovered(Property targetProp, MappingMethod method) {
        // NOTE: This coverage logic must stay in sync with PartialGraphRenderer.isCovered().
        // If you add a new coverage rule here, add it there too.
        // 1. Explicit @Map annotation
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) return true;
        }
        // 2. Name-match from any source parameter
        for (VariableElement param : method.getParameters()) {
            Element element = env.getTypeUtils().asElement(param.asType());
            if (!(element instanceof TypeElement)) continue;
            TypeElement paramType = (TypeElement) element;
            List<Property> sourceProps = PropertyMerger.merge(strategies, paramType, env);
            for (Property srcProp : sourceProps) {
                if (srcProp.getName().equals(targetProp.getName())) return true;
            }
        }
        // 3. A converter method whose return type matches the target property type
        for (ExecutableElement converter : method.getConverterCandidates()) {
            if (env.getTypeUtils().isSameType(converter.getReturnType(), targetProp.getType())) return true;
        }
        return false;
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

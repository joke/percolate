package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.Set;

/**
 * Renders a tree showing which target properties are resolved and which are not.
 * Traverses from the target type root.
 */
public final class PartialGraphRenderer {

    private PartialGraphRenderer() {}

    public static String render(MappingMethod method, Set<PropertyDiscoveryStrategy> strategies,
                                ProcessingEnvironment env) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nPartial resolution graph (from target ")
                .append(method.getTargetType().getSimpleName()).append("):\n");
        sb.append("  ").append(method.getTargetType().getSimpleName()).append("\n");

        List<Property> targetProps = PropertyMerger.merge(strategies, method.getTargetType(), env);
        for (int i = 0; i < targetProps.size(); i++) {
            Property targetProp = targetProps.get(i);
            boolean isLast = i == targetProps.size() - 1;
            boolean resolved = isCovered(targetProp, method, strategies, env);
            String branch = isLast ? "  \u2514\u2500\u2500 " : "  \u251C\u2500\u2500 ";
            String mark = resolved ? "\u2713" : "\u2717";
            sb.append(branch).append(targetProp.getName())
                    .append("  ").append(mark);
            if (resolved) {
                sb.append("  \u2190 ").append(resolvedDescription(targetProp, method, strategies, env));
            } else {
                sb.append("  \u2190 unresolved (").append(targetProp.getType()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static boolean isCovered(Property targetProp, MappingMethod method,
                                     Set<PropertyDiscoveryStrategy> strategies,
                                     ProcessingEnvironment env) {
        // 1. Explicit @Map annotation
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) return true;
        }
        // 2. Name-match from any source parameter
        for (VariableElement param : method.getParameters()) {
            Element element = env.getTypeUtils().asElement(param.asType());
            if (!(element instanceof TypeElement)) continue;
            TypeElement paramType = (TypeElement) element;
            for (Property srcProp : PropertyMerger.merge(strategies, paramType, env)) {
                if (srcProp.getName().equals(targetProp.getName())) return true;
            }
        }
        // 3. Converter method whose return type matches
        for (ExecutableElement converter : method.getConverterCandidates()) {
            if (env.getTypeUtils().isSameType(converter.getReturnType(), targetProp.getType())) return true;
        }
        return false;
    }

    private static String resolvedDescription(Property targetProp, MappingMethod method,
                                              Set<PropertyDiscoveryStrategy> strategies,
                                              ProcessingEnvironment env) {
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) return ann.getSource();
        }
        for (VariableElement param : method.getParameters()) {
            Element element = env.getTypeUtils().asElement(param.asType());
            if (!(element instanceof TypeElement)) continue;
            TypeElement paramType = (TypeElement) element;
            for (Property srcProp : PropertyMerger.merge(strategies, paramType, env)) {
                if (srcProp.getName().equals(targetProp.getName())) {
                    return param.getSimpleName() + "." + srcProp.getName();
                }
            }
        }
        for (ExecutableElement converter : method.getConverterCandidates()) {
            if (env.getTypeUtils().isSameType(converter.getReturnType(), targetProp.getType())) {
                return "this." + converter.getSimpleName() + "(...)";
            }
        }
        return "?";
    }
}

package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * Renders a tree showing which target properties are resolved and which are not.
 * Traverses from the target type root.
 */
public final class PartialGraphRenderer {

    private PartialGraphRenderer() {}

    public static String render(
            MappingMethod method, Set<PropertyDiscoveryStrategy> strategies, ProcessingEnvironment env) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nPartial resolution graph (from target ")
                .append(method.getTargetType().getSimpleName())
                .append("):\n");
        sb.append("  ").append(method.getTargetType().getSimpleName()).append("\n");

        List<Property> targetProps = PropertyMerger.merge(strategies, method.getTargetType(), env);
        for (int i = 0; i < targetProps.size(); i++) {
            Property targetProp = targetProps.get(i);
            boolean isLast = i == targetProps.size() - 1;
            boolean resolved = isCovered(targetProp, method, strategies, env);
            String branch = isLast ? "  \u2514\u2500\u2500 " : "  \u251C\u2500\u2500 ";
            String mark = resolved ? "\u2713" : "\u2717";
            sb.append(branch).append(targetProp.getName()).append("  ").append(mark);
            if (resolved) {
                sb.append("  \u2190 ").append(resolvedDescription(targetProp, method, strategies, env));
            } else {
                sb.append("  \u2190 unresolved (").append(targetProp.getType()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static boolean isCovered(
            Property targetProp,
            MappingMethod method,
            Set<PropertyDiscoveryStrategy> strategies,
            ProcessingEnvironment env) {
        // NOTE: This coverage logic must stay in sync with ValidationStage.isCovered().
        // If you add a new coverage rule here, add it there too.
        // 1. Explicit @Map annotation — verify source resolves
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (!ann.getTarget().equals(targetProp.getName())) continue;
            String source = ann.getSource();
            if (source.isEmpty()) continue; // empty source doesn't resolve
            if (sourcePathResolves(source, method, strategies, env)) return true;
            // source doesn't resolve → not actually covered, fall through
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

    /** Returns true if the source path can be resolved to an actual property. */
    private static boolean sourcePathResolves(
            String sourcePath,
            MappingMethod method,
            Set<PropertyDiscoveryStrategy> strategies,
            ProcessingEnvironment env) {
        int dot = sourcePath.indexOf('.');
        if (dot > 0 && dot < sourcePath.length() - 1 && sourcePath.indexOf('.', dot + 1) < 0) {
            // "param.propName" form
            String paramName = sourcePath.substring(0, dot);
            String propName = sourcePath.substring(dot + 1);
            for (VariableElement param : method.getParameters()) {
                if (!param.getSimpleName().toString().equals(paramName)) continue;
                Element element = env.getTypeUtils().asElement(param.asType());
                if (!(element instanceof TypeElement)) continue;
                for (Property srcProp : PropertyMerger.merge(strategies, (TypeElement) element, env)) {
                    if (srcProp.getName().equals(propName)) return true;
                }
            }
            return false;
        } else if (dot < 0) {
            // bare "propName" form — search all params
            for (VariableElement param : method.getParameters()) {
                Element element = env.getTypeUtils().asElement(param.asType());
                if (!(element instanceof TypeElement)) continue;
                for (Property srcProp : PropertyMerger.merge(strategies, (TypeElement) element, env)) {
                    if (srcProp.getName().equals(sourcePath)) return true;
                }
            }
            return false;
        }
        // multi-level path or unsupported form — not resolvable
        return false;
    }

    private static String resolvedDescription(
            Property targetProp,
            MappingMethod method,
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
        throw new IllegalStateException(
                "resolvedDescription() called but no resolution found — isCovered() parity bug");
    }
}

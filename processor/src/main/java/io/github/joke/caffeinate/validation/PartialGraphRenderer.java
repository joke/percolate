package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.resolution.ResolvedMapAnnotation;
import io.github.joke.caffeinate.resolution.ResolvedMappingMethod;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.jspecify.annotations.Nullable;

public final class PartialGraphRenderer {

    private PartialGraphRenderer() {}

    public static String render(
            ResolvedMappingMethod method, Set<PropertyDiscoveryStrategy> strategies, ProcessingEnvironment env) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nPartial resolution graph (from target ")
                .append(method.getTargetType().getSimpleName())
                .append("):\n");
        sb.append("  ").append(method.getTargetType().getSimpleName()).append("\n");

        List<Property> targetProps = PropertyMerger.merge(strategies, method.getTargetType(), env);
        int last = targetProps.size() - 1;
        for (int i = 0; i <= last; i++) {
            Property tgtProp = targetProps.get(i);
            ResolvedMapAnnotation rma = findMapping(tgtProp, method);
            String branch = (i == last) ? "\u2514\u2500\u2500 " : "\u251c\u2500\u2500 ";
            String mark = rma != null ? "\u2713" : "\u2717";
            sb.append("  ")
                    .append(branch)
                    .append(tgtProp.getName())
                    .append("  ")
                    .append(mark);
            if (rma != null) {
                sb.append("  \u2190 ").append(rma.getSourceExpression());
            } else {
                sb.append("  \u2190 unresolved (").append(tgtProp.getType()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Nullable
    private static ResolvedMapAnnotation findMapping(Property tgtProp, ResolvedMappingMethod method) {
        for (ResolvedMapAnnotation rma : method.getResolvedMappings()) {
            if (rma.getTargetProperty().getName().equals(tgtProp.getName())) return rma;
        }
        return null;
    }

    /**
     * Legacy render method for backward compatibility during pipeline transition. Uses MappingMethod
     * from analysis stage.
     *
     * @deprecated Use {@link #render(ResolvedMappingMethod, Set, ProcessingEnvironment)} after
     *     ResolutionStage is wired.
     */
    @Deprecated
    public static String renderLegacy(
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
            boolean resolved = isCoveredLegacy(targetProp, method, strategies, env);
            String branch = isLast ? "  \u2514\u2500\u2500 " : "  \u251c\u2500\u2500 ";
            String mark = resolved ? "\u2713" : "\u2717";
            sb.append(branch).append(targetProp.getName()).append("  ").append(mark);
            if (resolved) {
                sb.append("  \u2190 ").append(resolvedDescriptionLegacy(targetProp, method, strategies, env));
            } else {
                sb.append("  \u2190 unresolved (").append(targetProp.getType()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static boolean isCoveredLegacy(
            Property targetProp,
            MappingMethod method,
            Set<PropertyDiscoveryStrategy> strategies,
            ProcessingEnvironment env) {
        // 1. Explicit @Map annotation — verify source resolves
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (!ann.getTarget().equals(targetProp.getName())) continue;
            String source = ann.getSource();
            if (source.isEmpty()) continue;
            if (sourcePathResolvesLegacy(source, method, strategies, env)) return true;
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

    private static boolean sourcePathResolvesLegacy(
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

    private static String resolvedDescriptionLegacy(
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
                "resolvedDescriptionLegacy() called but no resolution found — isCoveredLegacy() parity bug");
    }
}

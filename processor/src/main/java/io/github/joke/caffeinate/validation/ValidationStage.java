package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.ResolutionResult;
import io.github.joke.caffeinate.resolution.ResolvedMapAnnotation;
import io.github.joke.caffeinate.resolution.ResolvedMapperDescriptor;
import io.github.joke.caffeinate.resolution.ResolvedMappingMethod;
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
import org.jspecify.annotations.Nullable;

public class ValidationStage {

    private final ProcessingEnvironment env;
    private final Set<PropertyDiscoveryStrategy> propertyStrategies;

    @Inject
    public ValidationStage(ProcessingEnvironment env, Set<PropertyDiscoveryStrategy> propertyStrategies) {
        this.env = env;
        this.propertyStrategies = propertyStrategies;
    }

    public ValidationResult validate(ResolutionResult resolution) {
        boolean hasFatalErrors = false;
        ConverterRegistry registry = resolution.getConverterRegistry();

        for (ResolvedMapperDescriptor descriptor : resolution.getMappers()) {
            for (ResolvedMappingMethod method : descriptor.getMethods()) {
                if (!validateMethod(method, descriptor, registry)) {
                    hasFatalErrors = true;
                }
            }
        }
        return new ValidationResult(resolution, hasFatalErrors);
    }

    /**
     * Deprecated overload for backward compatibility during pipeline transition. Will be removed in Task 7.
     *
     * @deprecated Use {@link #validate(ResolutionResult)} after ResolutionStage is wired into Pipeline.
     */
    @Deprecated
    public ValidationResult validate(AnalysisResult analysis) {
        boolean hasFatalErrors = false;
        for (MapperDescriptor descriptor : analysis.getMappers()) {
            for (MappingMethod method : descriptor.getMethods()) {
                if (!validateMethodLegacy(method, descriptor)) {
                    hasFatalErrors = true;
                }
            }
        }
        // Create a stub ResolutionResult for downstream compatibility
        // TODO(Task 7): Remove this legacy path when Pipeline is rewired with ResolutionStage
        return new ValidationResult(createStubResolutionResult(), analysis.getMappers(), hasFatalErrors);
    }

    /**
     * Legacy validation logic that mirrors the old ValidationStage behavior. Used only during pipeline
     * transition.
     */
    private boolean validateMethodLegacy(MappingMethod method, MapperDescriptor descriptor) {
        List<Property> targetProperties = PropertyMerger.merge(propertyStrategies, method.getTargetType(), env);

        List<Property> uncovered = new ArrayList<>();
        for (Property targetProp : targetProperties) {
            if (!isCoveredLegacy(targetProp, method)) {
                uncovered.add(targetProp);
            }
        }

        if (uncovered.isEmpty()) return true;

        String graph = PartialGraphRenderer.renderLegacy(method, propertyStrategies, env);
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

    /**
     * Legacy coverage check. Mirrors old isCovered() logic for backward compatibility.
     */
    private boolean isCoveredLegacy(Property targetProp, MappingMethod method) {
        // 1. Explicit @Map annotation — verify source resolves
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (!ann.getTarget().equals(targetProp.getName())) continue;
            String source = ann.getSource();
            if (source.isEmpty()) continue;
            if (sourcePathResolves(source, method)) return true;
        }
        // 2. Name-match from any source parameter
        for (VariableElement param : method.getParameters()) {
            Element element = env.getTypeUtils().asElement(param.asType());
            if (!(element instanceof TypeElement)) continue;
            TypeElement paramType = (TypeElement) element;
            List<Property> sourceProps = PropertyMerger.merge(propertyStrategies, paramType, env);
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

    /** Returns true if the source path can be resolved to an actual property. */
    private boolean sourcePathResolves(String sourcePath, MappingMethod method) {
        int dot = sourcePath.indexOf('.');
        if (dot > 0 && dot < sourcePath.length() - 1 && sourcePath.indexOf('.', dot + 1) < 0) {
            // "param.propName" form
            String paramName = sourcePath.substring(0, dot);
            String propName = sourcePath.substring(dot + 1);
            for (VariableElement param : method.getParameters()) {
                if (!param.getSimpleName().toString().equals(paramName)) continue;
                Element element = env.getTypeUtils().asElement(param.asType());
                if (!(element instanceof TypeElement)) continue;
                for (Property srcProp : PropertyMerger.merge(propertyStrategies, (TypeElement) element, env)) {
                    if (srcProp.getName().equals(propName)) return true;
                }
            }
            return false;
        } else if (dot < 0) {
            // bare "propName" form — search all params
            for (VariableElement param : method.getParameters()) {
                Element element = env.getTypeUtils().asElement(param.asType());
                if (!(element instanceof TypeElement)) continue;
                for (Property srcProp : PropertyMerger.merge(propertyStrategies, (TypeElement) element, env)) {
                    if (srcProp.getName().equals(sourcePath)) return true;
                }
            }
            return false;
        }
        // multi-level path or unsupported form — not resolvable
        return false;
    }

    /**
     * Creates a stub ResolutionResult for backward compatibility during pipeline transition. This
     * creates a minimal ResolutionResult with no resolved mappers. The actual mappers are preserved
     * in ValidationResult's legacyMappers field for downstream code generation. This is a temporary
     * bridge and should be removed when Task 7 wires ResolutionStage.
     */
    private ResolutionResult createStubResolutionResult() {
        // TODO(Task 7): Remove this stub after proper ResolutionStage integration
        // Create empty list of resolved mappers - the legacy mappers are preserved separately
        return new ResolutionResult(List.of(), new ConverterRegistry());
    }

    private boolean validateMethod(
            ResolvedMappingMethod method, ResolvedMapperDescriptor descriptor, ConverterRegistry registry) {

        List<Property> targetProperties = PropertyMerger.merge(propertyStrategies, method.getTargetType(), env);
        List<Property> uncovered = new ArrayList<>();

        for (Property tgtProp : targetProperties) {
            ResolvedMapAnnotation rma = findMapping(tgtProp, method);
            if (rma == null) {
                uncovered.add(tgtProp);
                continue;
            }
            // Check type compatibility: same type or a converter exists.
            if (!env.getTypeUtils().isSameType(rma.getSourceType(), tgtProp.getType())
                    && !registry.hasConverter(rma.getSourceType(), tgtProp.getType())) {
                env.getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                String.format(
                                        "[Percolate] %s.%s: cannot convert '%s' to '%s'"
                                                + " for target property '%s'"
                                                + " — no converter or strategy found.\n"
                                                + "  Consider adding: %s map%s(%s source)",
                                        descriptor.getMapperInterface().getSimpleName(),
                                        method.getMethod().getSimpleName(),
                                        rma.getSourceType(),
                                        tgtProp.getType(),
                                        tgtProp.getName(),
                                        tgtProp.getType(),
                                        capitalize(tgtProp.getName()),
                                        rma.getSourceType()),
                                method.getMethod());
                return false;
            }
        }

        if (uncovered.isEmpty()) return true;

        String graph = PartialGraphRenderer.render(method, propertyStrategies, env);
        Property first = uncovered.get(0);
        env.getMessager()
                .printMessage(
                        Diagnostic.Kind.ERROR,
                        String.format(
                                "[Percolate] %s.%s: validation failed.\n%s\nConsider adding:" + " %s map%s(%s source)",
                                descriptor.getMapperInterface().getSimpleName(),
                                method.getMethod().getSimpleName(),
                                graph,
                                first.getType(),
                                capitalize(first.getName()),
                                first.getType()),
                        method.getMethod());
        return false;
    }

    @Nullable
    private ResolvedMapAnnotation findMapping(Property tgtProp, ResolvedMappingMethod method) {
        for (ResolvedMapAnnotation rma : method.getResolvedMappings()) {
            if (rma.getTargetProperty().getName().equals(tgtProp.getName())) return rma;
        }
        return null;
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

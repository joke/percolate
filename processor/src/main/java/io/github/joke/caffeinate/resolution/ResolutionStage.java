package io.github.joke.caffeinate.resolution;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.jspecify.annotations.Nullable;

public class ResolutionStage {

    private final ProcessingEnvironment env;
    private final Set<PropertyDiscoveryStrategy> propertyStrategies;
    private final Set<TypeMappingStrategy> typeMappingStrategies;

    @Inject
    public ResolutionStage(
            ProcessingEnvironment env,
            Set<PropertyDiscoveryStrategy> propertyStrategies,
            Set<TypeMappingStrategy> typeMappingStrategies) {
        this.env = env;
        this.propertyStrategies = propertyStrategies;
        this.typeMappingStrategies = typeMappingStrategies;
    }

    public ResolutionResult resolve(AnalysisResult analysis) {
        ConverterRegistry registry = new ConverterRegistry();

        // Pass 1: register all single-param mapper methods as explicit MethodConverters.
        for (MapperDescriptor descriptor : analysis.getMappers()) {
            for (MappingMethod method : descriptor.getMethods()) {
                if (method.getParameters().size() == 1) {
                    TypeMirror paramType = method.getParameters().get(0).asType();
                    TypeMirror returnType = method.getMethod().getReturnType();
                    registry.register(paramType, returnType, new MethodConverter(method.getMethod()));
                }
            }
        }

        // Resolve all mapping methods.
        List<ResolvedMapperDescriptor> resolved = new ArrayList<>();
        List<TypePairForFixpoint> pairsToCheck = new ArrayList<>();
        boolean hasErrors = false;

        for (MapperDescriptor descriptor : analysis.getMappers()) {
            List<ResolvedMappingMethod> resolvedMethods = new ArrayList<>();
            for (MappingMethod method : descriptor.getMethods()) {
                if (!isAbstract(method)) continue;
                ResolvedMappingMethod rmm = resolveMethod(method, descriptor, pairsToCheck);
                if (rmm == null) {
                    hasErrors = true;
                } else {
                    resolvedMethods.add(rmm);
                }
            }
            resolved.add(new ResolvedMapperDescriptor(descriptor.getMapperInterface(), resolvedMethods));
        }

        // Pass 2: strategy fixpoint — add virtual edges for unresolved type pairs.
        if (!hasErrors) {
            runStrategyFixpoint(pairsToCheck, registry);
        }

        return new ResolutionResult(resolved, registry);
    }

    private boolean isAbstract(MappingMethod method) {
        return method.getMethod().getModifiers().contains(Modifier.ABSTRACT);
    }

    /**
     * Resolves all @Map annotations for a single mapping method. Returns null if any path
     * validation fails (errors emitted via Messager).
     */
    @Nullable
    private ResolvedMappingMethod resolveMethod(
            MappingMethod method, MapperDescriptor descriptor, List<TypePairForFixpoint> pairsToCheck) {

        boolean multiParam = method.getParameters().size() > 1;
        List<ResolvedMapAnnotation> resolvedMappings = new ArrayList<>();
        boolean hasErrors = false;

        TypeElement targetType = method.getTargetType();
        List<Property> targetProperties = PropertyMerger.merge(propertyStrategies, targetType, env);

        for (MapAnnotation ann : method.getMapAnnotations()) {
            String sourceStr = ann.getSource();
            String targetStr = ann.getTarget();

            if (isWildcard(sourceStr)) {
                // Expand param.* into one ResolvedMapAnnotation per first-level property.
                String paramName = sourceStr.substring(0, sourceStr.length() - 2); // strip ".*"
                VariableElement param = findParam(paramName, method);
                if (param == null) {
                    env.getMessager()
                            .printMessage(
                                    Diagnostic.Kind.ERROR,
                                    String.format(
                                            "[Percolate] %s.%s: wildcard source '%s' — no parameter named '%s'",
                                            descriptor.getMapperInterface().getSimpleName(),
                                            method.getMethod().getSimpleName(),
                                            sourceStr,
                                            paramName),
                                    method.getMethod());
                    hasErrors = true;
                    continue;
                }
                TypeElement paramType = asTypeElement(param.asType());
                if (paramType == null) continue;
                List<Property> sourceProps = PropertyMerger.merge(propertyStrategies, paramType, env);

                for (Property srcProp : sourceProps) {
                    // Find the matching target property by name.
                    Property tgtProp = findTargetProperty(srcProp.getName(), targetProperties);
                    if (tgtProp == null) continue; // no match on target — skip silently
                    String srcExpr = accessorExpr(paramName, srcProp);
                    resolvedMappings.add(new ResolvedMapAnnotation(tgtProp, srcExpr, srcProp.getType()));
                    if (!env.getTypeUtils().isSameType(srcProp.getType(), tgtProp.getType())) {
                        pairsToCheck.add(new TypePairForFixpoint(srcProp.getType(), tgtProp.getType()));
                    }
                }
                continue;
            }

            // Validate target path.
            Property tgtProp = resolveTargetPath(targetStr, targetType, descriptor, method);
            if (tgtProp == null) {
                hasErrors = true;
                continue;
            }

            // Validate and resolve source path.
            SourceResolution src = resolveSourcePath(sourceStr, method, descriptor);
            if (src == null) {
                hasErrors = true;
                continue;
            }

            resolvedMappings.add(new ResolvedMapAnnotation(tgtProp, src.expression, src.type));
            if (!env.getTypeUtils().isSameType(src.type, tgtProp.getType())) {
                pairsToCheck.add(new TypePairForFixpoint(src.type, tgtProp.getType()));
            }
        }

        // For single-param methods, add name-matched properties not already covered.
        if (!multiParam && method.getParameters().size() == 1) {
            VariableElement param = method.getParameters().get(0);
            TypeElement paramType = asTypeElement(param.asType());
            if (paramType != null) {
                List<Property> sourceProps = PropertyMerger.merge(propertyStrategies, paramType, env);
                for (Property tgtProp : targetProperties) {
                    if (isAlreadyCovered(tgtProp, resolvedMappings)) continue;
                    for (Property srcProp : sourceProps) {
                        if (!srcProp.getName().equals(tgtProp.getName())) continue;
                        String srcExpr = accessorExpr(param.getSimpleName().toString(), srcProp);
                        resolvedMappings.add(new ResolvedMapAnnotation(tgtProp, srcExpr, srcProp.getType()));
                        if (!env.getTypeUtils().isSameType(srcProp.getType(), tgtProp.getType())) {
                            pairsToCheck.add(new TypePairForFixpoint(srcProp.getType(), tgtProp.getType()));
                        }
                        break;
                    }
                }
            }
        }

        if (hasErrors) return null;

        return new ResolvedMappingMethod(
                method.getMethod(), targetType, method.getParameters(), multiParam, resolvedMappings);
    }

    /**
     * Validates a target path (e.g. "zip" or "address.city") against the target type. Returns the
     * terminal Property if valid, null and emits an error if not.
     */
    @Nullable
    private Property resolveTargetPath(
            String targetPath, TypeElement targetType, MapperDescriptor descriptor, MappingMethod method) {
        if (targetPath.equals(".")) return null; // handled by wildcard caller — should not reach here
        @SuppressWarnings("StringSplitter")
        String[] segArray = targetPath.split("\\.");
        TypeElement currentType = targetType;
        Property result = null;
        for (String segment : segArray) {
            List<Property> props = PropertyMerger.merge(propertyStrategies, currentType, env);
            Property found = findTargetProperty(segment, props);
            if (found == null) {
                env.getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                String.format(
                                        "[Percolate] %s.%s: @Map target path '%s' — no property '%s' on %s",
                                        descriptor.getMapperInterface().getSimpleName(),
                                        method.getMethod().getSimpleName(),
                                        targetPath,
                                        segment,
                                        currentType.getSimpleName()),
                                method.getMethod());
                return null;
            }
            result = found;
            TypeElement next = asTypeElement(found.getType());
            if (next == null) break; // terminal type (primitive, String, etc.)
            currentType = next;
        }
        return result;
    }

    /**
     * Validates and resolves a source path (e.g. "ticket.ticketId" or bare "zipCode"). Returns a
     * SourceResolution with the Java expression and resolved type, or null on error.
     *
     * <p>Rules:
     * - Multi-param method: first segment must be a parameter name.
     * - Single-param method: bare property (no dot) is relative to the single parameter.
     */
    @Nullable
    private SourceResolution resolveSourcePath(String sourcePath, MappingMethod method, MapperDescriptor descriptor) {

        boolean multiParam = method.getParameters().size() > 1;
        @SuppressWarnings("StringSplitter")
        String[] segArray = sourcePath.split("\\.");

        VariableElement startParam;
        int propStart;

        if (segArray.length == 1 && !multiParam) {
            // Bare property on single param.
            startParam = method.getParameters().get(0);
            propStart = 0;
        } else if (segArray.length >= 2) {
            String paramName = segArray[0];
            startParam = findParam(paramName, method);
            if (startParam == null) {
                env.getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                String.format(
                                        "[Percolate] %s.%s: source path '%s' — no parameter named '%s'",
                                        descriptor.getMapperInterface().getSimpleName(),
                                        method.getMethod().getSimpleName(),
                                        sourcePath,
                                        paramName),
                                method.getMethod());
                return null;
            }
            propStart = 1;
        } else {
            env.getMessager()
                    .printMessage(
                            Diagnostic.Kind.ERROR,
                            String.format(
                                    "[Percolate] %s.%s: source path '%s' — must include parameter name for multi-param method",
                                    descriptor.getMapperInterface().getSimpleName(),
                                    method.getMethod().getSimpleName(),
                                    sourcePath),
                            method.getMethod());
            return null;
        }

        // Navigate remaining segments.
        StringBuilder expr = new StringBuilder(startParam.getSimpleName());
        TypeElement currentType = asTypeElement(startParam.asType());
        TypeMirror currentTypeMirror = startParam.asType();
        if (currentType == null) {
            env.getMessager()
                    .printMessage(
                            Diagnostic.Kind.ERROR,
                            String.format(
                                    "[Percolate] %s.%s: parameter '%s' is not a class type",
                                    descriptor.getMapperInterface().getSimpleName(),
                                    method.getMethod().getSimpleName(),
                                    startParam.getSimpleName()),
                            method.getMethod());
            return null;
        }

        for (int i = propStart; i < segArray.length; i++) {
            String seg = segArray[i];
            if (currentType == null) {
                // Terminal type reached — cannot navigate further.
                env.getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                String.format(
                                        "[Percolate] %s.%s: source path '%s' — segment '%s' does not exist",
                                        descriptor.getMapperInterface().getSimpleName(),
                                        method.getMethod().getSimpleName(),
                                        sourcePath,
                                        seg),
                                method.getMethod());
                return null;
            }
            List<Property> props = PropertyMerger.merge(propertyStrategies, currentType, env);
            Property found = null;
            for (Property p : props) {
                if (p.getName().equals(seg)) {
                    found = p;
                    break;
                }
            }
            if (found == null) {
                env.getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                String.format(
                                        "[Percolate] %s.%s: source path '%s' — no property '%s' on %s",
                                        descriptor.getMapperInterface().getSimpleName(),
                                        method.getMethod().getSimpleName(),
                                        sourcePath,
                                        seg,
                                        currentType.getSimpleName()),
                                method.getMethod());
                return null;
            }
            expr.append(".").append(accessorSuffix(found));
            currentTypeMirror = found.getType();
            currentType = asTypeElement(found.getType()); // may be null for terminal types
        }

        return new SourceResolution(expr.toString(), currentTypeMirror);
    }

    /** Runs strategy fixpoint: adds StrategyConverter entries for all unresolved type pairs. */
    private void runStrategyFixpoint(List<TypePairForFixpoint> pairs, ConverterRegistry registry) {
        boolean added = true;
        while (added) {
            added = false;
            for (TypePairForFixpoint pair : pairs) {
                if (registry.hasConverter(pair.source, pair.target)) continue;
                for (TypeMappingStrategy strategy : typeMappingStrategies) {
                    if (strategy.canContribute(pair.source, pair.target, registry, env)) {
                        registry.register(
                                pair.source, pair.target, new StrategyConverter(strategy, pair.source, pair.target));
                        added = true;
                        break;
                    }
                }
            }
        }
    }

    private boolean isWildcard(String source) {
        return source.endsWith(".*");
    }

    @Nullable
    private VariableElement findParam(String name, MappingMethod method) {
        for (VariableElement param : method.getParameters()) {
            if (param.getSimpleName().toString().equals(name)) return param;
        }
        return null;
    }

    @Nullable
    private Property findTargetProperty(String name, List<Property> properties) {
        for (Property p : properties) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    private boolean isAlreadyCovered(Property tgtProp, List<ResolvedMapAnnotation> existing) {
        for (ResolvedMapAnnotation rma : existing) {
            if (rma.getTargetProperty().getName().equals(tgtProp.getName())) return true;
        }
        return false;
    }

    @Nullable
    private TypeElement asTypeElement(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return null;
        Element el = ((DeclaredType) type).asElement();
        return (el instanceof TypeElement) ? (TypeElement) el : null;
    }

    private String accessorExpr(String prefix, Property property) {
        return prefix + "." + accessorSuffix(property);
    }

    private String accessorSuffix(Property property) {
        if (property.getAccessor().getKind() == ElementKind.METHOD) {
            return property.getAccessor().getSimpleName() + "()";
        }
        return property.getName();
    }

    /** Holds a (source, target) type pair for the strategy fixpoint loop. */
    private static final class TypePairForFixpoint {
        final TypeMirror source;
        final TypeMirror target;

        TypePairForFixpoint(TypeMirror source, TypeMirror target) {
            this.source = source;
            this.target = target;
        }
    }

    /** Result of resolving a source path: the Java expression and its type. */
    private static final class SourceResolution {
        final String expression;
        final TypeMirror type;

        SourceResolution(String expression, TypeMirror type) {
            this.expression = expression;
            this.type = type;
        }
    }
}

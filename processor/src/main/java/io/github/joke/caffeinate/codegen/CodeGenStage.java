package io.github.joke.caffeinate.codegen;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public class CodeGenStage {

    private final ProcessingEnvironment env;
    private final Filer filer;
    private final Set<PropertyDiscoveryStrategy> strategies;
    private final Set<TypeMappingStrategy> typeMappingStrategies;

    @Inject
    public CodeGenStage(
            ProcessingEnvironment env,
            Filer filer,
            Set<PropertyDiscoveryStrategy> strategies,
            Set<TypeMappingStrategy> typeMappingStrategies) {
        this.env = env;
        this.filer = filer;
        this.strategies = strategies;
        this.typeMappingStrategies = typeMappingStrategies;
    }

    public void generate(List<MapperDescriptor> mappers) {
        for (MapperDescriptor descriptor : mappers) {
            generateMapper(descriptor);
        }
    }

    private void generateMapper(MapperDescriptor descriptor) {
        TypeElement iface = descriptor.getMapperInterface();
        String implName = iface.getSimpleName() + "Impl";
        String packageName =
                env.getElementUtils().getPackageOf(iface).getQualifiedName().toString();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(TypeName.get(iface.asType()));

        for (MappingMethod method : descriptor.getMethods()) {
            classBuilder.addMethod(generateMethod(method));
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            String msg = e.getMessage();
            env.getMessager()
                    .printMessage(
                            Diagnostic.Kind.ERROR,
                            "[Percolate] Failed to write " + implName + ": " + (msg != null ? msg : e.toString()));
        }
    }

    private MethodSpec generateMethod(MappingMethod method) {
        ExecutableElement elem = method.getMethod();
        MethodSpec.Builder builder = MethodSpec.overriding(elem);

        // For single-parameter methods: check if a TypeMappingStrategy handles the
        // whole conversion (e.g., enum-to-enum, list-to-list at top level).
        if (method.getParameters().size() == 1) {
            VariableElement param = method.getParameters().get(0);
            TypeMirror sourceType = param.asType();
            TypeMirror targetType = elem.getReturnType();
            for (TypeMappingStrategy strategy : typeMappingStrategies) {
                if (strategy.supports(sourceType, targetType, env)) {
                    Optional<String> converterRef = findConverterRef(sourceType, targetType, method);
                    // Pass converterRef (possibly null) — strategies handle null for identity cases.
                    if (converterRef.isPresent() || strategy.supportsIdentity(sourceType, targetType, env)) {
                        String expr = strategy.generate(
                                        param.getSimpleName().toString(),
                                        sourceType,
                                        targetType,
                                        converterRef.orElse(null),
                                        env)
                                .toString();
                        builder.addStatement("return $L", expr);
                        return builder.build();
                    }
                    // No converter found and not identity — fall through to property-level mapping
                }
            }
        }

        List<Property> targetProps = PropertyMerger.merge(strategies, method.getTargetType(), env);

        List<String> args = new ArrayList<>();
        for (Property targetProp : targetProps) {
            String expr = resolveExpression(targetProp, method);
            args.add(expr);
        }

        String targetFqn = method.getTargetType().getQualifiedName().toString();
        String argsJoined = String.join(", ", args);
        builder.addStatement("return new $L($L)", targetFqn, argsJoined);

        return builder.build();
    }

    /**
     * Returns a Java expression for a target property value.
     * Priority: explicit @Map > name-match from source > converter delegate.
     */
    private String resolveExpression(Property targetProp, MappingMethod method) {
        // 1. Explicit @Map annotation
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) {
                return resolveSourcePath(ann.getSource(), method);
            }
        }

        // 2. Name-match from any source parameter
        for (VariableElement param : method.getParameters()) {
            Element paramElement = env.getTypeUtils().asElement(param.asType());
            if (!(paramElement instanceof TypeElement)) continue;
            TypeElement paramType = (TypeElement) paramElement;
            List<Property> sourceProps = PropertyMerger.merge(strategies, paramType, env);
            for (Property srcProp : sourceProps) {
                if (!srcProp.getName().equals(targetProp.getName())) continue;
                // 2a. Types match — direct assignment
                if (env.getTypeUtils().isSameType(srcProp.getType(), targetProp.getType())) {
                    return accessorExpr(param.getSimpleName().toString(), srcProp);
                }
                // 2b. Types differ — try TypeMappingStrategy
                for (TypeMappingStrategy strategy : typeMappingStrategies) {
                    if (strategy.supports(srcProp.getType(), targetProp.getType(), env)) {
                        Optional<String> converterRef =
                                findConverterRef(srcProp.getType(), targetProp.getType(), method);
                        if (converterRef.isPresent()) {
                            return strategy.generate(
                                            accessorExpr(param.getSimpleName().toString(), srcProp),
                                            srcProp.getType(),
                                            targetProp.getType(),
                                            converterRef.get(),
                                            env)
                                    .toString();
                        }
                        // No converter found — skip this strategy and continue
                    }
                }
                // 2c. No strategy matched — fall through to converter delegate
            }
        }

        // 3. Converter delegate
        for (ExecutableElement candidate : method.getConverterCandidates()) {
            if (env.getTypeUtils().isSameType(candidate.getReturnType(), targetProp.getType())) {
                if (!candidate.getParameters().isEmpty()) {
                    Element converterParamElement = env.getTypeUtils()
                            .asElement(candidate.getParameters().get(0).asType());
                    if (!(converterParamElement instanceof TypeElement)) continue;
                    TypeElement converterSourceType = (TypeElement) converterParamElement;
                    for (VariableElement param : method.getParameters()) {
                        Element paramElement = env.getTypeUtils().asElement(param.asType());
                        if (!(paramElement instanceof TypeElement)) continue;
                        TypeElement paramType = (TypeElement) paramElement;
                        List<Property> sourceProps = PropertyMerger.merge(strategies, paramType, env);
                        for (Property srcProp : sourceProps) {
                            if (env.getTypeUtils().isSameType(srcProp.getType(), converterSourceType.asType())) {
                                return "this." + candidate.getSimpleName() + "("
                                        + accessorExpr(param.getSimpleName().toString(), srcProp) + ")";
                            }
                        }
                    }
                }
            }
        }

        return "null /* unresolved: " + targetProp.getName() + " */";
    }

    /**
     * Resolves "param.property" dot-notation to a Java expression.
     * E.g., "ticket.ticketId" -> "ticket.getTicketId()"
     * Also resolves bare property names (no dot) by searching all source parameters.
     * E.g., "zipCode" -> "venue.getZipCode()" (when venue is the only parameter with zipCode)
     */
    private String resolveSourcePath(String sourcePath, MappingMethod method) {
        int dot = sourcePath.indexOf('.');
        if (dot > 0 && dot < sourcePath.length() - 1 && sourcePath.indexOf('.', dot + 1) < 0) {
            // "param.property" form
            String paramName = sourcePath.substring(0, dot);
            String propName = sourcePath.substring(dot + 1);
            for (VariableElement param : method.getParameters()) {
                if (param.getSimpleName().toString().equals(paramName)) {
                    Element paramElement = env.getTypeUtils().asElement(param.asType());
                    if (!(paramElement instanceof TypeElement)) continue;
                    TypeElement paramType = (TypeElement) paramElement;
                    List<Property> sourceProps = PropertyMerger.merge(strategies, paramType, env);
                    for (Property srcProp : sourceProps) {
                        if (srcProp.getName().equals(propName)) {
                            return accessorExpr(paramName, srcProp);
                        }
                    }
                }
            }
        } else if (dot < 0) {
            // Bare property name — search all parameters
            for (VariableElement param : method.getParameters()) {
                Element paramElement = env.getTypeUtils().asElement(param.asType());
                if (!(paramElement instanceof TypeElement)) continue;
                TypeElement paramType = (TypeElement) paramElement;
                List<Property> sourceProps = PropertyMerger.merge(strategies, paramType, env);
                for (Property srcProp : sourceProps) {
                    if (srcProp.getName().equals(sourcePath)) {
                        return accessorExpr(param.getSimpleName().toString(), srcProp);
                    }
                }
            }
        }
        return "null /* unresolved path: " + sourcePath + " */";
    }

    /**
     * Finds the name of a converter method whose source element type (unwrapped from container)
     * matches the source element type and whose return element type matches the target element type.
     * For simple types, matches directly on the full type.
     */
    private Optional<String> findConverterRef(TypeMirror sourceType, TypeMirror targetType, MappingMethod method) {
        TypeMirror srcElem = elementType(sourceType);
        TypeMirror tgtElem = elementType(targetType);
        for (ExecutableElement candidate : method.getConverterCandidates()) {
            if (candidate.getParameters().isEmpty()) continue;
            TypeMirror paramType = candidate.getParameters().get(0).asType();
            TypeMirror retType = candidate.getReturnType();
            if (env.getTypeUtils().isSameType(paramType, srcElem)
                    && env.getTypeUtils().isSameType(retType, tgtElem)) {
                return Optional.of(candidate.getSimpleName().toString());
            }
        }
        return Optional.empty();
    }

    /** Returns the first type argument of a generic declared type, or the type itself. */
    private TypeMirror elementType(TypeMirror type) {
        if (type instanceof DeclaredType) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (!args.isEmpty()) {
                return args.get(0);
            }
        }
        return type;
    }

    private String accessorExpr(String paramName, Property property) {
        if (property.getAccessor().getKind() == ElementKind.METHOD) {
            return paramName + "." + property.getAccessor().getSimpleName() + "()";
        } else {
            return paramName + "." + property.getName();
        }
    }
}

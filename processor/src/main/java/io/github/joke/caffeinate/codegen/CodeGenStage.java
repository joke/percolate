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
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.MethodConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

        ConverterRegistry registry = buildTemporaryRegistry(method);

        // For single-parameter methods: check if a TypeMappingStrategy handles the
        // whole conversion at the top level (e.g., enum-to-enum, list-to-list).
        if (method.getParameters().size() == 1) {
            VariableElement param = method.getParameters().get(0);
            TypeMirror sourceType = param.asType();
            TypeMirror targetType = elem.getReturnType();
            for (TypeMappingStrategy strategy : typeMappingStrategies) {
                if (strategy.canContribute(sourceType, targetType, registry, env)) {
                    String expr = strategy.generate(
                                    param.getSimpleName().toString(), sourceType, targetType, registry, env)
                            .toString();
                    builder.addStatement("return $L", expr);
                    return builder.build();
                }
            }
        }

        List<Property> targetProps = PropertyMerger.merge(strategies, method.getTargetType(), env);
        List<String> args = new ArrayList<>();
        for (Property targetProp : targetProps) {
            args.add(resolveExpression(targetProp, method, registry));
        }

        String targetFqn = method.getTargetType().getQualifiedName().toString();
        builder.addStatement("return new $L($L)", targetFqn, String.join(", ", args));
        return builder.build();
    }

    /** Builds a registry from the method's explicit converter candidates. No strategy fixpoint. */
    private ConverterRegistry buildTemporaryRegistry(MappingMethod method) {
        ConverterRegistry registry = new ConverterRegistry();
        for (ExecutableElement candidate : method.getConverterCandidates()) {
            if (candidate.getParameters().isEmpty()) continue;
            TypeMirror paramType = candidate.getParameters().get(0).asType();
            TypeMirror returnType = candidate.getReturnType();
            registry.register(paramType, returnType, new MethodConverter(candidate));
        }
        return registry;
    }

    /**
     * Returns a Java expression for a target property value.
     * Priority: explicit @Map > name-match from source > converter delegate.
     */
    private String resolveExpression(Property targetProp, MappingMethod method, ConverterRegistry registry) {
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
            for (Property srcProp : PropertyMerger.merge(strategies, paramType, env)) {
                if (!srcProp.getName().equals(targetProp.getName())) continue;
                if (env.getTypeUtils().isSameType(srcProp.getType(), targetProp.getType())) {
                    return accessorExpr(param.getSimpleName().toString(), srcProp);
                }
                String srcExpr = accessorExpr(param.getSimpleName().toString(), srcProp);
                for (TypeMappingStrategy strategy : typeMappingStrategies) {
                    if (strategy.canContribute(srcProp.getType(), targetProp.getType(), registry, env)) {
                        return strategy.generate(srcExpr, srcProp.getType(), targetProp.getType(), registry, env)
                                .toString();
                    }
                }
            }
        }

        // 3. Converter delegate
        for (ExecutableElement candidate : method.getConverterCandidates()) {
            if (!env.getTypeUtils().isSameType(candidate.getReturnType(), targetProp.getType())) continue;
            if (candidate.getParameters().isEmpty()) continue;
            TypeMirror converterParamType = candidate.getParameters().get(0).asType();
            for (VariableElement param : method.getParameters()) {
                Element paramElement = env.getTypeUtils().asElement(param.asType());
                if (!(paramElement instanceof TypeElement)) continue;
                for (Property srcProp : PropertyMerger.merge(strategies, (TypeElement) paramElement, env)) {
                    if (env.getTypeUtils().isSameType(srcProp.getType(), converterParamType)) {
                        return "this." + candidate.getSimpleName() + "("
                                + accessorExpr(param.getSimpleName().toString(), srcProp) + ")";
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

    private String accessorExpr(String paramName, Property property) {
        if (property.getAccessor().getKind() == ElementKind.METHOD) {
            return paramName + "." + property.getAccessor().getSimpleName() + "()";
        } else {
            return paramName + "." + property.getName();
        }
    }
}

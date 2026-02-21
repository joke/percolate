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
import io.github.joke.caffeinate.graph.GraphResult;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CodeGenStage {

    private final ProcessingEnvironment env;
    private final Filer filer;
    private final Set<PropertyDiscoveryStrategy> strategies;

    @Inject
    public CodeGenStage(ProcessingEnvironment env, Filer filer,
                         Set<PropertyDiscoveryStrategy> strategies) {
        this.env = env;
        this.filer = filer;
        this.strategies = strategies;
    }

    public void generate(GraphResult graph, List<MapperDescriptor> mappers) {
        for (MapperDescriptor descriptor : mappers) {
            generateMapper(descriptor, graph);
        }
    }

    private void generateMapper(MapperDescriptor descriptor, GraphResult graph) {
        TypeElement iface = descriptor.getMapperInterface();
        String implName = iface.getSimpleName() + "Impl";
        String packageName = env.getElementUtils()
                .getPackageOf(iface).getQualifiedName().toString();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(TypeName.get(iface.asType()));

        for (MappingMethod method : descriptor.getMethods()) {
            classBuilder.addMethod(generateMethod(method, graph));
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            String msg = e.getMessage();
            env.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "[Percolate] Failed to write " + implName + ": " + (msg != null ? msg : e.toString()));
        }
    }

    private MethodSpec generateMethod(MappingMethod method, GraphResult graph) {
        ExecutableElement elem = method.getMethod();
        MethodSpec.Builder builder = MethodSpec.overriding(elem);

        List<Property> targetProps = PropertyMerger.merge(
                strategies, method.getTargetType(), env);

        List<String> args = new ArrayList<>();
        for (Property targetProp : targetProps) {
            String expr = resolveExpression(targetProp, method, graph);
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
    @SuppressWarnings("UnusedVariable")
    private String resolveExpression(Property targetProp, MappingMethod method, GraphResult graph) {
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
                if (srcProp.getName().equals(targetProp.getName())) {
                    return accessorExpr(param.getSimpleName().toString(), srcProp);
                }
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
                            if (env.getTypeUtils().isSameType(
                                    srcProp.getType(), converterSourceType.asType())) {
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
     */
    private String resolveSourcePath(String sourcePath, MappingMethod method) {
        int dot = sourcePath.indexOf('.');
        if (dot > 0 && dot < sourcePath.length() - 1 && sourcePath.indexOf('.', dot + 1) < 0) {
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

package io.github.joke.percolate.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.edge.PropertyAccessEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.CreationDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class CodeGenStage {

    private final Filer filer;
    private final Types types;

    @Inject
    CodeGenStage(ProcessingEnvironment processingEnv) {
        this.filer = processingEnv.getFiler();
        this.types = processingEnv.getTypeUtils();
    }

    public void execute(OptimizedGraphResult optimizedResult) {
        GraphResult graphResult = optimizedResult.graphResult();
        DirectedWeightedMultigraph<GraphNode, GraphEdge> graph = graphResult.getGraph();

        graphResult.getMappers().forEach(mapper -> generateMapper(graph, mapper));
    }

    private void generateMapper(DirectedWeightedMultigraph<GraphNode, GraphEdge> graph, MapperDefinition mapper) {
        ClassName mapperName = ClassName.get(mapper.getPackageName(), mapper.getSimpleName());
        ClassName implName = ClassName.get(mapper.getPackageName(), mapper.getSimpleName() + "Impl");

        List<MethodSpec> methods = mapper.getMethods().stream()
                .filter(MethodDefinition::isAbstract)
                .map(method -> generateMethod(graph, method, mapper))
                .collect(toList());

        TypeSpec typeSpec = TypeSpec.classBuilder(implName)
                .addModifiers(PUBLIC)
                .addSuperinterface(mapperName)
                .addMethods(methods)
                .build();

        JavaFile javaFile = JavaFile.builder(mapper.getPackageName(), typeSpec).build();

        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write generated source for " + implName, e);
        }
    }

    private MethodSpec generateMethod(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph, MethodDefinition method, MapperDefinition mapper) {
        TypeMirror returnType = method.getReturnType();
        String returnTypeQualified = getQualifiedName(returnType);

        // Find the ConstructorNode for the return type
        ConstructorNode constructorNode = graph.vertexSet().stream()
                .filter(ConstructorNode.class::isInstance)
                .map(ConstructorNode.class::cast)
                .filter(cn -> cn.getTargetType().getQualifiedName().toString().equals(returnTypeQualified))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ConstructorNode found for " + returnType));

        CreationDescriptor descriptor = constructorNode.getDescriptor();
        List<Property> parameters = descriptor.getParameters();

        // Get ConstructorParamEdges sorted by parameter index
        List<ConstructorParamEdge> paramEdges = graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .sorted(comparingInt(ConstructorParamEdge::getParameterIndex))
                .collect(toList());

        // Build argument expressions in constructor parameter order
        List<String> args = new ArrayList<>();
        for (Property param : parameters) {
            @Nullable
            ConstructorParamEdge edge = paramEdges.stream()
                    .filter(e -> e.getParameterName().equals(param.getName()))
                    .findFirst()
                    .orElse(null);

            if (edge == null) {
                args.add("null /* unmapped: " + param.getName() + " */");
                continue;
            }

            GraphNode sourceNode = graph.getEdgeSource(edge);
            String expression = buildExpression(graph, sourceNode);

            // Apply type conversion if needed
            @Nullable TypeMirror sourceType = getNodeType(sourceNode);
            TypeMirror targetType = param.getType();

            if (sourceType != null && !types.isSameType(sourceType, targetType)) {
                expression = applyConversion(expression, sourceType, targetType, mapper);
            }

            args.add(expression);
        }

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(method.getName())
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.get(returnType));

        method.getParameters().forEach(p -> methodBuilder.addParameter(TypeName.get(p.getType()), p.getName()));

        String argsJoined = String.join(", ", args);
        methodBuilder.addStatement("return new $L($L)", returnTypeQualified, argsJoined);

        return methodBuilder.build();
    }

    private String applyConversion(
            String expression, TypeMirror sourceType, TypeMirror targetType, MapperDefinition mapper) {
        // 1. Check for a direct mapper method (sourceType -> targetType)
        @Nullable MethodDefinition directMethod = findMapperMethod(mapper, sourceType, targetType);
        if (directMethod != null) {
            return "this." + directMethod.getName() + "(" + expression + ")";
        }

        // 2. Check for List<A> -> List<B> conversion
        if (isListType(sourceType) && isListType(targetType)) {
            @Nullable TypeMirror sourceElement = getFirstTypeArgument(sourceType);
            @Nullable TypeMirror targetElement = getFirstTypeArgument(targetType);
            if (sourceElement != null && targetElement != null) {
                @Nullable MethodDefinition elementConverter = findMapperMethod(mapper, sourceElement, targetElement);
                if (elementConverter != null) {
                    return expression + ".stream().map(this::" + elementConverter.getName()
                            + ").collect(java.util.stream.Collectors.toList())";
                }
            }
        }

        // 3. Check for non-Optional -> Optional wrapping
        if (isOptionalType(targetType) && !isOptionalType(sourceType)) {
            @Nullable TypeMirror innerTarget = getFirstTypeArgument(targetType);
            if (innerTarget != null) {
                if (isSameType(sourceType, innerTarget)) {
                    return "java.util.Optional.ofNullable(" + expression + ")";
                }
                @Nullable MethodDefinition converter = findMapperMethod(mapper, sourceType, innerTarget);
                if (converter != null) {
                    return "java.util.Optional.ofNullable(this." + converter.getName() + "(" + expression + "))";
                }
            }
        }

        // 4. Check for enum-to-enum conversion
        if (isEnumType(sourceType) && isEnumType(targetType)) {
            return getQualifiedName(targetType) + ".valueOf(" + expression + ".name())";
        }

        // No conversion found — use expression as-is
        return expression;
    }

    private boolean isSameType(TypeMirror a, TypeMirror b) {
        return types.isSameType(types.erasure(a), types.erasure(b));
    }

    private boolean isListType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        String erasedName = ((DeclaredType) type).asElement().toString();
        return "java.util.List".equals(erasedName);
    }

    private boolean isOptionalType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        String erasedName = ((DeclaredType) type).asElement().toString();
        return "java.util.Optional".equals(erasedName);
    }

    private boolean isEnumType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        Element element = ((DeclaredType) type).asElement();
        return element.getKind() == ElementKind.ENUM;
    }

    private @Nullable TypeMirror getFirstTypeArgument(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        if (args.isEmpty()) {
            return null;
        }
        return args.get(0);
    }

    private @Nullable MethodDefinition findMapperMethod(
            MapperDefinition mapper, TypeMirror sourceType, TypeMirror targetType) {
        return mapper.getMethods().stream()
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> isSameType(m.getParameters().get(0).getType(), sourceType))
                .filter(m -> isSameType(m.getReturnType(), targetType))
                .findFirst()
                .orElse(null);
    }

    private @Nullable TypeMirror getNodeType(GraphNode node) {
        if (node instanceof TypeNode) {
            return ((TypeNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getProperty().getType();
        }
        return null;
    }

    private String buildExpression(DirectedWeightedMultigraph<GraphNode, GraphEdge> graph, GraphNode node) {
        List<String> chain = new ArrayList<>();
        GraphNode current = node;

        while (current instanceof PropertyNode) {
            PropertyNode propertyNode = (PropertyNode) current;
            Property property = propertyNode.getProperty();
            Element accessor = property.getAccessor();

            if (accessor instanceof ExecutableElement) {
                chain.add(0, ((ExecutableElement) accessor).getSimpleName().toString() + "()");
            } else {
                chain.add(0, property.getName());
            }

            // Walk up to parent via incoming PropertyAccessEdge
            @Nullable GraphNode parent = findParentViaPropertyAccess(graph, current);
            if (parent == null) {
                // fallback: use the stored parent reference
                parent = propertyNode.getParent();
            }
            current = parent;
        }

        if (current instanceof TypeNode) {
            chain.add(0, ((TypeNode) current).getLabel());
        }

        return String.join(".", chain);
    }

    private @Nullable GraphNode findParentViaPropertyAccess(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph, GraphNode node) {
        return graph.incomingEdgesOf(node).stream()
                .filter(PropertyAccessEdge.class::isInstance)
                .map(graph::getEdgeSource)
                .findFirst()
                .orElse(null);
    }

    private String getQualifiedName(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType) {
            return ((DeclaredType) typeMirror).asElement().toString();
        }
        return typeMirror.toString();
    }
}

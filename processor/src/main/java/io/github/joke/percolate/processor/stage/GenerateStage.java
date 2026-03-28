package io.github.joke.percolate.processor.stage;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.MappingEdge;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.PropertyNode;
import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import io.github.joke.percolate.processor.model.ConstructorParamAccessor;
import io.github.joke.percolate.processor.model.DiscoveredMethod;
import io.github.joke.percolate.processor.model.FieldReadAccessor;
import io.github.joke.percolate.processor.model.FieldWriteAccessor;
import io.github.joke.percolate.processor.model.GetterAccessor;
import io.github.joke.percolate.processor.model.ReadAccessor;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.tools.Diagnostic.Kind;

public final class GenerateStage {

    private final Filer filer;

    @Inject
    GenerateStage(final Filer filer) {
        this.filer = filer;
    }

    @SuppressWarnings("NullAway")
    public StageResult<JavaFile> execute(final MappingGraph mappingGraph) {
        final var mapperType = mappingGraph.getMapperType();
        final PackageElement packageElement = (PackageElement) mapperType.getEnclosingElement();
        final var packageName = packageElement.getQualifiedName().toString();
        final var simpleName = mapperType.getSimpleName().toString();
        final var mapperName = ClassName.get(packageName, simpleName);
        final var implName = ClassName.get(packageName, simpleName + "Impl");

        final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(mapperName);

        for (final DiscoveredMethod method : mappingGraph.getMethods()) {
            classBuilder.addMethod(generateMethod(method, mappingGraph));
        }

        final JavaFile javaFile =
                JavaFile.builder(mapperName.packageName(), classBuilder.build()).build();

        try {
            javaFile.writeTo(filer);
        } catch (final IOException e) {
            return StageResult.failure(List.of(
                    new Diagnostic(mapperType, "Failed to write generated file: " + e.getMessage(), Kind.ERROR)));
        }

        return StageResult.success(javaFile);
    }

    private MethodSpec generateMethod(final DiscoveredMethod method, final MappingGraph mappingGraph) {
        final var executableElement = method.getOriginal().getMethod();
        final var sourceParam = executableElement.getParameters().get(0);
        final var sourceParamName = sourceParam.getSimpleName().toString();
        final var returnType = TypeName.get(method.getOriginal().getTargetType());

        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(
                        executableElement.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType)
                .addParameter(TypeName.get(method.getOriginal().getSourceType()), sourceParamName);

        final var graph = mappingGraph.getGraph();
        final List<TargetPropertyNode> targetNodes = new ArrayList<>();

        for (final PropertyNode node : graph.vertexSet()) {
            if (node instanceof TargetPropertyNode) {
                targetNodes.add((TargetPropertyNode) node);
            }
        }

        final boolean allConstructor =
                targetNodes.stream().allMatch(t -> t.accessor() instanceof ConstructorParamAccessor);

        if (allConstructor) {
            generateConstructorBody(methodBuilder, targetNodes, graph, sourceParamName, returnType);
        } else {
            generateFieldBody(methodBuilder, targetNodes, graph, sourceParamName, returnType);
        }

        return methodBuilder.build();
    }

    private void generateConstructorBody(
            final MethodSpec.Builder methodBuilder,
            final List<TargetPropertyNode> targetNodes,
            final org.jgrapht.graph.DefaultDirectedGraph<PropertyNode, MappingEdge> graph,
            final String sourceParamName,
            final TypeName returnType) {

        targetNodes.sort(Comparator.comparingInt(t -> ((ConstructorParamAccessor) t.accessor()).paramIndex()));

        final List<CodeBlock> args = new ArrayList<>();
        for (final TargetPropertyNode targetNode : targetNodes) {
            final var edges = graph.incomingEdgesOf(targetNode);
            if (edges.isEmpty()) {
                args.add(CodeBlock.of("null"));
                continue;
            }
            final var edge = edges.iterator().next();
            final SourcePropertyNode sourceNode = (SourcePropertyNode) graph.getEdgeSource(edge);
            args.add(generateReadExpression(sourceNode.accessor(), sourceParamName));
        }

        methodBuilder.addStatement("return new $T($L)", returnType, CodeBlock.join(args, ", "));
    }

    private void generateFieldBody(
            final MethodSpec.Builder methodBuilder,
            final List<TargetPropertyNode> targetNodes,
            final org.jgrapht.graph.DefaultDirectedGraph<PropertyNode, MappingEdge> graph,
            final String sourceParamName,
            final TypeName returnType) {

        methodBuilder.addStatement("$T target = new $T()", returnType, returnType);

        for (final TargetPropertyNode targetNode : targetNodes) {
            final var edges = graph.incomingEdgesOf(targetNode);
            if (edges.isEmpty()) {
                continue;
            }
            final var edge = edges.iterator().next();
            final SourcePropertyNode sourceNode = (SourcePropertyNode) graph.getEdgeSource(edge);
            final var readExpr = generateReadExpression(sourceNode.accessor(), sourceParamName);

            if (targetNode.accessor() instanceof FieldWriteAccessor) {
                methodBuilder.addStatement("target.$L = $L", targetNode.name(), readExpr);
            }
        }

        methodBuilder.addStatement("return target");
    }

    private CodeBlock generateReadExpression(final ReadAccessor accessor, final String sourceParamName) {
        if (accessor instanceof GetterAccessor) {
            final GetterAccessor getter = (GetterAccessor) accessor;
            return CodeBlock.of("$L.$L()", sourceParamName, getter.method().getSimpleName());
        }
        if (accessor instanceof FieldReadAccessor) {
            return CodeBlock.of("$L.$L", sourceParamName, accessor.name());
        }
        return CodeBlock.of("null");
    }
}

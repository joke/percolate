package io.github.joke.percolate.processor.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.tools.Diagnostic.Kind.ERROR;

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
import java.util.List;
import javax.annotation.processing.Filer;
import javax.lang.model.element.PackageElement;

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

        final TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(implName).addModifiers(PUBLIC, FINAL).addSuperinterface(mapperName);

        for (final DiscoveredMethod method : mappingGraph.getMethods()) {
            classBuilder.addMethod(generateMethod(method, mappingGraph));
        }

        final JavaFile javaFile =
                JavaFile.builder(mapperName.packageName(), classBuilder.build()).build();

        try {
            javaFile.writeTo(filer);
        } catch (final IOException e) {
            return StageResult.failure(
                    List.of(new Diagnostic(mapperType, "Failed to write generated file: " + e.getMessage(), ERROR)));
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
                .addModifiers(PUBLIC)
                .returns(returnType)
                .addParameter(TypeName.get(method.getOriginal().getSourceType()), sourceParamName);

        final var graph = mappingGraph.getGraph();
        final List<TargetPropertyNode> targetNodes = new ArrayList<>(graph.vertexSet().stream()
                .filter(TargetPropertyNode.class::isInstance)
                .map(TargetPropertyNode.class::cast)
                .collect(toUnmodifiableList()));

        final boolean allConstructor =
                targetNodes.stream().allMatch(t -> t.getAccessor() instanceof ConstructorParamAccessor);

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

        targetNodes.sort(comparingInt(t -> ((ConstructorParamAccessor) t.getAccessor()).getParamIndex()));

        final List<CodeBlock> args = new ArrayList<>();
        for (final TargetPropertyNode targetNode : targetNodes) {
            final var edges = graph.incomingEdgesOf(targetNode);
            if (edges.isEmpty()) {
                args.add(CodeBlock.of("null"));
                continue;
            }
            final var edge = edges.iterator().next();
            final SourcePropertyNode sourceNode = (SourcePropertyNode) graph.getEdgeSource(edge);
            args.add(generateReadExpression(sourceNode.getAccessor(), sourceParamName));
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
            final var readExpr = generateReadExpression(sourceNode.getAccessor(), sourceParamName);

            if (targetNode.getAccessor() instanceof FieldWriteAccessor) {
                methodBuilder.addStatement("target.$L = $L", targetNode.getName(), readExpr);
            }
        }

        methodBuilder.addStatement("return target");
    }

    private CodeBlock generateReadExpression(final ReadAccessor accessor, final String sourceParamName) {
        if (accessor instanceof GetterAccessor) {
            final GetterAccessor getter = (GetterAccessor) accessor;
            return CodeBlock.of("$L.$L()", sourceParamName, getter.getMethod().getSimpleName());
        }
        if (accessor instanceof FieldReadAccessor) {
            return CodeBlock.of("$L.$L", sourceParamName, accessor.getName());
        }
        return CodeBlock.of("null");
    }
}

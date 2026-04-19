package io.github.joke.percolate.processor.stage;

import static java.util.Comparator.comparingInt;
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
import io.github.joke.percolate.processor.graph.ComposeKind;
import io.github.joke.percolate.processor.graph.LiftEdge;
import io.github.joke.percolate.processor.graph.PropertyReadEdge;
import io.github.joke.percolate.processor.graph.TargetSlotNode;
import io.github.joke.percolate.processor.graph.TypeTransformEdge;
import io.github.joke.percolate.processor.graph.ValueEdge;
import io.github.joke.percolate.processor.graph.ValueEdgeVisitor;
import io.github.joke.percolate.processor.graph.ValueNode;
import io.github.joke.percolate.processor.match.MethodMatching;
import io.github.joke.percolate.processor.match.ResolvedAssignment;
import io.github.joke.percolate.processor.model.ConstructorParamAccessor;
import io.github.joke.percolate.processor.model.FieldWriteAccessor;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class GenerateStage {

    private final Filer filer;

    @SuppressWarnings("NullAway")
    public StageResult<JavaFile> execute(
            final TypeElement mapperType, final Map<MethodMatching, List<ResolvedAssignment>> resolvedAssignments) {
        final PackageElement packageElement = (PackageElement) mapperType.getEnclosingElement();
        final var packageName = packageElement.getQualifiedName().toString();
        final var simpleName = mapperType.getSimpleName().toString();
        final var mapperName = ClassName.get(packageName, simpleName);
        final var implName = ClassName.get(packageName, simpleName + "Impl");

        final TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(implName).addModifiers(PUBLIC, FINAL).addSuperinterface(mapperName);

        for (final var entry : resolvedAssignments.entrySet()) {
            classBuilder.addMethod(generateMethod(entry.getKey(), entry.getValue()));
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

    private MethodSpec generateMethod(final MethodMatching matching, final List<ResolvedAssignment> assignments) {
        final var method = matching.getModel();
        final var executableElement = method.getMethod();
        final var sourceParam = executableElement.getParameters().get(0);
        final var sourceParamName = sourceParam.getSimpleName().toString();
        final var returnType = TypeName.get(method.getTargetType());

        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(
                        executableElement.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(returnType)
                .addParameter(TypeName.get(method.getSourceType()), sourceParamName);

        final Map<TargetSlotNode, CodeBlock> slotExpressions = computeSlotExpressions(assignments);

        final boolean allConstructor = assignments.stream()
                .allMatch(ra -> targetSlot(ra).getWriteAccessor() instanceof ConstructorParamAccessor);

        if (allConstructor) {
            generateConstructorBody(methodBuilder, assignments, slotExpressions, returnType);
        } else {
            generateFieldBody(methodBuilder, assignments, slotExpressions, returnType);
        }

        return methodBuilder.build();
    }

    private static Map<TargetSlotNode, CodeBlock> computeSlotExpressions(final List<ResolvedAssignment> assignments) {
        final List<GraphPath<ValueNode, ValueEdge>> paths = new ArrayList<>();
        for (final var ra : assignments) {
            if (ra.getPath() != null) {
                paths.add(ra.getPath());
            }
        }
        if (paths.isEmpty()) {
            return Map.of();
        }

        final Graph<ValueNode, ValueEdge> graph = paths.get(0).getGraph();
        final Set<ValueNode> vertices = new LinkedHashSet<>();
        final Set<ValueEdge> winningEdges = new LinkedHashSet<>();
        for (final var path : paths) {
            vertices.addAll(path.getVertexList());
            winningEdges.addAll(path.getEdgeList());
        }

        final var subgraph = new AsSubgraph<>(graph, vertices, winningEdges);

        final Map<ValueNode, CodeBlock> cache = new LinkedHashMap<>();
        final var iterator = new TopologicalOrderIterator<>(subgraph);
        while (iterator.hasNext()) {
            final var node = iterator.next();
            final Map<ValueEdge, CodeBlock> inputs = new LinkedHashMap<>();
            for (final var edge : subgraph.incomingEdgesOf(node)) {
                final var source = subgraph.getEdgeSource(edge);
                final var sourceExpr = cache.get(source);
                if (sourceExpr == null) {
                    throw new IllegalStateException(
                            "Topological order violated: source " + source + " not yet composed for edge " + edge);
                }
                inputs.put(edge, edge.accept(new EmitVisitor(sourceExpr, graph)));
            }
            cache.put(node, node.compose(inputs, ComposeKind.EXPRESSION));
        }

        final Map<TargetSlotNode, CodeBlock> result = new LinkedHashMap<>();
        for (final var ra : assignments) {
            final var slot = targetSlot(ra);
            if (slot != null) {
                final var expr = cache.get(slot);
                if (expr != null) {
                    result.put(slot, expr);
                }
            }
        }
        return result;
    }

    private void generateConstructorBody(
            final MethodSpec.Builder methodBuilder,
            final List<ResolvedAssignment> assignments,
            final Map<TargetSlotNode, CodeBlock> slotExpressions,
            final TypeName returnType) {

        final List<ResolvedAssignment> sorted = new ArrayList<>(assignments);
        sorted.sort(
                comparingInt(ra -> ((ConstructorParamAccessor) targetSlot(ra).getWriteAccessor()).getParamIndex()));

        final List<CodeBlock> args = new ArrayList<>();
        for (final ResolvedAssignment ra : sorted) {
            args.add(slotExpressions.get(targetSlot(ra)));
        }

        methodBuilder.addStatement("return new $T($L)", returnType, CodeBlock.join(args, ", "));
    }

    private void generateFieldBody(
            final MethodSpec.Builder methodBuilder,
            final List<ResolvedAssignment> assignments,
            final Map<TargetSlotNode, CodeBlock> slotExpressions,
            final TypeName returnType) {

        methodBuilder.addStatement("$T target = new $T()", returnType, returnType);

        for (final ResolvedAssignment ra : assignments) {
            final var slot = targetSlot(ra);
            if (slot.getWriteAccessor() instanceof FieldWriteAccessor) {
                methodBuilder.addStatement(
                        "target.$L = $L", ra.getAssignment().getTargetName(), slotExpressions.get(slot));
            }
        }

        methodBuilder.addStatement("return target");
    }

    private static TargetSlotNode targetSlot(final ResolvedAssignment ra) {
        final GraphPath<ValueNode, ValueEdge> path = ra.getPath();
        if (path == null) {
            throw new IllegalStateException("ResolvedAssignment has no path for target: "
                    + ra.getAssignment().getTargetName());
        }
        final List<ValueNode> vertices = path.getVertexList();
        return (TargetSlotNode) vertices.get(vertices.size() - 1);
    }

    /**
     * Applies each {@link ValueEdge}'s template to the composed source expression. Templates for
     * non-{@link LiftEdge} edges were eagerly materialised at construction in
     * {@code BuildValueGraphStage}; {@link LiftEdge} templates are composed on demand here via
     * {@link LiftEdge#composeTemplate(Graph, Set)}.
     */
    @RequiredArgsConstructor
    private static final class EmitVisitor implements ValueEdgeVisitor<CodeBlock> {

        private final CodeBlock input;
        private final Graph<ValueNode, ValueEdge> graph;

        @Override
        public CodeBlock visitPropertyRead(final PropertyReadEdge edge) {
            return edge.getTemplate().apply(input);
        }

        @Override
        public CodeBlock visitTypeTransform(final TypeTransformEdge edge) {
            return edge.getCodeTemplate().apply(input);
        }

        @Override
        public CodeBlock visitLift(final LiftEdge edge) {
            return edge.composeTemplate(graph).apply(input);
        }

        @Override
        public CodeBlock visitNullWiden(final io.github.joke.percolate.processor.graph.NullWidenEdge edge) {
            throw new IllegalStateException("NullWidenEdge is not constructed by this refactor: " + edge);
        }
    }
}

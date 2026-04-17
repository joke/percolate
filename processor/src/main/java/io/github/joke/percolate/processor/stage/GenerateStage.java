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
import io.github.joke.percolate.processor.graph.LiftEdge;
import io.github.joke.percolate.processor.graph.LiftKind;
import io.github.joke.percolate.processor.graph.NullWidenEdge;
import io.github.joke.percolate.processor.graph.PropertyNode;
import io.github.joke.percolate.processor.graph.PropertyReadEdge;
import io.github.joke.percolate.processor.graph.TargetSlotNode;
import io.github.joke.percolate.processor.graph.TypeTransformEdge;
import io.github.joke.percolate.processor.graph.ValueEdge;
import io.github.joke.percolate.processor.graph.ValueEdgeVisitor;
import io.github.joke.percolate.processor.graph.ValueNode;
import io.github.joke.percolate.processor.match.MethodMatching;
import io.github.joke.percolate.processor.match.ResolvedAssignment;
import io.github.joke.percolate.processor.model.ConstructorParamAccessor;
import io.github.joke.percolate.processor.model.FieldReadAccessor;
import io.github.joke.percolate.processor.model.FieldWriteAccessor;
import io.github.joke.percolate.processor.model.GetterAccessor;
import io.github.joke.percolate.processor.model.ReadAccessor;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;
import org.jgrapht.GraphPath;

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
            final var matching = entry.getKey();
            final var assignments = entry.getValue();
            classBuilder.addMethod(generateMethod(matching, assignments));
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

        final boolean allConstructor = assignments.stream()
                .allMatch(ra -> targetSlot(ra).getWriteAccessor() instanceof ConstructorParamAccessor);

        if (allConstructor) {
            generateConstructorBody(methodBuilder, assignments, sourceParamName, returnType);
        } else {
            generateFieldBody(methodBuilder, assignments, sourceParamName, returnType);
        }

        return methodBuilder.build();
    }

    @SuppressWarnings("NullAway")
    private void generateConstructorBody(
            final MethodSpec.Builder methodBuilder,
            final List<ResolvedAssignment> assignments,
            final String sourceParamName,
            final TypeName returnType) {

        final List<ResolvedAssignment> sorted = new ArrayList<>(assignments);
        final var comparator = comparingInt((ResolvedAssignment ra) ->
                ((ConstructorParamAccessor) targetSlot(ra).getWriteAccessor()).getParamIndex());
        sorted.sort(comparator);

        final List<CodeBlock> args = new ArrayList<>();
        for (final ResolvedAssignment ra : sorted) {
            args.add(generateValueExpression(ra, sourceParamName));
        }

        methodBuilder.addStatement("return new $T($L)", returnType, CodeBlock.join(args, ", "));
    }

    @SuppressWarnings("NullAway")
    private void generateFieldBody(
            final MethodSpec.Builder methodBuilder,
            final List<ResolvedAssignment> assignments,
            final String sourceParamName,
            final TypeName returnType) {

        methodBuilder.addStatement("$T target = new $T()", returnType, returnType);

        for (final ResolvedAssignment ra : assignments) {
            if (targetSlot(ra).getWriteAccessor() instanceof FieldWriteAccessor) {
                final var targetName = ra.getAssignment().getTargetName();
                final var valueExpr = generateValueExpression(ra, sourceParamName);
                methodBuilder.addStatement("target.$L = $L", targetName, valueExpr);
            }
        }

        methodBuilder.addStatement("return target");
    }

    @SuppressWarnings("NullAway") // codeTemplate is set by OptimizePathStage before edges reach GenerateStage
    private CodeBlock generateValueExpression(final ResolvedAssignment ra, final String sourceParamName) {
        final GraphPath<ValueNode, ValueEdge> path = ra.getPath();
        final List<ValueNode> vertices = path.getVertexList();
        final List<ValueEdge> edges = path.getEdgeList();

        var result = CodeBlock.of("$L", sourceParamName);
        for (int i = 0; i < edges.size(); i++) {
            final var nextVertex = vertices.get(i + 1);
            result = edges.get(i).accept(new EmitVisitor(result, nextVertex));
        }
        return result;
    }

    /**
     * Compile-time exhaustive dispatch over {@link ValueEdge} subtypes — adding a fifth subtype
     * forces a method here, replacing the old {@code instanceof} ladder and its default branch.
     */
    @RequiredArgsConstructor
    private static final class EmitVisitor implements ValueEdgeVisitor<CodeBlock> {

        private final CodeBlock input;
        private final ValueNode nextVertex;

        @Override
        public CodeBlock visitPropertyRead(final PropertyReadEdge edge) {
            return appendAccessor(input, ((PropertyNode) nextVertex).getReadAccessor());
        }

        @Override
        @SuppressWarnings("NullAway") // codeTemplate is set by OptimizePathStage before edges reach GenerateStage
        public CodeBlock visitTypeTransform(final TypeTransformEdge edge) {
            return edge.getCodeTemplate().apply(input);
        }

        @Override
        @SuppressWarnings("NullAway") // codeTemplate is set by OptimizePathStage before edges reach GenerateStage
        public CodeBlock visitLift(final LiftEdge edge) {
            if (edge.getKind() == LiftKind.NULL_CHECK) {
                throw new IllegalStateException("LiftEdge(NULL_CHECK) is not constructed by this refactor: " + edge);
            }
            return edge.getCodeTemplate().apply(input);
        }

        @Override
        public CodeBlock visitNullWiden(final NullWidenEdge edge) {
            throw new IllegalStateException("NullWidenEdge is not constructed by this refactor: " + edge);
        }
    }

    private static CodeBlock appendAccessor(final CodeBlock base, final ReadAccessor accessor) {
        if (accessor instanceof GetterAccessor) {
            final GetterAccessor getter = (GetterAccessor) accessor;
            return CodeBlock.of("$L.$L()", base, getter.getMethod().getSimpleName());
        }
        if (accessor instanceof FieldReadAccessor) {
            return CodeBlock.of("$L.$L", base, accessor.getName());
        }
        return base;
    }

    @SuppressWarnings("NullAway")
    private static TargetSlotNode targetSlot(final ResolvedAssignment ra) {
        final GraphPath<ValueNode, ValueEdge> path = ra.getPath();
        final List<ValueNode> vertices = path.getVertexList();
        return (TargetSlotNode) vertices.get(vertices.size() - 1);
    }
}

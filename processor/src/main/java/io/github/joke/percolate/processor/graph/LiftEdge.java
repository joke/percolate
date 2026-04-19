package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jgrapht.Graph;
import org.jgrapht.alg.shortestpath.BFSShortestPath;

/**
 * Edge wrapping a sub-path under a container or null-safe scope.
 *
 * <p>The lift captures the endpoints of the inner sub-path — {@code innerInputNode} and
 * {@code innerOutputNode} — together with its {@link LiftKind}. The inner path itself is not
 * frozen at construction: it is resolved lazily by {@link #composeTemplate(Graph)} at
 * generation time by running {@link BFSShortestPath} over the parent graph.
 *
 * <p>{@link LiftKind#NULL_CHECK} is declared but never constructed in this refactor.
 */
@Getter
@ToString
@RequiredArgsConstructor
public final class LiftEdge extends ValueEdge {

    private final LiftKind kind;
    private final ValueNode innerInputNode;
    private final ValueNode innerOutputNode;

    @Override
    public <R> R accept(final ValueEdgeVisitor<R> visitor) {
        return visitor.visitLift(this);
    }

    /**
     * Compose the element-level template by running BFS between {@link #innerInputNode} and
     * {@link #innerOutputNode} over the enclosing {@code ValueGraph} and wrapping the composed
     * inner expression per this edge's {@link LiftKind}.
     *
     * <p>All non-{@code LiftEdge} templates are materialised at edge construction in
     * {@code BuildValueGraphStage}, so BFS may traverse the full graph without template-null
     * concerns.
     *
     * @param graph the enclosing {@code ValueGraph} built by {@code BuildValueGraphStage}
     * @return a {@link CodeTemplate} that wraps the inner composition per {@link LiftKind}
     * @throws IllegalStateException if no inner path is found or a {@link LiftKind} other than
     *     {@link LiftKind#OPTIONAL} or {@link LiftKind#STREAM} is encountered
     */
    public CodeTemplate composeTemplate(final Graph<ValueNode, ValueEdge> graph) {

        final var innerPath = new BFSShortestPath<>(graph).getPath(innerInputNode, innerOutputNode);
        if (innerPath == null) {
            throw new IllegalStateException(
                    "LiftEdge inner path not found from " + innerInputNode + " to " + innerOutputNode);
        }

        final CodeTemplate innerTemplate = composeInnerTemplates(innerPath.getEdgeList());

        switch (kind) {
            case OPTIONAL:
            case STREAM:
                return input -> CodeBlock.of("$L.map(e -> $L)", input, innerTemplate.apply(CodeBlock.of("e")));
            default:
                throw new IllegalStateException("Unsupported LiftKind: " + kind);
        }
    }

    private static CodeTemplate composeInnerTemplates(final java.util.List<ValueEdge> edges) {
        return input -> {
            var result = input;
            for (final var edge : edges) {
                result = edge.accept(new InnerEmitVisitor(result));
            }
            return result;
        };
    }

    @RequiredArgsConstructor
    private static final class InnerEmitVisitor implements ValueEdgeVisitor<CodeBlock> {

        private final CodeBlock input;

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
            throw new IllegalStateException("Nested LiftEdge inside inner path is not supported: " + edge);
        }

        @Override
        public CodeBlock visitNullWiden(final NullWidenEdge edge) {
            throw new IllegalStateException("NullWidenEdge inside inner path is not supported: " + edge);
        }
    }
}

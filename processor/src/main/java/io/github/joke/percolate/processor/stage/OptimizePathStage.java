package io.github.joke.percolate.processor.stage;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.LiftEdge;
import io.github.joke.percolate.processor.graph.NullWidenEdge;
import io.github.joke.percolate.processor.graph.PropertyReadEdge;
import io.github.joke.percolate.processor.graph.TypeTransformEdge;
import io.github.joke.percolate.processor.graph.ValueEdge;
import io.github.joke.percolate.processor.match.MethodMatching;
import io.github.joke.percolate.processor.match.ResolvedAssignment;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;

/**
 * Materialises code templates for all on-path edges in the resolved assignments produced by
 * {@link ResolvePathStage}.
 *
 * <p>For every {@link ResolvedAssignment} with a non-null path, this stage walks each edge and:
 * <ul>
 *   <li>{@link TypeTransformEdge} — calls {@link TypeTransformEdge#resolveTemplate()} exactly once,
 *       copying the pre-stored {@code proposalTemplate} into {@code codeTemplate}.
 *   <li>{@link LiftEdge} — recursively materialises inner-path {@link TypeTransformEdge}s, composes
 *       a per-element {@link CodeTemplate}, then calls {@link LiftEdge#resolveTemplate(CodeTemplate)}.
 *   <li>{@link PropertyReadEdge} — skipped; property reads carry no code template.
 *   <li>{@link NullWidenEdge} — panics; not constructed by this refactor.
 * </ul>
 *
 * <p>Off-path edges are never visited, so their {@code codeTemplate} remains {@code null}.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public final class OptimizePathStage {

    public StageResult<Map<MethodMatching, List<ResolvedAssignment>>> execute(
            final Map<MethodMatching, List<ResolvedAssignment>> resolvedAssignments) {

        for (final var assignments : resolvedAssignments.values()) {
            for (final var ra : assignments) {
                if (!ra.isResolved() || ra.getPath() == null) {
                    continue;
                }
                materialiseEdges(ra.getPath().getEdgeList());
            }
        }

        return StageResult.success(resolvedAssignments);
    }

    private static void materialiseEdges(final List<ValueEdge> edges) {
        for (final var edge : edges) {
            if (edge instanceof PropertyReadEdge) {
                // Property reads carry no code template — skip.
            } else if (edge instanceof TypeTransformEdge) {
                ((TypeTransformEdge) edge).resolveTemplate();
            } else if (edge instanceof LiftEdge) {
                materialiseLiftEdge((LiftEdge) edge);
            } else if (edge instanceof NullWidenEdge) {
                throw new IllegalStateException("NullWidenEdge encountered during template materialisation"
                        + " — not constructed by this refactor: "
                        + edge);
            } else {
                throw new IllegalStateException("Unknown ValueEdge subtype: " + edge.getClass());
            }
        }
    }

    @SuppressWarnings("NullAway") // codeTemplate on inner TypeTransformEdge is set by resolveTemplate() before apply()
    private static void materialiseLiftEdge(final LiftEdge liftEdge) {
        final List<ValueEdge> innerEdges = liftEdge.getInnerPath().getEdgeList();

        // Materialise any TypeTransformEdge templates within the inner path first.
        for (final var innerEdge : innerEdges) {
            if (innerEdge instanceof TypeTransformEdge) {
                ((TypeTransformEdge) innerEdge).resolveTemplate();
            }
        }

        // Compose the inner templates into a single per-element CodeTemplate.
        final CodeTemplate innerTemplate = composeInnerTemplates(innerEdges);

        final CodeTemplate liftTemplate;
        switch (liftEdge.getKind()) {
            case OPTIONAL:
            case STREAM:
                liftTemplate = input -> CodeBlock.of("$L.map(e -> $L)", input, innerTemplate.apply(CodeBlock.of("e")));
                break;
            default:
                throw new IllegalStateException("Unsupported LiftKind in this refactor: " + liftEdge.getKind());
        }

        liftEdge.resolveTemplate(liftTemplate);
    }

    @SuppressWarnings("NullAway") // codeTemplate is set by resolveTemplate() before this closure reads it
    private static CodeTemplate composeInnerTemplates(final List<ValueEdge> edges) {
        return input -> {
            var result = input;
            for (final var edge : edges) {
                if (edge instanceof TypeTransformEdge) {
                    result = ((TypeTransformEdge) edge).getCodeTemplate().apply(result);
                }
            }
            return result;
        };
    }
}

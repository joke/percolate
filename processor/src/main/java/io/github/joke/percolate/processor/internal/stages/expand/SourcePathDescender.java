package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.internal.graph.AccessPath;
import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.PortBinding;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.SourceLocation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Materialises a directive's source-path leaf by forward, target-bound descent (design D1/D2 of change
 * {@code forward-target-bound-accessor-descent}, decomposed out of {@code ExpandStage.Driver} by
 * {@code decompose-engine-stages}): the scope-input root {@code LEAF} is created, then each further segment's
 * accessor is landed against the type of the {@code Value} landed for the previous segment, advancing to the leaf —
 * over-emitting every matching accessor per segment (cost prunes later), with no typing pre-walk and no
 * {@code findFirst}.
 */
@RequiredArgsConstructor
final class SourcePathDescender {

    private final List<ExpansionStrategy> strategies;
    private final ResolveCtx resolveCtx;
    private final NullabilityResolver resolver;
    private final MapperGraph graph;
    private final Applier applier;
    private final OperationLander operationLander;

    /**
     * The leaf source {@code Value} for a directive's source {@code segments}, or {@code null} when the path is
     * empty or a segment resolves no accessor. Idempotent through the dedup index, so re-deriving the same source
     * path re-lands nothing new.
     */
    @Nullable
    Value pinnedSource(final Scope scope, final List<String> segments) {
        if (segments.isEmpty()) {
            return null;
        }
        var parent = materialiseRoot(scope, segments.get(0));
        for (var depth = 1; parent != null && depth < segments.size(); depth++) {
            parent = descendSegment(scope, parent, segments.subList(0, depth + 1));
        }
        return parent;
    }

    /** The scope-input root {@code LEAF} for the path's first {@code segment}, or {@code null} when no input declares it. */
    @Nullable
    Value materialiseRoot(final Scope scope, final String segment) {
        return scope.inputDecls(resolver::resolve)
                .filter(decl -> decl.getLocation().slotName().equals(segment))
                .findFirst()
                .map(decl -> applier.apply(
                        graph, new AddValue(scope, decl.getLocation(), decl.getType(), decl.getNullness())))
                .orElse(null);
    }

    /**
     * Lands every accessor that reads {@code path}'s last segment off {@code parent}, returning the produced source
     * {@code Value} at {@code path} — the deduped child shared by equal-typed accessors. {@code null} when no
     * accessor resolves the segment.
     */
    @Nullable
    Value descendSegment(final Scope scope, final Value parent, final List<String> path) {
        final var demand = new DescendView(parent.type(), parent.nullness(), path.get(path.size() - 1), resolver);
        final var childLoc = new SourceLocation(new AccessPath(List.copyOf(path)));
        Value child = null;
        for (final var spec : TargetProducer.dedup(descend(demand, resolveCtx))) {
            final var ports = List.of(new PortBinding(spec.getPorts().get(0), operationLander.reuse(parent)));
            final var output = new AddValue(scope, childLoc, spec.getOutputType(), spec.getOutputNullness());
            final var operation = operationLander.landOperation(spec, ports, output);
            if (child == null) {
                child = graph.outputOf(operation).orElse(null);
            }
        }
        return child;
    }

    /** Every accessor spec the strategy set offers for {@code demand}. */
    List<OperationSpec> descend(final DescendView demand, final ResolveCtx ctx) {
        return strategies.stream()
                .flatMap(strategy -> strategy.descend(demand, ctx))
                .collect(toUnmodifiableList());
    }
}

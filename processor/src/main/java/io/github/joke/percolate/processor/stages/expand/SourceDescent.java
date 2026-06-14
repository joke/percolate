package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.AddOperation;
import io.github.joke.percolate.processor.graph.AddValue;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.PortBinding;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.Value;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Candidate;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Materializes a binding's source access path into the bipartite graph (design source-path-resolution): starting
 * from the method's parameter-root {@code Value} (the single-segment supply root), it resolves each subsequent
 * segment to a unary accessor {@code Operation} via the path-resolver strategies and lands it through the
 * {@link Applier}, returning the deepest source {@code Value} (the bridge candidate) — or {@code null} when the
 * parameter root is absent or a segment is unresolvable. Each accessor's produced {@code Value} is typed from the
 * resolver-discovered member type and nullness.
 */
@RequiredArgsConstructor
final class SourceDescent {

    private final MapperGraph graph;
    private final Applier applier;
    private final List<ExpansionStrategy> generalStrategies;
    private final ResolveCtx resolveCtx;
    private final NullabilityResolver resolver;

    /** Materializes {@code segments} from the param root; returns the deepest source Value, or {@code null}. */
    @Nullable
    Value materialize(final Scope scope, final List<String> segments) {
        if (segments.isEmpty()) {
            return null;
        }
        var prev = findValue(
                scope,
                loc -> loc instanceof SourceLocation
                        && ((SourceLocation) loc).getPath().getSegments().equals(segments.subList(0, 1)));
        if (prev == null) {
            return null;
        }
        for (var depth = 1; depth < segments.size(); depth++) {
            final var spec = resolveAccessor(type(prev), segments.get(depth));
            if (spec == null) {
                return null;
            }
            final var loc = new SourceLocation(new AccessPath(List.copyOf(segments.subList(0, depth + 1))));
            final var output = new AddValue(scope, loc, spec.getOutputType(), spec.getOutputNullness());
            final var port = new PortBinding(
                    spec.getPorts().get(0), new AddValue(scope, prev.getLoc(), type(prev), nullness(prev)));
            applier.apply(
                    graph,
                    new AddOperation(
                            "accessor",
                            spec.getCodegen().getClass().getName(),
                            spec.getCodegen(),
                            spec.getWeight(),
                            spec.isPartial(),
                            List.of(port),
                            output,
                            Optional.empty()));
            prev = graph.valueFor(scope, loc, spec.getOutputType(), spec.getOutputNullness());
        }
        return prev;
    }

    @Nullable
    private OperationSpec resolveAccessor(final TypeMirror parentType, final String segment) {
        final var demand = new DemandView(
                parentType,
                Nullability.NON_NULL,
                Optional.of(BindingDirective.segment(segment)),
                java.util.Set.of(),
                List.of(new Candidate(parentType)),
                resolver);
        return generalStrategies.stream()
                .flatMap(strategy -> strategy.expand(demand, resolveCtx))
                .filter(spec -> spec.getPorts().size() == 1
                        && spec.getChildScope().isEmpty()
                        && resolveCtx.types().isSameType(spec.getPorts().get(0).getType(), parentType)
                        && !resolveCtx.types().isSameType(spec.getOutputType(), parentType))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private Value findValue(final Scope scope, final Predicate<Location> locPredicate) {
        return graph.valuesIn(scope)
                .filter(value -> locPredicate.test(value.getLoc()))
                .findFirst()
                .orElse(null);
    }

    private TypeMirror type(final Value value) {
        return value.getType().orElseThrow(() -> new IllegalStateException("untyped Value: " + value.id()));
    }

    private Nullability nullness(final Value value) {
        return value.getNullness().orElseThrow(() -> new IllegalStateException("unnulled Value: " + value.id()));
    }
}

package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.AddValue;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.Value;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Source-binding lookup for the expansion driver (demand-driven-expansion D4): given a scope, the in-scope source
 * {@link Value} that can feed a demanded {@link Port} — an already-materialised graph source first, else a matching
 * method parameter materialised on demand as a {@code LEAF} through the {@link Applier} (parameters are not
 * pre-seeded). It also exposes the in-scope source <em>types</em> (signature parameters plus discovered graph
 * sources) that grounding-by-match unifies a type-variable port against — never a strategy-facing candidate snapshot
 * (the engine sources inputs). A cohesive collaborator the work-list driver delegates to (mirroring
 * {@link AccessorResolver}), so the driver stays the work-list dispatch + Operation landing site.
 */
@RequiredArgsConstructor
final class SourceCandidates {

    private final MapperGraph graph;
    private final Applier applier;
    private final NullabilityResolver resolver;
    private final ResolveCtx resolveCtx;

    /**
     * The in-scope source <em>types</em> — method parameters plus discovered graph sources — that grounding-by-match
     * unifies a type-variable port against. The same set the port-sourcing reuse draws from, exposed type-only so the
     * driver can ground a template port without the (producer-facing) candidate snapshot.
     */
    List<TypeMirror> sourceTypes(final Scope scope) {
        return Stream.concat(paramTypes(scope), sourceValues(scope).map(this::type))
                .collect(toUnmodifiableList());
    }

    private Stream<TypeMirror> paramTypes(final Scope scope) {
        if (!(scope instanceof MethodScope)) {
            return Stream.empty();
        }
        return ((MethodScope) scope).getMethod().getParameters().stream().map(Element::asType);
    }

    /**
     * The in-scope source Value that can feed {@code port}: an already-materialised graph source first, else a
     * matching method parameter materialised on demand as a {@code LEAF} (parameters are not pre-seeded).
     */
    @Nullable
    Value matchingSource(final Scope scope, final Port port) {
        final var existing = sourceValues(scope)
                .filter(value -> matchesPort(value, port))
                .min(Comparator.comparing(Value::id))
                .orElse(null);
        return existing != null ? existing : matchingParam(scope, port);
    }

    /** Whether {@code value} can feed {@code port}: same type and a non-null source satisfies any nullness. */
    boolean matchesPort(final Value value, final Port port) {
        return matches(type(value), nullness(value), port);
    }

    private @Nullable Value matchingParam(final Scope scope, final Port port) {
        if (!(scope instanceof MethodScope)) {
            return null;
        }
        return ((MethodScope) scope)
                .getMethod().getParameters().stream()
                        .filter(param -> matches(param.asType(), resolver.resolve(param.asType(), param), port))
                        .findFirst()
                        .map(param -> applier.apply(
                                graph,
                                new AddValue(
                                        scope,
                                        new SourceLocation(AccessPath.of(
                                                param.getSimpleName().toString())),
                                        param.asType(),
                                        resolver.resolve(param.asType(), param))))
                        .orElse(null);
    }

    private Stream<Value> sourceValues(final Scope scope) {
        return graph.valuesIn(scope).filter(value -> {
            final var role = value.getLoc().role();
            return role == Location.Role.ACCESS || role == Location.Role.LEAF;
        });
    }

    /** Whether a source of {@code (type, nullness)} can feed {@code port}: same type, non-null satisfies any. */
    private boolean matches(final TypeMirror sourceType, final Nullability sourceNullness, final Port port) {
        final var nullnessClash = port.getNullness() == Nullability.NON_NULL && sourceNullness == Nullability.NULLABLE;
        return !nullnessClash && resolveCtx.types().isSameType(sourceType, port.getType());
    }

    private TypeMirror type(final Value value) {
        return value.getType().orElseThrow(() -> new IllegalStateException("untyped Value: " + value.id()));
    }

    private Nullability nullness(final Value value) {
        return value.getNullness().orElseThrow(() -> new IllegalStateException("unnulled Value: " + value.id()));
    }
}

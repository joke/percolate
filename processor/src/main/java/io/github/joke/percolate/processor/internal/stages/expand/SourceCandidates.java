package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.InputDecl;
import io.github.joke.percolate.processor.internal.graph.Location;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Source-binding lookup for the expansion driver (demand-driven-expansion D4): given a scope, the in-scope source
 * {@link Value} that can feed a demanded {@link Port} — an already-materialised graph source first, else a matching
 * scope input declaration ({@link Scope#inputDecls}) materialised on demand as a {@code LEAF} through the
 * {@link Applier} (inputs are declared, not pre-seeded). It also exposes the in-scope source <em>types</em> (declared
 * inputs plus discovered graph sources) that grounding-by-match unifies a type-variable port against — never a
 * strategy-facing candidate snapshot (the engine sources inputs). The path is uniform across scope kinds: a method
 * parameter and a container element root are both just input declarations, with no {@code instanceof} test. A cohesive
 * collaborator the work-list driver delegates to, so the driver stays the work-list dispatch + Operation landing
 * site.
 */
@RequiredArgsConstructor
final class SourceCandidates {

    private final MapperGraph graph;
    private final Applier applier;
    private final NullabilityResolver resolver;
    private final ResolveCtx resolveCtx;

    /**
     * The in-scope source <em>types</em> — declared inputs plus discovered graph sources — that grounding-by-match
     * unifies a type-variable port against. The declared input types are available without materialising any
     * {@link Value}, so a template port grounds without the (producer-facing) candidate snapshot.
     */
    List<TypeMirror> sourceTypes(final Scope scope) {
        return Stream.concat(
                        scope.inputDecls(resolver::resolve).map(InputDecl::getType),
                        sourceValues(scope).map(this::type))
                .collect(toUnmodifiableList());
    }

    /**
     * The in-scope source Value that can feed {@code port}, ranked: a matching directive-{@code pinnedSource} first
     * (so a same-typed sibling can never shadow it), then an already-materialised graph source of least id, else the
     * first matching scope input declaration materialised on demand as a {@code LEAF} (idempotent through the dedup
     * index). {@code pinnedSource} is {@code null} when the demand carries no directive source path.
     */
    @Nullable
    Value matchingSource(final Scope scope, final Port port, final @Nullable Value pinnedSource) {
        if (pinnedSource != null && matchesPort(pinnedSource, port)) {
            return pinnedSource;
        }
        final var existing = sourceValues(scope)
                .filter(value -> matchesPort(value, port))
                .min(Comparator.comparing(Value::id))
                .orElse(null);
        return existing != null ? existing : materialiseMatchingInput(scope, port);
    }

    /** Whether {@code value} can feed {@code port}: same type and a non-null source satisfies any nullness. */
    boolean matchesPort(final Value value, final Port port) {
        return matches(type(value), nullness(value), port);
    }

    private @Nullable Value materialiseMatchingInput(final Scope scope, final Port port) {
        return scope.inputDecls(resolver::resolve)
                .filter(decl -> matches(decl.getType(), decl.getNullness(), port))
                .findFirst()
                .map(decl -> applier.apply(
                        graph, new AddValue(scope, decl.getLocation(), decl.getType(), decl.getNullness())))
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
        return !nullnessClash && resolveCtx.isSameType(sourceType, port.getType());
    }

    private TypeMirror type(final Value value) {
        return value.getType().orElseThrow(() -> new IllegalStateException("untyped Value: " + value.id()));
    }

    private Nullability nullness(final Value value) {
        return value.getNullness().orElseThrow(() -> new IllegalStateException("unnulled Value: " + value.id()));
    }
}

package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.InputDecl;
import io.github.joke.percolate.processor.internal.graph.Location;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.graph.Visibility;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    private final ResolveCtx resolveCtx;

    /**
     * The in-scope source <em>types</em> — declared inputs plus discovered graph sources — that grounding-by-match
     * unifies a type-variable port against. The declared input types are available without materialising any
     * {@link Value}, so a template port grounds without the (producer-facing) candidate snapshot.
     *
     * <p>Deterministic by construction (graph-expansion "Type-matched source selection SHALL be deterministic"):
     * declared inputs precede discovered graph sources, each in a stable order — {@link Scope#inputDecls} streams
     * an ordered {@code List} in declaration order, and {@link MapperGraph#valuesIn} is sorted by {@link Value#id}.
     * A same-typed pair of parameters is therefore always offered to {@link BindingEnumerator} in declaration
     * order, so grounding-by-match over-emits and the extraction fold prunes ties in that same order.
     */
    List<TypeMirror> sourceTypes(final Scope scope) {
        return Stream.concat(
                        scope.inputDecls().map(InputDecl::getType),
                        sourceValues(scope).map(Value::type))
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
        return pinnedMatch(pinnedSource, port)
                .or(() -> existingMatch(scope, port))
                .orElseGet(() -> materialiseMatchingInput(scope, port));
    }

    Optional<Value> pinnedMatch(final @Nullable Value pinnedSource, final Port port) {
        return Optional.ofNullable(pinnedSource).filter(value -> matchesPort(value, port));
    }

    Optional<Value> existingMatch(final Scope scope, final Port port) {
        return sourceValues(scope).filter(value -> matchesPort(value, port)).min(Comparator.comparing(Value::id));
    }

    /** Whether {@code value} can feed {@code port}: same type and a non-null source satisfies any nullness. */
    boolean matchesPort(final Value value, final Port port) {
        return matches(value.type(), value.nullness(), port);
    }

    /**
     * Deterministic by construction: {@link Scope#inputDecls} streams the scope's input declarations in
     * declaration order (an ordered {@code List}, never a hash-ordered collection), so {@code findFirst()} always
     * selects the earlier-declared match when two declarations — e.g. two same-typed parameters — both fit.
     */
    @Nullable
    Value materialiseMatchingInput(final Scope scope, final Port port) {
        return scope.inputDecls()
                .filter(decl -> matches(decl.getType(), decl.getNullness(), port))
                .findFirst()
                .map(decl -> applier.apply(
                        graph, new AddValue(scope, decl.getLocation(), decl.getType(), decl.getNullness())))
                .orElse(null);
    }

    /**
     * The named {@link Value} feeding a {@code BY_NAME} port: the declaration named {@code port.getBindingName()}
     * in the scope's own declarations, or failing that in the nearest ancestor scope declaring it
     * {@link Visibility#INHERITED} (design D5) — materialised at that declaration's own location, but in the
     * <b>requesting</b> {@code scope}: a {@link Dep} edge never crosses a scope boundary, so a descendant scope
     * consuming an inherited binding gets its own {@code Value} at the same location, not the declaring scope's.
     * {@code null} when the name is unbound anywhere in the chain, or when it is bound but the binding's type is
     * not assignable to the port's declared type (design Decision 2: verified, not encoded into the name). Either
     * failure is reported by {@link PortSourceResolver}'s {@code REQUIRE} handling — the engine's own concern, not
     * this collaborator's.
     */
    @Nullable
    Value byNameSource(final Scope scope, final Port port) {
        return byNameDecl(scope, port.getBindingName())
                .filter(decl -> resolveCtx.isAssignable(decl.getType(), port.getType()))
                .map(decl -> applier.apply(
                        graph, new AddValue(scope, decl.getLocation(), decl.getType(), decl.getNullness())))
                .orElse(null);
    }

    /** The declared type of the binding named {@code port.getBindingName()}, for a REQUIRE-miss mismatch message. */
    Optional<TypeMirror> byNameDeclaredType(final Scope scope, final Port port) {
        return byNameDecl(scope, port.getBindingName()).map(InputDecl::getType);
    }

    /** {@code scope}'s own declaration named {@code name}, else the nearest ancestor's {@code INHERITED} one. */
    Optional<InputDecl> byNameDecl(final Scope scope, final String name) {
        return ownNamedDecl(scope, name).or(() -> inheritedAncestorDecl(scope, name));
    }

    Optional<InputDecl> ownNamedDecl(final Scope scope, final String name) {
        return scope.inputDecls().filter(decl -> decl.getName().equals(name)).findFirst();
    }

    /** Walks {@code scope}'s ancestor chain for the nearest {@code INHERITED} declaration named {@code name}. */
    Optional<InputDecl> inheritedAncestorDecl(final Scope scope, final String name) {
        return scope.parent()
                .flatMap(parent -> ownInheritedDecl(parent, name).or(() -> inheritedAncestorDecl(parent, name)));
    }

    Optional<InputDecl> ownInheritedDecl(final Scope scope, final String name) {
        return scope.inputDecls()
                .filter(decl -> decl.getVisibility() == Visibility.INHERITED
                        && decl.getName().equals(name))
                .findFirst();
    }

    Stream<Value> sourceValues(final Scope scope) {
        return graph.valuesIn(scope).filter(value -> {
            final var role = value.getLoc().role();
            return role == Location.Role.ACCESS || role == Location.Role.LEAF;
        });
    }

    /** Whether a source of {@code (type, nullness)} can feed {@code port}: same type, non-null satisfies any. */
    boolean matches(final TypeMirror sourceType, final Nullability sourceNullness, final Port port) {
        final var nullnessClash = port.getNullness() == Nullability.NON_NULL && sourceNullness == Nullability.NULLABLE;
        return !nullnessClash && resolveCtx.isSameType(sourceType, port.getType());
    }
}
